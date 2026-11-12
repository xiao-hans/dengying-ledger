package com.meow.ledger.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.meow.ledger.MainActivity
import com.meow.ledger.R
import com.meow.ledger.shared.bills.BillsRepository
import com.meow.ledger.shared.bills.RefundRepository
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.manage.BudgetRepository
import com.meow.ledger.shared.manage.PlanRepository
import com.meow.ledger.shared.manage.WidgetRepository
import com.meow.ledger.shared.manage.WIDGET_ROW_BUDGET
import com.meow.ledger.shared.manage.WIDGET_ROW_PLAN
import com.meow.ledger.shared.manage.WIDGET_ROW_REFUND
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 桌面小组件：今日收支 + 可选的预算/存钱/退款行，点击进入记账。 */
class AppWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE).toLong()
        var expense = 0L
        var income = 0L
        val extras = mutableMapOf<String, String>()
        try {
            val driver = AndroidSqliteDriver(MeowDatabase.Schema, context, "meow_ledger.db")
            val db = MeowDatabase(driver)
            val summary = BillsRepository(db).daySum(day)
            expense = summary.expense
            income = summary.income
            val rows = WidgetRepository(db).extraRows()
            val ym = day / 100
            if (WIDGET_ROW_BUDGET in rows) {
                val view = BudgetRepository(db).view(ym)
                extras[WIDGET_ROW_BUDGET] = "预算 " + money(view.budget?.money ?: 0) + " · 已用 " + money(view.spent)
            }
            if (WIDGET_ROW_PLAN in rows) {
                val plans = PlanRepository(db).savePlans().filter { it.status != 2L }
                extras[WIDGET_ROW_PLAN] = "存钱计划 " + plans.size + " 个 · 已存 " + money(plans.sumOf { it.saved_money })
            }
            if (WIDGET_ROW_REFUND in rows) {
                val sum = RefundRepository(db).all()
                    .filter { it.status == 0L && it.day / 100 == ym }
                    .sumOf { it.money }
                extras[WIDGET_ROW_REFUND] = "本月退款 " + money(sum)
            }
        } catch (_: Throwable) {
            // 库还没建好时显示 0，不打断桌面。
        }
        appWidgetIds.forEach { id -> render(context, appWidgetManager, id, expense, income, extras) }
    }

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        expense: Long,
        income: Long,
        extras: Map<String, String>,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_meow)
        views.setTextViewText(R.id.widget_expense, "支出 " + money(expense))
        views.setTextViewText(R.id.widget_income, "收入 " + money(income))
        extra(views, R.id.widget_budget, extras[WIDGET_ROW_BUDGET])
        extra(views, R.id.widget_plan, extras[WIDGET_ROW_PLAN])
        extra(views, R.id.widget_refund, extras[WIDGET_ROW_REFUND])
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

    /** 配置里选中的行显示，没选的隐藏。 */
    private fun extra(views: RemoteViews, viewId: Int, text: String?) {
        if (text == null) {
            views.setViewVisibility(viewId, View.GONE)
        } else {
            views.setViewVisibility(viewId, View.VISIBLE)
            views.setTextViewText(viewId, text)
        }
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
