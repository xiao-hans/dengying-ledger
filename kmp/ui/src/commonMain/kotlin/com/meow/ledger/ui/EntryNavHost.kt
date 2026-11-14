package com.meow.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.meow.ledger.shared.auto.DeepLinkTarget
import com.meow.ledger.shared.auto.DEFAULT_WEBVIEW_URL
import com.meow.ledger.shared.auto.THEME_SYSTEM
import com.meow.ledger.shared.auto.isAllowedWebViewUrl
import com.meow.ledger.ui.theme.LocalPalette
import com.meow.ledger.ui.theme.glassCard
import com.meow.ledger.ui.theme.MeowTheme
import com.meow.ledger.ui.theme.paletteOf

/** 平台侧能力接口。Android 入口注入真实实现，预览可提供只读假实现。 */
interface EntryHost {
    fun permissions(): List<PermissionItem>

    fun requestPermissions()

    /** 逐项跳转；实现方每次只启动一个系统设置页，避免连续覆盖。 */
    fun openSettings(key: String)

    /** 对未开启项按序引导；实现方维护索引，用户返回后继续下一项。 */
    fun startGuidedSettings()

    /** 扫码：优先系统扫码组件，不可用时返回兜底文案。 */
    fun openScan(): String

    /**
     * 外部网页：仅在 [allowed] 为 true 时用 ACTION_VIEW 打开；
     * 校验由 shared 的 [isAllowedWebViewUrl] 完成，false 时只回 App 内帮助页。
     */
    fun openWebView(url: String, allowed: Boolean): String

    /** 票据识别：确认 OCR 引擎可用并返回状态；真正的选图在 OcrPage。 */
    fun openTicket(): String

    /** 支付回调：解析结果并核对本地订单，返回给用户看的中文状态。 */
    fun openPaymentCallback(result: String): String

    /** 启动 MediaProjection 截屏采集；拒绝或组件缺失时返回兜底文案。 */
    fun startScreenCapture(): String
}

data class PermissionItem(
    val key: String,
    val label: String,
    val purpose: String,
    val granted: Boolean,
    val canOpenSettings: Boolean = true,
)

/** 兼容旧引用：App 内帮助页对应的官方地址，与 shared 的白名单常量保持一致。 */
const val DEFAULT_HELP_URL = DEFAULT_WEBVIEW_URL

/**
 * 入口壳：只负责权限引导、三态主题、以及深链附带的系统动作。
 * 深链目标本身交给 content(MainApp) 落到真实功能页，不再渲染说明页。
 * content 的每个二级页都通过 BackHeader / BackButton 回到底部 Dock（§6.9）。
 */
@Composable
fun EntryNavHost(
    initialTarget: DeepLinkTarget,
    host: EntryHost?,
    theme: String = THEME_SYSTEM,
    content: @Composable (DeepLinkTarget) -> Unit,
) {
    // 入口页与主界面共用同一套三态主题（跟随系统 / 浅色 / 深色），避免手动切主题后配色错乱（§6.8）。
    val palette = paletteOf(theme)
    var permissionChecked by rememberSaveable { mutableStateOf(false) }
    var banner by rememberSaveable { mutableStateOf("") }
    // 系统动作（打开浏览器/扫码/拉起截屏）只在同一个深链上执行一次；
    // launchId 让重复的磁贴点击也能重新触发，旋转屏幕则不会反复拉起外部 Activity。
    var handledKey by rememberSaveable { mutableStateOf("") }
    val key = with(initialTarget) { "$kind|$clientId|$url|$paymentResult|$launchId" }

    LaunchedEffect(key) {
        if (key == handledKey || host == null) return@LaunchedEffect
        handledKey = key
        // 平台侧的每个系统动作都自行兜底；这里再包一层，防御第三方实现抛异常。
        banner = runCatching {
            when (initialTarget.kind) {
                DeepLinkTarget.Kind.SCAN -> host.openScan()
                DeepLinkTarget.Kind.WEBVIEW -> {
                    val url = initialTarget.url.ifBlank { DEFAULT_WEBVIEW_URL }
                    host.openWebView(url, isAllowedWebViewUrl(url))
                }
                DeepLinkTarget.Kind.TICKET -> host.openTicket()
                DeepLinkTarget.Kind.CAPTURE -> host.startScreenCapture()
                DeepLinkTarget.Kind.PAYMENT -> host.openPaymentCallback(initialTarget.paymentResult)
                else -> ""
            }
        }.getOrDefault("系统功能暂不可用，已回到底部导航，可在对应页面手动操作。")
    }

    MeowTheme(theme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.appBg)
                .statusBarsPadding(),
        ) {
            // 冷启动（MAIN）或明确请求权限页时先走一次权限引导；深链直达功能不被打断。
            val needsGate = initialTarget.kind == DeepLinkTarget.Kind.MAIN ||
                initialTarget.kind == DeepLinkTarget.Kind.PERMISSION
            if (host != null && !permissionChecked && needsGate) {
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
                content(initialTarget)
            }
            if (banner.isNotEmpty()) {
                StatusBanner(banner, Modifier.align(Alignment.TopCenter)) { banner = "" }
            }
        }
    }
}

/** 系统动作的状态条：权限被拒绝或组件缺失时给出可读的兜底说明，可手动关闭。 */
@Composable
private fun StatusBanner(message: String, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .glassCard(elevation = 2.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            "关闭",
            color = palette.brand,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp).clickable(onClick = onDismiss),
        )
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .glassCard(elevation = 2.dp)
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

/** 保留路由字符串常量，供平台侧显式 EXTRA_ROUTE 与旧文档对照；导航本身用 DeepLinkTarget。 */
const val ROUTE_MAIN = "main"
const val ROUTE_CAPTURE = "capture"
const val ROUTE_BILL_EDIT = "bill-edit"
