package com.meow.ledger.shared.manage

import com.meow.ledger.shared.bills.BillsRepository
import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.Bill_remind
import com.meow.ledger.shared.db.Fixed_bill
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.random.Random

/** 每月、每周、每年。 */
const val CYCLE_MONTH = 1
const val CYCLE_WEEK = 2
const val CYCLE_YEAR = 3

/** 周期账：到日只提醒，确认后才入账。 */
class FixedBillRepository(private val db: MeowDatabase) {
    fun all(): List<Fixed_bill> = db.fixedQueries.selectAll().executeAsList()

    fun one(id: String): Fixed_bill? = all().firstOrNull { it.id == id }

    fun save(
        desc: String,
        money: Long,
        billType: Long = 1L,
        fixedType: Long = CYCLE_MONTH.toLong(),
        fixedDay: Long = 1L,
        notInBudget: Boolean = false,
        id: String = "",
    ): Fixed_bill? {
        val now = currentTimeMillis()
        val old = if (id.isEmpty()) null else one(id) ?: return null
        val row = Fixed_bill(
            id = old?.id ?: newId(),
            member_cate_id = old?.member_cate_id ?: if (billType == 2L) 101 else 9,
            money = money,
            type = billType,
            fixed_type = fixedType,
            fixed_day = fixedDay.coerceIn(1, 31),
            book_id = old?.book_id ?: 1,
            assets_id = old?.assets_id ?: 1,
            desc = desc.ifBlank { "周期账" },
            tag_ids = old?.tag_ids ?: "",
            status = 1,
            not_in_budget = if (notInBudget) 1 else 0,
            next_execute_date = old?.next_execute_date ?: now,
            create_time = old?.create_time ?: now,
            update_time = now,
        )
        db.fixedQueries.upsert(row)
        return row
    }

    fun delete(id: String) {
        db.fixedQueries.deleteOne(id)
    }

    fun reminders(): List<Bill_remind> = db.fixedQueries.selectReminds().executeAsList()

    fun remindersOf(fixedBillId: String): List<Bill_remind> =
        reminders().filter { it.fixed_bill_id == fixedBillId }

    /** 本月已到点：只提示，不写账单。 */
    fun due(nowMillis: Long = currentTimeMillis()): List<Bill_remind> {
        val day = dayOfMonth(nowMillis)
        val hour = hourOfDay(nowMillis)
        return reminders().filter {
            it.enabled == 1L && it.day in 1..day && it.hour <= hour
        }
    }

    fun saveReminder(
        title: String,
        day: Long = 1L,
        hour: Long = 9L,
        fixedBillId: String = "",
    ): Bill_remind? {
        val text = title.trim()
        if (text.isEmpty()) return null
        val now = currentTimeMillis()
        val row = Bill_remind(
            id = newId(),
            title = text,
            time = "",
            day = day.coerceIn(1, 31),
            hour = hour.coerceIn(0, 23),
            fixed_bill_id = fixedBillId,
            repeat_type = 1,
            enabled = 1,
            create_time = now,
            update_time = now,
        )
        db.fixedQueries.upsertRemind(row)
        return row
    }

    fun deleteReminder(id: String) {
        db.fixedQueries.deleteRemind(id)
    }

    private fun newId(): String = "f${currentTimeMillis()}${Random.nextInt(1000, 9999)}"

    /** 到期周期账自动入账：source=5 周期，余额与埋点走 BillsRepository.saveWithBalance；幂等可重入。 */
    fun generateDueBills(nowMillis: Long = currentTimeMillis(), memberId: Long = 0L): Int {
        val bills = BillsRepository(db)
        var created = 0
        all().filter { it.status == 1L }.forEach { fb ->
            var next = if (fb.next_execute_date > 0L) fb.next_execute_date else fb.create_time
            var guard = 0
            while (next in 1..nowMillis && guard < 120) {
                val period = periodKey(next)
                val clientId = "fixed-${fb.id}-$period"
                if (bills.get(memberId, clientId) == null) {
                    val bill = Bill(
                        member_id = memberId, client_id = clientId, money = fb.money, refund_money = 0, type = fb.type,
                        exchange = 0, book_id = fb.book_id, member_cate_id = fb.member_cate_id, currency = "CNY",
                        day = period, desc = fb.desc, not_in_budget = fb.not_in_budget, images = "",
                        update_date = nowMillis, create_date = nowMillis, status = 0,
                        to_assets_id = if (fb.type == 2L) fb.assets_id else 0,
                        from_assets_id = if (fb.type == 2L) 0 else fb.assets_id,
                        interest = 0, interest_reckon_type = 0, tag_ids = fb.tag_ids,
                        source = 5, import_id = 0L, repayment_day = 0,
                        local_create_time = nowMillis, local_update_time = nowMillis,
                        installment_plan_id = "", installment_num = 0, installment_money = 0, installment_interest = 0,
                    )
                    bills.saveWithBalance(bill)
                    created++
                }
                next = advancePeriod(next, fb.fixed_type, fb.fixed_day)
                guard++
            }
            if (guard >= 120) next = nowMillis + 1
            if (next != fb.next_execute_date) {
                db.fixedQueries.upsert(fb.copy(next_execute_date = next, update_time = nowMillis))
            }
        }
        return created
    }
}

private fun currentTimeMillis(): Long = System.currentTimeMillis()

/** 用 1970-01-01 起的天数换算每月几号，避开平台日历 API。 */
internal fun dayOfMonth(millis: Long): Long {
    val days = millis / 86_400_000
    var year = 1970
    var left = days
    while (true) {
        val yearDays = if (isLeap(year)) 366 else 365
        if (left < yearDays) break
        left -= yearDays
        year += 1
    }
    val lengths = intArrayOf(31, if (isLeap(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    for (length in lengths) {
        if (left < length) return left + 1
        left -= length
    }
    return 1
}

internal fun hourOfDay(millis: Long): Long = (millis / 3_600_000) % 24

private fun isLeap(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

/** yyyyMMdd 期次键，同时是自动入账幂等 client_id 的一部分。 */
internal fun periodKey(millis: Long): Long {
    val (year, month, day) = millisToYmd(millis)
    return year * 10_000L + month * 100L + day
}

/** 推进到下一期应执行时间；月份按 fixed_day 收敛到当月长度。 */
internal fun advancePeriod(millis: Long, fixedType: Long, fixedDay: Long): Long {
    val (year, month, day) = millisToYmd(millis)
    val want = fixedDay.coerceIn(1, 31).toInt()
    return when (fixedType) {
        CYCLE_WEEK.toLong() -> millis + 7 * 86_400_000L
        CYCLE_YEAR.toLong() -> ymdToMillis(year + 1, month, want)
        else -> {
            if (day < want.coerceAtMost(monthLength(year, month))) {
                ymdToMillis(year, month, want.coerceAtMost(monthLength(year, month)))
            } else {
                val (ny, nm) = if (month == 12) year + 1 to 1 else year to month + 1
                ymdToMillis(ny, nm, want.coerceAtMost(monthLength(ny, nm)))
            }
        }
    }
}

internal fun millisToYmd(millis: Long): Triple<Int, Int, Int> {
    val days = millis / 86_400_000
    var year = 1970
    var left = days
    while (true) {
        val yearDays = if (isLeap(year)) 366L else 365L
        if (left < yearDays) break
        left -= yearDays
        year += 1
    }
    val lengths = intArrayOf(31, if (isLeap(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var month = 1
    for (length in lengths) {
        if (left < length) break
        left -= length
        month += 1
    }
    return Triple(year, month, (left + 1).toInt())
}

internal fun ymdToMillis(year: Int, month: Int, day: Int): Long {
    var days = 0L
    for (y in 1970 until year) days += if (isLeap(y)) 366L else 365L
    val lengths = intArrayOf(31, if (isLeap(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    for (m in 1 until month) days += lengths[m - 1]
    days += day - 1
    return days * 86_400_000L
}

internal fun monthLength(year: Int, month: Int): Int =
    intArrayOf(31, if (isLeap(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[month - 1]
