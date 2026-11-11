package com.meow.ledger.platform

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 通知采集入口。解析出的候选写入待确认队列，由用户在 App 内通过后才能入账。
 */
class NotificationCaptureService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras: Bundle = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return
        CandidateQueue.add(
            CaptureCandidate(
                source = "notification",
                sourceApp = sbn.packageName.orEmpty(),
                title = title,
                text = text,
                occurredAt = sbn.postTime,
            ),
        )
    }
}
