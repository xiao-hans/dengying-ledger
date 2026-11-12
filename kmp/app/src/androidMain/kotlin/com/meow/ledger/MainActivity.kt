package com.meow.ledger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.meow.ledger.platform.AndroidPermissionGateway
import com.meow.ledger.platform.BillAlarmReceiver
import com.meow.ledger.platform.PermissionStatus
import com.meow.ledger.platform.ScreenCaptureActivity
import com.meow.ledger.platform.ScreenCaptureTileService
import com.meow.ledger.shared.auto.DEFAULT_WEBVIEW_URL
import com.meow.ledger.shared.auto.DeepLinkTarget
import com.meow.ledger.shared.auto.isAllowedWebViewUrl
import com.meow.ledger.shared.auto.parseDeepLink
import com.meow.ledger.shared.auto.parseExplicitRoute
import com.meow.ledger.ui.EntryHost
import com.meow.ledger.ui.MeowApp
import com.meow.ledger.ui.PermissionItem

class MainActivity : ComponentActivity() {
    private val permissions = mutableStateOf(emptyList<PermissionItem>())
    private val target = mutableStateOf(DeepLinkTarget(DeepLinkTarget.Kind.MAIN))
    private lateinit var gateway: AndroidPermissionGateway
    private lateinit var host: EntryHost
    private var guidedKeys = emptyList<String>()
    private var guidedIndex = 0
    private var launchSeq = 0L

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissions.value = gateway.statuses().map(::toItem)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        gateway = AndroidPermissionGateway(this)
        permissions.value = gateway.statuses().map(::toItem)
        host = object : EntryHost {
            override fun permissions(): List<PermissionItem> = permissions.value

            override fun requestPermissions() {
                val missing = gateway.missingRuntimePermissions().toTypedArray()
                if (missing.isEmpty()) {
                    permissions.value = gateway.statuses().map(::toItem)
                } else {
                    requestPermissions.launch(missing)
                }
            }

            override fun openSettings(key: String) = gateway.openSettings(key)

            override fun startGuidedSettings() {
                guidedKeys = gateway.statuses()
                    .filter { !it.granted && it.settingsAction != null }
                    .map { it.key }
                guidedIndex = 0
                openNextGuidedSetting()
            }

            /** 没有内置扫码组件：尝试系统扫码，失败落到账本的「加入账本」邀请码输入框。 */
            override fun openScan(): String = runCatching {
                val scan = Intent("com.google.zxing.client.android.SCAN")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (scan.resolveActivity(packageManager) == null) {
                    "没有可用的扫码组件，已打开「加入账本」，可直接粘贴邀请码。"
                } else {
                    startActivity(scan)
                    "已打开系统扫码；也可以在「加入账本」粘贴邀请码。"
                }
            }.getOrDefault("扫码组件不可用，已打开「加入账本」粘贴邀请码。")

            /**
             * 外部网页：先由 shared 的白名单纯函数校验，再决定能否 ACTION_VIEW。
             * 未通过校验（http、userinfo、形似域名、任意第三方 URL）一律不打开浏览器，
             * 只回 App 内帮助页，避免 meowledger://webview 变成开放重定向。
             */
            override fun openWebView(url: String, allowed: Boolean): String {
                if (!allowed || !isAllowedWebViewUrl(url)) {
                    return "链接不在允许的官方域名内，已打开 App 内帮助与协议。"
                }
                val safe = runCatching { Uri.parse(url) }.getOrNull()
                val opened = if (safe != null && safe.scheme.equals("https", ignoreCase = true)) {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, safe).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        true
                    }.getOrDefault(false)
                } else {
                    false
                }
                return if (opened) "已在浏览器打开帮助与协议；返回后继续记账。" else "没有可用的浏览器，已打开 App 内帮助页。"
            }

            override fun openTicket(): String = "已打开票据识别，选图或粘贴文本后结果只进待确认队列。"

            /** 支付回调：只解析结果参数，展示本地订单/会员状态，不假装支付成功。 */
            override fun openPaymentCallback(result: String): String {
                val status = com.meow.ledger.shared.auto.paymentStatusOf(result)
                runCatching {
                    com.meow.ledger.shared.manage.AccountRepository(
                        com.meow.ledger.ui.AppGraph.database,
                    ).track("ac_pay_callback", result.take(200))
                }
                return status
            }

            override fun startScreenCapture(): String = runCatching {
                if (ScreenCaptureActivity.launch(this@MainActivity)) {
                    "已请求截屏授权；授权后只抓一帧，识别结果进入待确认队列。"
                } else {
                    "无法打开截屏授权，请稍后重试或改用票据识别。"
                }
            }.getOrDefault("当前系统不允许截屏，已打开待确认队列，可改用票据识别。")
        }
        target.value = targetFrom(intent)
        setContent {
            MeowApp(
                initialTarget = target.value,
                entryHost = host,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        target.value = targetFrom(intent)
    }

    override fun onResume() {
        super.onResume()
        // 回前台时同步桌面小组件的今日收支。
        com.meow.ledger.platform.AppWidgetReceiver.refresh(this)
        if (::gateway.isInitialized) {
            permissions.value = gateway.statuses().map(::toItem)
            if (guidedIndex < guidedKeys.size) {
                val key = guidedKeys[guidedIndex]
                if (gateway.statuses().firstOrNull { it.key == key }?.granted == true) {
                    guidedIndex++
                    openNextGuidedSetting()
                }
            }
        }
    }

    private fun openNextGuidedSetting() {
        if (guidedIndex >= guidedKeys.size) return
        gateway.openSettings(guidedKeys[guidedIndex])
    }

    private fun toItem(status: PermissionStatus): PermissionItem =
        PermissionItem(
            key = status.key,
            label = getString(status.labelRes),
            purpose = status.purpose,
            granted = status.granted,
            canOpenSettings = status.settingsAction != null,
        )

    /** 显式 EXTRA_ROUTE（磁贴、通知）与 meowledger:// 深链共用一套目标解析。 */
    private fun targetFrom(intent: Intent?): DeepLinkTarget {
        launchSeq++
        val id = launchSeq
        val explicit = intent?.getStringExtra(ScreenCaptureTileService.EXTRA_ROUTE)
        if (!explicit.isNullOrEmpty()) return when (explicit) {
            BillAlarmReceiver.ROUTE_BILL_EDIT -> parseExplicitRoute("bill-edit").copy(launchId = id)
            ScreenCaptureTileService.ROUTE_CAPTURE -> parseExplicitRoute("capture").copy(launchId = id)
            ScreenCaptureTileService.ROUTE_PENDING -> parseExplicitRoute("pending").copy(launchId = id)
            else -> parseExplicitRoute(explicit).copy(launchId = id)
        }
        val data = intent?.data ?: return DeepLinkTarget(DeepLinkTarget.Kind.MAIN, launchId = id)
        if (data.scheme != "meowledger") return DeepLinkTarget(DeepLinkTarget.Kind.MAIN, launchId = id)
        val query: (String) -> String? = { key ->
            runCatching { data.getQueryParameter(key) }.getOrNull()
        }
        val parsed = parseDeepLink(data.host, query).copy(launchId = id)
        // webview 没带 url 时用官方帮助页兜底；wx 兼容旧写法 wx:<result>。
        return when {
            parsed.kind == DeepLinkTarget.Kind.WEBVIEW && parsed.url.isBlank() ->
                parsed.copy(url = DEFAULT_WEBVIEW_URL, webUrlAllowed = true)
            data.host == "wx" && parsed.paymentResult.isBlank() ->
                parsed.copy(paymentResult = query("result").orEmpty())
            else -> parsed
        }
    }
}
