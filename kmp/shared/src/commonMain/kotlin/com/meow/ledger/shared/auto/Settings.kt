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
// 兼容旧库保留，但不再使用：AGENTS.md §6.7 要求识别结果一律进待确认队列、绝不静默入账，
// 故「自动确认」开关已从 UI 下线，也不读取该值。保留键名只为避免旧版本写过的 user_setting 行变成孤儿数据。
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

/**
 * 界面里镜像设置的那几项内存状态。开机初始化与「清空本机数据」后的重置都读这份快照，
 * 保证界面显示和库里的设置用同一组缺省值，不会一处改了另一处漏改（AGENTS.md §6.8）。
 */
data class SettingsSnapshot(
    val theme: String,
    val autoBill: Boolean,
    val keepAlive: Boolean,
    val watchApps: Set<String>,
    val lockPin: String,
)

/** 从库里读一份当前快照；设置行不存在的项由 SettingsStore 回退到缺省值。 */
fun SettingsStore.snapshot(): SettingsSnapshot = SettingsSnapshot(
    theme = text(KEY_THEME, THEME_SYSTEM),
    autoBill = flag(KEY_AUTO_BILL, false),
    keepAlive = flag(KEY_KEEP_ALIVE, false),
    watchApps = watchApps(),
    lockPin = text(KEY_APP_LOCK),
)
