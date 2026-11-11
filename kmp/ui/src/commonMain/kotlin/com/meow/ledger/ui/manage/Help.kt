package com.meow.ledger.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.ui.auto.BackHeader
import com.meow.ledger.ui.theme.LocalPalette

/** 帮助与协议。文案为本复刻自行撰写，不含原 App 的协议文本。 */
@Composable
fun HelpPage(onBack: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BackHeader("帮助与协议", onBack)
        sections.forEach { (title, body) ->
            GlassCard {
                Text(title, color = palette.textPrimary, fontWeight = FontWeight.Medium)
                Text(body, color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Text("喵啊记账 复刻版 v1.8.1", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

private val sections = listOf(
    "怎么记账" to "在账单页点右下角的加号，选分类、填金额就能记下。返回时草稿还在，提交成功才清空。",
    "自动记账" to "打开自动记账后，把通知、截屏或无障碍识别到的文字粘进来。识别结果先进入待确认，你核对后才入账，不会悄悄记账。",
    "预算" to "在我的里设置本月预算，也可以给单个分类设上限。超过预算的数字会标红。",
    "导入和导出" to "导出给出全部账单的 CSV，复制即可。导入时把 CSV 粘回来，重复的账单会自动跳过。",
    "应用锁" to "设置四位数字口令后，每次打开都要先输入。忘记口令只能清空本机数据。",
    "数据在哪里" to "账单、账本和设置都存在这台设备上。清空本机会删掉全部数据，且不能恢复。",
    "使用协议" to "本应用是功能复刻，供学习与个人记账使用。金额以整数分计算，避免小数累加误差。",
)
