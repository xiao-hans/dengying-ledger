package com.meow.ledger.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.Notification
import com.meow.ledger.MainActivity
import com.meow.ledger.R

/** 周期记账与账单提醒的系统通知出口。 */
class BillAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifEmpty { "该记一笔了" }
        val body = intent?.getStringExtra(EXTRA_BODY).orEmpty().ifEmpty { "打开账本记录这一笔" }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "账单提醒", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .putExtra(ScreenCaptureTileService.EXTRA_ROUTE, ROUTE_BILL_EDIT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val EXTRA_TITLE = "com.meow.ledger.alarm.title"
        const val EXTRA_BODY = "com.meow.ledger.alarm.body"
        const val ROUTE_BILL_EDIT = "bill-edit"
        private const val CHANNEL_ID = "bill_reminder"
        private const val NOTIFICATION_ID = 2201
        private const val REQUEST_OPEN = 2202
    }
}
