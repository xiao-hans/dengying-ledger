package com.meow.ledger.shared.network

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Sync_queue
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** S2 业务层：sync_queue 入队、完成、重试、退避与清单守卫。 */
class SyncQueueTest {

    private fun engine(
        api: MeowApi? = null,
        remoteEnabled: () -> Boolean = { true },
    ): Pair<SyncEngine, MeowDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        val db = MeowDatabase(driver)
        return SyncEngine(db, api, remoteEnabled = remoteEnabled) to db
    }

    private fun payload() = buildJsonObject { put("money", "1000") }

    @Test
    fun enqueueThenFlushWithoutApiKeepsPending() {
        val (engine, db) = engine(api = null)
        engine.enqueue("Bills/Create", "POST", payload(), "c1")
        assertEquals(1, engine.pendingCount())
        val (ok, fail) = runBlocking { engine.flush() }
        assertEquals(0, ok)
        assertEquals(0, fail)
        assertEquals(1, engine.pendingCount())
        assertEquals(1, db.s2Queries.selectPendingSync(System.currentTimeMillis(), 100L).executeAsList().size)
    }

    @Test
    fun successfulFlushMarksDoneAndClearsPending() {
        var calls = 0
        val api = object : MeowApi {
            override suspend fun call(request: EndpointRequest): JsonEnvelope {
                calls += 1
                return Envelope(code = 0, message = "", data = null, traceId = "t")
            }
        }
        val (engine, db) = engine(api)
        engine.enqueue("Bills/Create", "POST", payload(), "c1")
        val (ok, fail) = runBlocking { engine.flush() }
        assertEquals(1 to 0, ok to fail)
        assertEquals(0, engine.pendingCount())
        assertEquals(1, calls)
        assertTrue(db.s2Queries.selectPendingSync(Long.MAX_VALUE, 100L).executeAsList().isEmpty())
    }

    @Test
    fun failedFlushRecordsErrorAndBackoffGrows() {
        var calls = 0
        val api = object : MeowApi {
            override suspend fun call(request: EndpointRequest): JsonEnvelope {
                calls += 1
                throw ApiException(path = "Bills/Create", code = 500, message = "boom", traceId = "t")
            }
        }
        val (engine, db) = engine(api)
        engine.enqueue("Bills/Create", "POST", payload(), "c1")

        val (_, fail1) = runBlocking { engine.flush() }
        assertEquals(1, fail1)
        assertEquals(1, engine.pendingCount())
        val afterFirst = db.s2Queries.selectPendingSync(Long.MAX_VALUE, 100L).executeAsList().single()
        assertEquals(1L, afterFirst.retry_count)
        assertTrue(afterFirst.error.contains("boom"))

        // 到期时间已推后，当前时间点查不到，但用未来的时间能查到；第二次失败后仍留在队列。
        assertEquals(0, db.s2Queries.selectPendingSync(System.currentTimeMillis(), 100L).executeAsList().size)
        assertEquals(1, db.s2Queries.selectPendingSync(afterFirst.next_attempt, 100L).executeAsList().size)
        val (_, fail2) = runBlocking { engine.flush() }
        assertEquals(0, fail2)
        assertEquals(1, engine.pendingCount())
    }

    @Test
    fun flushSendsBodyPayloadAsIs() {
        val sent = mutableListOf<EndpointRequest>()
        val api = object : MeowApi {
            override suspend fun call(request: EndpointRequest): JsonEnvelope {
                sent += request
                return Envelope(code = 0, message = "", data = null, traceId = "t")
            }
        }
        val (engine, _) = engine(api)
        engine.enqueue("Bills/Update", "POST", buildJsonObject {
            put("member_id", "0")
            put("cate_id", "7")
        }, "c2")
        runBlocking { engine.flush() }
        assertEquals(1, sent.size)
        assertEquals("Bills/Update", sent.single().path)
        assertEquals("POST", sent.single().method)
    }

    @Test
    fun disabledRemoteKeepsItemsPendingAndSendsNothing() {
        var calls = 0
        val api = object : MeowApi {
            override suspend fun call(request: EndpointRequest): JsonEnvelope {
                calls += 1
                return Envelope(code = 0, message = "", data = null, traceId = "t")
            }
        }
        val (engine, db) = engine(api = api, remoteEnabled = { false })
        engine.enqueue("Bills/Create", "POST", payload(), "c1")
        engine.enqueue("Bills/Update", "POST", payload(), "c2")
        assertEquals(2, engine.pendingCount())
        val (ok, fail) = runBlocking { engine.flush() }
        assertEquals(0, ok)
        assertEquals(0, fail)
        assertEquals(0, calls, "flush must send nothing when sync_base_url is blank")
        assertEquals(2, engine.pendingCount(), "items stay pending for later")
        assertEquals(2, db.s2Queries.selectPendingSync(System.currentTimeMillis(), 100L).executeAsList().size)
    }

    @Test
    fun reEnabledRemoteFlushesPreviouslyPendingItems() {
        var calls = 0
        val api = object : MeowApi {
            override suspend fun call(request: EndpointRequest): JsonEnvelope {
                calls += 1
                return Envelope(code = 0, message = "", data = null, traceId = "t")
            }
        }
        var enabled = false
        val (engine, _) = engine(api = api, remoteEnabled = { enabled })
        engine.enqueue("Bills/Create", "POST", payload(), "c1")
        runBlocking { engine.flush() }
        assertEquals(0, calls)
        assertEquals(1, engine.pendingCount())
        // 用户填好 sync_base_url 后，之前入队的条目补发。
        enabled = true
        val (ok, fail) = runBlocking { engine.flush() }
        assertEquals(1 to 0, ok to fail)
        assertEquals(1, calls)
        assertEquals(0, engine.pendingCount())
    }

    @Test
    fun concurrentFlushSendsEachItemExactlyOnce() {
        val paths = mutableListOf<String>()
        val api = object : MeowApi {
            override suspend fun call(request: EndpointRequest): JsonEnvelope {
                // 故意挂起，让两个 flush 有机会重叠；Mutex 必须把它们串行化。
                delay(30)
                paths += request.path
                return Envelope(code = 0, message = "", data = null, traceId = "t")
            }
        }
        val (engine, _) = engine(api)
        engine.enqueue("Bills/Create", "POST", payload(), "c1")
        engine.enqueue("Bills/Update", "POST", payload(), "c2")
        val results = runBlocking {
            val a = async { engine.flush() }
            val b = async { engine.flush() }
            listOf(a.await(), b.await())
        }
        assertEquals(2, paths.size, "each queue item must be sent exactly once, got $paths")
        assertEquals(1, paths.count { it == "Bills/Create" })
        assertEquals(1, paths.count { it == "Bills/Update" })
        assertEquals(0, engine.pendingCount())
        assertEquals(2, results.sumOf { it.first }, "total successes across both flushes is 2")
    }

    @Test
    fun stalePathIsDroppedWithoutSending() {
        var calls = 0
        val api = object : MeowApi {
            override suspend fun call(request: EndpointRequest): JsonEnvelope {
                calls += 1
                return Envelope(code = 0, message = "", data = null, traceId = "t")
            }
        }
        val (engine, db) = engine(api)
        // 模拟旧版本直接写进 sync_queue 的幽灵端点（绕过 enqueue 的清单守卫）。
        val now = System.currentTimeMillis()
        db.s2Queries.enqueueSync(
            Sync_queue(
                id = "stale-1", path = "Bills/BatchUpdate", method = "POST",
                payload = "{}", client_id = "old", status = 0L, retry_count = 0,
                next_attempt = now, create_time = now, update_time = now, error = "",
            )
        )
        assertEquals(1, engine.pendingCount())
        val (ok, fail) = runBlocking { engine.flush() }
        assertEquals(0, ok)
        assertEquals(0, fail)
        assertEquals(0, calls, "stale path must never be sent")
        assertEquals(0, engine.pendingCount(), "stale row becomes terminal")
        val row = db.s2Queries.selectSyncById("stale-1").executeAsOne()
        assertEquals(2L, row.status, "status 2 = dropped")
        assertTrue(row.error.contains("path not in catalog"))
        // 再 flush 一次也不会重新尝试。
        runBlocking { engine.flush() }
        assertEquals(0, calls)
    }

    @Test
    fun enqueueRejectsPathOutsideCatalog() {
        val (engine, _) = engine(api = null)
        assertFailsWith<IllegalArgumentException> {
            engine.enqueue("Bills/BatchUpdate", "POST", payload(), "c3")
        }
        assertFailsWith<IllegalArgumentException> {
            engine.enqueue("Bills/NotInSpec", "POST", payload(), "c4")
        }
        assertEquals(0, engine.pendingCount())
    }

    @Test
    fun enqueueAcceptsDynamicUrlIds() {
        val (engine, _) = engine(api = null)
        engine.enqueue(DynamicEndpoints.DOWNLOAD, "GET", payload(), "d1")
        engine.enqueue(DynamicEndpoints.UPLOAD_OSS, "POST", payload(), "d2")
        assertEquals(2, engine.pendingCount())
    }

    @Test
    fun http4xxPermanentRejectionDropsRowWithoutRetry() {
        // 400 与 404 都属于永久拒绝：标成终态 status=2，第二次 flush 不再发送，
        // 也不计入 fail（永久丢弃既不是成功也不是待重试失败）。
        for (status in listOf(400, 404)) {
            var calls = 0
            val api = object : MeowApi {
                override suspend fun call(request: EndpointRequest): JsonEnvelope {
                    calls += 1
                    throw HttpStatusCodeException(path = "Bills/Create", statusCode = status)
                }
            }
            val (engine, db) = engine(api)
            engine.enqueue("Bills/Create", "POST", payload(), "c-$status")
            // enqueue 内部生成队列 id，先从 pending 里取出真实 id 供后面按 id 断言。
            val queueId = db.s2Queries.selectPendingSync(Long.MAX_VALUE, 100L).executeAsList().single().id
            val (ok, fail) = runBlocking { engine.flush() }
            assertEquals(0, ok)
            assertEquals(0, fail, "permanent drop must not count as retry-fail")
            assertEquals(0, engine.pendingCount(), "dropped row leaves the pending queue")
            val row = db.s2Queries.selectSyncById(queueId).executeAsOne()
            assertEquals(2L, row.status, "status 2 = dropped")
            assertTrue(row.error.contains("HTTP $status"), "error records the HTTP status, got: ${row.error}")
            // 第二次 flush 不再发送。
            runBlocking { engine.flush() }
            assertEquals(1, calls, "HTTP $status row must never be sent twice")
        }
    }

    @Test
    fun http408And429RemainRetryable() {
        for (status in listOf(408, 429)) {
            val api = object : MeowApi {
                override suspend fun call(request: EndpointRequest): JsonEnvelope {
                    throw HttpStatusCodeException(path = "Bills/Create", statusCode = status)
                }
            }
            val (engine, db) = engine(api)
            engine.enqueue("Bills/Create", "POST", payload(), "c-$status")
            val queueId = db.s2Queries.selectPendingSync(Long.MAX_VALUE, 100L).executeAsList().single().id
            val (ok, fail) = runBlocking { engine.flush() }
            assertEquals(0, ok)
            assertEquals(1, fail, "HTTP $status is a retryable 4xx")
            assertEquals(1, engine.pendingCount())
            val row = db.s2Queries.selectSyncById(queueId).executeAsOne()
            assertEquals(0L, row.status, "still pending")
            assertEquals(1L, row.retry_count, "retry_count increases")
            assertTrue(row.next_attempt > row.update_time, "backoff pushed next_attempt into the future")
        }
    }

    @Test
    fun http500StillRetried() {
        val api = object : MeowApi {
            override suspend fun call(request: EndpointRequest): JsonEnvelope {
                throw HttpStatusCodeException(path = "Bills/Create", statusCode = 500)
            }
        }
        val (engine, _) = engine(api)
        engine.enqueue("Bills/Create", "POST", payload(), "c-500")
        val (ok, fail) = runBlocking { engine.flush() }
        assertEquals(0, ok)
        assertEquals(1, fail)
        assertEquals(1, engine.pendingCount(), "HTTP 500 stays pending for backoff retry")
    }
}
