package com.meow.ledger.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.shared.bills.DayGroup
import com.meow.ledger.shared.bills.MonthSummary
import com.meow.ledger.shared.bills.netPaid
import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.bills.TYPE_TRANSFER
import com.meow.ledger.shared.db.Bill
import com.meow.ledger.ui.theme.LocalPalette

private val FILTERS = listOf("全部", "支出", "收入", "转账")
private val FILTER_TYPES = listOf(0L, TYPE_EXPENSE, TYPE_INCOME, TYPE_TRANSFER)

/** 0 表示不限类型；搜索只匹配备注，空串不过滤。 */
private fun matches(bill: Bill, type: Long, query: String): Boolean {
    if (type != 0L && bill.type != type) return false
    val q = query.trim()
    return q.isEmpty() || bill.desc.contains(q, ignoreCase = true)
}

/** 账单首页，对齐 src/features/home 的 BillsHome：品牌头、搜索胶囊、筛选 chip、月汇总卡、按天分组。 */
@Composable
fun BillsHome(
    summary: MonthSummary,
    groups: List<DayGroup>,
    onAdd: () -> Unit = {},
    onOpen: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val shown = groups.mapNotNull { group ->
        val bills = group.bills.filter { matches(it, FILTER_TYPES[filter], query) }
        if (bills.isEmpty()) null else group.copy(bills = bills)
    }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 104.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(palette.brand),
                contentAlignment = Alignment.Center,
            ) {
                Text("咪", color = palette.cardBg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "咪啊记账",
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.4.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(36.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = palette.textPrimary)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 10.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.searchBg)
                .border(1.dp, palette.divider, RoundedCornerShape(999.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = palette.textPrimary, fontSize = 14.sp),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("搜索备注", color = palette.textSecondary, fontSize = 14.sp)
                    inner()
                },
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAdd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(palette.brand),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = palette.cardBg, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("快捷记账", color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            FILTERS.forEachIndexed { index, label ->
                Chip(label, selected = index == filter) { filter = index }
            }
        }

        SummaryCard(summary, Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp))

        if (shown.isEmpty()) {
            Text(
                if (groups.isEmpty()) "这个月还没有账单，点右下角记一笔" else "没有符合条件的账单",
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 48.dp).align(Alignment.CenterHorizontally),
            )
        } else {
            shown.forEach { DaySection(it, onOpen) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .shadow(if (selected) 2.dp else 0.dp, shape, ambientColor = palette.glassShadow, spotColor = palette.glassShadow)
            .clip(shape)
            .background(if (selected) palette.chipSelectedFill else palette.chipFill)
            .border(1.dp, palette.glassBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) palette.textPrimary else palette.textSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SummaryCard(summary: MonthSummary, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(18.dp), ambientColor = palette.glassShadow, spotColor = palette.glassShadow)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.cardBg)
            .border(1.dp, palette.divider, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            SummaryCell("本月支出", summary.expense, palette.danger, Modifier.weight(1f))
            SummaryCell("本月收入", summary.income, palette.brand, Modifier.weight(1f))
            SummaryCell("结余", summary.income - summary.expense, palette.textPrimary, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text("${summary.count} 笔", color = palette.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun SummaryCell(label: String, cents: Long, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val palette = LocalPalette.current
    Column(modifier) {
        Text(label, color = palette.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(formatYuan(cents), color = valueColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DaySection(group: DayGroup, onOpen: (String) -> Unit) {
    val palette = LocalPalette.current
    Text(
        formatDay(group.day),
        color = palette.textSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
    )
    Column(
        Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(18.dp), ambientColor = palette.glassShadow, spotColor = palette.glassShadow)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.cardBg)
            .border(1.dp, palette.divider, RoundedCornerShape(18.dp)),
    ) {
        group.bills.forEachIndexed { index, bill ->
            BillRow(bill, group.refunded[bill.client_id] ?: 0, onOpen)
            if (index != group.bills.lastIndex) {
                Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(palette.divider))
            }
        }
    }
}

@Composable
private fun BillRow(bill: Bill, refunded: Long, onOpen: (String) -> Unit) {
    val palette = LocalPalette.current
    val sign = when (bill.type) {
        TYPE_INCOME -> "+"
        TYPE_TRANSFER -> ""
        else -> "-"
    }
    val color = when (bill.type) {
        TYPE_INCOME -> palette.brand
        TYPE_TRANSFER -> palette.textSecondary
        else -> palette.danger
    }
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable { onOpen(bill.client_id) }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(palette.searchBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(typeGlyph(bill.type), color = palette.textPrimary, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            bill.desc.ifBlank { "未填写" },
            color = palette.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text("$sign${formatYuan(netPaid(bill.money, refunded))}", color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun typeGlyph(type: Long) = when (type) {
    TYPE_INCOME -> "收"
    TYPE_TRANSFER -> "转"
    else -> "支"
}

/** 分转元，保留两位小数，不用 double 累加。 */
fun formatYuan(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}

/** yyyyMMdd 转 MM月dd日。 */
fun formatDay(day: Long): String {
    val month = (day / 100 % 100).toString().padStart(2, '0')
    val date = (day % 100).toString().padStart(2, '0')
    return "${month}月${date}日"
}
