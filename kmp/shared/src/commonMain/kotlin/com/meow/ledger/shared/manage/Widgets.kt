package com.meow.ledger.shared.manage

import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.bills.netPaid
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Widget_user
import kotlin.random.Random

const val WIDGET_SIZE_SMALL = "small"
const val WIDGET_SIZE_MEDIUM = "medium"
const val WIDGET_SIZE_LARGE = "large"

const val WIDGET_KIND_MONTH = "month"
const val WIDGET_KIND_BUDGET = "budget"
const val WIDGET_KIND_PLAN = "plan"

data class WidgetSize(val id: String, val name: String)

val WIDGET_SIZES = listOf(
    WidgetSize(WIDGET_SIZE_SMALL, "小"),
    WidgetSize(WIDGET_SIZE_MEDIUM, "中"),
    WidgetSize(WIDGET_SIZE_LARGE, "大"),
)

data class WidgetKind(val id: String, val name: String)

val WIDGET_KINDS = listOf(
    WidgetKind(WIDGET_KIND_MONTH, "本月收支"),
    WidgetKind(WIDGET_KIND_BUDGET, "预算剩余"),
    WidgetKind(WIDGET_KIND_PLAN, "存钱进度"),
)

/** 小组件预览上的两行数字，金额都是分。 */
data class WidgetPreview(
    val firstLabel: String,
    val firstValue: Long,
    val secondLabel: String,
    val secondValue: Long,
)

/** 桌面小组件额外行配置，存 user_setting:widget_rows，逗号分隔。 */
const val WIDGET_ROWS_KEY = "widget_rows"
const val WIDGET_ROW_BUDGET = "budget"
const val WIDGET_ROW_PLAN = "plan"
const val WIDGET_ROW_REFUND = "refund"

/** 桌面小组件的本地配置。只写本地表，不挂到系统桌面。 */
class WidgetRepository(private val db: MeowDatabase) {

    /** 桌面小组件显示的额外行。 */
    fun extraRows(): Set<String> =
        (db.configQueries.selectSetting(WIDGET_ROWS_KEY).executeAsOneOrNull() ?: "")
            .split(',').filter { it.isNotBlank() }.toSet()

    /** 设置桌面小组件显示的额外行。 */
    fun setExtraRows(rows: Set<String>) {
        db.configQueries.upsertSetting(
            com.meow.ledger.shared.db.User_setting(WIDGET_ROWS_KEY, rows.filter { it.isNotBlank() }.sorted().joinToString(",")),
        )
    }

    fun all(): List<Widget_user> = db.widgetQueries.selectWidgets().executeAsList()

    fun get(id: String): Widget_user? = db.widgetQueries.selectWidget(id).executeAsOneOrNull()

    /** 新建或修改。修改不存在的组件返回 null。 */
    fun save(name: String, size: String, kind: String, id: String = ""): Widget_user? {
        val old = if (id.isEmpty()) null else get(id) ?: return null
        val row = Widget_user(
            id = old?.id ?: "w${System.currentTimeMillis()}${Random.nextInt(1000, 9999)}",
            name = name.ifBlank { WIDGET_KINDS.firstOrNull { it.id == kind }?.name ?: "小组件" },
            size = WIDGET_SIZES.firstOrNull { it.id == size }?.id ?: WIDGET_SIZE_SMALL,
            kind = WIDGET_KINDS.firstOrNull { it.id == kind }?.id ?: WIDGET_KIND_MONTH,
            create_time = old?.create_time ?: System.currentTimeMillis(),
        )
        db.widgetQueries.upsertWidget(row)
        return row
    }

    fun delete(id: String) {
        db.widgetQueries.deleteWidget(id)
    }

    /** 按组件种类取当前数字。yearMonth 形如 202611。 */
    fun preview(kind: String, yearMonth: Long): WidgetPreview {
        return when (kind) {
            WIDGET_KIND_BUDGET -> budget(yearMonth)
            WIDGET_KIND_PLAN -> plan()
            else -> month(yearMonth)
        }
    }

    private fun month(yearMonth: Long): WidgetPreview {
        val from = yearMonth * 100 + 1
        val to = yearMonth * 100 + 31
        val bills = db.billQueries.selectByRange(0, from, to).executeAsList()
        val expense = bills.filter { it.type == TYPE_EXPENSE }.sumOf { netPaid(it.money, refunded(it.client_id)) }
        val income = bills.filter { it.type == TYPE_INCOME }.sumOf { it.money }
        return WidgetPreview("支出", expense, "收入", income)
    }

    private fun budget(yearMonth: Long): WidgetPreview {
        val view = BudgetRepository(db).view(yearMonth)
        val total = view.budget?.money ?: 0
        return WidgetPreview("预算", total, "剩余", total - view.spent)
    }

    private fun plan(): WidgetPreview {
        val plans = db.planQueries.selectSavePlans().executeAsList().filter { it.archived == 0L }
        return WidgetPreview("目标", plans.sumOf { it.target_money }, "已存", plans.sumOf { it.saved_money })
    }

    private fun refunded(clientId: String): Long =
        db.refundQueries.selectByBill(clientId).executeAsList().sumOf { it.money }
}
