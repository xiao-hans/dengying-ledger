package com.meow.ledger.shared.network

import com.meow.ledger.shared.auto.KEY_AUTH_TOKEN
import com.meow.ledger.shared.auto.KEY_SYNC_BASE_URL
import com.meow.ledger.shared.auto.KEY_SYNC_CONFIG_BASE_URL
import com.meow.ledger.shared.auto.SettingsStore
import com.meow.ledger.shared.db.MeowDatabase

/**
 * 每次请求时解析后端 base URL。返回 `null` 表示**远程同步未启用**，调用方不得发请求。
 *
 * 隐私门禁：`HttpConfig.MAIN_BASE` / `CONFIG_BASE` 是样本原 App 的第三方生产域名。
 * 本工程用自建后端（AGENTS.md §2 明文 JSON），`SettingsStore` 的 `sync_base_url`
 * 为空白时禁止任何外发；只有用户显式填了自建地址，才往那个 host 发。
 * `sync_config_base_url` 可选；空白时 `Config/GetV2` 复用主域。
 */
fun interface BaseUrlProvider {
    fun base(isConfig: Boolean): String?
}

/**
 * 测试 / 契约自检默认：用 [HttpConfig] 的样本域名常量，保证既有 112 端点 host 断言不漂移。
 * 生产装配不要用它，用 [SettingsBaseUrlProvider]。
 */
object SpecBaseUrlProvider : BaseUrlProvider {
    override fun base(isConfig: Boolean): String? =
        if (isConfig) HttpConfig.CONFIG_BASE else HttpConfig.MAIN_BASE
}

/**
 * 生产用的 [BaseUrlProvider]：从 `user_setting` 读用户配置的 base。
 * shared 层不依赖 Android Context；`SettingsStore` 由 app 层注入。
 */
class SettingsBaseUrlProvider(private val settings: SettingsStore) : BaseUrlProvider {
    /** 主域空白 → 返回 null，同步禁用；配置域优先取专用键，空白回退主域。 */
    override fun base(isConfig: Boolean): String? {
        val main = settings.text(KEY_SYNC_BASE_URL).trim()
        if (main.isBlank()) return null
        if (!isConfig) return main
        return settings.text(KEY_SYNC_CONFIG_BASE_URL).trim().ifBlank { main }
    }
}

/** 从 base URL 取主机名（去掉 userinfo / 端口 / 路径，统一小写）；没有主机名返回 null。 */
private fun hostOf(url: String): String? =
    url.substringAfter("://", missingDelimiterValue = "").takeIf { it.isNotEmpty() }
        ?.substringBefore('/')
        ?.substringAfterLast('@')
        ?.substringBefore(':')
        ?.takeIf { it.isNotBlank() }
        ?.lowercase()

/**
 * 样本原 App 的生产域名（取自 [HttpConfig] 常量），用户不得填回。
 * [hostOf] 已把两侧主机名统一小写，因此大小写不同的写法同样会被拒绝。
 */
private val SAMPLE_PRODUCTION_HOSTS: Set<String> =
    setOfNotNull(hostOf(HttpConfig.MAIN_BASE), hostOf(HttpConfig.CONFIG_BASE))

/**
 * 校验用户填写的自建同步地址。空白（trim 后）合法，表示禁用远程同步；
 * 非空白必须是 `http://` 或 `https://` 且带主机名，并拒绝样本原生产域名。
 * 返回 `null` 表示合法，否则是可直接展示给用户的错误提示。
 */
fun validateSyncUrl(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    val scheme = value.substringBefore("://", missingDelimiterValue = "").lowercase()
    if (scheme != "http" && scheme != "https") return "必须以 http:// 或 https:// 开头"
    val host = hostOf(value) ?: return "缺少主机名"
    if (host in SAMPLE_PRODUCTION_HOSTS) return "不能填写样本原生产域名"
    return null
}

/** 从 [MeowDatabase] 读 `sync_base_url` 判定远程同步是否启用。 */
fun remoteSyncEnabled(db: MeowDatabase): Boolean =
    SettingsStore(db).text(KEY_SYNC_BASE_URL).isNotBlank()

/** 从 [MeowDatabase] 读真实服务端令牌；没有就不发 Authorization。 */
fun serverToken(db: MeowDatabase): String =
    SettingsStore(db).text(KEY_AUTH_TOKEN).trim()
