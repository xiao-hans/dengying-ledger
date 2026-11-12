package com.meow.ledger.shared.manage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M10：愿望清单与存钱计划互不混列。 */
class WishTest {

    private fun repo(): PlanRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return PlanRepository(MeowDatabase(driver))
    }

    @Test
    fun addWishCreatesWishTypeAndHiddenFromPlans() {
        val repo = repo()
        val wish = repo.addWish("新手机", 500000)
        assertEquals(WISH_TYPE, wish.type_id)
        assertEquals(1, repo.wishes().size)
        assertTrue(repo.savePlans().none { it.id == wish.id })
    }

    @Test
    fun grantMarksDoneAndKeepsWish() {
        val repo = repo()
        val wish = repo.addWish("旅行", 100000)
        val done = repo.grantSavePlan(wish.id)!!
        assertEquals(SAVE_PLAN_DONE, done.status)
        assertEquals(SAVE_PLAN_DONE, repo.wishes().first { it.id == wish.id }.status)
    }

    @Test
    fun grantMissingReturnsNull() {
        assertNull(repo().grantSavePlan("no-such"))
    }

    @Test
    fun savePlanStaysOutOfWishes() {
        val repo = repo()
        val plan = repo.saveSavePlan("买房", 1000000)!!
        assertEquals(1L, plan.type_id)
        assertTrue(repo.wishes().none { it.id == plan.id })
        assertEquals(1, repo.savePlans().size)
    }
}
