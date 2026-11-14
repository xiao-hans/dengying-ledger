package com.meow.ledger.ui.auto

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
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
import com.meow.ledger.shared.auto.SOURCE_ACCESSIBILITY
import com.meow.ledger.shared.auto.SOURCE_NOTIFICATION
import com.meow.ledger.shared.auto.SOURCE_OCR
import com.meow.ledger.shared.auto.sourceLabel
import com.meow.ledger.ui.theme.LocalPalette
import com.meow.ledger.ui.theme.glassCard

private data class CaptureRow(val source: String, val hint: String)

private val CAPTURES = listOf(
    CaptureRow(SOURCE_NOTIFICATION, "粘贴通知栏文本"),
    CaptureRow(SOURCE_ACCESSIBILITY, "粘贴无障碍读取的文本"),
    CaptureRow(SOURCE_OCR, "粘贴截图识别出的文字"),
)

/**
 * 自动记账首页，对齐 src/features/home 的 AutoHome。
 * Web 没有系统级权限，三路采集都以粘贴文本代替；识别结果只进待确认队列。
 */
@Composable
fun AutoHome(
    enabled: Boolean,
    pendingCount: Int,
    ruleCount: Int,
    watchCount: Int,
    onToggle: (Boolean) -> Unit,
    onCapture: (source: String) -> Unit,
    onOcr: () -> Unit,
    onPending: () -> Unit,
    onRules: () -> Unit,
    onApps: () -> Unit,
    onKeepAlive: () -> Unit,
    onGuide: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 104.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "自动记账",
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "自动记账设置",
                    tint = palette.textPrimary,
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .glassCard(elevation = 2.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("总开关", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (enabled) "识别结果进入待确认队列" else "已关闭，不会采集任何文本",
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }

        if (!enabled) {
            Text(
                "打开总开关后，三路采集才会生效。",
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
            )
        }

        CAPTURES.forEach { row ->
            MenuRow(
                title = sourceLabel(row.source),
                subtitle = row.hint,
                enabled = enabled,
                onClick = { if (enabled) onCapture(row.source) },
            )
        }

        Spacer(Modifier.height(8.dp))
        MenuRow("票据识别", "选图登记或粘贴支付文本", true, onOcr)
        MenuRow("待确认队列", if (pendingCount == 0) "没有待处理的识别结果" else "$pendingCount 条等待确认", true, onPending)
        MenuRow("匹配规则", "$ruleCount 条", true, onRules)
        MenuRow("监听 App 白名单", "$watchCount 个", true, onApps)
        MenuRow("保活设置", "降低系统回收后台的概率", true, onKeepAlive)
        MenuRow("使用引导", "五步了解自动记账怎么工作", true, onGuide)
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 5.dp)
            .glassCard(elevation = 2.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) palette.textPrimary else palette.textSecondary, fontSize = 15.sp)
            Text(subtitle, color = palette.textSecondary, fontSize = 12.sp)
        }
        Text(">", color = palette.textSecondary, fontSize = 16.sp)
    }
}

/** 粘贴文本采集。没有识别出金额时给出原因，不静默丢弃。 */
@Composable
fun CapturePage(
    source: String,
    message: String?,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val palette = LocalPalette.current
    var text by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        BackHeader(sourceLabel(source), onBack)
        Text(
            "把一条或多条通知文本粘贴到下面，每行当作一条。没有金额的行会被跳过并计数。",
            color = palette.textSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = androidx.compose.ui.text.TextStyle(color = palette.textPrimary, fontSize = 15.sp),
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.searchBg)
                .padding(14.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("例如：在星巴克消费 ¥35.50 支付成功", color = palette.textSecondary, fontSize = 14.sp)
                    inner()
                }
            },
        )
        if (message != null) {
            Spacer(Modifier.height(10.dp))
            Text(message, color = palette.textPrimary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        ActionButton("识别并加入待确认", text.isNotBlank()) { onSubmit(text); text = "" }
    }
}

@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("<", color = palette.textPrimary, fontSize = 18.sp)
        }
        Spacer(Modifier.width(6.dp))
        Text(title, color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ActionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) palette.brand else palette.divider)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = palette.cardBg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
