package com.meow.ledger.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.bills.TYPE_TRANSFER
import com.meow.ledger.shared.bills.centsToYuan
import com.meow.ledger.shared.manage.ExchangeRepository
import com.meow.ledger.ui.auto.BackHeader
import com.meow.ledger.ui.theme.LocalPalette

private val filters = listOf(0L to "全部", TYPE_EXPENSE to "支出", TYPE_INCOME to "收入", TYPE_TRANSFER to "转账")

/** 搜索账单。匹配备注、分类名、账单编号、金额和日期。 */
@Composable
fun SearchPage(repo: ExchangeRepository, onOpenBill: (String) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(0L) }
    val bills = remember(query, type) { repo.search(query, type) }
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader("搜索", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Field("关键字", query, { query = it }, "备注、分类、金额或日期")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { (value, label) ->
                    ChoiceChip(label, type == value) { type = value }
                }
            }
            Text("${bills.size} 条", color = palette.textSecondary, fontSize = 12.sp)
            if (bills.isEmpty()) {
                Empty("没有匹配的账单", "换个关键字，或把类型改回全部")
            }
            bills.forEach { bill ->
                GlassCard(onClick = { onOpenBill(bill.client_id) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(bill.desc.ifBlank { "未填写备注" }, color = palette.textPrimary)
                        Text("${centsToYuan(bill.money)} 元", color = palette.textPrimary, fontSize = 13.sp)
                    }
                    Text("${bill.day}", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}
