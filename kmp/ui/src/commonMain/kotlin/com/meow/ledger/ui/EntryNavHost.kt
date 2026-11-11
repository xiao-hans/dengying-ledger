package com.meow.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import com.meow.ledger.ui.theme.DarkPalette
import com.meow.ledger.ui.theme.LightPalette
import com.meow.ledger.ui.theme.LocalPalette

/** 平台侧能力接口。Android 入口注入真实实现，预览可提供只读假实现。 */
interface EntryHost {
    fun permissions(): List<PermissionItem>

    fun requestPermissions()

    /** 逐项跳转；实现方每次只启动一个系统设置页，避免连续覆盖。 */
    fun openSettings(key: String)

    /** 对未开启项按序引导；实现方维护索引，用户返回后继续下一项。 */
    fun startGuidedSettings()

    fun openScan(): String

    fun openWebView(url: String): String

    fun openTicket(): String

    fun openPaymentCallback(result: String): String
}

data class PermissionItem(
    val key: String,
    val label: String,
    val purpose: String,
    val granted: Boolean,
    val canOpenSettings: Boolean = true,
)

/** 入口路由用可保存状态承载；返回时保留草稿与当前位置。 */
@Composable
fun EntryNavHost(
    initialRoute: String,
    host: EntryHost?,
    content: @Composable () -> Unit,
) {
    val palette = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    var route by rememberSaveable(initialRoute) { mutableStateOf(initialRoute.ifEmpty { ROUTE_MAIN }) }
    var scanResult by rememberSaveable { mutableStateOf("") }
    var webMessage by rememberSaveable { mutableStateOf("") }
    var ticketMessage by rememberSaveable { mutableStateOf("") }
    var callbackMessage by rememberSaveable { mutableStateOf("") }
    var permissionChecked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialRoute) {
        if (initialRoute.isEmpty()) return@LaunchedEffect
        route = initialRoute
        when {
            initialRoute == ROUTE_SCAN && host != null -> scanResult = host.openScan()
            initialRoute == ROUTE_WEBVIEW && host != null -> webMessage = host.openWebView("https://meow-config.aiyouaiyou.cn/api/help")
            initialRoute == ROUTE_TICKET && host != null -> ticketMessage = host.openTicket()
            initialRoute.startsWith(ROUTE_WX) && host != null -> callbackMessage = host.openPaymentCallback(initialRoute)
        }
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.appBg)
                .statusBarsPadding(),
        ) {
            when (route) {
                ROUTE_MAIN -> {
                    if (host != null && !permissionChecked) {
                        PermissionGate(
                            items = host.permissions(),
                            onRequest = {
                                host.requestPermissions()
                                permissionChecked = true
                            },
                            onContinue = { permissionChecked = true },
                            onGuidedSettings = host::startGuidedSettings,
                        )
                    } else {
                        content()
                    }
                }

                ROUTE_PERMISSION, ROUTE_SPLASH -> {
                    if (host == null) {
                        content()
                    } else {
                        PermissionGate(
                            items = host.permissions(),
                            onRequest = {
                                host.requestPermissions()
                                permissionChecked = true
                                route = ROUTE_MAIN
                            },
                            onContinue = {
                                permissionChecked = true
                                route = ROUTE_MAIN
                            },
                            onGuidedSettings = host::startGuidedSettings,
                        )
                    }
                }

                ROUTE_SCAN, ROUTE_WEBVIEW, ROUTE_TICKET, ROUTE_CAPTURE,
                ROUTE_BILL_EDIT, ROUTE_INTERSTITIAL,
                -> EntryPage(
                    title = entryTitle(route),
                    message = when (route) {
                        ROUTE_SCAN -> scanResult.ifEmpty { "扫描账本二维码或手动输入分享码。" }
                        ROUTE_WEBVIEW -> webMessage.ifEmpty { "打开帮助与协议页面。" }
                        ROUTE_TICKET -> ticketMessage.ifEmpty { "选择票据并识别金额、商户和日期。" }
                        ROUTE_CAPTURE -> "截屏结果会进入待确认队列，确认后才入账。"
                        ROUTE_BILL_EDIT -> "打开记一笔，保留未提交的内容。"
                        else -> "此入口只负责跳转，不展示广告。"
                    },
                    onBack = { route = ROUTE_MAIN },
                )

                else -> if (route.startsWith(ROUTE_WX)) {
                    EntryPage(
                        title = "支付结果",
                        message = callbackMessage.ifEmpty { "等待支付回调。" },
                        onBack = { route = ROUTE_MAIN },
                    )
                } else {
                    content()
                }
            }
        }
    }
}

@Composable
private fun PermissionGate(
    items: List<PermissionItem>,
    onRequest: () -> Unit,
    onContinue: () -> Unit,
    onGuidedSettings: () -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("进入账本前", color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "权限按功能单独开启。拒绝后仍可手工记账，相关自动功能会明确标注。",
            color = palette.textSecondary,
            fontSize = 13.sp,
        )
        items.forEach { item ->
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(palette.cardBg)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.label, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(item.purpose, color = palette.textSecondary, fontSize = 12.sp)
                    Text(
                        if (item.granted) "已开启" else "未开启",
                        color = if (item.granted) palette.textSecondary else palette.danger,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = palette.brand, contentColor = palette.cardBg),
        ) { Text("申请常用权限") }
        Button(
            onClick = {
                onRequest()
                onGuidedSettings()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = palette.chipFill, contentColor = palette.textPrimary),
        ) { Text("逐项去设置") }
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = palette.chipFill, contentColor = palette.textPrimary),
        ) { Text("继续使用本地记账") }
    }
}

@Composable
private fun EntryPage(title: String, message: String, onBack: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.cardBg)
                .padding(14.dp),
        ) {
            Text(message, color = palette.textSecondary, fontSize = 14.sp)
        }
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = palette.brand, contentColor = palette.cardBg),
        ) { Text("返回账本") }
    }
}

private fun entryTitle(route: String): String = when (route) {
    ROUTE_SCAN -> "扫码加入账本"
    ROUTE_WEBVIEW -> "帮助与协议"
    ROUTE_TICKET -> "票据识别"
    ROUTE_CAPTURE -> "截屏记一笔"
    ROUTE_BILL_EDIT -> "记一笔"
    else -> "入口"
}

const val ROUTE_MAIN = "main"
const val ROUTE_SPLASH = "splash"
const val ROUTE_PERMISSION = "permission"
const val ROUTE_SCAN = "scan"
const val ROUTE_WEBVIEW = "webview"
const val ROUTE_TICKET = "ticket"
const val ROUTE_CAPTURE = "capture"
const val ROUTE_BILL_EDIT = "bill-edit"
const val ROUTE_INTERSTITIAL = "interstitial"
const val ROUTE_WX = "wx"
