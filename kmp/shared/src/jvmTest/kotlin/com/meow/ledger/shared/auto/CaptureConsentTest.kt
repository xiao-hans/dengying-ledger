package com.meow.ledger.shared.auto

import kotlin.test.Test
import kotlin.test.assertEquals

/** 截屏授权结果判定的纯 JVM 回归：任何失败路径都必须有用户可见反馈。 */
class CaptureConsentTest {

    @Test
    fun onlyOkResultWithDataAndStartedServiceLaunchesCapture() {
        assertEquals(
            CaptureConsentOutcome.START_SERVICE,
            decideCaptureConsent(resultOk = true, hasData = true, serviceStarted = true),
        )
    }

    @Test
    fun deniedOrCancelledResultNeverStartsService() {
        assertEquals(
            CaptureConsentOutcome.NOTIFY_DENIED,
            decideCaptureConsent(resultOk = false, hasData = true, serviceStarted = false),
        )
        assertEquals(
            CaptureConsentOutcome.NOTIFY_DENIED,
            decideCaptureConsent(resultOk = false, hasData = false, serviceStarted = false),
        )
    }

    @Test
    fun missingDataEvenWithOkResultNeverStartsService() {
        assertEquals(
            CaptureConsentOutcome.NOTIFY_DENIED,
            decideCaptureConsent(resultOk = true, hasData = false, serviceStarted = false),
        )
    }

    @Test
    fun failedServiceStartFallsBackToVisibleDeniedFeedback() {
        // startForegroundService 抛异常时不能静默 finish，必须走 Toast。
        assertEquals(
            CaptureConsentOutcome.NOTIFY_DENIED,
            decideCaptureConsent(resultOk = true, hasData = true, serviceStarted = false),
        )
    }

    @Test
    fun allEightCombinationsHaveADecision() {
        var startCount = 0
        for (ok in listOf(false, true)) {
            for (data in listOf(false, true)) {
                for (started in listOf(false, true)) {
                    val outcome = decideCaptureConsent(ok, data, started)
                    if (outcome == CaptureConsentOutcome.START_SERVICE) startCount++
                    assertEquals(ok && data && started, outcome == CaptureConsentOutcome.START_SERVICE)
                }
            }
        }
        assertEquals(1, startCount)
    }

    @Test
    fun consentIntentIsLaunchedOnlyOnceAcrossRecreation() {
        assertEquals(true, shouldLaunchCaptureConsent(consentAlreadyLaunched = false))
        assertEquals(false, shouldLaunchCaptureConsent(consentAlreadyLaunched = true))
    }
}
