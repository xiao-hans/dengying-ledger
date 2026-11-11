package com.meow.ledger.shared.bills

import com.meow.ledger.shared.db.Bill
import kotlin.test.Test
import kotlin.test.assertEquals

class BillsTest {

    private fun bill(day: Long, type: Long, money: Long, status: Long = 0) = Bill(
        member_id = 0, client_id = "$day-$type-$money", money = money, refund_money = 0,
        type = type, exchange = 0, book_id = 1, member_cate_id = 1, currency = "CNY",
        day = day, desc = "", not_in_budget = 0, images = "", update_date = 0, create_date = 0,
        status = status, to_assets_id = 0, from_assets_id = 0, interest = 0,
        interest_reckon_type = 0, tag_ids = "", source = 0, import_id = 0, repayment_day = 0,
        local_create_time = 0, local_update_time = 0, installment_plan_id = "",
        installment_num = 0, installment_money = 0, installment_interest = 0,
    )

    @Test
    fun sumsExpenseAndIncomeSeparately() {
        val view = viewOf(
            listOf(
                bill(20261102, TYPE_EXPENSE, 1500),
                bill(20261102, TYPE_INCOME, 8000),
                bill(20261101, TYPE_EXPENSE, 500),
                bill(20261101, TYPE_TRANSFER, 300), // 转账不计入收支
            )
        )
        assertEquals(2000, view.summary.expense)
        assertEquals(8000, view.summary.income)
        assertEquals(4, view.summary.count)
    }

    @Test
    fun groupsByDayDescending() {
        val view = viewOf(
            listOf(
                bill(20261101, TYPE_EXPENSE, 100),
                bill(20261103, TYPE_EXPENSE, 100),
                bill(20261102, TYPE_EXPENSE, 100),
            )
        )
        assertEquals(listOf(20261103L, 20261102L, 20261101L), view.groups.map { it.day })
    }

    @Test
    fun skipsDeletedBills() {
        val view = viewOf(listOf(bill(20261101, TYPE_EXPENSE, 999, status = 1)))
        assertEquals(0, view.summary.count)
        assertEquals(0, view.summary.expense)
    }

    @Test
    fun deductsRefundsFromTheDayAndTheMonth() {
        val refunded = bill(20261102, TYPE_EXPENSE, 1500)
        val view = viewOf(
            listOf(refunded, bill(20261102, TYPE_EXPENSE, 500)),
            mapOf(refunded.client_id to 600L),
        )
        assertEquals(1400, view.summary.expense)
        assertEquals(1400, view.groups.single().expense)
    }
}
