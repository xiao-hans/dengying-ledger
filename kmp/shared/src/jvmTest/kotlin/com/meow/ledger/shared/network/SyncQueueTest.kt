package com.meow.ledger.shared.network

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlinx.serialization.json.buildJsonObject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S2 业务层：sync_queue 入队、完成、重试与退避。 */
class SyncQueueTest {

    private fun engine(api: MeowApi? = null): Pair<SyncEngine, MeowDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        val db = MeowDatabase(driver)
        return SyncEngine(db, api) to db
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

        // 到期时间已推后，当前时间点查不到，但用未来的时间能查到。
        assertEquals(0, db.s2Queries.selectPendingSync(System.currentTimeMillis(), 100L).executeAsList().size)
        assertEquals(1, db.s2Queries.selectPendingSync(afterFirst.next_attempt, 100L).executeAsList().size)
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
        engine.enqueue("Bills/BatchUpdate", "POST", buildJsonObject {
            put("member_id", "0")
            put("cate_id", "7")
        }, "c2")
        runBlocking { engine.flush() }
        assertEquals(1, sent.size)
        assertEquals("Bills/BatchUpdate", sent.single().path)
        assertEquals("POST", sent.single().method)
    }

    @Test
    fun batchUpdateEnqueueCarriesIdsAndCate() {
        val (engine, db) = engine(api = null)
        val id = engine.enqueueBatchUpdate(0L, listOf("q1", "q2"), cateId = 7L)
        val row = db.s2Queries.selectPendingSync(System.currentTimeMillis(), 100L).executeAsList()
            .first { it.id == id }
        assertEquals("Bills/BatchUpdate", row.path)
        assertTrue(row.payload.contains("q1") && row.payload.contains("q2"))
        assertTrue(row.payload.contains("cate_id"))
    }
}
