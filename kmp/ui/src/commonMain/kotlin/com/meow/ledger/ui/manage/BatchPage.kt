package com.meow.ledger.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.shared.bills.BillsRepository
import com.meow.ledger.shared.bills.CategoryRepository
import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.bills.netPaid
import com.meow.ledger.ui.AppGraph
import com.meow.ledger.ui.auto.BackHeader
import com.meow.ledger.ui.bills.ConfirmBox
import com.meow.ledger.ui.theme.LocalPalette
import com.meow.ledger.ui.theme.glassCard

private fun moneyYuan(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}

/** 批量修改页：选中一个月里的多笔账单，一起改分类（逐笔入队 Bills/Update）。 */
@Composable
internal fun BatchPage(onBack: () -> Unit) {
    val palette = LocalPalette.current
    val database = AppGraph.database
    val bills = remember { BillsRepository(database, AppGraph.sync) }
    val cates = remember { CategoryRepository(database) }
    val cateNames = remember {
        (cates.ofType(TYPE_EXPENSE) + cates.ofType(TYPE_INCOME)).associate { it.id to it.title }
    }

    // 默认月份：库里最新一笔账单的月份；空库回退 202611。
    var yearMonth by rememberSaveable {
        mutableIntStateOf(
            database.billQueries.selectAll().executeAsList().maxOfOrNull { (it.day / 100).toInt() } ?: 202611,
        )
    }
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var pickingCate by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf<Long?>(null) }
    var doneCount by rememberSaveable { mutableIntStateOf(0) }

    val view = remember(yearMonth, doneCount) { bills.month(yearMonth) }
    val flat = view.groups.flatMap { it.bills }


    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BackHeader("批量修改", onBack)

        // 月份切换，显式展示当前值。
        Row(
            Modifier.fillMaxWidth().glassCard(elevation = 2.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", fontSize = 22.sp, color = palette.textSecondary,
                modifier = Modifier.clip(CircleShape).clickable { yearMonth -= 1 }.padding(horizontal = 10.dp))
            Spacer(Modifier.weight(1f))
            Text("${yearMonth / 100} 年 ${yearMonth % 100} 月 · ${flat.size} 笔", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("›", fontSize = 22.sp, color = palette.textSecondary,
                modifier = Modifier.clip(CircleShape).clickable { yearMonth += 1 }.padding(horizontal = 10.dp))
        }

        if (flat.isEmpty()) {
            GlassCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("这个月还没有账单", fontWeight = FontWeight.SemiBold)
                    Text("先去记一笔，或用 ‹ › 切到有账单的月份", fontSize = 12.sp, color = palette.textSecondary)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selected.size == flat.size) "取消全选" else "全选",
                    color = palette.brand,
                    modifier = Modifier.clickable {
                        selected = if (selected.size == flat.size) emptySet() else flat.map { it.client_id }.toSet()
                    }.padding(vertical = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                Text("已选 ${selected.size} 笔", fontSize = 12.sp, color = palette.textSecondary)
            }

            flat.forEach { bill ->
                val checked = bill.client_id in selected
                GlassCard(onClick = {
                    selected = if (checked) selected - bill.client_id else selected + bill.client_id
                }) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // 复选圆圈
                        Box(
                            Modifier.size(22.dp).clip(CircleShape)
                                .background(if (checked) palette.brand else palette.searchBg)
                                .border(1.dp, if (checked) palette.brand else palette.divider, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (checked) Text("✓", fontSize = 13.sp, color = palette.cardBg)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(bill.desc.ifBlank { "无备注" }, maxLines = 1)
                            Text(
                                "${bill.day} · ${cateNames[bill.member_cate_id] ?: "未分类"}",
                                fontSize = 12.sp, color = palette.textSecondary,
                            )
                        }
                        Text(
                            moneyYuan(netPaid(bill.money, 0)),
                            color = if (bill.type == TYPE_INCOME) palette.brand else palette.textPrimary,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(if (selected.isEmpty()) palette.searchBg else palette.brand)
                .clickable(enabled = selected.isNotEmpty()) { pickingCate = true }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (selected.isEmpty()) "先勾选要改的账单" else "改分类（${selected.size} 笔）",
                color = if (selected.isEmpty()) palette.textSecondary else palette.cardBg,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (doneCount > 0) {
            Text("已改 $doneCount 笔，可继续选择或返回", fontSize = 12.sp, color = palette.textSecondary)
        }
    }

    // 分类选择对话框
    if (pickingCate) {
        Box(Modifier.fillMaxSize().background(palette.glassShadow).clickable { pickingCate = false },
            contentAlignment = Alignment.Center) {
            GlassCard {
                Column(Modifier.padding(16.dp).height(320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("改成哪个分类", fontWeight = FontWeight.SemiBold)
                    listOf("支出" to TYPE_EXPENSE, "收入" to TYPE_INCOME).forEach { (label, type) ->
                        Text(label, fontSize = 12.sp, color = palette.textSecondary)
                        cates.ofType(type).forEach { cate ->
                            Text(
                                cate.title,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable { confirming = cate.id }.padding(vertical = 8.dp, horizontal = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // 二次确认：写明后果
    confirming?.let { cateId ->
        ConfirmBox(
            title = "确认修改",
            message = "把选中的 ${selected.size} 笔账单改为「${cateNames[cateId] ?: "分类 $cateId"}」。改动立即生效，之后仍可再次批量修改。",
            onConfirm = {
                val n = bills.batchUpdate(0, selected.toList(), cateId)
                doneCount = n
                selected = emptySet()
                confirming = null
                pickingCate = false
            },
            onCancel = { confirming = null },
        )
    }
}
