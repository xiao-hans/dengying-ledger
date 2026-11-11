package com.meow.ledger.shared.manage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountTest {
    private fun repo(): AccountRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return AccountRepository(MeowDatabase(driver))
    }

    @Test
    fun loginNeedsARealCode() {
        val repo = repo()
        assertNull(repo.sendCode("123"))
        val code = repo.sendCode("13800000000")!!
        assertNull(repo.loginByPhone("13800000000", "000000"))
        assertEquals("phone", repo.loginByPhone("13800000000", code)!!.login_type)
    }

    @Test
    fun unbindKeepsTheLogin() {
        val repo = repo()
        repo.loginByDevice()
        repo.unbind()
        assertEquals("device", repo.profile().login_type)
        assertEquals("", repo.profile().bind_type)
    }

    @Test
    fun changePhoneNeedsBothCodes() {
        val repo = repo()
        val oldCode = repo.sendCode("13800000000")!!
        repo.loginByPhone("13800000000", oldCode)
        val newCode = repo.sendCode("13900000000")!!

        assertNull(repo.changePhone(oldCode, "13900000000", "000000"))
        assertEquals("13800000000", repo.profile().phone)

        assertNull(repo.changePhone("000000", "13900000000", newCode))
        assertNull(repo.changePhone(oldCode, "13800000000", newCode))
        assertNull(repo.changePhone(oldCode, "123", newCode))

        val changed = repo.changePhone(oldCode, "13900000000", newCode)
        assertEquals("13900000000", changed!!.phone)
        assertEquals("phone", changed.bind_type)
    }

    @Test
    fun changePhoneWithoutABoundPhoneDoesNothing() {
        val repo = repo()
        val code = repo.sendCode("13900000000")!!
        assertNull(repo.changePhone("000000", "13900000000", code))
        assertEquals("", repo.profile().phone)
    }

    @Test
    fun migrationRunsOnceAndDeletesNothing() {
        val repo = repo()
        assertFalse(repo.migration().done)

        val first = repo.migrateOldUser(1_000)
        assertFalse(first.already)
        assertTrue(first.done)
        assertEquals(1_000, first.timeMillis)

        val second = repo.migrateOldUser(9_000)
        assertTrue(second.already)
        assertEquals(1_000, repo.migration().timeMillis)
    }

    @Test
    fun blankNicknameIsRejected() {
        assertNull(repo().updateNickname("   "))
    }

    @Test
    fun deletingAnImportKeepsTheRecordGone() {
        val repo = repo()
        val record = repo.recordImport("支付宝.csv", 3, "")
        repo.deleteImport(record.id)
        assertTrue(repo.imports().isEmpty())
    }

    @Test
    fun orderExtendsVip() {
        val repo = repo()
        assertNull(repo.createOrder("nope"))
        val order = repo.createOrder("month")!!
        assertEquals(1, order.status)
        assertEquals(1, repo.profile().vip)
        assertTrue(repo.profile().vip_expire > 0)
    }

    @Test
    fun unsignNeedsAnActiveContract() {
        val repo = repo()
        assertFalse(repo.unsignContract())
        repo.signContract("支付宝", "月卡", 600, "2026-12-01")
        assertTrue(repo.unsignContract())
        assertEquals(0, repo.contract().signed)
    }

    @Test
    fun shortFeedbackIsRejected() {
        val repo = repo()
        assertNull(repo.addFeedback("太短"))
        assertNotNull(repo.addFeedback("这里有五个字以上"))
        assertEquals(1, repo.feedback().size)
    }

    @Test
    fun remoteSwitchFallsBackToDefault() {
        val repo = repo()
        assertEquals("1", repo.remote("auto_bill_enabled"))
        repo.setRemote("auto_bill_enabled", "0")
        assertEquals("0", repo.remote("auto_bill_enabled"))
    }
}
