package com.meow.ledger.shared.network

import com.meow.ledger.shared.bills.newClientId
import com.meow.ledger.shared.db.MeowDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import com.meow.ledger.shared.db.Sync_queue

/**
 * 本地优先的同步引擎。写操作先入 sync_queue，flush 时逐条送达；
 * 失败按指数退避重试，间隔 = retry^2 × 30s，上限 30 分钟。
 * api 为 null 时（纯本地模式）只入队不发送。
 */
class SyncEngine(
    private val db: MeowDatabase,
    private val api: MeowApi? = null,
    private val json: Json = Json,
) {

    /** 入队一个写操作。返回队列 id。 */
    fun enqueue(path: String, method: String, payload: JsonObject, clientId: String): String {
        val id = "q" + newClientId()
        val now = System.currentTimeMillis()
        db.s2Queries.enqueueSync(
            Sync_queue(
                id = id, path = path, method = method,
                payload = json.encodeToString(JsonObject.serializer(), payload),
                client_id = clientId, status = 0, retry_count = 0,
                next_attempt = now, create_time = now, update_time = now, error = "",
            )
        )
        return id
    }

    /** 批量改分类入队（Bills/BatchUpdate）。返回队列 id。 */
    fun enqueueBatchUpdate(memberId: Long, clientIds: List<String>, cateId: Long): String {
        val payload = buildJsonObject {
            put("member_id", memberId.toString())
            put("cate_id", cateId.toString())
            putJsonArray("client_ids") { clientIds.forEach { add(it) } }
        }
        return enqueue("Bills/BatchUpdate", "POST", payload, clientIds.firstOrNull() ?: "")
    }

    /** 待发送条数。 */
    fun pendingCount(): Int = db.s2Queries.countPending().executeAsOne().toInt()

    /**
     * 尝试把到期条目送达。返回 (成功数, 失败数)。
     * 网络失败不抛出——写操作已在本地完成，同步失败静默重试。
     */
    suspend fun flush(limit: Int = 20): Pair<Int, Int> {
        val api = api ?: return 0 to 0
        var ok = 0
        var fail = 0
        val items = db.s2Queries.selectPendingSync(System.currentTimeMillis(), limit.toLong()).executeAsList()
        for (item in items) {
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
        return ok to fail
    }
}
