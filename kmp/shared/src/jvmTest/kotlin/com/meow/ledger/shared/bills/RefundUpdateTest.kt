package com.meow.ledger.shared.bills

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Refund_bill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** M4：退款编辑。合计退款不能超过原账单金额，排除本条自身。 */
class RefundUpdateTest {

    private fun repo(): RefundRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return RefundRepository(MeowDatabase(driver))
    }

    @Test
    fun updateChangesMoney() {
        val repo = repo()
        val first = repo.add("bill-1", 10000, 3000, 20261112)
        val updated = repo.update(first, 5000, 10000)
        assertEquals(5000, updated.money)
        assertEquals(5000, repo.ofBill("bill-1").first().money)
    }

    @Test
    fun updateCountsOtherRefundsButNotItself() {
        val repo = repo()
        val first = repo.add("bill-1", 10000, 6000, 20261112)
        val second = repo.add("bill-1", 10000, 2000, 20261112)
        // 6000 + 5000 > 10000：超出
        assertFailsWith<RefundTooLarge> { repo.update(second, 5000, 10000) }
        // 本条 2000 改 4000：6000 + 4000 = 10000，恰好不超
        val updated = repo.update(second, 4000, 10000)
        assertEquals(4000, updated.money)
        assertEquals(2, repo.ofBill("bill-1").size)
        kotlin.test.assertNotNull(first)
    }
}
