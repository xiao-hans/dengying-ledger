package com.meow.ledger.shared.network

import com.meow.ledger.shared.bills.newClientId
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Sync_queue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** `sync_queue.status`：0 待发送、1 已送达（markSyncDone）、2 已丢弃（markSyncDropped）。 */
private const val STATUS_PENDING = 0L

/**
 * 本地优先的同步引擎。写操作先落本地库再入 `sync_queue`；flush 时逐条送达。
 * 失败按指数退避重试，间隔 = retry² × 30s，上限 30 分钟。
 *
 * - `api` 为 null（纯本地模式）或 [remoteEnabled] 为 false（未配置 `sync_base_url`）
 *   时只入队不发送：账单/设备号/令牌不会离开本机。
 * - 网络异常一律在 flush 内吞掉，不阻塞 UI。
 * - [flushMutex] 把 flush 串行化，避免 `SyncScheduler` 并发触发时把同一条队列项发两遍。
 */
class SyncEngine(
    private val db: MeowDatabase,
    private val api: MeowApi? = null,
    private val json: Json = Json,
    /** 入队后不等待地触发 flush 的作用域；null 时只入队，由调用方自行 flush。 */
    private val scheduler: SyncScheduler? = null,
    /** 远程同步开关；默认 true 便于既有测试，生产由 app 层传 `sync_base_url` 判定。 */
    private val remoteEnabled: () -> Boolean = { true },
) {
    private val flushMutex = Mutex()

    /**
     * 入队一个写操作。返回队列 id。
     * `path` 必须是 `EndpointCatalog` 的 112 条注解路径之一，或 2 个动态 `@Url` id 之一；
     * 清单外的路径（如旧代码里的 `Bills/BatchUpdate`）一律拒绝，避免把幽灵端点送达服务端。
     * 远程同步禁用时**仍然入队**（本地优先、不丢写），只是不发送。
     */
    fun enqueue(path: String, method: String, payload: JsonObject, clientId: String): String {
        require(EndpointRegistry.isKnown(path)) { "path not in EndpointCatalog: $path" }
        val id = "q" + newClientId()
        val now = System.currentTimeMillis()
        db.s2Queries.enqueueSync(
            Sync_queue(
                id = id, path = path, method = method,
                payload = json.encodeToString(JsonObject.serializer(), payload),
                client_id = clientId, status = STATUS_PENDING, retry_count = 0,
                next_attempt = now, create_time = now, update_time = now, error = "",
            )
        )
        scheduler?.onEnqueued()
        return id
    }

    /** 待发送条数（不含已送达 / 已丢弃）。 */
    fun pendingCount(): Int = db.s2Queries.countPending().executeAsOne().toInt()

    /** 应用启动时补发上次没送达的条目。 */
    fun flushOnStart() {
        scheduler?.onEnqueued()
    }

    /**
     * 尝试把到期条目送达。返回 (成功数, 失败数)。
     * 网络失败不抛出——写操作已在本地完成，同步失败静默重试。
     *
     * 用 [flushMutex] 串行化：并发的 `SyncScheduler.onEnqueued` 触发也只会一个个跑，
     * 第二条进来时队列项已被标记完成，不会重复发送。
     */
    suspend fun flush(limit: Int = 20): Pair<Int, Int> = flushMutex.withLock {
        val api = api ?: return@withLock (0 to 0)
        // 未配置自建后端：一条都不发，条目保持 pending，等配置好再补发。
        if (!remoteEnabled()) return@withLock (0 to 0)
        var ok = 0
        var fail = 0
        val items = db.s2Queries.selectPendingSync(System.currentTimeMillis(), limit.toLong()).executeAsList()
        for (item in items) {
            // 老版本可能入队过已下线的路径（如 Bills/BatchUpdate）。清单外路径不重试、
            // 也不发送，直接标成终态「已丢弃」，避免永远卡在队列里。
            if (!EndpointRegistry.isKnown(item.path)) {
                markDropped(item.id, "path not in catalog: ${item.path}")
                continue
            }
            try {
                val payload = json.decodeFromString(JsonObject.serializer(), item.payload)
                api.call(EndpointRequest(path = item.path, method = item.method, body = payload))
                db.s2Queries.markSyncDone(now = System.currentTimeMillis(), id = item.id)
                ok += 1
            } catch (e: Exception) {
                val retry = item.retry_count + 1
                val backoff = (retry * retry * 30_000L).coerceAtMost(30 * 60_000L)
                db.s2Queries.markSyncRetry(
                    id = item.id,
                    nextAttempt = System.currentTimeMillis() + backoff,
                    now = System.currentTimeMillis(),
                    error = (e.message ?: e::class.simpleName ?: "error").take(500),
                )
                fail += 1
            }
        }
        ok to fail
    }

    /** 标成终态并记下丢弃原因，不再出现在 pending 里。 */
    private fun markDropped(id: String, error: String) {
        db.s2Queries.markSyncDropped(
            id = id,
            now = System.currentTimeMillis(),
            error = error.take(500),
        )
    }
}

/**
 * 入队后的异步 flush 触发器：进程级作用域 `Dispatchers.Default`，不等待结果。
 * 每个 SyncEngine 持有自己的作用域，异常不影响 App 其它协程；网络失败由 flush 内部吞掉。
 * 并发触发由 [SyncEngine.flush] 内的 Mutex 串行化，不会重复送达。
 */
class SyncScheduler(private val engine: () -> SyncEngine?) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 每次入队都触发一次 flush；重复触发由 Mutex + 数据库到期状态收敛。 */
    fun onEnqueued() {
        scope.launch {
            try {
                engine()?.flush()
            } catch (e: Exception) {
                // 同步永不阻塞 UI：数据库/协程异常同样只记不抛。
                runCatching { println("SyncScheduler flush failed: ${e.message}") }
            }
        }
    }
}
