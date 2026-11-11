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
        assertEquals("0", repo.remote("ad_enabled"))
        assertTrue(repo.setRemote("ad_enabled", "1"))
        assertEquals("1", repo.remote("ad_enabled"))
    }

    @Test
    fun remoteRejectsBadValues() {
        val repo = repo()
        assertFalse(repo.setRemote("ad_enabled", "2"))
        assertFalse(repo.setRemote("importBillsMaxRow", "很多"))
        assertFalse(repo.setRemote("不存在的开关", "1"))
        assertEquals("5000", repo.remote("importBillsMaxRow"))
        assertTrue(repo.setRemote("min_version", ""))
        assertEquals("", repo.remote("min_version"))
        assertEquals(24, repo.remoteAll().size)
    }

    @Test
    fun refreshLoginIssuesLocalToken() {
        val repo = repo()
        val first = repo.refreshLogin()
        val second = repo.refreshLogin()
        assertTrue(first.startsWith("local_"))
        assertTrue(first != second)
    }
}
