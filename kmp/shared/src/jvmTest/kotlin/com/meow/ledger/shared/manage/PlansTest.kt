package com.meow.ledger.shared.manage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlansTest {
    private fun repo(): PlanRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return PlanRepository(MeowDatabase(driver))
    }

    @Test
    fun createSplitsIntoPeriods() {
        val repo = repo()
        val plan = repo.createInstallment("手机", 1200, count = 12)
        assertEquals(12, repo.installmentItems(plan.id).size)
        assertEquals(100, repo.installmentItems(plan.id).first().money)
        assertEquals(INSTALLMENT_ACTIVE, plan.status)
    }

    @Test
    fun payOffClearsTheBalance() {
        val repo = repo()
        val plan = repo.createInstallment("手机", 1200)
        val done = repo.payOff(plan.id)!!
        assertEquals(INSTALLMENT_PAID_OFF, done.status)
        assertEquals(0, done.remaining_money)
    }

    @Test
    fun deleteKeepsNothing() {
        val repo = repo()
        val plan = repo.createInstallment("手机", 1200, count = 3)
        repo.deleteInstallment(plan.id)
        assertNull(repo.installment(plan.id))
        assertTrue(repo.installmentItems(plan.id).isEmpty())
    }

    @Test
    fun savingAddsUp() {
        val repo = repo()
        val plan = repo.saveSavePlan("旅行", 10000)!!
        repo.addSaveItem(plan.id, 3000)
        repo.addSaveItem(plan.id, 2000)
        assertEquals(5000, repo.savePlan(plan.id)!!.saved_money)
        assertEquals(2, repo.saveItems(plan.id).size)
    }

    @Test
    fun archiveHidesThePlan() {
        val repo = repo()
        val plan = repo.saveSavePlan("旅行", 10000)!!
        repo.archiveSavePlan(plan.id)
        assertTrue(repo.savePlans().isEmpty())
        assertEquals(1, repo.savePlan(plan.id)!!.archived)
    }

    @Test
    fun editingAMissingPlanFails() {
        assertNull(repo().saveSavePlan("旅行", 10000, id = "missing"))
    }


    @Test
    fun payOffZeroesIouAsset() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        val db = MeowDatabase(driver)
        val repo = PlanRepository(db)
        fun asset(id: Long, type: Long, balance: Long) = com.meow.ledger.shared.db.Assets(
            id = id, type_id = type, type = type, type_name = "t", type_title = "T",
            name = "A$id", desc = "", icon = "", currency = "CNY",
            create_date = 0, update_date = 0, credit_line = 0, repayment_date = 0,
            statement_date = 0, is_remind = 0, is_total_assets = 1, is_next_bills = 0,
            status = 1, balance_cents = balance,
        )
        db.assetQueries.upsert(asset(6, IOU_TYPE, 50000))
        db.assetQueries.upsert(asset(2, 1L, 70000))
        val plan = repo.createInstallment("手机分期", 50000, count = 3, deductionAssetId = 6)
        assertEquals(6L, plan.deduction_asset_id)
        repo.payOff(plan.id)
        assertEquals(0L, db.assetQueries.selectOne(6).executeAsOneOrNull()!!.balance_cents)
        val plan2 = repo.createInstallment("电脑分期", 30000, count = 3, deductionAssetId = 2)
        repo.payOff(plan2.id)
        assertEquals(70000L, db.assetQueries.selectOne(2).executeAsOneOrNull()!!.balance_cents)
    }
}
