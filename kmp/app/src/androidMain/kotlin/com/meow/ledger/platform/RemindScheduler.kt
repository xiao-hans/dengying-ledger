package com.meow.ledger.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.meow.ledger.shared.db.Bill_remind
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.manage.FixedBillRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 把 bill_remind 表排进系统精确闹钟。App 启动与开机广播都会重建。 */
object RemindScheduler {
    fun scheduleAll(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val reminders: List<Bill_remind> = try {
            val driver = AndroidSqliteDriver(MeowDatabase.Schema, context, "meow_ledger.db")
            FixedBillRepository(MeowDatabase(driver))
                .reminders()
                .filter { it.enabled == 1L }
        } catch (t: Throwable) {
            return
        }
        val today = LocalDate.now()
        reminders.forEach { remind ->
            val day = remind.day.coerceIn(1, today.lengthOfMonth().toLong()).toInt()
            // 已过的日期排到下月同一天；未到的排本月。day 必须落进目标月份，否则固定成 today 会排错日子。
            val fireDate = if (day < today.dayOfMonth) today.plusMonths(1).withDayOfMonth(day) else today.withDayOfMonth(day)
            val fireAt = LocalDateTime.of(fireDate, LocalTime.of(remind.hour.coerceIn(0, 23).toInt(), 0))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val intent = Intent(context, BillAlarmReceiver::class.java)
                .putExtra(BillAlarmReceiver.EXTRA_TITLE, remind.title)
                .putExtra(BillAlarmReceiver.EXTRA_BODY, "今天是 $day 号，打开账本记一笔")
            val pending = PendingIntent.getBroadcast(
                context,
                remind.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                manager.canScheduleExactAlarms()
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
            }
        }
    }
}
