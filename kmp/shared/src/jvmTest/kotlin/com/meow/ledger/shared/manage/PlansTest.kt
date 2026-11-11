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
}
