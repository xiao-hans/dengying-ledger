package com.meow.ledger.shared.stats

import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.bills.TYPE_TRANSFER
import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.Refund_bill
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsTest {

    @Test
    fun refundsReduceTheExpenseAndNeverGoBelowZero() {
        val bills = listOf(
            bill("a", 5000, TYPE_EXPENSE, 1, 20261102),
            bill("b", 1000, TYPE_EXPENSE, 1, 20261103),
        )
        val refunds = mapOf("a" to 2000L, "b" to 5000L)
        val summary = summarize(bills, TYPE_EXPENSE, { "餐饮" }, refunds)
        assertEquals(3000L, summary.expense)
        assertEquals(2, summary.count)
        assertEquals(100, summary.byCate.single().pct)
    }

    @Test
    fun categoryShareUsesNetAmountAndSortsBySize() {
        val bills = listOf(
            bill("a", 3000, TYPE_EXPENSE, 1, 20261101),
            bill("b", 7000, TYPE_EXPENSE, 2, 20261101),
            bill("c", 2000, TYPE_INCOME, 101, 20261101),
            bill("d", 9000, TYPE_TRANSFER, 1, 20261101),
            bill("e", 4000, TYPE_EXPENSE, 1, 20261101, status = 1),
        )
        val summary = summarize(bills, TYPE_EXPENSE, { if (it == 1L) "餐饮" else "交通" })
        assertEquals(10000L, summary.expense)
        assertEquals(2000L, summary.income)
        assertEquals(-8000L, summary.net)
        assertEquals(2, summary.count)
        assertEquals(listOf(2L, 1L), summary.byCate.map { it.id })
        assertEquals(70, summary.byCate.first().pct)
        assertEquals("交通", summary.byCate.first().name)
    }

    @Test
    fun trendFillsMissingMonthsAndKeepsOrder() {
        val anchor = TrendPeriod.MONTH.shift(monthOf(todayAsDay())!!, -1)
        val bills = listOf(
            bill("a", 1000, TYPE_EXPENSE, 1, anchor.toLong() * 100 + 5),
            bill("b", 4000, TYPE_INCOME, 101, todayAsDay()),
        )
        val trend = trendOf(bills, emptyMap(), TrendPeriod.MONTH, 3)
        val first = TrendPeriod.MONTH.shift(anchor, -1)
        assertEquals(listOf(first, anchor, monthOf(todayAsDay())), trend.map { it.key })
        assertEquals(0L, trend[0].expense)
        assertEquals(1000L, trend[1].expense)
        assertEquals(4000L, trend[2].income)
    }

    @Test
    fun weekStartsOnMondayAndCrossesYears() {
        assertEquals(weekOf(20200106), weekOf(20200106))
        assertEquals(1, weekOf(20200113)!! - weekOf(20200106)!!)
        assertEquals(1, weekOf(20260105)!! - weekOf(20251229)!!)
        val start = periodSpan(TrendPeriod.WEEK, 1, 20261109).first
        assertEquals(20261109, start)
    }

    fun monthShiftWrapsAcrossYears() {
        assertEquals(202601, TrendPeriod.MONTH.shift(202511, 2))
        assertEquals(202512, TrendPeriod.MONTH.shift(202601, -1))
    }

    fun refundsFromDeletedRecordsAreIgnored() {
        val kept = refundByBill(listOf(
            refund("r1", "a", 500, status = 0),
            refund("r2", "a", 300, status = 1),
            refund("r3", "", 900, status = 0),
        ))
        assertEquals(mapOf("a" to 500L), kept)
    }
}

private fun bill(
    clientId: String,
    money: Long,
    type: Long,
    category: Long,
    day: Long,
    status: Long = 0,
) = Bill(
    member_id = 0,
    client_id = clientId,
    money = money,
    refund_money = 0,
    type = type,
    exchange = 0,
    book_id = 1,
    member_cate_id = category,
    currency = "CNY",
    day = day,
    desc = "",
    not_in_budget = 0,
    images = "",
    update_date = 0,
    create_date = 0,
    status = status,
    to_assets_id = 0,
    from_assets_id = 0,
    interest = 0,
    interest_reckon_type = 0,
    tag_ids = "",
    source = 0,
    import_id = 0,
    repayment_day = 0,
    local_create_time = 0,
    local_update_time = 0,
    installment_plan_id = "",
    installment_num = 0,
    installment_money = 0,
    installment_interest = 0,
)

private fun refund(id: String, clientId: String, money: Long, status: Long) = Refund_bill(
    id = id,
    client_id = clientId,
    member_id = 0,
    money = money,
    asset_id = 0,
    day = 0,
    desc = "",
    images = "",
    source = 0,
    status = status,
    create_date = 0,
    update_date = 0,
)
