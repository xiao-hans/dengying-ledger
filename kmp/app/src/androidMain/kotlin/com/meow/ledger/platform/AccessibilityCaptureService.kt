package com.meow.ledger.platform

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍采集入口。只收集窗口标题与可见文本作为候选，不代替用户点击，也不自动入账。
 */
class AccessibilityCaptureService : AccessibilityService() {
    /** 同一包名的窗口变化会连发几十条，800ms 内只取最后一条文本。 */
    private val lastAt = HashMap<String, Long>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return
        val pkg = event.packageName?.toString().orEmpty()
        val now = event.eventTime
        val last = lastAt[pkg] ?: 0L
        if (now - last < THROTTLE_MS) return
        lastAt[pkg] = now
        val text = event.text?.joinToString(" ") { it.toString() }.orEmpty()
        if (text.isBlank()) return
        if (text.length > 500) return
        CandidateQueue.add(
            CaptureCandidate(
                source = "accessibility",
                sourceApp = event.packageName?.toString().orEmpty(),
                title = event.className?.toString().orEmpty(),
                text = text,
                occurredAt = event.eventTime,
            ),
        )
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val THROTTLE_MS = 800L
    }
}
