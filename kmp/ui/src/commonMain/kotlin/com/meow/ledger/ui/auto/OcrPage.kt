package com.meow.ledger.ui.auto

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.ui.PickImageButton
import com.meow.ledger.ui.platformOcrName
import com.meow.ledger.ui.theme.LocalPalette

/**
 * 截屏 OCR 页。三步：
 * 1. 选图（引擎接入时自动识别）或粘贴支付文本；
 * 2. 结果只进待确认队列，绝不静默入账；
 * 3. 金额、商户都可以在确认前改。
 */
@Composable
fun OcrPage(
    onBack: () -> Unit,
    onCaptureText: (String) -> String,
    onOpenPending: () -> Unit,
) {
    val palette = LocalPalette.current
    var pasted by rememberSaveable { mutableStateOf("") }
    var pickedPath by rememberSaveable { mutableStateOf("") }
    var manualMoney by rememberSaveable { mutableStateOf("") }
    var manualMerchant by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 104.dp)) {
        BackHeader("票据识别", onBack)
        Column(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
            Text("识别方式", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (platformOcrName == null) "文字识别引擎未接入。可以先选图登记，或粘贴支付文本提取金额与商户。"
                else "使用 " + platformOcrName + " 识别图片文字。",
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            PickImageButton(enabled = true, modifier = Modifier.fillMaxWidth()) { path, ocrText ->
                pickedPath = path
                if (ocrText.isNullOrBlank()) {
                    message = "图片已登记：" + path.substringAfterLast('/')
                } else {
                    message = onCaptureText(ocrText)
                }
            }
            if (pickedPath.isNotEmpty()) {
                Text(pickedPath, color = palette.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }

            Spacer(Modifier.height(12.dp))
            TextFieldCard(
                value = pasted,
                onValueChange = { pasted = it },
                hint = "例如：在星巴克消费 ¥35.50 支付成功",
                minLines = 3,
            )
            Spacer(Modifier.height(10.dp))
            ActionButton("识别文本并加入待确认", pasted.isNotBlank()) {
                message = onCaptureText(pasted)
                pasted = ""
            }

            Text("手动登记", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                TextFieldCard(
                    value = manualMoney,
                    onValueChange = { manualMoney = it },
                    hint = "金额",
                    modifier = Modifier.weight(1f),
                )
                TextFieldCard(
                    value = manualMerchant,
                    onValueChange = { manualMerchant = it },
                    hint = "商户",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            ActionButton(
                "登记为待确认",
                manualMoney.toDoubleOrNull() != null && manualMerchant.isNotBlank(),
            ) {
                message = onCaptureText("在" + manualMerchant + "消费¥" + manualMoney)
                manualMoney = ""
                manualMerchant = ""
            }

            if (message.isNotEmpty()) {
                Text(message, color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(12.dp))
            ActionButton("去待确认队列", true, onOpenPending)
        }
    }
}

/** 与 CapturePage 同款的圆角输入卡。 */
@Composable
private fun TextFieldCard(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
) {
    val palette = LocalPalette.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = palette.textPrimary, fontSize = 15.sp),
        minLines = minLines,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.searchBg)
            .padding(14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, color = palette.textSecondary, fontSize = 14.sp)
                inner()
            }
        },
    )
}
