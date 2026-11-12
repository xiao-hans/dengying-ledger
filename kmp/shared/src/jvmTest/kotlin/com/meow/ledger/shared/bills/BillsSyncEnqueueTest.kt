package com.meow.ledger.shared.bills

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.network.EndpointRequest
import com.meow.ledger.shared.network.JsonEnvelope
import com.meow.ledger.shared.network.Envelope
import com.meow.ledger.shared.network.MeowApi
import com.meow.ledger.shared.network.SyncEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 本地优先：仓储写库在先、入队在后；null sync 时纯本地模式不抛错。 */
class BillsSyncEnqueueTest {

    private fun setup(withSync: Boolean): Triple<BillsRepository, MeowDatabase, SyncEngine?> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        val db = MeowDatabase(driver)
        val engine = if (withSync) SyncEngine(db, api = object : MeowApi {
            override suspend fun call(request: EndpointRequest): JsonEnvelope = Envelope(data = JsonNull)
        }) else null
        return Triple(BillsRepository(db, engine), db, engine)
    }

    private fun bill(clientId: String) = Bill(
        member_id = 0, client_id = clientId, money = 1000, refund_money = 0, type = TYPE_EXPENSE,
        exchange = 0, book_id = 1, member_cate_id = 1, currency = "CNY", day = 20261101, desc = "备注",
        not_in_budget = 0, images = "", update_date = 0, create_date = 0, status = 0,
        to_assets_id = 0, from_assets_id = 0, interest = 0, interest_reckon_type = 0, tag_ids = "",
        source = 0, import_id = 0L, repayment_day = 0, local_create_time = 20261101000,
        local_update_time = 20261101000, installment_plan_id = "", installment_num = 0,
        installment_money = 0, installment_interest = 0,
    )

    @Test
    fun `saveWithBalance writes local first then enqueues Bills Create`() {
        val (repo, db, engine) = setup(withSync = true)
        repo.saveWithBalance(bill("c1"))
        assertNotNull(repo.get(0, "c1"))
        assertEquals(1, engine!!.pendingCount())
        val row = db.s2Queries.selectPendingSync(System.currentTimeMillis(), 100L).executeAsList().single()
        assertEquals("Bills/Create", row.path)
        assertEquals("POST", row.method)
        assertEquals("c1", row.client_id)
        assertTrue(row.payload.contains("clientId") && row.payload.contains("c1"))
        assertTrue(row.payload.contains("items"))
    }

    @Test
    fun `editing an existing bill enqueues Bills Update`() {
        val (repo, db, engine) = setup(withSync = true)
        repo.saveWithBalance(bill("c1"))
        repo.saveWithBalance(bill("c1").copy(money = 2500))
        assertEquals(2500L, repo.get(0, "c1")!!.money)
        assertEquals(2, engine!!.pendingCount())
        val paths = db.s2Queries.selectPendingSync(System.currentTimeMillis(), 100L).executeAsList().map { it.path }
        assertEquals(listOf("Bills/Create", "Bills/Update"), paths)
    }

    @Test
    fun `deleteWithBalance writes local first then enqueues Bills Delete`() {
        val (repo, db, engine) = setup(withSync = true)
        repo.saveWithBalance(bill("c1"))
        repo.deleteWithBalance(0, "c1")
        assertEquals(null, repo.get(0, "c1"))
        assertEquals(2, engine!!.pendingCount())
        val paths = db.s2Queries.selectPendingSync(System.currentTimeMillis(), 100L).executeAsList().map { it.path }
        assertEquals(listOf("Bills/Create", "Bills/Delete"), paths)
    }

    @Test
    fun `batchUpdate enqueues full BillItem payload per changed bill`() {
        val (repo, db, engine) = setup(withSync = true)
        repo.save(bill("a"))
        repo.save(bill("b"))
        assertEquals(2, repo.batchUpdate(0, listOf("a", "b", "ghost"), 7L))
        assertEquals(7L, repo.get(0, "a")!!.member_cate_id)
        val rows = db.s2Queries.selectPendingSync(System.currentTimeMillis(), 100L).executeAsList()
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.path == "Bills/Update" && it.method == "POST" })
        // 与 saveWithBalance 同形状的完整 Items<BillItem>，不能只带 memberCateId。
        assertTrue(rows.all { it.payload.contains("items") })
        assertTrue(rows.all { it.payload.contains("clientId") && it.payload.contains("money") })
        assertTrue(rows.all { it.payload.contains("billType") && it.payload.contains("billBookId") })
        assertTrue(rows.all { it.payload.contains("memberCateId") && it.payload.contains("7") })
        assertEquals(setOf("a", "b"), rows.map { it.client_id }.toSet())
        assertEquals(2, engine!!.pendingCount())
    }

    @Test
    fun `batchUpdate payload matches saveWithBalance payload shape`() {
        val (repo, db, _) = setup(withSync = true)
        repo.save(bill("a"))
        repo.saveWithBalance(bill("b"))
        repo.saveWithBalance(bill("b").copy(money = 2500))
        // b 第二次保存是编辑 → saveWithBalance 走 Bills/Update；batchUpdate 也走 Bills/Update。
        repo.batchUpdate(0, listOf("a"), 9L)
        val rows = db.s2Queries.selectPendingSync(System.currentTimeMillis(), 100L).executeAsList()
        val saveRow = rows.single { it.client_id == "b" && it.path == "Bills/Update" }
        val batchRow = rows.single { it.client_id == "a" && it.path == "Bills/Update" }
        val keysOf = { raw: String ->
            kotlinx.serialization.json.Json.decodeFromString(
                kotlinx.serialization.json.JsonObject.serializer(),
                raw,
            )["items"]!!.jsonArray.single().jsonObject.keys
        }
        assertEquals(keysOf(saveRow.payload), keysOf(batchRow.payload), "同一 payload builder，字段集合一致")
    }

    @Test
    fun `null sync keeps repository purely local`() {
        val (repo, db, engine) = setup(withSync = false)
        repo.saveWithBalance(bill("c1"))
        repo.delete(0, "c1")
        assertEquals(null, repo.get(0, "c1"))
        assertEquals(null, engine)
        assertEquals(0, db.s2Queries.selectPendingSync(System.currentTimeMillis(), 100L).executeAsList().size)
    }

    @Test
    fun `flush marks queue done on success`() {
        val (repo, _, engine) = setup(withSync = true)
        repo.saveWithBalance(bill("c1"))
        assertEquals(1, engine!!.pendingCount())
        val (ok, fail) = runBlocking { engine.flush() }
        assertEquals(1 to 0, ok to fail)
        assertEquals(0, engine.pendingCount())
    }
}
