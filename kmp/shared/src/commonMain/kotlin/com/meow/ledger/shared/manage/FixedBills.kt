package com.meow.ledger.shared.manage

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
