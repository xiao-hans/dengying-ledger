package com.meow.ledger.ui.auto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.shared.auto.GUIDE_STEPS
import com.meow.ledger.shared.auto.WATCH_APPS
import com.meow.ledger.shared.auto.sourceLabel
import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.bills.centsToYuan
import com.meow.ledger.shared.db.Auto_event
import com.meow.ledger.shared.db.Rule
import com.meow.ledger.ui.theme.LocalPalette

/** 待确认队列。逐条通过或忽略，绝不批量静默入账。 */
@Composable
fun PendingPage(
    events: List<Auto_event>,
    onBack: () -> Unit,
    onConfirm: (Auto_event) -> Unit,
    onIgnore: (Auto_event) -> Unit,
) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        BackHeader("待确认队列", onBack)
        if (events.isEmpty()) {
            EmptyHint("没有待确认的识别结果", "去三路采集粘贴一条支付通知试试")
        }
        events.forEach { event ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.cardBg)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(event.merchant, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    val sign = if (event.type == TYPE_INCOME) "+" else "-"
                    Text("$sign${centsToYuan(event.money)}", color = if (event.type == TYPE_INCOME) palette.brandSecondary else palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text("${sourceLabel(event.source)} · ${event.raw_text}", color = palette.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallButton("忽略", false) { onIgnore(event) }
                    SmallButton("确认入账", true) { onConfirm(event) }
                }
            }
        }
    }
}

/** 匹配规则。按关键词命中后自动带入分类与账户。 */
@Composable
fun RulesPage(
    rules: List<Rule>,
    onBack: () -> Unit,
    onAdd: (keyword: String, type: Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val palette = LocalPalette.current
    var keyword by rememberSaveable { mutableStateOf("") }
    var income by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        BackHeader("匹配规则", onBack)
        Text("关键词越长越优先。命中后，新的识别结果会带上对应的收支类型。", color = palette.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        BasicTextField(
            value = keyword,
            onValueChange = { keyword = it },
            textStyle = androidx.compose.ui.text.TextStyle(color = palette.textPrimary, fontSize = 15.sp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(999.dp)).background(palette.searchBg).padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { inner -> Box { if (keyword.isEmpty()) Text("关键词，例如 星巴克", color = palette.textSecondary, fontSize = 14.sp); inner() } },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (income) "记为收入" else "记为支出", color = palette.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(checked = income, onCheckedChange = { income = it })
        }
        ActionButton("添加规则", keyword.trim().isNotEmpty()) {
            onAdd(keyword.trim(), if (income) TYPE_INCOME else TYPE_EXPENSE)
            keyword = ""
        }
        Spacer(Modifier.height(12.dp))
        if (rules.isEmpty()) EmptyHint("还没有规则", "添加一条关键词开始自动分类")
        rules.forEach { rule ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(palette.cardBg).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(rule.key_word, color = palette.textPrimary, fontSize = 15.sp)
                    Text(if (rule.type == TYPE_INCOME) "收入" else "支出", color = palette.textSecondary, fontSize = 12.sp)
                }
                Text("删除", color = palette.danger, fontSize = 14.sp, modifier = Modifier.clickable { onDelete(rule.id) }.padding(6.dp))
            }
        }
    }
}

/** 监听白名单。从未保存过时默认前三个。 */
@Composable
fun AppsPage(
    selected: Set<String>,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        BackHeader("监听 App 白名单", onBack)
        Text("只识别白名单内 App 的文本，其余一律跳过。", color = palette.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        WATCH_APPS.forEach { app ->
            val on = app.pkg in selected
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(palette.cardBg).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(app.name, color = palette.textPrimary, fontSize = 15.sp)
                    Text(app.pkg, color = palette.textSecondary, fontSize = 11.sp)
                }
                Switch(checked = on, onCheckedChange = { onToggle(app.pkg) })
            }
        }
    }
}

/** 保活说明。Web 端没有后台进程，这里只保留开关与说明，不假装拥有系统权限。 */
@Composable
fun KeepAlivePage(on: Boolean, onBack: () -> Unit, onToggle: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        BackHeader("保活设置", onBack)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.cardBg).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("前台服务保活", color = palette.textPrimary, fontSize = 15.sp)
                Text("安卓端用前台通知降低被回收的概率", color = palette.textSecondary, fontSize = 12.sp)
            }
            Switch(checked = on, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "当前是 Web 复刻，没有常驻后台。开关只记录你的偏好，真正的保活要在安卓端配合厂商的自启动与电池白名单。",
            color = palette.textSecondary,
            fontSize = 13.sp,
        )
    }
}

/** 五步引导，对齐 GuideAutoBillActivity。 */
@Composable
fun GuidePage(onBack: () -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        BackHeader("使用引导", onBack)
        GUIDE_STEPS.forEachIndexed { index, step ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Box(
                    Modifier.height(24.dp).clip(RoundedCornerShape(999.dp)).background(palette.brand).padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", color = palette.cardBg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(step.title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(step.body, color = palette.textSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

/** 自动记账设置：自动确认开关。默认关闭，避免静默入账。 */
@Composable
fun AutoSettingsPage(autoConfirm: Boolean, onBack: () -> Unit, onToggle: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        BackHeader("自动记账设置", onBack)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.cardBg).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("命中规则后自动入账", color = palette.textPrimary, fontSize = 15.sp)
                Text("默认关闭。开启后仍只处理命中规则的结果", color = palette.textSecondary, fontSize = 12.sp)
            }
            Switch(checked = autoConfirm, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "没有命中规则的识别结果始终进入待确认队列，需要你逐条确认。",
            color = palette.textSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SmallButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier.height(36.dp).clip(RoundedCornerShape(999.dp)).background(if (filled) palette.brand else palette.searchBg).clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) palette.cardBg else palette.textPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyHint(title: String, next: String) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = palette.textPrimary, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(next, color = palette.textSecondary, fontSize = 13.sp)
    }
}
