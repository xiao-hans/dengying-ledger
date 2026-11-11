package com.meow.ledger.shared.manage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupTest {
    private fun repo(): GroupRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return GroupRepository(MeowDatabase(driver))
    }

    @Test
    fun blankShowIdIsRejected() {
        val repo = repo()
        assertNull(repo.bind("   "))
        assertNull(repo.bind("a".repeat(SHOW_ID_MAX + 1)))
        assertTrue(repo.list().isEmpty())
    }

    @Test
    fun sameShowIdKeepsTheFirstRow() {
        val repo = repo()
        val first = repo.bind("team-1")!!
        val again = repo.bind(" team-1 ")!!
        assertEquals(first.id, again.id)
        assertEquals(1, repo.list().size)
        assertEquals(1, first.status)
    }

    @Test
    fun checkMarksVipOnlyAfterAnOrder() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        val db = MeowDatabase(driver)
        val group = GroupRepository(db)
        val missing = group.check("team-1")
        assertEquals(0, missing.isVip)
        assertEquals(0, missing.expireTime)

        group.bind("team-1")
        assertEquals(0, group.check("team-1").isVip)

        AccountRepository(db).createOrder("month")
        val hit = group.check("team-1")
        assertEquals(1, hit.isVip)
        assertTrue(hit.expireTime > 0)
        assertEquals("team-1", hit.showId)
    }
}
