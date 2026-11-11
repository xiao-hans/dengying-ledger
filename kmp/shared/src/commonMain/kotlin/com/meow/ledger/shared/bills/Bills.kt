package com.meow.ledger.shared.bills

import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.MeowDatabase

const val TYPE_EXPENSE = 1L
const val TYPE_INCOME = 2L
const val TYPE_TRANSFER = 3L

/** 一个月的收支汇总，金额全部整数分。 */
data class MonthSummary(
    val expense: Long,
    val income: Long,
    val count: Int,
)

/** 按天分组后的一天账单。day 为 yyyyMMdd。 */
data class DayGroup(
    val day: Long,
    val bills: List<Bill>,
    val expense: Long,
    val income: Long,
    /** 每笔已退的分，列表用它显示实付而不是原价。 */
    val refunded: Map<String, Long> = emptyMap(),
)

/** 当月账单的展示结果。 */
data class MonthView(
    val summary: MonthSummary,
    val groups: List<DayGroup>,
)

class BillsRepository(private val db: MeowDatabase) {

    /** 取一个月的账单并汇总。yearMonth 形如 202611，bookId 为 0 时不限账本。 */
    fun month(yearMonth: Int, bookId: Long = 0): MonthView {
        val from = yearMonth.toLong() * 100 + 1
        val to = yearMonth.toLong() * 100 + 31
        val bills = db.billQueries.selectByRange(bookId, from, to).executeAsList()
        val refunded = refundsOf(bills)
        return viewOf(bills, refunded)
    }

    /** 每个账单已退的分，只算 status 为 0 的退款。 */
    private fun refundsOf(bills: List<Bill>): Map<String, Long> =
        bills.associate { bill ->
            bill.client_id to db.refundQueries.selectByBill(bill.client_id).executeAsList().sumOf { it.money }
        }

    fun get(memberId: Long, clientId: String): Bill? =
        db.billQueries.selectOne(memberId, clientId).executeAsOneOrNull()

    fun save(bill: Bill) {
        db.billQueries.upsert(bill)
    }

    fun delete(memberId: Long, clientId: String) {
        db.billQueries.deleteOne(memberId, clientId)
    }
}

internal fun viewOf(bills: List<Bill>, refunded: Map<String, Long> = emptyMap()): MonthView {
    val active = bills.filter { it.status == 0L }
    val groups = active.groupBy { it.day }
        .entries
        .sortedByDescending { it.key }
        .map { (day, items) ->
            DayGroup(
                day = day,
                bills = items,
                expense = items.filter { it.type == TYPE_EXPENSE }.sumOf { netPaid(it.money, refunded[it.client_id] ?: 0) },
                income = items.filter { it.type == TYPE_INCOME }.sumOf { netPaid(it.money, refunded[it.client_id] ?: 0) },
                refunded = items.associate { it.client_id to (refunded[it.client_id] ?: 0) },
            )
        }
    return MonthView(
        summary = MonthSummary(
            expense = groups.sumOf { it.expense },
            income = groups.sumOf { it.income },
            count = active.size,
        ),
        groups = groups,
    )
}
