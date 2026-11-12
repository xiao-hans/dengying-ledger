package com.meow.ledger.shared.manage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.bills.BillsRepository
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S8 后续：到期周期账自动入账（source=5），幂等可重入。 */
class FixedGenerateTest {

    private fun repo(): Triple<FixedBillRepository, BillsRepository, MeowDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        val db = MeowDatabase(driver)
        return Triple(FixedBillRepository(db), BillsRepository(db), db)
    }

    @Test
    fun monthlyGeneratesOncePerPeriodAndIsIdempotent() {
        val (fixed, bills, db) = repo()
        fixed.save(desc = "房租", money = 150000L, billType = 1, fixedType = CYCLE_MONTH.toLong(), fixedDay = 1)
        val fb = fixed.all().first()
        db.fixedQueries.upsert(fb.copy(next_execute_date = ymdToMillis(2026, 11, 1)))
        val now = ymdToMillis(2026, 11, 15)
        assertEquals(1, fixed.generateDueBills(now))
        val row = bills.get(0, "fixed-${fb.id}-20261101")!!
        assertEquals(150000L, row.money)
        assertEquals(5L, row.source)
        assertEquals(20261101L, row.day)
        // 重入：不重复生成
        assertEquals(0, fixed.generateDueBills(now))
        // next 推进到下月 1 日
        assertEquals(ymdToMillis(2026, 12, 1), fixed.all().first().next_execute_date)
    }

    @Test
    fun catchUpGeneratesEveryMissedPeriod() {
        val (fixed, bills, db) = repo()
        fixed.save(desc = "会员", money = 1500L, billType = 1, fixedType = CYCLE_MONTH.toLong(), fixedDay = 1)
        val fb = fixed.all().first()
        db.fixedQueries.upsert(fb.copy(next_execute_date = ymdToMillis(2026, 10, 1)))
        assertEquals(2, fixed.generateDueBills(ymdToMillis(2026, 11, 15)))
        assertTrue(bills.get(0, "fixed-${fb.id}-20261001") != null)
        assertTrue(bills.get(0, "fixed-${fb.id}-20261101") != null)
        assertEquals(ymdToMillis(2026, 12, 1), fixed.all().first().next_execute_date)
    }

    @Test
    fun notDueYetGeneratesNothing() {
        val (fixed, bills, db) = repo()
        fixed.save(desc = "房租", money = 150000L, billType = 1, fixedType = CYCLE_MONTH.toLong(), fixedDay = 1)
        val fb = fixed.all().first()
        db.fixedQueries.upsert(fb.copy(next_execute_date = ymdToMillis(2026, 12, 1)))
        assertEquals(0, fixed.generateDueBills(ymdToMillis(2026, 11, 15)))
        assertEquals(0, bills.page(1, 50).size)
    }

    @Test
    fun weeklyAdvancesSevenDays() {
        val (fixed, bills, db) = repo()
        fixed.save(desc = "健身", money = 3000L, billType = 1, fixedType = CYCLE_WEEK.toLong(), fixedDay = 1)
        val fb = fixed.all().first()
        db.fixedQueries.upsert(fb.copy(next_execute_date = ymdToMillis(2026, 11, 2)))
        assertEquals(2, fixed.generateDueBills(ymdToMillis(2026, 11, 15))) // 11-02 与 11-09 两期
        assertTrue(bills.get(0, "fixed-${fb.id}-20261102") != null)
        assertTrue(bills.get(0, "fixed-${fb.id}-20261109") != null)
        assertEquals(ymdToMillis(2026, 11, 16), fixed.all().first().next_execute_date)
    }

    @Test
    fun expenseDeductsAssetBalance() {
        val (fixed, _, db) = repo()
        db.assetQueries.upsert(
            com.meow.ledger.shared.db.Assets(
                id = 1, type_id = 1, type = 1, type_name = "bank", type_title = "储蓄卡",
                name = "卡1", desc = "", icon = "", currency = "CNY",
                create_date = 0, update_date = 0, credit_line = 0, repayment_date = 0,
                statement_date = 0, is_remind = 0, is_total_assets = 1, is_next_bills = 0,
                status = 1, balance_cents = 1000000,
            ),
        )
        val before = 1000000L
        fixed.save(desc = "房租", money = 150000L, billType = 1, fixedType = CYCLE_MONTH.toLong(), fixedDay = 1)
        val fb = fixed.all().first()
        db.fixedQueries.upsert(fb.copy(next_execute_date = ymdToMillis(2026, 11, 1)))
        fixed.generateDueBills(ymdToMillis(2026, 11, 15))
        val after = db.assetQueries.selectAll().executeAsList().first().balance_cents
        assertEquals(before - 150000L, after)
        // 埋点跟随 saveWithBalance 落库
        assertTrue(AccountRepository(db).events("ac_add_bill").isNotEmpty())
    }
}
