package com.meow.ledger.shared.auto

import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.User_setting

/**
 * 设置键，对齐 React 端 `src/features/auto/engine.ts` 的 `loadSettings`。
 * 值都是字符串：开关用 "1"/"0"，白名单用逗号分隔的包名。
 */
const val KEY_AUTO_BILL = "auto_bill"
const val KEY_NOTIFICATION = "notification_capture"
const val KEY_ACCESSIBILITY = "accessibility_capture"
const val KEY_OCR = "screenshot_ocr"
const val KEY_AUTO_CONFIRM = "auto_confirm"
const val KEY_WATCH_APPS = "auto_watch_apps"
const val KEY_KEEP_ALIVE = "keep_alive"

/** 外观三态：跟随系统 / 浅色 / 深色。对齐灯影 `ThemeMode`。 */
const val KEY_THEME = "theme_mode"
const val THEME_SYSTEM = "system"
const val THEME_LIGHT = "light"
const val THEME_DARK = "dark"

/** 应用锁的四位口令。空表示未开启。 */
const val KEY_APP_LOCK = "app_lock_pin"

/**
 * 自建后端 base URL。**空白表示禁用远程同步**：写操作照常落本地 `sync_queue`，
 * 但 `SyncEngine.flush()` 不发任何请求，绝不把账单/设备号/令牌发到样本原域名。
 * 由用户自行填写自建后端地址后才启用同步（见 `docs/接口实现对照表.md` 网络章节）。
 */
const val KEY_SYNC_BASE_URL = "sync_base_url"

/** 可选的配置域 base URL；空白时 `Config/GetV2` 复用主域。 */
const val KEY_SYNC_CONFIG_BASE_URL = "sync_config_base_url"

/** 真实服务端签发的登录令牌；空表示未登录，不发 `Authorization`。 */
const val KEY_AUTH_TOKEN = "auth_token"

/**
 * 用户设置的读写，落在 `user_setting` 表上。
 * 每个开关都有明确的默认值，从来没存过和存成关闭不是一回事。
 */
class SettingsStore(private val db: MeowDatabase) {
    fun flag(key: String, default: Boolean): Boolean {
        val raw = db.configQueries.selectSetting(key).executeAsOneOrNull() ?: return default
        return raw == "1"
    }

    fun setFlag(key: String, on: Boolean) {
        write(key, if (on) "1" else "0")
    }

    fun text(key: String, default: String = ""): String =
        db.configQueries.selectSetting(key).executeAsOneOrNull() ?: default

    fun setText(key: String, value: String) {
        write(key, value)
    }

    /** 监听白名单。没存过时回退到前三个常用 App，存成空就是真的没有。 */
    fun watchApps(): Set<String> {
        val raw = db.configQueries.selectSetting(KEY_WATCH_APPS).executeAsOneOrNull()
            ?: return DEFAULT_WATCH_APPS.toSet()
        return raw.split(',').filter { it.isNotBlank() }.toSet()
    }

    fun setWatchApps(packages: Set<String>) {
        write(KEY_WATCH_APPS, packages.joinToString(","))
    }

    private fun write(key: String, value: String) {
        db.configQueries.upsertSetting(User_setting(key, value))
    }
}
