package com.meow.ledger.shared.stats

import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Refund_bill

/** 一个分类的汇总。cents 为扣掉退款后的净额，pct 为占本期同类型总额的百分比。 */
data class CateTotal(
    val id: Long,
    val name: String,
    val cents: Long,
    val count: Int,
    val pct: Int,
)

/** 一天的收支。day 为 yyyyMMdd。 */
data class DayTotal(
    val day: Long,
    val expense: Long,
    val income: Long,
)

/** 一段时间的统计结果。金额全部整数分，net = income - expense。 */
data class Summary(
    val expense: Long,
    val income: Long,
    val net: Long,
    val count: Int,
    val byCate: List<CateTotal>,
    val byDay: List<DayTotal>,
)

/** 一个统计期间。key 按周为距纪元的周序号，按月为 yyyyMM，按年为 yyyy。 */
data class PeriodTotal(
    val key: Int,
    val expense: Long,
    val income: Long,
)

class StatsRepository(private val db: MeowDatabase) {

    /** 取一段日期内的账单并按类型汇总。type 为 1 支出或 2 收入，转账不计入。 */
    fun summary(
        fromDay: Long,
        toDay: Long,
        type: Long,
        categoryName: (Long) -> String,
        bookId: Long = 0,
    ): Summary {
        val bills = db.billQueries.selectByRange(bookId, fromDay, toDay).executeAsList()
        val refunds = db.refundQueries.selectByRange(fromDay, toDay).executeAsList()
        return summarize(bills, type, categoryName, refundByBill(refunds))
    }

    /** 最近 count 个周期的收支对比，按时间从早到晚。 */
    fun trend(period: TrendPeriod, count: Int = 12, bookId: Long = 0): List<PeriodTotal> {
        val (from, to) = periodSpan(period, count)
        val bills = db.billQueries.selectByRange(bookId, from, to).executeAsList()
        val refunds = db.refundQueries.selectByRange(from, to).executeAsList()
        return trendOf(bills, refundByBill(refunds), period, count)
    }
}

/** 按月还是按年归组。 */
enum class TrendPeriod { WEEK, MONTH, YEAR }

/** 退款按账单 client_id 归集。统计一律用净额，避免退款后支出被多记。 */
internal fun refundByBill(refunds: List<Refund_bill>): Map<String, Long> {
    val map = mutableMapOf<String, Long>()
    for (refund in refunds) {
        if (refund.status != 0L || refund.client_id.isEmpty()) continue
        map[refund.client_id] = (map[refund.client_id] ?: 0L) + refund.money
    }
    return map
}

/** 扣掉退款后的金额，不会低于 0。 */
internal fun netMoney(bill: Bill, refunds: Map<String, Long>): Long =
    (bill.money - (refunds[bill.client_id] ?: 0L)).coerceAtLeast(0L)

/** 把账单汇总成分类与按天的结果。已删除的账单不计入。 */
internal fun summarize(
    bills: List<Bill>,
    type: Long,
    categoryName: (Long) -> String,
    refunds: Map<String, Long> = emptyMap(),
): Summary {
    val active = bills.filter { it.status == 0L }
    val amount = { bill: Bill -> netMoney(bill, refunds) }
    val expense = active.filter { it.type == TYPE_EXPENSE }.sumOf(amount)
    val income = active.filter { it.type == TYPE_INCOME }.sumOf(amount)
    val picked = active.filter { it.type == type }
    val total = picked.sumOf(amount)
    val byCate = picked.groupBy { it.member_cate_id }
        .map { (id, items) ->
            val cents = items.sumOf(amount)
            CateTotal(
                id = id,
                name = categoryName(id),
                cents = cents,
                count = items.size,
                pct = if (total == 0L) 0 else (cents * 100 / total).toInt(),
            )
        }
        .sortedByDescending { it.cents }
    val byDay = active.groupBy { it.day }
        .entries
        .sortedBy { it.key }
        .map { (day, items) ->
            DayTotal(
                day = day,
                expense = items.filter { it.type == TYPE_EXPENSE }.sumOf(amount),
                income = items.filter { it.type == TYPE_INCOME }.sumOf(amount),
            )
        }
    return Summary(
        expense = expense,
        income = income,
        net = income - expense,
        count = picked.size,
        byCate = byCate,
        byDay = byDay,
    )
}

/** 最近 count 个周期，缺数据的周期补 0，从早到晚。 */
internal fun trendOf(
    bills: List<Bill>,
    refunds: Map<String, Long>,
    period: TrendPeriod,
    count: Int,
): List<PeriodTotal> {
    val (from, _) = periodSpan(period, count)
    val totals = mutableMapOf<Int, PeriodTotal>()
    for (bill in bills) {
        if (bill.status != 0L) continue
        val key = period.keyOf(bill.day) ?: continue
        val current = totals[key] ?: PeriodTotal(key, 0L, 0L)
        val money = netMoney(bill, refunds)
        totals[key] = when (bill.type) {
            TYPE_EXPENSE -> current.copy(expense = current.expense + money)
            TYPE_INCOME -> current.copy(income = current.income + money)
            else -> current
        }
    }
    return (0 until count).map { offset ->
        val key = period.shift(period.keyOf(from) ?: 0, offset)
        totals[key] ?: PeriodTotal(key, 0L, 0L)
    }
}

/** yyyyMMdd 取月份。非法值返回 null。 */
internal fun monthOf(day: Long): Int? {
    val month = (day / 100).toInt()
    return if (month in 190001..299912) month else null
}

/** yyyyMMdd 取年份。 */
internal fun yearOf(day: Long): Int? {
    val year = (day / 10000).toInt()
    return if (year in 1900..2999) year else null
}

private fun TrendPeriod.keyOf(day: Long): Int? = when (this) {
    TrendPeriod.WEEK -> weekOf(day)
    TrendPeriod.MONTH -> monthOf(day)
    TrendPeriod.YEAR -> yearOf(day)
}

fun TrendPeriod.shift(key: Int, offset: Int): Int = when (this) {
    TrendPeriod.WEEK -> key + offset
    TrendPeriod.MONTH -> {
        val months = key / 100 * 12 + key % 100 - 1 + offset
        months / 12 * 100 + months % 12 + 1
    }
    TrendPeriod.YEAR -> key + offset
}

/** 覆盖最近 count 个周期的日期范围。结束日取当前月末或当年底，以便查询包住本期。 */
fun periodSpan(period: TrendPeriod, count: Int, today: Long = todayAsDay()): Pair<Long, Long> {
    return period.covering(count, today)
}

private fun TrendPeriod.covering(count: Int, today: Long): Pair<Long, Long> {
    val key = keyOf(today) ?: return 0L to 0L
    val start = shift(key, -(count - 1))
    return when (this) {
        TrendPeriod.WEEK -> addDays(start * 7L) to addDays(key * 7L + 6)
        TrendPeriod.MONTH -> start.toLong() * 100 + 1 to key.toLong() * 100 + 31
        TrendPeriod.YEAR -> start.toLong() * 10000 + 101 to key.toLong() * 10000 + 1231
    }
}

/** 周序号：1970-01-05（周一）为第 0 周，之后每 7 天一周。 */
internal fun weekOf(day: Long): Int? {
    val days = daysSinceEpoch(day) ?: return null
    return ((days - 4) / 7).toInt()
}

private fun daysSinceEpoch(day: Long): Long? {
    val year = (day / 10000).toInt()
    val month = (day / 100 % 100).toInt()
    val date = (day % 100).toInt()
    if (year !in 1970..2999 || month !in 1..12 || date !in 1..31) return null
    val monthDays = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    if (date > monthDays[month] && !(month == 2 && date == 29 && leap(year))) return null
    var days = (1970 until year).sumOf { if (leap(it)) 366L else 365L }
    for (m in 1 until month) days += if (m == 2 && leap(year)) 29 else monthDays[m]
    return days + date - 1
}

private fun addDays(weekDay: Long): Long {
    var days = weekDay + 4
    var year = 1970
    while (true) {
        val length = if (leap(year)) 366L else 365L
        if (days < length) break
        days -= length
        year++
    }
    val monthDays = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var month = 1
    for (length in monthDays) {
        val daysInMonth = if (month == 2 && leap(year)) 29 else length
        if (days < daysInMonth) break
        days -= daysInMonth
        month++
    }
    return year * 10000L + month * 100 + days + 1
}

private fun leap(year: Int) = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

internal expect fun todayAsDay(): Long
