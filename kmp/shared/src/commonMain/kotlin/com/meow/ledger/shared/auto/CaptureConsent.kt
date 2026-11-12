package com.meow.ledger.shared.auto

/**
 * 截屏授权结果的纯逻辑判定（不依赖 Android，可在 JVM 单测）。
 *
 * 授权 Activity 必须等系统 MediaProjection 同意对话框返回结果，所以清单里不能带
 * `noHistory`；结果回来后由这里决定「启动采集服务」还是「给用户可见的拒绝反馈」。
 * 任何一条不满足都必须落到 NOTIFY_DENIED，绝不静默结束。
 */
enum class CaptureConsentOutcome {
    /** resultCode=OK + data 非空 + 前台服务已启动：服务通知会给用户可见反馈。 */
    START_SERVICE,

    /** 拒绝 / 取消 / 无 data / 服务启动失败：Activity 必须弹 Toast 再 finish。 */
    NOTIFY_DENIED,
}

/** 只有三条都成立才允许把 resultCode+data 交给采集服务。 */
fun decideCaptureConsent(
    resultOk: Boolean,
    hasData: Boolean,
    serviceStarted: Boolean,
): CaptureConsentOutcome = if (resultOk && hasData && serviceStarted) {
    CaptureConsentOutcome.START_SERVICE
} else {
    CaptureConsentOutcome.NOTIFY_DENIED
}

/**
 * 配置变更（旋转屏幕等）导致 Activity 重建时，savedInstanceState 里已有
 * 「授权 intent 已发出」标记就不能再发一次，否则会出现两层同意对话框并丢结果。
 */
fun shouldLaunchCaptureConsent(consentAlreadyLaunched: Boolean): Boolean = !consentAlreadyLaunched
