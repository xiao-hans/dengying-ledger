package com.meow.ledger.shared.auto

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 待确认队列的批量通过 / 批量忽略 / 修改后确认（交互纪律 §6.7）：
 * 只有显式调用 confirm/confirmAll 才写账单，capture 绝不入账。
 */
class AutoBatchConfirmTest {
    private fun setup(): Pair<MeowDatabase, AutoRepository> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        val db = MeowDatabase(driver)
        return db to AutoRepository(db)
    }

    private fun bills(db: MeowDatabase) = db.billQueries.selectAll().executeAsList()

    @Test
    fun captureOnlyQueuesAndNeverWritesBills() {
        val (db, repo) = setup()
        repeat(3) { i -> repo.capture("在 商户$i 消费 ¥${10 + i}.50", "notification", occurredAt = 1_700_000_000_000L + i * 120_000) }
        assertEquals(3, repo.pending().size)
        assertEquals(0, bills(db).size)
    }

    @Test
    fun confirmAllWritesOneBillPerSelectedPendingId() {
        val (db, repo) = setup()
        repeat(3) { i -> repo.capture("在 商户$i 消费 ¥${10 + i}.50", "notification", occurredAt = 1_700_000_000_000L + i * 120_000) }
        val ids = repo.pending().map { it.id }

        val count = repo.confirmAll(ids.take(2))

        assertEquals(2, count)
        assertEquals(2, bills(db).size)
        assertEquals(1, repo.pending().size, "未选中的仍留在待确认队列")
    }

    @Test
    fun confirmAllAppliesPerIdOverrides() {
        val (db, repo) = setup()
        repo.capture("在 瑞幸咖啡 消费 ¥23.50", "notification", occurredAt = 1_700_000_000_000L)
        val event = repo.pending().single()
        val edit = PendingEdit(money = 9900L, desc = "请同事喝咖啡", memberCateId = 8L)

        repo.confirmAll(setOf(event.id), mapOf(event.id to edit))

        val bill = bills(db).single()
        assertEquals(9900L, bill.money)
        assertEquals("请同事喝咖啡", bill.desc)
        assertEquals(8L, bill.member_cate_id)
        assertEquals("auto-${event.hash}", bill.client_id)
        assertEquals(0, repo.pending().size)
    }

    @Test
    fun confirmWithBlankDescFallsBackToMerchant() {
        val (db, repo) = setup()
        repo.capture("在 盒马鲜生 消费 ¥128.40", "notification", occurredAt = 1_700_000_000_000L)
        val event = repo.pending().single()

        repo.confirm(event, edit = PendingEdit(event.money, "", 9L))

        assertEquals("盒马鲜生", bills(db).single().desc)
    }

    @Test
    fun ignoreAllMarksOnlySelectedWithoutWritingBills() {
        val (db, repo) = setup()
        repeat(3) { i -> repo.capture("在 商户$i 消费 ¥${10 + i}.50", "notification", occurredAt = 1_700_000_000_000L + i * 120_000) }
        val all = repo.pending()
        val ids = all.take(2).map { it.id }.toSet()

        val count = repo.ignoreAll(ids)

        assertEquals(2, count)
        assertEquals(0, bills(db).size)
        assertEquals(1, repo.pending().size)
        assertEquals(all.last().id, repo.pending().single().id)
    }

    @Test
    fun batchOperationsSkipAlreadyHandledIdsAndDuplicates() {
        val (db, repo) = setup()
        repo.capture("在 瑞幸咖啡 消费 ¥23.50", "notification", occurredAt = 1_700_000_000_000L)
        val id = repo.pending().single().id

        repo.confirmAll(listOf(id, id, "missing-id"))
        // 再对同一个 id 批量通过/忽略都不生效，幂等且不会重复写账单。
        assertEquals(0, repo.confirmAll(setOf(id)))
        assertEquals(0, repo.ignoreAll(setOf(id)))

        assertEquals(1, bills(db).size)
        assertNull(repo.pendingById(id))
    }

    @Test
    fun pendingByIdReturnsNullForConfirmedEvent() {
        val (_, repo) = setup()
        repo.capture("在 瑞幸咖啡 消费 ¥23.50", "notification", occurredAt = 1_700_000_000_000L)
        val id = repo.pending().single().id
        repo.ignoreAll(setOf(id))
        assertNull(repo.pendingById(id))
    }
}
