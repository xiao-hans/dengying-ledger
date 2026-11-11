package com.meow.ledger

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.meow.ledger.platform.AndroidPermissionGateway
import com.meow.ledger.platform.BillAlarmReceiver
import com.meow.ledger.platform.PermissionStatus
import com.meow.ledger.platform.ScreenCaptureTileService
import com.meow.ledger.ui.EntryHost
import com.meow.ledger.ui.MeowApp
import com.meow.ledger.ui.PermissionItem
import com.meow.ledger.ui.ROUTE_BILL_EDIT
import com.meow.ledger.ui.ROUTE_CAPTURE
import com.meow.ledger.ui.ROUTE_INTERSTITIAL
import com.meow.ledger.ui.ROUTE_PERMISSION
import com.meow.ledger.ui.ROUTE_SCAN
import com.meow.ledger.ui.ROUTE_TICKET
import com.meow.ledger.ui.ROUTE_WEBVIEW

class MainActivity : ComponentActivity() {
    private val permissions = mutableStateOf(emptyList<PermissionItem>())
    private val route = mutableStateOf("")
    private lateinit var gateway: AndroidPermissionGateway
    private lateinit var host: EntryHost
    private var guidedKeys = emptyList<String>()
    private var guidedIndex = 0

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

            override fun openScan(): String = "打开系统扫码界面；也可在账本页手动输入分享码。"

            override fun openWebView(url: String): String = "打开帮助与协议：$url"

            override fun openTicket(): String = "选择票据后先显示识别结果，确认后才保存。"

            override fun openPaymentCallback(result: String): String = "支付结果：$result"
        }
        route.value = routeFrom(intent)
        setContent {
            MeowApp(
                initialRoute = route.value,
                entryHost = host,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route.value = routeFrom(intent)
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

    private fun routeFrom(intent: Intent?): String {
        val explicit = intent?.getStringExtra(ScreenCaptureTileService.EXTRA_ROUTE)
        if (!explicit.isNullOrEmpty()) return when (explicit) {
            BillAlarmReceiver.ROUTE_BILL_EDIT -> ROUTE_BILL_EDIT
            ScreenCaptureTileService.ROUTE_CAPTURE -> ROUTE_CAPTURE
            else -> explicit
        }
        val data = intent?.data ?: return ""
        if (data.scheme != "meowledger") return ""
        return when (data.host) {
            "permission" -> ROUTE_PERMISSION
            "scan" -> ROUTE_SCAN
            "webview" -> ROUTE_WEBVIEW
            "ticket" -> ROUTE_TICKET
            "capture" -> ROUTE_CAPTURE
            "bill-edit" -> ROUTE_BILL_EDIT
            "interstitial" -> ROUTE_INTERSTITIAL
            "wx" -> "wx:${data.getQueryParameter("result").orEmpty()}"
            else -> ""
        }
    }
}
