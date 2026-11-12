package com.meow.ledger.shared.auto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 深链 → 功能目标的纯 JVM 回归：每个入口都要落到真实功能，不能停在说明页。 */
class DeepLinkTargetTest {

    private fun params(vararg pairs: Pair<String, String>): (String) -> String? =
        { key -> pairs.firstOrNull { it.first == key }?.second }

    @Test
    fun everyDeepLinkHostMapsToRealFeature() {
        val expected = mapOf(
            "permission" to DeepLinkTarget.Kind.PERMISSION,
            "scan" to DeepLinkTarget.Kind.SCAN,
            "webview" to DeepLinkTarget.Kind.WEBVIEW,
            "ticket" to DeepLinkTarget.Kind.TICKET,
            "capture" to DeepLinkTarget.Kind.CAPTURE,
            "bill-edit" to DeepLinkTarget.Kind.BILL_EDIT,
            "wx" to DeepLinkTarget.Kind.PAYMENT,
        )
        expected.forEach { (host, kind) ->
            assertEquals(kind, parseDeepLink(host, params()).kind, "host=$host")
        }
    }

    @Test
    fun billEditCarriesClientIdAndWebviewCarriesUrl() {
        val edit = parseDeepLink("bill-edit", params("clientId" to "auto-abc"))
        assertEquals(DeepLinkTarget.Kind.BILL_EDIT, edit.kind)
        assertEquals("auto-abc", edit.clientId)

        val web = parseDeepLink("webview", params("url" to "https://example.com/help"))
        assertEquals(DeepLinkTarget.Kind.WEBVIEW, web.kind)
        assertEquals("https://example.com/help", web.url)
        assertEquals(false, web.webUrlAllowed)
    }

    @Test
    fun webviewDeepLinkMarksOfficialUrlAsAllowed() {
        val official = parseDeepLink("webview", params("url" to "https://meow-config.aiyouaiyou.cn/api/help"))
        assertEquals(DeepLinkTarget.Kind.WEBVIEW, official.kind)
        assertEquals(true, official.webUrlAllowed)
        assertEquals(false, parseDeepLink("webview", params()).webUrlAllowed)
        val explicit = parseExplicitRoute("webview")
        assertEquals(DEFAULT_WEBVIEW_URL, explicit.url)
        assertEquals(true, isAllowedWebViewUrl(explicit.url))
    }

    @Test
    fun paymentCarriesOrderIdAndResult() {
        val target = parseDeepLink("wx", params("result" to "ok", "orderId" to "o123"))
        assertEquals(DeepLinkTarget.Kind.PAYMENT, target.kind)
        assertEquals("ok", target.paymentResult)
        assertEquals("o123", target.clientId)
    }

    @Test
    fun interstitialAndUnknownHostsFallBackToMainDock() {
        // 无广告：插屏入口绝不能展示广告，直接回主 Dock。
        assertEquals(DeepLinkTarget.Kind.MAIN, parseDeepLink("interstitial", params()).kind)
        assertEquals(DeepLinkTarget.Kind.MAIN, parseDeepLink("nonsense", params()).kind)
        assertEquals(DeepLinkTarget.Kind.MAIN, parseDeepLink(null, params()).kind)
        assertEquals(DeepLinkTarget.Kind.MAIN, parseDeepLink("", params()).kind)
    }

    @Test
    fun explicitRoutesCoverTileAndReminderAndLegacyWx() {
        assertEquals(DeepLinkTarget.Kind.CAPTURE, parseExplicitRoute("capture").kind)
        // 截屏完成后的结果通知只回待确认队列，不再拉起一次授权。
        assertEquals(DeepLinkTarget.Kind.PENDING, parseExplicitRoute("pending").kind)
        assertEquals(DeepLinkTarget.Kind.PENDING, parseDeepLink("pending", params()).kind)
        assertEquals(DeepLinkTarget.Kind.BILL_EDIT, parseExplicitRoute("bill-edit").kind)
        assertEquals(DeepLinkTarget.Kind.MAIN, parseExplicitRoute("interstitial").kind)
        assertEquals(DeepLinkTarget.Kind.MAIN, parseExplicitRoute("").kind)
        val wx = parseExplicitRoute("wx:ok")
        assertEquals(DeepLinkTarget.Kind.PAYMENT, wx.kind)
        assertEquals("ok", wx.paymentResult)
    }

    @Test
    fun launchIdMakesRepeatedDeepLinksDistinguishable() {
        val first = parseExplicitRoute("capture").copy(launchId = 1)
        val second = parseExplicitRoute("capture").copy(launchId = 2)
        assertEquals(DeepLinkTarget.Kind.CAPTURE, first.kind)
        assertEquals(DeepLinkTarget.Kind.CAPTURE, second.kind)
        kotlin.test.assertNotEquals(first.launchId, second.launchId)
    }

    @Test
    fun allowedWebViewUrlsOnlyAcceptOfficialHttpsDomains() {
        val accepted = listOf(
            "https://meow-config.aiyouaiyou.cn/api/help",
            "https://MEOW-CONFIG.AIYOUAIYOU.CN/api/help", // host 大小写归一化
            "https://cqmiaoa.com",
            "https://puppy-api.cqmiaoa.com/api/", // 子域
            "https://aiyouaiyou.cn/agreement",
            "https://sub.aiyouaiyou.cn:8443/help", // 合法端口不影响 host 判定
            "https://cqmiaoa.com:443",
            "https://meow-config.aiyouaiyou.cn/api/help?from=deep-link",
        )
        accepted.forEach { url -> assertTrue(isAllowedWebViewUrl(url), "应放行：$url") }
        assertTrue(isAllowedWebViewUrl(DEFAULT_WEBVIEW_URL))
    }

    @Test
    fun allowedWebViewUrlsRejectOpenRedirectAttempts() {
        val rejected = listOf(
            "",
            "   ",
            "not a url",
            "https://",
            "http://meow-config.aiyouaiyou.cn/api/help", // 明文 http
            "HTTP://cqmiaoa.com",
            "HTTPS://cqmiaoa.com", // scheme 必须小写，避免与 Uri.parse 大小写归一化产生歧义
            "ftp://cqmiaoa.com",
            "javascript://cqmiaoa.com",
            "//cqmiaoa.com/help",
            "https:meow-config.aiyouaiyou.cn/api/help",
            "https://evil.com",
            "https://evil.com/?x=cqmiaoa.com", // 白名单域出现在 query 里
            "https://evil.com/https://cqmiaoa.com", // 白名单域出现在 path 里
            "https://cqmiaoa.com.evil.com", // 后缀伪装
            "https://evilcqmiaoa.com", // 前缀伪装
            "https://aiyouaiyou.cn.evil.com",
            "https://cqmiaoa.com@evil.com", // userinfo 把真正 host 换成 evil.com
            "https://meow-config.aiyouaiyou.cn@evil.com/phish",
            "https://user:pass@cqmiaoa.com",
            "https://cqmiaoa.com:abc/help", // 非法端口
            "https://cqmiaoa.com:", // 空端口
            "https://[::1]/help", // IPv6 字面量不接受
            "https://cqm..com/help", // 空 host 段
            "https://cqmiaoa.com./help", // 尾点
            "https://cqm iaoa.com/help", // 空白字符
            "https://cqmiaoa.com\n.evil.com",
        )
        rejected.forEach { url -> assertFalse(isAllowedWebViewUrl(url), "应拒绝：$url") }
    }

    @Test
    fun paymentStatusNeverClaimsSuccessForMissingResult() {
        assertEquals("支付成功，会员权益与订单状态已更新", paymentStatusOf("0"))
        assertEquals("支付成功，会员权益与订单状态已更新", paymentStatusOf("success"))
        assertEquals("支付已取消，订单未生效", paymentStatusOf("cancel"))
        assertEquals("支付未完成，请重试或联系客服", paymentStatusOf("fail"))
        assertEquals("支付结果待确认，请稍后在会员页查看订单状态", paymentStatusOf(null))
        assertEquals("支付结果待确认，请稍后在会员页查看订单状态", paymentStatusOf(""))
        assertEquals("支付结果待确认，请稍后在会员页查看订单状态", paymentStatusOf("pending"))
        assertEquals("收到支付回调（weird），请在会员页核对订单状态", paymentStatusOf("weird"))
    }
}
