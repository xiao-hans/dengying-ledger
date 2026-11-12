package com.meow.ledger.shared.network

object HttpConfig {
    /**
     * 样本原 App 的生产域名。**只作为契约自检 / mock 测试的默认 host**（见
     * [SpecBaseUrlProvider]），生产装配绝不把它们当默认值：App 从 `sync_base_url`
     * 读自建后端地址，未配置时远程同步整体禁用，账单/设备号/令牌都不会发到这两个域。
     */
    const val MAIN_BASE = "https://puppy-api.cqmiaoa.com/api/"
    const val CONFIG_BASE = "https://meow-config.aiyouaiyou.cn/api/"

    const val CONNECT_TIMEOUT_MS = 30_000L
    const val REQUEST_TIMEOUT_MS = 120_000L
    const val SOCKET_TIMEOUT_MS = 60_000L
    const val PING_INTERVAL_MS = 5_000L
    const val CONFIG_PATH = "Config/GetV2"

    /** 契约自检用：判断一条注解路径该走主域还是配置域。 */
    fun isConfigRequest(path: String, urlType: String? = null): Boolean =
        path == CONFIG_PATH || urlType == "configManage"

    /** 契约自检用：按样本口径返回默认 host；生产请走 [BaseUrlProvider]。 */
    fun baseFor(path: String, urlType: String? = null): String =
        if (isConfigRequest(path, urlType)) CONFIG_BASE else MAIN_BASE

    /**
     * 拼接 base 与相对 path，容忍用户手填的 base 带不带结尾 `/`，保证中间恰好一个 `/`。
     * 动态 `@Url` 的绝对地址不经过这里。
     */
    fun compose(base: String, path: String): String =
        base.trim().trimEnd('/') + "/" + path.trimStart('/')
}
