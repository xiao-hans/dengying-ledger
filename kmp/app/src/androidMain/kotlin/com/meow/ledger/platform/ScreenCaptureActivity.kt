package com.meow.ledger.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.meow.ledger.R
import com.meow.ledger.shared.auto.CaptureConsentOutcome
import com.meow.ledger.shared.auto.decideCaptureConsent
import com.meow.ledger.shared.auto.shouldLaunchCaptureConsent

/**
 * 透明 Activity：向系统申请一次 MediaProjection 授权，拿到结果后把 resultCode/data
 * 交给前台服务抓一帧，随后显式 finish()。
 *
 * 清单里**不能**带 `noHistory`：系统同意对话框覆盖本 Activity 时 `noHistory` 会把它
 * 结束掉，结果再也收不到，截屏会静默失败。所有结束路径都给用户可见反馈：授权成功由
 * 服务通知承担，其余路径弹 [R.string.capture_consent_denied] Toast。
 */
class ScreenCaptureActivity : ComponentActivity() {

    /** 同意 intent 是否已发出；配置变更重建时据此避免重复申请授权。 */
    private var consentLaunched = false

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val granted = result.resultCode == Activity.RESULT_OK && data != null
        var serviceStarted = false
        if (granted) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            // 所有系统启动都兜底：Android 14+ 对 mediaProjection 前台服务类型有额外限制。
            serviceStarted = runCatching { startForegroundService(intent); true }
                .recoverCatching { startService(intent); true }
                .getOrDefault(false)
        }
        when (decideCaptureConsent(granted, data != null, serviceStarted)) {
            CaptureConsentOutcome.START_SERVICE -> {
                // 用户可见反馈由 ScreenCaptureService 的前台/结果通知给出。
            }
            CaptureConsentOutcome.NOTIFY_DENIED -> showDeniedToast()
        }
        // 结果已处理，显式结束；noHistory 由清单移除，不会再被系统提前收走。
        finishSafely()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consentLaunched = savedInstanceState?.getBoolean(KEY_CONSENT_LAUNCHED, false) ?: false
        // 旋转屏幕 / 语言切换等配置变更时，ActivityResult 契约会重新投递结果；
        // 这里若再 launch 一次，会出现两层同意对话框并丢掉第一次的结果。
        // 标记来自 savedInstanceState 时只等结果回调，既不重发也不弹「进行中」提示。
        if (!shouldLaunchCaptureConsent(consentLaunched)) return
        launchConsent()
    }

    /**
     * FLAG_ACTIVITY_SINGLE_TOP 下，重复点击 / 同意页卡住 / 结果重投递会把新请求交给
     * onNewIntent。未发过同意就走与 onCreate 相同的 [launchConsent]；已发过则只提示
     * 「授权进行中」，绝不弹第二个系统对话框，也不结束正在进行的 Activity。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (shouldLaunchCaptureConsent(consentLaunched)) {
            launchConsent()
        } else {
            showInProgressToast()
        }
    }

    /** onCreate 与 onNewIntent 共用的唯一授权路径：构造系统同意 intent 并 launch 一次。 */
    private fun launchConsent() {
        val manager = runCatching {
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        }.getOrNull()
        val prompt = manager?.let { runCatching { it.createScreenCaptureIntent() }.getOrNull() }
        if (prompt == null) {
            // 系统组件缺失也要让用户知道本次没采集，不能静默结束。
            showDeniedToast()
            finishSafely()
            return
        }
        // 先落标记再 launch：同意对话框一弹出就可能发生配置变更，标记必须已经可保存。
        consentLaunched = true
        // ActivityNotFoundException / SecurityException 等系统异常统一走兜底文案并结束。
        runCatching { consent.launch(prompt) }
            .onFailure { _: Throwable ->
                showDeniedToast()
                finishSafely()
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_CONSENT_LAUNCHED, consentLaunched)
        super.onSaveInstanceState(outState)
    }

    private fun showDeniedToast() {
        runCatching {
            Toast.makeText(applicationContext, getString(R.string.capture_consent_denied), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInProgressToast() {
        runCatching {
            Toast.makeText(applicationContext, getString(R.string.capture_consent_in_progress), Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishSafely() {
        runCatching { finish() }
    }

    companion object {
        private const val KEY_CONSENT_LAUNCHED = "com.meow.ledger.capture.consentLaunched"

        /**
         * 从前台的 MainActivity 拉起授权流程。返回是否真的启动了 Activity：
         * 启动失败不吞错，调用方据此给用户可见的失败反馈，避免「假成功」。
         */
        fun launch(context: Context): Boolean {
            val intent = Intent(context, ScreenCaptureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return runCatching { context.startActivity(intent); true }.getOrDefault(false)
        }
    }
}
