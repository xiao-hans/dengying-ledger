package com.meow.ledger.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.shared.bills.centsToYuan
import com.meow.ledger.shared.bills.toCents
import com.meow.ledger.shared.manage.BudgetRepository
import com.meow.ledger.shared.manage.BudgetView
import com.meow.ledger.ui.auto.ActionButton
import com.meow.ledger.ui.auto.BackHeader
import com.meow.ledger.ui.theme.LocalPalette

/** 预算。page 为 budget、budget-set，或 budget-edit:<categoryId>。 */
@Composable
fun BudgetPage(repo: BudgetRepository, month: Long, page: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val view = remember(page, month) { repo.view(month) }
    when {
        page == "budget-set" -> MonthBudgetEdit(repo, month, view.budget?.money ?: 0, view.budget?.is_remind == 1L, view.budget?.remind_rate ?: 80, onBack)
        page.startsWith("budget-edit") -> {
            val id = page.substringAfter(':').toLongOrNull()
            val category = view.categories.firstOrNull { it.categoryId == id }
            if (category == null) Missing("分类不存在", onBack) else CategoryBudgetEdit(repo, month, category.categoryId, category.title, category.money, onBack)
        }
        else -> BudgetHome(repo, view, onOpen, onBack)
    }
}

@Composable
private fun BudgetHome(repo: BudgetRepository, view: BudgetView, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val budget = view.budget
    var notice by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader("预算", onBack)
        if (notice.isNotEmpty()) {
            Text(notice, color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassCard {
                if (budget == null) {
                    Text("这个月还没有预算", color = palette.textPrimary, fontWeight = FontWeight.Medium)
                    Text("设一个总额，超支时会标红", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                } else {
                    Text("本月预算 ${centsToYuan(budget.money)} 元", color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("已用 ${centsToYuan(view.spent)} 元", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    if (budget.is_remind == 1L) {
                        Text("用到 ${budget.remind_rate}% 时提醒", color = palette.textSecondary, fontSize = 12.sp)
                    }
                }
            }
            val included = view.categories.filter { it.money > 0 }
            val excluded = view.categories.filter { it.money == 0L }
            if (included.isNotEmpty()) {
                Text("包含的分类预算（${included.size}）", color = palette.textSecondary, fontSize = 13.sp)
                included.forEach { category -> CateCard(category, onOpen) }
            }
            if (excluded.isNotEmpty()) {
                Text(
                    "未设分类预算（${excluded.size}）· 这些分类仍计入总预算",
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                )
                excluded.forEach { category -> CateCard(category, onOpen) }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (budget == null) {
                ActionButton("复制上月预算", true) {
                    notice = if (repo.copyPrevious(view.month) == null) "上月没有预算可复制" else "已复制上月预算"
                }
            }
            ActionButton("设置月预算", true) { onOpen("budget-set") }
        }
    }
}

@Composable
private fun CateCard(category: com.meow.ledger.shared.manage.CategoryBudget, onOpen: (String) -> Unit) {
    val palette = LocalPalette.current
    val over = category.money > 0 && category.spent > category.money
    GlassCard(onClick = { onOpen("budget-edit:${category.categoryId}") }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(category.title, color = palette.textPrimary)
            Text(
                if (category.money == 0L) "未设预算" else "${centsToYuan(category.spent)} / ${centsToYuan(category.money)}",
                color = if (over) palette.danger else palette.textSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

/** 月预算。金额留空或填 0 会清掉这个月的预算。 */
@Composable
private fun MonthBudgetEdit(repo: BudgetRepository, month: Long, current: Long, remindOn: Boolean, rate: Long, onBack: () -> Unit) {
    val palette = LocalPalette.current
    var money by rememberSaveable { mutableStateOf(if (current == 0L) "" else centsToYuan(current)) }
    var remind by rememberSaveable { mutableStateOf(remindOn) }
    var remindRate by rememberSaveable { mutableStateOf(rate.toString()) }
    var error by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader("月预算", onBack)
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("预算金额（元）", money, { money = it }, "0 表示清除本月预算")
            ChoiceChip(if (remind) "已开超支提醒" else "超支提醒关", remind) { remind = !remind }
            if (remind) Field("提醒比例（%）", remindRate, { remindRate = it.filter { c -> c.isDigit() }.take(3) }, "80")
            if (error.isNotEmpty()) Text(error, color = palette.danger, fontSize = 13.sp)
        }
        Box(Modifier.padding(16.dp)) {
            ActionButton("保存", true) {
                val cents = if (money.isBlank()) 0 else toCents(money)
                if (cents == null) {
                    error = "请输入有效金额（最多两位小数）"
                    return@ActionButton
                }
                repo.save(month, cents, remind, remindRate.toLongOrNull() ?: 80)
                onBack()
            }
        }
    }
}

@Composable
private fun CategoryBudgetEdit(
    repo: BudgetRepository,
    month: Long,
    categoryId: Long,
    title: String,
    current: Long,
    onBack: () -> Unit,
) {
    val palette = LocalPalette.current
    var money by rememberSaveable { mutableStateOf(if (current == 0L) "" else centsToYuan(current)) }
    var error by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader(title, onBack)
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("预算金额（元）", money, { money = it }, "0 表示清除")
            if (error.isNotEmpty()) Text(error, color = palette.danger, fontSize = 13.sp)
            Text("保存后立即生效，已用金额按本月支出扣掉退款计算", color = palette.textSecondary, fontSize = 12.sp)
        }
        Box(Modifier.padding(16.dp)) {
            ActionButton("保存", true) {
                val cents = if (money.isBlank()) 0 else toCents(money)
                if (cents == null) {
                    error = "请输入有效金额（最多两位小数）"
                    return@ActionButton
                }
                repo.saveCategory(month, categoryId, cents)
                onBack()
            }
        }
    }
}
