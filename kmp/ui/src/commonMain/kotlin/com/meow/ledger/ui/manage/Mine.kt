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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.shared.db.Member_profile
import com.meow.ledger.ui.theme.LocalPalette

/** 我的首页。只负责入口，具体页面由导航按 key 打开。 */
@Composable
fun MineHome(profile: Member_profile, onOpen: (String) -> Unit) {
    val palette = LocalPalette.current
    val name = profile.nickname.ifBlank { "未登录" }
    val vip = if (profile.vip == 1L) "会员" else "普通用户"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)) {
        Text("我的", color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        GlassCard(onClick = { onOpen("account") }, modifier = Modifier.padding(top = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, color = palette.textPrimary, fontWeight = FontWeight.Medium)
                Text(vip, color = palette.textSecondary)
            }
            Text(profile.phone.ifBlank { "登录后可以同步账本" }, color = palette.textSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        entries.forEach { (key, title, hint) ->
            GlassCard(onClick = { onOpen(key) }) {
                Text(title, color = palette.textPrimary)
                Text(hint, color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

private val entries = listOf(
    Triple("books", "账本", "新建、共享、成员"),
    Triple("assets", "资产", "账户余额和隐藏账户"),
    Triple("categories", "分类与标签", "支出、收入分类和标签"),
    Triple("fixed", "周期账", "到期只提醒，不自动入账"),
    Triple("installments", "分期", "每期金额和还款日"),
    Triple("save-plans", "存钱计划", "目标和已存金额"),
    Triple("imports", "导入账单", "记录导入过的文件"),
    Triple("vip", "会员", "开通和订单"),
    Triple("contract", "自动续费", "签约和解约"),
    Triple("feedback", "反馈", "问题和联系方式"),
    Triple("lock", "应用锁", "四位口令"),
    Triple("settings", "设置", "主题、提醒、清空数据"),
)
