package com.meow.ledger.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.shared.db.Installment_item
import com.meow.ledger.shared.db.Installment_plan
import com.meow.ledger.shared.db.Save_plan
import com.meow.ledger.shared.db.Save_plan_item
import com.meow.ledger.shared.manage.INSTALLMENT_PAID_OFF
import com.meow.ledger.shared.bills.centsToYuan
import com.meow.ledger.shared.bills.toCents
import com.meow.ledger.shared.manage.PLAN_ICONS
import com.meow.ledger.shared.manage.PlanRepository
import com.meow.ledger.shared.manage.SAVE_PLAN_DONE
import com.meow.ledger.shared.manage.WISH_TYPE
import com.meow.ledger.ui.auto.ActionButton
import com.meow.ledger.ui.auto.BackHeader
import com.meow.ledger.ui.theme.GlassChip
import com.meow.ledger.ui.theme.GlassPanel
import com.meow.ledger.ui.theme.LocalPalette

/** 分期列表、新建、详情，以及存钱计划的列表、编辑、详情。 */
@Composable
fun PlansPage(repo: PlanRepository, page: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val planId = page.substringAfter(":", "")
    when {
        page == "installments" -> InstallmentList(repo, onOpen, onBack)
        page == "installment-new" -> InstallmentEdit(repo, onBack)
        page.startsWith("installment:") -> InstallmentDetail(repo, planId, onBack)
        page == "save-plans" -> SavePlanList(repo, onOpen, onBack)
        page == "wishes" -> WishList(repo, onBack)
        page == "save-plan-new" -> SavePlanEdit(repo, "", onBack)
        page.startsWith("save-plan-edit:") -> SavePlanEdit(repo, planId, onBack)
        page.startsWith("save-plan:") -> SavePlanDetail(repo, planId, onOpen, onBack)
    }
}

@Composable
private fun InstallmentList(repo: PlanRepository, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    var version by remember { mutableStateOf(0) }
    val plans = remember(version) { repo.installments() }
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader("分期", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (plans.isEmpty()) {
                Empty("还没有分期", "每期金额和还款日会自动算好", "新建分期") { onOpen("installment-new") }
            }
            plans.forEach { plan ->
                val done = plan.executed_count
                val total = plan.total_count.coerceAtLeast(1)
                GlassCard(onClick = { onOpen("installment:${plan.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(plan.name, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "已还 ${done}/${plan.total_count} 期 · 剩余 ${centsToYuan(plan.remaining_money)} 元",
                                color = palette.textSecondary, fontSize = 12.sp,
                            )
                        }
                        StatusChip(if (plan.status == INSTALLMENT_PAID_OFF) "已结清" else "进行中")
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(done.toFloat() / total)
                }
            }
        }
        Box(Modifier.padding(16.dp)) { ActionButton("新建分期", true) { onOpen("installment-new") } }
    }
}

@Composable
private fun InstallmentEdit(repo: PlanRepository, onBack: () -> Unit) {
    val palette = LocalPalette.current
    var name by remember { mutableStateOf("") }
    var money by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("12") }
    var interest by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader("新建分期", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field("名称", name, { name = it }, "例如：手机分期")
            Field("金额（元）", money, { money = it }, "0.00")
            Field("期数（2-60）", count, { count = it.filter(Char::isDigit).take(2) }, "12")
            Field("利息（元，可选）", interest, { interest = it }, "0.00")
            if (error.isNotEmpty()) Text(error, color = palette.danger, fontSize = 12.sp)
        }
        Box(Modifier.padding(16.dp)) {
            ActionButton("保存", true) {
                val cents = toCents(money)
                val periods = count.toIntOrNull() ?: 0
                if (cents == null || cents <= 0) { error = "请填写大于 0 的金额"; return@ActionButton }
                if (periods !in 2..60) { error = "期数要在 2 到 60 之间"; return@ActionButton }
                repo.createInstallment(name, cents, periods, toCents(interest) ?: 0)
                onBack()
            }
        }
    }
}

@Composable
private fun InstallmentDetail(repo: PlanRepository, id: String, onBack: () -> Unit) {
    val palette = LocalPalette.current
    var version by remember { mutableStateOf(0) }
    val plan = remember(version) { repo.installment(id) }
    var confirm by remember { mutableStateOf("") }
    if (plan == null) { Missing("分期不存在", onBack); return }
    val items = remember(version) { repo.installmentItems(id) }
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader(plan.name, onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassCard {
                Text("总额 ${centsToYuan(plan.money)} 元 · 利息 ${centsToYuan(plan.interest)} 元", color = palette.textPrimary, fontSize = 14.sp)
                Text("剩余 ${centsToYuan(plan.remaining_money)} 元", color = palette.textSecondary, fontSize = 12.sp)
            }
            items.forEach { PeriodRow(it) }
        }
        if (confirm.isEmpty()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    ActionButton("提前结清", plan.status != INSTALLMENT_PAID_OFF) { confirm = "payoff" }
                }
                Box(Modifier.weight(1f)) { ActionButton("删除分期", true) { confirm = "delete" } }
            }
        } else {
            ConfirmBar(
                text = if (confirm == "payoff") "结清后剩余金额与利息都会归零。" else "删除分期不会删掉已入账的账单。",
                onCancel = { confirm = "" },
                onConfirm = {
                    if (confirm == "payoff") { repo.payOff(id); version += 1 } else { repo.deleteInstallment(id); onBack() }
                    confirm = ""
                },
            )
        }
    }
}

@Composable
private fun PeriodRow(item: Installment_item) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("第 ${item.num} 期", color = palette.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("${centsToYuan(item.money)} 元", color = palette.textPrimary, fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        StatusChip(if (item.status == 1L) "已还" else "待还")
    }
}

@Composable
private fun SavePlanList(repo: PlanRepository, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    var version by remember { mutableStateOf(0) }
    var archived by remember { mutableStateOf(false) }
    var typeId by remember { mutableStateOf(0L) }
    val plans = remember(version, archived, typeId) {
        repo.savePlans().filter { (it.archived == 1L) == archived && (typeId == 0L || it.type_id == typeId) }
    }
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader("存钱计划", onBack)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("进行中", !archived) { archived = false }
            ChoiceChip("已归档", archived) { archived = true }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceChip("全部", typeId == 0L) { typeId = 0 }
            SAVE_TYPES.forEach { (id, label) -> ChoiceChip(label, typeId == id) { typeId = id } }
            ChoiceChip("愿望清单", false) { onOpen("wishes") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (plans.isEmpty()) Empty(
                if (archived) "没有归档的计划" else "还没有存钱计划",
                if (archived) "归档后的计划会出现在这里" else "定一个目标，每次存入都有记录",
                if (archived) "" else "新建计划",
            ) { onOpen("save-plan-new") }
            plans.forEach { plan ->
                GlassCard(onClick = { onOpen("save-plan:${plan.id}") }) {
                    SavePlanSummary(plan)
                }
            }
        }
        Box(Modifier.padding(16.dp)) { ActionButton("新建存钱计划", true) { onOpen("save-plan-new") } }
    }
}

@Composable
private fun SavePlanSummary(plan: Save_plan) {
    val palette = LocalPalette.current
    val target = plan.target_money.coerceAtLeast(1)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(plan.name, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(SAVE_TYPES[plan.type_id] ?: "计划", color = palette.textSecondary, fontSize = 12.sp)
        }
        Text("已存 ${centsToYuan(plan.saved_money)} / ${centsToYuan(plan.target_money)} 元", color = palette.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        ProgressBar(plan.saved_money.toFloat() / target)
    }
}

@Composable
private fun SavePlanEdit(repo: PlanRepository, id: String, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val existing = remember { if (id.isEmpty()) null else repo.savePlan(id) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var money by remember { mutableStateOf(existing?.let { centsToYuan(it.target_money) } ?: "") }
    var typeId by remember { mutableStateOf(existing?.type_id ?: 1L) }
    var icon by remember { mutableStateOf(existing?.icon ?: PLAN_ICONS.first()) }
    var error by remember { mutableStateOf("") }
    if (id.isNotEmpty() && existing == null) { Missing("计划不存在", onBack); return }
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader(if (id.isEmpty()) "新建存钱计划" else "编辑存钱计划", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field("名称", name, { name = it }, "例如：旅行")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("旅行", "买电脑", "换新手机", "应急基金").forEach { wish ->
                    ChoiceChip(wish, name == wish) { name = wish }
                }
            }
            Field("目标金额（元）", money, { money = it }, "0.00")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SAVE_TYPES.forEach { (tid, label) -> ChoiceChip(label, typeId == tid) { typeId = tid } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PLAN_ICONS.forEach { ChoiceChip(it, icon == it) { icon = it } }
            }
            if (error.isNotEmpty()) Text(error, color = palette.danger, fontSize = 12.sp)
        }
        Box(Modifier.padding(16.dp)) {
            ActionButton("保存", true) {
                val cents = toCents(money)
                if (name.isBlank()) { error = "请填写名称"; return@ActionButton }
                if (cents == null || cents <= 0) { error = "请填写大于 0 的目标金额"; return@ActionButton }
                repo.saveSavePlan(name, cents, typeId, icon, id)
                onBack()
            }
        }
    }
}

@Composable
private fun SavePlanDetail(repo: PlanRepository, id: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    var version by remember { mutableStateOf(0) }
    val plan = remember(version) { repo.savePlan(id) }
    var adding by remember { mutableStateOf(false) }
    var money by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    if (plan == null) { Missing("计划不存在", onBack); return }
    val items = remember(version) { repo.saveItems(id) }
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader(plan.name, onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassCard { SavePlanSummary(plan) }
            Text("存入记录", color = palette.textSecondary, fontSize = 12.sp)
            if (items.isEmpty()) Empty("还没有存入记录", "填一笔金额，记到这个计划里")
            items.forEach { SaveItemRow(it) }
            if (adding) {
                Field("存入金额（元）", money, { money = it }, "0.00")
                Field("备注", remark, { remark = it }, "可选")
                if (error.isNotEmpty()) Text(error, color = palette.danger, fontSize = 12.sp)
            }
        }
        when {
            confirm.isNotEmpty() -> ConfirmBar(
                text = if (confirm == "archive") "归档后不再出现在进行中。" else "删除计划会一并删掉存入记录。",
                onCancel = { confirm = "" },
                onConfirm = {
                    if (confirm == "archive") repo.archiveSavePlan(id) else repo.deleteSavePlan(id)
                    onBack()
                },
            )
            adding -> Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { ActionButton("取消", true) { adding = false } }
                Box(Modifier.weight(1f)) {
                    ActionButton("记一笔", true) {
                        val cents = toCents(money)
                        if (cents == null || cents <= 0) { error = "请填写大于 0 的金额"; return@ActionButton }
                        repo.addSaveItem(id, cents, remark)
                        money = ""; remark = ""; error = ""; adding = false; version += 1
                    }
                }
            }
            else -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("记一笔存入", plan.archived == 0L) { adding = true }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { ActionButton("编辑", true) { onOpen("save-plan-edit:$id") } }
                    Box(Modifier.weight(1f)) {
                        ActionButton(if (plan.archived == 0L) "归档" else "删除", true) {
                            confirm = if (plan.archived == 0L) "archive" else "delete"
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveItemRow(item: Save_plan_item) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("第 ${item.num} 笔", color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (item.remark.isNotEmpty()) Text(item.remark, color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(2f))
        Text("${centsToYuan(item.money)} 元", color = palette.textPrimary, fontSize = 14.sp)
    }
}

private val SAVE_TYPES = mapOf(1L to "目标计划", 2L to "定额储蓄", 3L to "自由储蓄")

@Composable
private fun ProgressBar(fraction: Float) {
    val palette = LocalPalette.current
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(palette.divider)) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(palette.brand),
        )
    }
}

@Composable
private fun StatusChip(label: String) {
    val palette = LocalPalette.current
    Text(
        label,
        color = palette.brand,
        fontSize = 11.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(palette.searchBg).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
internal fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // 统一走 theme 层玻璃胶囊：选中态不用墨色实底，用更亮更实的玻璃面 + 半粗文字。
    // 保持三参签名：50+ 调用点用尾随 lambda 传 onClick，加 modifier 会破坏全部调用。
    GlassChip(label = label, selected = selected, onClick = onClick)
}

/**
 * 列表卡（毛玻璃主题）。`manage` 包各页面的卡片统一走 theme 层的 [GlassPanel]：
 * 背板磨砂 + 玻璃填充 / 描边 / 投影 / sheen，内容保持清晰。
 * `onClick` 为空时不可点。保留本函数名与签名，60+ 调用点零改动即获得玻璃质感。
 */
@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    GlassPanel(
        modifier = modifier,
        onClick = onClick,
        content = { content() },
    )
}

/** onChange 为空时只读，用于展示不可改的字段。 */
@Composable
internal fun Field(label: String, value: String, onChange: ((String) -> Unit)? = null, hint: String = "") {
    val palette = LocalPalette.current
    Column {
        Text(label, color = palette.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange ?: {},
            readOnly = onChange == null,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = palette.textPrimary, fontSize = 15.sp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.searchBg).padding(12.dp),
            decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = palette.textSecondary, fontSize = 15.sp); inner() },
        )
    }
}

/** 空态必须给出下一步；action 为空时只显示说明。 */
@Composable
internal fun Empty(title: String, hint: String = "", action: String = "", onAction: () -> Unit = {}) {
    val palette = LocalPalette.current
    Column(
        Modifier.padding(vertical = 24.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = palette.textPrimary, fontSize = 16.sp)
        if (hint.isNotEmpty()) {
            Text(hint, color = palette.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        if (action.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            ActionButton(action, true, onAction)
        }
    }
}

@Composable
internal fun Missing(text: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LocalPalette.current.appBg)) {
        BackHeader(text, onBack)
        Empty("记录不存在或已删除", "返回上一页继续")
    }
}

@Composable
internal fun ConfirmBar(
    text: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "确认",
) {
    val palette = LocalPalette.current
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, color = palette.danger, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { ActionButton("取消", true, onCancel) }
            Box(Modifier.weight(1f)) { ActionButton(confirmLabel, true, onConfirm) }
        }
    }
}

/** 愿望清单：许愿-攒钱-达成，type_id = 0 的存钱计划。 */
@Composable
private fun WishList(repo: PlanRepository, onBack: () -> Unit) {
    val palette = LocalPalette.current
    var version by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var confirmId by remember { mutableStateOf<String?>(null) }
    val wishes = remember(version) { repo.wishes() }
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader("愿望清单", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (wishes.isEmpty()) Empty("还没有愿望", "许一个愿，慢慢攒", "许个愿") { adding = true }
            wishes.forEach { wish ->
                GlassCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(wish.name, color = palette.textPrimary, fontWeight = FontWeight.Medium)
                        Text(
                            "${centsToYuan(wish.saved_money)} / ${centsToYuan(wish.target_money)} 元",
                            color = palette.textPrimary,
                        )
                    }
                    val done = wish.status == SAVE_PLAN_DONE
                    Text(
                        if (done) "已达成" else "还差 ${centsToYuan((wish.target_money - wish.saved_money).coerceAtLeast(0))} 元",
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (!done) {
                        Text(
                            "标记达成 ›",
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp).clickable { confirmId = wish.id },
                        )
                    }
                }
            }
            if (adding) {
                Field("愿望（必填）", name, { name = it }, "比如：新手机")
                Field("目标金额（元）", target, { target = it }, "0.00")
                if (error.isNotEmpty()) Text(error, color = palette.danger, fontSize = 12.sp)
            }
        }
        when {
            confirmId != null -> ConfirmBar(
                text = "标记达成后，这个愿望会显示已达成。",
                onCancel = { confirmId = null },
                onConfirm = {
                    repo.grantSavePlan(confirmId ?: return@ConfirmBar)
                    confirmId = null
                    version++
                },
            )
            adding -> Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { ActionButton("取消", true) { adding = false; error = "" } }
                Box(Modifier.weight(1f)) {
                    ActionButton("许愿", true) {
                        val cents = toCents(target)
                        if (name.isBlank()) { error = "先给愿望起个名字"; return@ActionButton }
                        if (cents == null || cents <= 0) { error = "目标金额要大于 0"; return@ActionButton }
                        repo.addWish(name, cents)
                        name = ""; target = ""; error = ""; adding = false; version++
                    }
                }
            }
        }
    }
}
