package com.meow.ledger.shared.network

object HttpConfig {
    const val MAIN_BASE = "https://puppy-api.cqmiaoa.com/api/"
    const val CONFIG_BASE = "https://meow-config.aiyouaiyou.cn/api/"
    const val CONNECT_TIMEOUT_MS = 30_000L
    const val REQUEST_TIMEOUT_MS = 120_000L
    const val SOCKET_TIMEOUT_MS = 60_000L
    const val PING_INTERVAL_MS = 5_000L
    const val CONFIG_PATH = "Config/GetV2"

    fun baseFor(path: String, urlType: String? = null): String =
        if (path == CONFIG_PATH || urlType == "configManage") CONFIG_BASE else MAIN_BASE
}
