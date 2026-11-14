package com.meow.ledger.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.stats.PeriodTotal
import com.meow.ledger.shared.stats.Summary
import com.meow.ledger.shared.stats.TrendPeriod
import com.meow.ledger.ui.home.formatYuan
import com.meow.ledger.ui.theme.GlassChip
import com.meow.ledger.ui.theme.LocalPalette
import com.meow.ledger.ui.theme.glassCard

private val PERIODS = listOf("周" to TrendPeriod.WEEK, "月" to TrendPeriod.MONTH, "年" to TrendPeriod.YEAR)
private val TYPES = listOf("支出" to TYPE_EXPENSE, "收入" to TYPE_INCOME)

/** 统计首页：收支汇总、分类占比、近 12 期趋势。数据由调用方提供，本文件只管展示。 */
@Composable
fun StatsHome(
    summary: Summary,
    trend: List<PeriodTotal>,
    type: Long,
    period: TrendPeriod,
    onType: (Long) -> Unit,
    onPeriod: (TrendPeriod) -> Unit,
    onOpenCate: (Long) -> Unit = {},
    onOpenTags: () -> Unit = {},
    onOpenType: () -> Unit = {},
    onOpenReport: () -> Unit = {},
) {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 120.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            TYPES.forEach { (label, value) -> Chip(label, value == type) { onType(value) } }
            PERIODS.forEach { (label, value) -> Chip(label, value == period) { onPeriod(value) } }
        }

        SummaryCard(summary, Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp))

        SectionTitle("分类占比")
        if (summary.byCate.isEmpty()) {
            EmptyHint("这段时间没有${if (type == TYPE_INCOME) "收入" else "支出"}，记一笔后这里会出报表")
        } else {
            Column(
                Modifier
                    .padding(horizontal = 18.dp)
                    .card()
                    .padding(vertical = 6.dp),
            ) {
                summary.byCate.forEach { share ->
                    CategoryRow(share.name, share.cents, share.pct, type, Modifier.clickable { onOpenCate(share.id) })
                }
            }
        }

        SectionTitle("趋势")
        if (trend.all { it.expense == 0L && it.income == 0L }) {
            EmptyHint("还没有足够的账单画出趋势")
        } else {
            TrendChart(trend, type, Modifier.padding(horizontal = 18.dp).card().padding(16.dp))
        }

        SectionTitle("更多报表")
        Column(Modifier.padding(horizontal = 18.dp).card().padding(vertical = 4.dp)) {
            LinkRow("收支月报", "12 个月的支出与收入对比", onOpenType)
            LinkRow("年度报表", "全年收支与结余汇总", onOpenReport)
            LinkRow("标签", "按标签看一笔笔账单", onOpenTags)
        }
    }
}

@Composable
private fun LinkRow(title: String, hint: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(title, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(hint, color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    GlassChip(label = label, selected = selected, onClick = onClick)
}

@Composable
private fun SummaryCard(summary: Summary, modifier: Modifier) {
    val palette = LocalPalette.current
    Row(modifier.card().padding(vertical = 16.dp)) {
        Amount("支出", summary.expense, palette.danger, Modifier.weight(1f))
        Amount("收入", summary.income, palette.brand, Modifier.weight(1f))
        Amount("结余", summary.net, if (summary.net < 0) palette.danger else palette.textPrimary, Modifier.weight(1f))
    }
}

@Composable
private fun Amount(label: String, cents: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val palette = LocalPalette.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = palette.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(formatYuan(cents), color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryRow(name: String, cents: Long, pct: Int, type: Long, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val bar = if (type == TYPE_INCOME) palette.brand else palette.danger
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = palette.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${pct}%", color = palette.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            Text(formatYuan(cents), color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)).background(palette.searchBg),
        ) {
            Box(
                Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().clip(RoundedCornerShape(999.dp)).background(bar),
            )
        }
    }
}

@Composable
private fun TrendChart(trend: List<PeriodTotal>, type: Long, modifier: Modifier) {
    val palette = LocalPalette.current
    val value: (PeriodTotal) -> Long = if (type == TYPE_INCOME) { it -> it.income } else { it -> it.expense }
    val peak = trend.maxOf { value(it) }.coerceAtLeast(1L)
    val bar = if (type == TYPE_INCOME) palette.brand else palette.danger
    Row(
        modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        trend.forEach { point ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    Modifier
                        .width(10.dp)
                        .height((96f * value(point) / peak).dp.coerceAtLeast(2.dp))
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (value(point) == 0L) palette.searchBg else bar),
                )
                Spacer(Modifier.height(6.dp))
                Text(pointLabel(point.key), color = palette.textSecondary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = LocalPalette.current.textSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        color = LocalPalette.current.textSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** 内嵌在滚动内容里的统计卡片：与玻璃组件共用同一套填充 / 描边 / 投影 / sheen token。 */
@Composable
private fun Modifier.card(): Modifier = fillMaxWidth().glassCard(elevation = 2.dp)

private fun pointLabel(key: Int) = if (key > 9999) (key % 100).toString() else key.toString().takeLast(2)
