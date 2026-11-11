package com.meow.ledger.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机后重建周期记账提醒的精确闹钟。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        SchedulersState.markBootCompleted()
        goAsync().let {
            RemindScheduler.scheduleAll(context)
            AppWidgetReceiver.refresh(context)
            it.finish()
        }
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
