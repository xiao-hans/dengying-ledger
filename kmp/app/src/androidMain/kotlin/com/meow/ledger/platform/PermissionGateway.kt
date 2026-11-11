package com.meow.ledger.platform

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

/** 把系统权限状态映射为 App 内的功能状态，供 Compose 权限页显示。 */
data class PermissionStatus(
    val key: String,
    val labelRes: Int,
    val purpose: String,
    val granted: Boolean,
    val settingsAction: String? = null,
)

interface PermissionGateway {
    fun statuses(): List<PermissionStatus>

    /** 返回待系统弹窗申请的危险权限；空列表表示无需申请。 */
    fun missingRuntimePermissions(): List<String>

    fun openSettings(key: String)
}

class AndroidPermissionGateway(private val context: Context) : PermissionGateway {
    override fun statuses(): List<PermissionStatus> = buildList {
        add(
            PermissionStatus(
                key = KEY_NOTIFICATION,
                labelRes = com.meow.ledger.R.string.permission_notification,
                purpose = "显示账单提醒和周期记账通知",
                granted = runtimeGranted(Manifest.permission.POST_NOTIFICATIONS),
            ),
        )
        add(
            PermissionStatus(
                key = KEY_MEDIA_IMAGES,
                labelRes = com.meow.ledger.R.string.permission_media_images,
                purpose = "从相册选择票据并识别金额",
                granted = runtimeGranted(Manifest.permission.READ_MEDIA_IMAGES),
            ),
        )
        add(
            PermissionStatus(
                key = KEY_CAMERA,
                labelRes = com.meow.ledger.R.string.permission_camera,
                purpose = "拍摄票据或扫描账本二维码",
                granted = runtimeGranted(Manifest.permission.CAMERA),
            ),
        )
        add(
            PermissionStatus(
                key = KEY_SMS,
                labelRes = com.meow.ledger.R.string.permission_sms,
                purpose = "自动填入登录验证码",
                granted = runtimeGranted(Manifest.permission.READ_SMS),
            ),
        )
        add(
            PermissionStatus(
                key = KEY_NOTIFICATION_LISTENER,
                labelRes = com.meow.ledger.R.string.permission_notification_listener,
                purpose = "把支付通知放进待确认队列，确认后才入账",
                granted = notificationListenerGranted(),
                settingsAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
            ),
        )
        add(
            PermissionStatus(
                key = KEY_ACCESSIBILITY,
                labelRes = com.meow.ledger.R.string.permission_accessibility,
                purpose = "识别支付页面文字，结果仍须人工确认",
                granted = accessibilityGranted(),
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
            ),
        )
        add(
            PermissionStatus(
                key = KEY_USAGE_STATS,
                labelRes = com.meow.ledger.R.string.permission_usage_stats,
                purpose = "识别自动记账来源应用",
                granted = usageStatsGranted(),
                settingsAction = Settings.ACTION_USAGE_ACCESS_SETTINGS,
            ),
        )
        add(
            PermissionStatus(
                key = KEY_BIOMETRIC,
                labelRes = com.meow.ledger.R.string.permission_biometric,
                purpose = "使用生物识别解锁账本",
                granted = biometricGranted(),
                settingsAction = Settings.ACTION_SECURITY_SETTINGS,
            ),
        )
        add(
            PermissionStatus(
                key = KEY_EXACT_ALARM,
                labelRes = com.meow.ledger.R.string.permission_exact_alarm,
                purpose = "按设定时间触发周期记账提醒",
                granted = exactAlarmGranted(),
                settingsAction = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            ),
        )
        add(
            PermissionStatus(
                key = KEY_OVERLAY,
                labelRes = com.meow.ledger.R.string.permission_overlay,
                purpose = "在桌面显示悬浮宠物入口",
                granted = Settings.canDrawOverlays(context),
                settingsAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            ),
        )
    }

    override fun missingRuntimePermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.READ_SMS)
    }.filterNot(::runtimeGranted)

    override fun openSettings(key: String) {
        val action = statuses().firstOrNull { it.key == key }?.settingsAction ?: return
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (key == KEY_EXACT_ALARM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            intent.data = Uri.parse("package:${context.packageName}")
        }
        if (key == KEY_OVERLAY) intent.data = Uri.parse("package:${context.packageName}")
        context.startActivity(intent)
    }

    private fun runtimeGranted(permission: String): Boolean =
        Build.VERSION.SDK_INT < when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> Build.VERSION_CODES.TIRAMISU
            Manifest.permission.READ_MEDIA_IMAGES -> Build.VERSION_CODES.TIRAMISU
            else -> 0
        } || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun notificationListenerGranted(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return enabled.orEmpty().contains(context.packageName)
    }

    private fun accessibilityGranted(): Boolean {
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        )
        return enabled == 1 && Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty().contains(context.packageName)
    }

    private fun usageStatsGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun biometricGranted(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) ||
            context.packageManager.hasSystemFeature("android.hardware.biometrics")

    private fun exactAlarmGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        } else {
            true
        }

    companion object {
        const val KEY_NOTIFICATION = "notification"
        const val KEY_MEDIA_IMAGES = "media_images"
        const val KEY_CAMERA = "camera"
        const val KEY_SMS = "sms"
        const val KEY_NOTIFICATION_LISTENER = "notification_listener"
        const val KEY_ACCESSIBILITY = "accessibility"
        const val KEY_USAGE_STATS = "usage_stats"
        const val KEY_BIOMETRIC = "biometric"
        const val KEY_EXACT_ALARM = "exact_alarm"
        const val KEY_OVERLAY = "overlay"
    }
}
