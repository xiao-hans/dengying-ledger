package com.meow.ledger.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
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
import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.bills.TYPE_TRANSFER
import com.meow.ledger.ui.PickImageButton
import com.meow.ledger.shared.bills.centsToYuan
import com.meow.ledger.shared.bills.toCents
import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.Category
import com.meow.ledger.ui.manage.ChoiceChip
import com.meow.ledger.ui.theme.GlassChip
import com.meow.ledger.ui.theme.GlassPanel
import com.meow.ledger.ui.theme.LocalPalette

private val TYPES = listOf(TYPE_EXPENSE to "支出", TYPE_INCOME to "收入", TYPE_TRANSFER to "转账")

/** 记一笔 / 编辑一笔。existing 非空时沿用原账单的 client_id，保存即覆盖。 */
@Composable
fun BillEntry(
    today: Long,
    categories: List<Category>,
    onBack: () -> Unit,
    onSave: (Bill) -> Unit,
    existing: Bill? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    var type by rememberSaveable { mutableStateOf(existing?.type ?: TYPE_EXPENSE) }
    var money by rememberSaveable { mutableStateOf(existing?.let { centsToYuan(it.money) } ?: "") }
    var cateId by rememberSaveable { mutableStateOf(existing?.member_cate_id ?: 1L) }
    var desc by rememberSaveable { mutableStateOf(existing?.desc ?: "") }
    var notInBudget by rememberSaveable { mutableStateOf(existing?.not_in_budget == 1L) }
    var images by rememberSaveable { mutableStateOf(existing?.images ?: "") }
    var error by rememberSaveable { mutableStateOf("") }

    val visible = categories.filter { it.type == type || type == TYPE_TRANSFER }
    val selected = visible.firstOrNull { it.id == cateId } ?: visible.firstOrNull()

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = palette.textPrimary)
            }
            Text(if (existing == null) "记一笔" else "编辑账单", color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                TYPES.forEach { (value, label) ->
                    TypeChip(label, selected = value == type) {
                        type = value
                        // 支出默认餐饮，收入默认工资；转账沿用当前分类
                        cateId = if (value == TYPE_INCOME) 101 else 1
                    }
                }
            }

            GlassPanel(
                modifier = Modifier.padding(top = 14.dp),
                contentPadding = 16.dp,
            ) {
                Text("金额（元）", color = palette.textSecondary, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("¥", color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = money,
                        onValueChange = { money = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = palette.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (money.isEmpty()) {
                                Text("0.00", color = palette.textSecondary, fontSize = 28.sp)
                            }
                            inner()
                        },
                    )
                }
                Text("收入记为正向金额，转账不参与收支统计", color = palette.textSecondary, fontSize = 12.sp)
            }

            Text(
                "分类", color = palette.textSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(start = 2.dp, top = 18.dp, bottom = 8.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                visible.forEach { category ->
                    TypeChip(category.title, selected = category.id == selected?.id) { cateId = category.id }
                }
            }

            GlassPanel(
                modifier = Modifier.padding(top = 14.dp),
                contentPadding = 0.dp,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("备注", color = palette.textSecondary, fontSize = 12.sp)
                    androidx.compose.foundation.text.BasicTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = palette.textPrimary, fontSize = 14.sp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        decorationBox = { inner ->
                            if (desc.isEmpty()) Text("写点什么（选填）", color = palette.textSecondary, fontSize = 14.sp)
                            inner()
                        },
                    )
                }
            }

            GlassPanel(contentPadding = 0.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("图片", color = palette.textSecondary, fontSize = 12.sp)
                val picked = images.split(',').filter { it.isNotBlank() }
                if (picked.isEmpty()) {
                    Text("还没附图，可选一张凭证照片", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                } else {
                    picked.forEach { img ->
                        Text(
                            "· " + img.substringAfterLast('/'),
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp).clickable { images = picked.filter { it != img }.joinToString(",") },
                        )
                    }
                    Text("点文件名可移除", color = palette.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
                PickImageButton(enabled = true, modifier = Modifier.padding(top = 8.dp)) { path, _ ->
                    val list = images.split(',').filter { it.isNotBlank() }
                    if (path !in list) images = (list + path).joinToString(",")
                }
                }
            }

            ChoiceChip(if (notInBudget) "不计入本月预算" else "计入本月预算", notInBudget) { notInBudget = !notInBudget }

            if (error.isNotEmpty()) {
                Text(error, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(16.dp))
        }

        Row(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionButton("取消", primary = false, modifier = Modifier.weight(1f), onClick = onBack)
            ActionButton("保存账单", primary = true, modifier = Modifier.weight(2f)) {
                val cents = toCents(money)
                if (cents == null || cents == 0L) {
                    error = "请输入有效金额（最多两位小数）"
                    return@ActionButton
                }
                val category = selected
                if (category == null) {
                    error = "请选择分类"
                    return@ActionButton
                }
                error = ""
                onSave(newBill(today, type, cents, category.id, desc, notInBudget, images, existing))
            }
        }
    }
}

private fun newBill(today: Long, type: Long, cents: Long, cateId: Long, desc: String, notInBudget: Boolean, images: String, existing: Bill?) = Bill(
    member_id = existing?.member_id ?: 0,
    client_id = existing?.client_id ?: "manual-$today-${kotlin.random.Random.nextLong()}",
    money = cents, refund_money = existing?.refund_money ?: 0, type = type, exchange = existing?.exchange ?: 0,
    book_id = existing?.book_id ?: 1,
    member_cate_id = cateId, currency = existing?.currency ?: "CNY", day = existing?.day ?: today, desc = desc.trim(),
    not_in_budget = if (notInBudget) 1 else 0, images = images.ifBlank { existing?.images ?: "" }, update_date = today,
    create_date = existing?.create_date ?: today, status = existing?.status ?: 0,
    to_assets_id = existing?.to_assets_id ?: 0, from_assets_id = existing?.from_assets_id ?: 0,
    interest = existing?.interest ?: 0, interest_reckon_type = existing?.interest_reckon_type ?: 0,
    tag_ids = existing?.tag_ids ?: "", source = existing?.source ?: 0, import_id = existing?.import_id ?: 0,
    repayment_day = existing?.repayment_day ?: 0, local_create_time = existing?.local_create_time ?: today,
    local_update_time = today, installment_plan_id = existing?.installment_plan_id ?: "",
    installment_num = existing?.installment_num ?: 0,
    installment_money = existing?.installment_money ?: 0, installment_interest = existing?.installment_interest ?: 0,
)


@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    GlassChip(label = label, selected = selected, onClick = onClick)
}

@Composable
private fun ActionButton(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(if (primary) palette.brand else palette.cardBg)
            .border(1.dp, if (primary) palette.brand else palette.divider, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (primary) palette.cardBg else palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
