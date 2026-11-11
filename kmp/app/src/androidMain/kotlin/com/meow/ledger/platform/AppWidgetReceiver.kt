package com.meow.ledger.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.meow.ledger.MainActivity
import com.meow.ledger.R
import com.meow.ledger.shared.bills.BillsRepository
import com.meow.ledger.shared.db.MeowDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 桌面小组件：显示今日支出与收入，点击进入记一笔。 */
class AppWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE).toLong()
        val (expense, income) = try {
            val driver = AndroidSqliteDriver(MeowDatabase.Schema, context, "meow_ledger.db")
            val db = MeowDatabase(driver)
            val summary = BillsRepository(db).daySum(day)
            summary.expense to summary.income
        } catch (t: Throwable) {
            0L to 0L
        }
        appWidgetIds.forEach { id -> render(context, appWidgetManager, id, expense, income) }
    }

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        expense: Long,
        income: Long,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_meow)
        views.setTextViewText(R.id.widget_expense, "支出 " + money(expense))
        views.setTextViewText(R.id.widget_income, "收入 " + money(income))
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        manager.updateAppWidget(widgetId, views)
    }

    private fun money(cents: Long): String {
        val y = cents / 100
        val f = (cents % 100).let { if (it < 0) -it else it }
        return if (cents < 0) "-$y.${"%02d".format(f)}" else "$y.${"%02d".format(f)}"
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, AppWidgetReceiver::class.java))
            if (ids.isNotEmpty()) {
                AppWidgetReceiver().onUpdate(context, manager, ids)
            }
        }
    }
}
