package com.meow.ledger.shared.network

/**
 * 每个请求都要带的通用请求头来源。
 *
 * - `Authorization`：已登录时 `Bearer <token>`，未登录时省略该头（不是空值）。
 * - `X-Device-Id`：端侧持久化的设备号，Android 用 SharedPreferences 存 UUID。
 * - `X-App-Version`：BuildConfig.VERSION_NAME。
 * - `X-Client-Time`：客户端发起请求的 Unix 毫秒时间戳（epoch ms，十进制字符串）。
 *
 * 由 app 层在装配 [KtorMeowApi] 时注入；shared 层不依赖 Android Context。
 */
fun interface HeaderProvider {
    /** 每次请求时重新求值，登录后能立刻带上新令牌。 */
    fun headers(): Map<String, String>
}

/** 所有实现共用的头名常量，避免字符串漂移。 */
object ApiHeaderNames {
    const val AUTHORIZATION = "Authorization"
    const val DEVICE_ID = "X-Device-Id"
    const val APP_VERSION = "X-App-Version"
    const val CLIENT_TIME = "X-Client-Time"
}

/**
 * 默认实现：空令牌时不发 Authorization 头；时间由 [clock] 提供，便于测试。
 * 令牌、设备号、版本号都由 app 层以 lambda 形式给出，避免进程启动后读不到新值。
 */
class DefaultHeaderProvider(
    private val token: () -> String,
    private val deviceId: () -> String,
    private val appVersion: () -> String,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : HeaderProvider {
    override fun headers(): Map<String, String> = buildMap {
        val bearer = token().trim()
        if (bearer.isNotEmpty()) put(ApiHeaderNames.AUTHORIZATION, "Bearer $bearer")
        put(ApiHeaderNames.DEVICE_ID, deviceId())
        put(ApiHeaderNames.APP_VERSION, appVersion())
        put(ApiHeaderNames.CLIENT_TIME, clock().toString())
    }
}

/** 测试与纯本地模式用：只带最小头，不发 Authorization。 */
object EmptyHeaderProvider : HeaderProvider {
    override fun headers(): Map<String, String> = mapOf(
        ApiHeaderNames.DEVICE_ID to "",
        ApiHeaderNames.APP_VERSION to "",
        ApiHeaderNames.CLIENT_TIME to "0",
    )
}
