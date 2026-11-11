package com.meow.ledger.shared.manage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FixedBillsTest {
    private fun repo(): FixedBillRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return FixedBillRepository(MeowDatabase(driver))
    }

    @Test
    fun saveAndEdit() {
        val repo = repo()
        val created = repo.save("房租", 350000, fixedDay = 5)!!
        assertEquals("房租", created.desc)
        assertEquals(5, created.fixed_day)
        assertEquals(1, created.status)
        val edited = repo.save("房租", 360000, fixedDay = 6, notInBudget = true, id = created.id)!!
        assertEquals(1, repo.all().size)
        assertEquals(360000, edited.money)
        assertEquals(1, edited.not_in_budget)
    }

    @Test
    fun dayIsClampedAndBlankNameFallsBack() {
        val repo = repo()
        val row = repo.save("", 100, fixedDay = 99)!!
        assertEquals("周期账", row.desc)
        assertEquals(31, row.fixed_day)
    }

    @Test
    fun missingIdIsRejected() {
        assertNull(repo().save("房租", 100, id = "missing"))
    }

    @Test
    fun deleteKeepsOthers() {
        val repo = repo()
        val first = repo.save("房租", 100)!!
        repo.save("会员费", 200)
        repo.delete(first.id)
        assertEquals(listOf("会员费"), repo.all().map { it.desc })
    }

    @Test
    fun reminderRejectsBlankAndFiltersByBill() {
        val repo = repo()
        assertNull(repo.saveReminder("  "))
        val saved = repo.saveReminder("交房租", day = 3, hour = 25, fixedBillId = "bill-1")!!
        repo.saveReminder("其他", fixedBillId = "bill-2")
        assertEquals(23, saved.hour)
        assertEquals(listOf("交房租"), repo.remindersOf("bill-1").map { it.title })
    }

    @Test
    fun dueListsReachedRemindersOnly() {
        val repo = repo()
        repo.saveReminder("已到", day = 1, hour = 0)
        repo.saveReminder("未到", day = 28, hour = 23)
        val due = repo.due(millisOf(2026, 6, 15, 10))
        assertEquals(listOf("已到"), due.map { it.title })
    }

    @Test
    fun calendarMath() {
        assertEquals(1, dayOfMonth(millisOf(1970, 1, 1, 0)))
        assertEquals(29, dayOfMonth(millisOf(2024, 2, 29, 8)))
        assertEquals(15, dayOfMonth(millisOf(2026, 6, 15, 10)))
        assertEquals(10, hourOfDay(millisOf(2026, 6, 15, 10)))
        assertTrue(dayOfMonth(millisOf(2025, 12, 31, 23)) == 31L)
    }

    /** 公历日期换算成 UTC 毫秒，和 dayOfMonth 互为逆运算。 */
    private fun millisOf(year: Int, month: Int, day: Int, hour: Int): Long {
        var days = 0L
        for (y in 1970 until year) days += if (leap(y)) 366 else 365
        val lengths = intArrayOf(31, if (leap(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        for (m in 1 until month) days += lengths[m - 1]
        days += day - 1
        return (days * 24 + hour) * 3_600_000
    }

    private fun leap(year: Int) = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
}
