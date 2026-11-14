package com.meow.ledger.ui.bills

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.bills.TYPE_TRANSFER
import com.meow.ledger.shared.bills.netPaid
import com.meow.ledger.shared.bills.refundedCents
import com.meow.ledger.shared.bills.toCents
import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.Refund_bill
import com.meow.ledger.ui.home.formatDay
import com.meow.ledger.ui.home.formatYuan
import com.meow.ledger.ui.theme.GlassChip
import com.meow.ledger.ui.theme.LocalPalette
import com.meow.ledger.ui.theme.glassCard

private val SOURCE = mapOf(
    0L to "手动记账",
    1L to "通知识别",
    2L to "无障碍识别",
    3L to "截屏 OCR",
    4L to "账单导入",
    5L to "周期账",
)

private fun typeName(type: Long) = when (type) {
    TYPE_INCOME -> "收入"
    TYPE_TRANSFER -> "转账"
    else -> "支出"
}

/**
 * 账单详情：完整字段、退款清单、删除与编辑入口。
 * 退款不改原账单，合计超过原金额的退款被拒绝。
 */
@Composable
fun BillDetail(
    bill: Bill,
    categoryName: String,
    refunds: List<Refund_bill>,
    message: String?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddRefund: (Long) -> Unit,
    onEditRefund: (String, Long) -> Unit,
    onRevokeRefund: (String) -> Unit,
) {
    val palette = LocalPalette.current
    val refunded = refundedCents(refunds)
    var amount by rememberSaveable { mutableStateOf("") }
    var editRefundId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var revokeId by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.background(palette.appBg)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 104.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton("返回", onClick = onBack)
                Text(
                    "账单详情",
                    Modifier.weight(1f),
                    color = palette.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .glassCard(elevation = 2.dp)
                    .padding(18.dp),
            ) {
                Text(
                    "${typeName(bill.type)} · $categoryName",
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    signedMoney(bill.type, bill.money),
                    color = palette.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (refunded > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "已退 ¥ ${formatYuan(refunded)} · 实付 ¥ ${formatYuan(netPaid(bill.money, refunded))}",
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(formatDay(bill.day), color = palette.textSecondary, fontSize = 12.sp)
            }

            SectionLabel("明细")
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .glassCard(elevation = 2.dp),
            ) {
                DetailRow("备注", bill.desc.ifBlank { "无" })
                if (bill.images.isNotBlank()) {
                    DetailRow("图片", bill.images.split(',').filter { it.isNotBlank() }.size.toString() + " 张")
                }
                DetailRow("账本", "#${bill.book_id}")
                DetailRow("账户", "#${bill.from_assets_id}")
                DetailRow("标签", bill.tag_ids.ifBlank { "无" })
                DetailRow("来源", SOURCE[bill.source] ?: "未知")
                DetailRow("票据", imageCount(bill.images))
                DetailRow("账单编号", shortId(bill.client_id))
            }

            SectionLabel("退款")
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .glassCard(elevation = 2.dp),
            ) {
                if (refunds.isEmpty()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("还没有退款", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("退款不改原账单，报表按实付金额统计", color = palette.textSecondary, fontSize = 12.sp)
                    }
                }
                refunds.forEach { refund ->
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("退款 ¥ ${formatYuan(refund.money)}", color = palette.textPrimary, fontSize = 15.sp)
                            Text(
                                "${formatDay(refund.day)} · ${refund.desc.ifBlank { "无备注" }}",
                                color = palette.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                        TextButton("改金额") { editRefundId = refund.id; amount = formatYuan(refund.money) }
                        TextButton("撤销") { revokeId = refund.id }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (editRefundId == null) "新增退款（元）" else "修改退款（元）", Modifier.weight(1f), color = palette.textPrimary, fontSize = 15.sp)
                    BasicTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.textPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(palette.brand),
                        modifier = Modifier.fillMaxWidth(0.4f),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterEnd) {
                                if (amount.isEmpty()) Text("0.00", color = palette.textSecondary, fontSize = 15.sp)
                                inner()
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (editRefundId != null) {
                    Box(Modifier.weight(1f)) {
                        ActionButton("取消修改", primary = false) { editRefundId = null; amount = "" }
                    }
                }
                Box(Modifier.weight(1f)) {
                    ActionButton(if (editRefundId == null) "记录退款" else "保存修改", primary = false) {
                        val cents = toCents(amount)
                        if (cents == null || cents <= 0) return@ActionButton
                        val id = editRefundId
                        if (id == null) onAddRefund(cents) else onEditRefund(id, cents)
                        amount = ""
                        editRefundId = null
                    }
                }
            }
            if (message != null) {
                Text(
                    message,
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = palette.danger,
                    fontSize = 12.sp,
                )
            }
            Text(
                "账单编号是端侧幂等键，离线记账后同步不会重复入账。",
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = palette.textSecondary,
                fontSize = 12.sp,
            )
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton("删除", primary = false, modifier = Modifier.weight(1f)) { confirmDelete = true }
            ActionButton("编辑这笔", modifier = Modifier.weight(2f)) { onEdit() }
        }

        if (confirmDelete) {
            ConfirmBox(
                title = "删除这笔账单？",
                message = "删除后不可恢复。",
                onConfirm = { confirmDelete = false; onDelete() },
                onCancel = { confirmDelete = false },
            )
        }
        if (revokeId != null) {
            ConfirmBox(
                title = "撤销这笔退款？",
                message = "撤销后报表按原金额重新统计。",
                onConfirm = { onRevokeRefund(revokeId!!); revokeId = null },
                onCancel = { revokeId = null },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val palette = LocalPalette.current
    Text(
        text,
        Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
        color = palette.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = palette.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = palette.textPrimary, fontSize = 14.sp)
    }
}

@Composable
private fun TextButton(label: String, onClick: () -> Unit) {
    GlassChip(label = label, selected = false, onClick = onClick)
}

@Composable
internal fun ConfirmBox(title: String, message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier.background(palette.glassShadow).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .glassCard(elevation = 8.dp)
                .padding(20.dp),
        ) {
            Text(title, color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = palette.textSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("取消", primary = false, modifier = Modifier.weight(1f), onClick = onCancel)
                ActionButton("确认", modifier = Modifier.weight(1f), onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    primary: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(if (primary) palette.brand else palette.cardBg)
            .border(1.dp, if (primary) palette.brand else palette.divider, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) palette.cardBg else palette.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun signedMoney(type: Long, cents: Long): String {
    val sign = if (type == TYPE_INCOME) "+" else "-"
    return "$sign¥ ${formatYuan(cents)}"
}

private fun imageCount(images: String): String {
    val count = images.split(',').count { it.isNotBlank() }
    return if (count == 0) "无" else "$count 张"
}

private fun shortId(id: String): String = if (id.length > 14) id.take(14) + "…" else id
