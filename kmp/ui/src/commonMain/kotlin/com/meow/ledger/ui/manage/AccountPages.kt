package com.meow.ledger.ui.manage

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.shared.auto.KEY_APP_LOCK
import com.meow.ledger.shared.auto.KEY_THEME
import com.meow.ledger.shared.auto.SettingsStore
import com.meow.ledger.shared.auto.THEME_DARK
import com.meow.ledger.shared.auto.THEME_LIGHT
import com.meow.ledger.shared.auto.THEME_SYSTEM
import com.meow.ledger.shared.bills.centsToYuan
import com.meow.ledger.shared.bills.toCents
import com.meow.ledger.shared.manage.AccountRepository
import com.meow.ledger.shared.manage.REMOTE_DEFAULTS
import com.meow.ledger.shared.manage.VIP_PLANS
import com.meow.ledger.ui.auto.ActionButton
import com.meow.ledger.ui.auto.BackHeader
import com.meow.ledger.ui.theme.LocalPalette
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** 账号、会员、导入、合约、反馈、应用锁、设置。 */
@Composable
fun AccountPage(
    page: String,
    account: AccountRepository,
    settings: SettingsStore,
    onTheme: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    var notice by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        when (page) {
            "account" -> AccountHome(account, onBack) { notice = it }
            "vip" -> VipHome(account, onBack) { notice = it }
            "imports" -> ImportHome(account, onBack) { notice = it }
            "contract" -> ContractHome(account, onBack) { notice = it }
            "feedback" -> FeedbackHome(account, onBack) { notice = it }
            "lock" -> LockHome(settings, onBack) { notice = it }
            "settings" -> SettingsHome(settings, account, onTheme, onClear, onBack) { notice = it }
        }
        if (notice.isNotEmpty()) Text(notice, color = LocalPalette.current.danger, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun AccountHome(account: AccountRepository, onBack: () -> Unit, onNotice: (String) -> Unit) {
    val palette = LocalPalette.current
    var version by remember { mutableStateOf(0) }
    val profile = remember(version) { account.profile() }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf(profile.nickname) }
    var confirm by remember { mutableStateOf(false) }
    BackHeader("账号", onBack)
    GlassCard {
        Text(profile.nickname.ifBlank { "未登录" }, color = palette.textPrimary)
        Text(
            when {
                profile.phone.isNotBlank() -> "手机 ${profile.phone}"
                profile.bind_type.isNotBlank() -> "已绑定 ${profile.bind_type}"
                else -> "还没有登录"
            },
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Field("昵称", nickname, { nickname = it }, "显示在我的页面")
    ActionButton("保存昵称", nickname.isNotBlank()) {
        if (account.updateNickname(nickname) == null) onNotice("昵称不能是空的")
        else { version += 1; onNotice("昵称已保存") }
    }
    Field("手机号", phone, { phone = it }, "11 位")
    ActionButton("获取验证码", phone.length == 11) {
        val value = account.sendCode(phone)
        if (value == null) onNotice("手机号要是 1 开头的 11 位")
        else { sent = value; onNotice("验证码已生成，填到下面") }
    }
    if (sent.isNotEmpty()) Text("本机验证码 $sent，五分钟内有效", color = palette.textSecondary)
    Field("验证码", code, { code = it }, "六位数字")
    ActionButton("登录", code.length == 6) {
        if (account.loginByPhone(phone, code) == null) onNotice("验证码不对或已过期")
        else { version += 1; onNotice("已登录") }
    }
    ActionButton("以本机身份登录", true) { account.loginByDevice(); version += 1; onNotice("已用本机身份登录") }
    if (profile.bind_type.isNotBlank()) {
        if (confirm) ConfirmBar("解除绑定？登录状态还在，只是不再关联 ${profile.bind_type}。", { confirm = false }, {
            account.unbind(); confirm = false; version += 1; onNotice("已解除绑定")
        }, "解除")
        else ActionButton("解除绑定", true) { confirm = true }
    }
}

@Composable
private fun VipHome(account: AccountRepository, onBack: () -> Unit, onNotice: (String) -> Unit) {
    val palette = LocalPalette.current
    var version by remember { mutableStateOf(0) }
    val profile = remember(version) { account.profile() }
    val orders = remember(version) { account.orders() }
    BackHeader("会员", onBack)
    GlassCard {
        Text(if (profile.vip == 1L) "会员有效" else "还不是会员", color = palette.textPrimary)
        if (profile.vip_expire > 0) Text("到期 ${dateOf(profile.vip_expire)}", color = palette.textSecondary, modifier = Modifier.padding(top = 4.dp))
    }
    VIP_PLANS.forEach { plan ->
        GlassCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${plan.name} · ${plan.days} 天", color = palette.textPrimary)
                Text("${centsToYuan(plan.money)} 元", color = palette.textPrimary)
            }
            ActionButton("开通", true) {
                if (account.createOrder(plan.id) == null) onNotice("没有这个套餐")
                else { version += 1; onNotice("${plan.name}已开通") }
            }
        }
    }
    if (orders.isEmpty()) Empty("还没有订单", "开通后订单会列在这里")
    orders.forEach { order ->
        Text("${order.product_name} · ${centsToYuan(order.money)} 元 · ${dateOf(order.create_time)}", color = palette.textSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ImportHome(account: AccountRepository, onBack: () -> Unit, onNotice: (String) -> Unit) {
    var version by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf("") }
    val rows = remember(version) { account.imports() }
    BackHeader("导入账单", onBack)
    Text("这里只登记导入记录。每行算一笔，账单正文原样留存，删除记录不会删账单。", color = LocalPalette.current.textSecondary)
    Field("名称", name, { name = it }, "例如 10 月支付宝")
    Field("内容（每行一笔）", content, { content = it }, "早餐 12.5")
    ActionButton("登记", content.isNotBlank()) {
        val count = content.lines().count { it.isNotBlank() }
        account.recordImport(name, count, content)
        name = ""; content = ""; version += 1; onNotice("已登记 $count 笔")
    }
    if (rows.isEmpty()) Empty("还没有导入记录", "粘贴一份账单文本就能登记")
    rows.forEach { row ->
        GlassCard {
            Text("${row.name} · ${row.count} 笔", color = LocalPalette.current.textPrimary)
            Text(row.content.lineSequence().firstOrNull().orEmpty(), color = LocalPalette.current.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (pending == row.id) ConfirmBar("删除导入记录「${row.name}」？账单本身不会被删除。", { pending = "" }, {
            account.deleteImport(row.id); pending = ""; version += 1; onNotice("记录已删除")
        }, "删除")
        else ActionButton("删除记录", true) { pending = row.id }
    }
}

@Composable
private fun ContractHome(account: AccountRepository, onBack: () -> Unit, onNotice: (String) -> Unit) {
    var version by remember { mutableStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    val contract = remember(version) { account.contract() }
    val plan = VIP_PLANS.first()
    BackHeader("自动续费", onBack)
    if (contract.signed == 1L) {
        GlassCard {
            Text("已签约 ${contract.product_name}", color = LocalPalette.current.textPrimary)
            Text("${contract.provider} · ${centsToYuan(contract.money)} 元 · 下次 ${contract.next_date}", color = LocalPalette.current.textSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        if (confirm) ConfirmBar("解除自动续费？到期后不再扣费，已开通的会员不受影响。", { confirm = false }, {
            if (account.unsignContract()) { version += 1; onNotice("已解约") } else onNotice("当前没有生效的合约")
            confirm = false
        }, "解约")
        else ActionButton("解除自动续费", true) { confirm = true }
    } else {
        Empty("没有自动续费", "签约后到期会按月续，随时可以解约")
        ActionButton("签约${plan.name}", true) {
            account.signContract("local", plan.name, plan.money, "下月同日")
            version += 1
            onNotice("已签约 ${plan.name}")
        }
    }
}

@Composable
private fun FeedbackHome(account: AccountRepository, onBack: () -> Unit, onNotice: (String) -> Unit) {
    var version by remember { mutableStateOf(0) }
    var content by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    val rows = remember(version) { account.feedback() }
    BackHeader("反馈", onBack)
    Field("问题", content, { content = it }, "至少 5 个字")
    Field("联系方式", contact, { contact = it }, "可选")
    ActionButton("提交", content.trim().length >= 5) {
        if (account.addFeedback(content, contact) == null) onNotice("请至少写 5 个字")
        else { content = ""; contact = ""; version += 1; onNotice("已收到") }
    }
    rows.forEach { row ->
        Text(row.content, color = LocalPalette.current.textPrimary, modifier = Modifier.padding(top = 10.dp))
        if (row.contact.isNotBlank()) Text(row.contact, color = LocalPalette.current.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun LockHome(settings: SettingsStore, onBack: () -> Unit, onNotice: (String) -> Unit) {
    var version by remember { mutableStateOf(0) }
    val current = remember(version) { settings.text(KEY_APP_LOCK) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    BackHeader("应用锁", onBack)
    Text(if (current.isEmpty()) "还没有设置口令" else "已设置四位口令", color = LocalPalette.current.textSecondary)
    Field("四位数字", pin, { if (it.length <= 4 && it.all(Char::isDigit)) pin = it }, "例如 2580")
    ActionButton("保存口令", pin.length == 4) {
        settings.setText(KEY_APP_LOCK, pin)
        pin = ""
        version += 1
        onNotice("口令已保存")
    }
    if (current.isNotEmpty()) {
        if (confirm) ConfirmBar("关闭应用锁？下次打开不再需要口令。", { confirm = false }, {
            settings.setText(KEY_APP_LOCK, "")
            confirm = false
            version += 1
            onNotice("应用锁已关闭")
        }, "关闭")
        else ActionButton("关闭应用锁", true) { confirm = true }
    }
}

@Composable
private fun SettingsHome(
    settings: SettingsStore,
    account: AccountRepository,
    onTheme: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val palette = LocalPalette.current
    var version by remember { mutableStateOf(0) }
    val theme = remember(version) { settings.text(KEY_THEME, THEME_SYSTEM) }
    val remote = remember(version) { account.remoteAll() }
    var confirm by remember { mutableStateOf(false) }
    BackHeader("设置", onBack)
    Text("主题", color = palette.textSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        listOf(THEME_SYSTEM to "跟随系统", THEME_LIGHT to "浅色", THEME_DARK to "深色").forEach { (value, label) ->
            ChoiceChip(label, theme == value) {
                settings.setText(KEY_THEME, value)
                onTheme(value)
                version += 1
            }
        }
    }
    Text("远程开关", color = palette.textSecondary, modifier = Modifier.padding(top = 8.dp))
    REMOTE_DEFAULTS.keys.forEach { key ->
        val value = remote[key].orEmpty()
        val numeric = value.toIntOrNull()?.let { it > 1 } == true
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(remoteLabel(key), color = palette.textPrimary)
            if (numeric) Text(value, color = palette.textSecondary)
            else ChoiceChip(if (value == "1") "开" else "关", value == "1") {
                account.setRemote(key, if (value == "1") "0" else "1")
                version += 1
            }
        }
    }
    if (confirm) ConfirmBar("清空本机全部数据？账单、账本和设置都会消失，不能恢复。", { confirm = false }, onClear, "清空")
    else ActionButton("清空本机数据", true) { confirm = true }
    if (version < 0) onNotice("")
}

private fun remoteLabel(key: String) = when (key) {
    "auto_bill_enabled" -> "自动记账"
    "vip_banner" -> "会员入口"
    "feedback_enabled" -> "反馈入口"
    "max_books" -> "账本上限"
    else -> key
}

private fun dateOf(millis: Long): String {
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
}
