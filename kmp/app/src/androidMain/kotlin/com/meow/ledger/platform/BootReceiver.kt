package com.meow.ledger.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机后恢复周期记账提醒的注册入口。实际闹钟排程由后续周期账切片实现；
 * 这里只保留显式广播记录，避免开机静默启动采集服务。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        SchedulersState.markBootCompleted()
    }
}

object SchedulersState {
    @Volatile
    var bootCompleted: Boolean = false
        private set

    fun markBootCompleted() {
        bootCompleted = true
    }
}
