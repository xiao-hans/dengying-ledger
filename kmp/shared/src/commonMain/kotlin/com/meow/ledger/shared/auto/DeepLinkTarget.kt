package com.meow.ledger.shared.auto

/**
 * 深链 / 快捷入口的目标。纯数据，不依赖 Android 或 Compose，方便 JVM 单测。
 * 每个目标都要能落到真实功能，且能从底部 Dock 返回（交互纪律 §6.9）。
 */
data class DeepLinkTarget(
    val kind: Kind,
    /** bill-edit 深链带的账单 client_id；其余目标为空。 */
    val clientId: String = "",
    /** webview 深链带的网址；无参数时为空，由入口用默认帮助页兜底。 */
    val url: String = "",
    /** 微信支付回调的原始结果串，供入口解析后展示订单/会员状态。 */
    val paymentResult: String = "",
    /**
     * webview 深链的 url 是否通过了域名白名单校验。
     * 为 false 时平台侧不得 ACTION_VIEW 打开浏览器，只回 App 内帮助页（防开放重定向）。
     */
    val webUrlAllowed: Boolean = false,
    /** 每次新 Intent 递增；让同样的深链重复进来时也能重新触发系统动作。 */
    val launchId: Long = 0,
) {
    enum class Kind {
        MAIN,
        PERMISSION,
        SCAN,
        WEBVIEW,
        TICKET,
        CAPTURE,
        /** 只打开待确认队列，不拉起截屏授权（截屏结果通知用）。 */
        PENDING,
        BILL_EDIT,
        PAYMENT,
    }
}

/** 深链参数值：只接受白名单目标，其余一律回主 Dock，避免「进得去出不来」。 */
fun parseDeepLink(host: String?, query: (String) -> String?): DeepLinkTarget {
    val param = { key: String -> query(key).orEmpty() }
    return when (host?.trim()?.lowercase()) {
        null, "", "main", "interstitial" -> DeepLinkTarget(DeepLinkTarget.Kind.MAIN)
        "permission" -> DeepLinkTarget(DeepLinkTarget.Kind.PERMISSION)
        "scan" -> DeepLinkTarget(DeepLinkTarget.Kind.SCAN)
        "webview" -> {
            val url = param("url")
            DeepLinkTarget(DeepLinkTarget.Kind.WEBVIEW, url = url, webUrlAllowed = isAllowedWebViewUrl(url))
        }
        "ticket" -> DeepLinkTarget(DeepLinkTarget.Kind.TICKET)
        "capture" -> DeepLinkTarget(DeepLinkTarget.Kind.CAPTURE)
        "pending" -> DeepLinkTarget(DeepLinkTarget.Kind.PENDING)
        "bill-edit" -> DeepLinkTarget(DeepLinkTarget.Kind.BILL_EDIT, clientId = param("clientId"))
        "wx", "pay", "payment" -> DeepLinkTarget(
            DeepLinkTarget.Kind.PAYMENT,
            clientId = param("orderId"),
            paymentResult = param("result"),
        )
        else -> DeepLinkTarget(DeepLinkTarget.Kind.MAIN)
    }
}

/** 显式 EXTRA_ROUTE（磁贴、通知）也归一到同一套目标。 */
fun parseExplicitRoute(route: String?): DeepLinkTarget = when (route?.trim()?.lowercase()) {
    null, "", "main" -> DeepLinkTarget(DeepLinkTarget.Kind.MAIN)
    "permission" -> DeepLinkTarget(DeepLinkTarget.Kind.PERMISSION)
    "scan" -> DeepLinkTarget(DeepLinkTarget.Kind.SCAN)
    "webview" -> DeepLinkTarget(DeepLinkTarget.Kind.WEBVIEW, url = DEFAULT_WEBVIEW_URL)
    "ticket" -> DeepLinkTarget(DeepLinkTarget.Kind.TICKET)
    "capture" -> DeepLinkTarget(DeepLinkTarget.Kind.CAPTURE)
    "pending" -> DeepLinkTarget(DeepLinkTarget.Kind.PENDING)
    "bill-edit" -> DeepLinkTarget(DeepLinkTarget.Kind.BILL_EDIT)
    "interstitial" -> DeepLinkTarget(DeepLinkTarget.Kind.MAIN)
    else -> if (route.startsWith("wx:")) {
        DeepLinkTarget(DeepLinkTarget.Kind.PAYMENT, paymentResult = route.substringAfter("wx:"))
    } else {
        DeepLinkTarget(DeepLinkTarget.Kind.MAIN)
    }
}

/** 无 url 的 webview 深链 / 显式 webview 路由统一落到这个已通过白名单的官方帮助页。 */
const val DEFAULT_WEBVIEW_URL = "https://meow-config.aiyouaiyou.cn/api/help"

/** 唯一允许的明文协议前缀；只允许 TLS，大小写混写也一并拒绝（http / 相对路径进不来）。 */
private const val SecureScheme = "https://"

/**
 * 外部网页深链（meowledger://webview?url=...）唯一允许打开的域名，全部小写。
 * 只收录代码实际使用的官方域：帮助页走 meow-config.aiyouaiyou.cn，
 * 业务/配置域分别是 cqmiaoa.com 与 aiyouaiyou.cn 及其子域。
 * 新增官方 H5 域名时必须同时更新这里，并补一条 JVM 单测。
 */
val WEBVIEW_ALLOWED_HOSTS: Set<String> = setOf(
    "meow-config.aiyouaiyou.cn",
    "cqmiaoa.com",
    "aiyouaiyou.cn",
)

/**
 * 校验 webview 深链 url：只允许 https，且 host 等于白名单域或其子域。
 * 纯函数、无平台依赖，方便 JVM 单测覆盖开放重定向的各种绕过写法。
 *
 * 明确拒绝：http 明文、其它 scheme、无 host / 非法 URL、userinfo（`allowed@evil`）、
 * 形似域名（`evilcqmiaoa.com`、`cqmiaoa.com.evil.com`）、把白名单域塞进 query/path
 * （`https://evil.com/?x=cqmiaoa.com`）、大小写混写时按小写归一化后再比较。
 */
fun isAllowedWebViewUrl(rawUrl: String): Boolean {
    val url = rawUrl.trim()
    // 先卡死协议：只允许字面 https 开头，http 及 javascript:/data:/相对路径一律不要。
    if (!url.startsWith(SecureScheme)) return false
    if (url.length <= SecureScheme.length) return false
    // 出现空白/控制字符的 URL 一律拒绝，避免 Uri.parse 与这里解析出不同的 host。
    if (url.any { it <= ' ' || it == '\u007F' }) return false
    val authorityStart = SecureScheme.length
    val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), authorityStart).let {
        if (it < 0) url.length else it
    }
    val authority = url.substring(authorityStart, authorityEnd)
    // 禁止 userinfo：`https://allowed.com@evil.com` 的真正 host 是 evil.com。
    if (authority.isEmpty() || '@' in authority) return false
    // 去掉可选端口（含 IPv6 字面量整个拒绝，避免歧义）；端口必须是纯数字。
    val host = if (authority.startsWith('[')) {
        return false
    } else {
        val colon = authority.lastIndexOf(':')
        if (colon >= 0) {
            val port = authority.substring(colon + 1)
            if (port.isEmpty() || !port.all { it in '0'..'9' }) return false
            authority.substring(0, colon)
        } else {
            authority
        }
    }.lowercase()
    if (host.isEmpty() || host.endsWith('.')) return false
    // 逐段校验 host，杜绝空白、控制字符与异常字符。
    if (host.split('.').any { label ->
            label.isEmpty() || !label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
        }
    ) return false
    return WEBVIEW_ALLOWED_HOSTS.any { allowed -> host == allowed || host.endsWith(".$allowed") }
}

/** 支付回调的原始结果 → 给用户看的中文状态。缺失或非法一律按「未完成」处理，不假装成功。 */
fun paymentStatusOf(result: String?): String = when (result?.trim()?.lowercase()) {
    "0", "ok", "success", "successful" -> "支付成功，会员权益与订单状态已更新"
    "cancel", "cancelled", "-2" -> "支付已取消，订单未生效"
    "fail", "failed", "error", "-1" -> "支付未完成，请重试或联系客服"
    null, "", "pending", "unknown" -> "支付结果待确认，请稍后在会员页查看订单状态"
    else -> "收到支付回调（$result），请在会员页核对订单状态"
}
