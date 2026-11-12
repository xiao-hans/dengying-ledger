package com.meow.ledger

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.meow.ledger.shared.auto.SettingsStore
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.network.DefaultHeaderProvider
import com.meow.ledger.shared.network.KtorMeowApi
import com.meow.ledger.shared.network.SettingsBaseUrlProvider
import com.meow.ledger.shared.network.SyncEngine
import com.meow.ledger.shared.network.SyncScheduler
import com.meow.ledger.shared.network.remoteSyncEnabled
import com.meow.ledger.shared.network.serverToken
import com.meow.ledger.ui.AppGraph
import java.util.UUID

/** 设备号持久化键；只在首次启动时生成一次，卸载重装才会变。 */
private const val PREFS_NAME = "meow_network"
private const val KEY_DEVICE_ID = "device_id"

class MeowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val driver = AndroidSqliteDriver(MeowDatabase.Schema, this, "meow_ledger.db")
        val database = MeowDatabase(driver)
        AppGraph.database = database
        val settings = SettingsStore(database)

        // 隐私门禁：base URL 只来自用户填的 sync_base_url / sync_config_base_url。
        // 空白时 KtorMeowApi 解析不到 host，且 SyncEngine 的 remoteEnabled 直接关掉 flush，
        // 账单/设备号/令牌都不会发到样本原 App 的第三方生产域名。
        val api = KtorMeowApi(
            headerProvider = androidHeaderProvider(database),
            baseUrlProvider = SettingsBaseUrlProvider(settings),
        )
        AppGraph.api = api

        // 进程级作用域：入队后异步 flush，不等待；App 启动补发上次遗留条目。
        val scheduler = SyncScheduler { AppGraph.sync }
        val sync = SyncEngine(
            database,
            api,
            scheduler = scheduler,
            remoteEnabled = { remoteSyncEnabled(database) },
        )
        AppGraph.sync = sync
        sync.flushOnStart()

        // 文件桥与采集桥都需要进程级上下文。
        com.meow.ledger.ui.AndroidHost.appContext = this
        com.meow.ledger.platform.CaptureBridge.start(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default))
        // 补齐错过的周期账账期（source=5 自动入账，幂等），再重排提醒与小组件。
        com.meow.ledger.shared.manage.FixedBillRepository(AppGraph.database).generateDueBills()
        // 周期提醒随进程启动重排，桌面小组件同步今日收支。
        com.meow.ledger.platform.RemindScheduler.scheduleAll(this)
        com.meow.ledger.platform.AppWidgetReceiver.refresh(this)
    }

    /** Android 侧请求头来源：设备号（SharedPreferences UUID）、版本号、真实服务端令牌。 */
    private fun androidHeaderProvider(database: MeowDatabase): DefaultHeaderProvider {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val generated = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
            generated
        }
        val version = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName ?: ""
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName ?: ""
            }
        } catch (e: PackageManager.NameNotFoundException) {
            BuildConfig.VERSION_NAME
        }
        return DefaultHeaderProvider(
            // 只用真实服务端令牌（user_setting.auth_token）。本地账号的
            // refreshLogin 令牌是 `local_...` 假串，不能冒充 Authorization。
            token = { serverToken(database) },
            deviceId = { deviceId },
            appVersion = { version },
        )
    }
}
