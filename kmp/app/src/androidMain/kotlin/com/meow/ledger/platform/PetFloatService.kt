package com.meow.ledger.platform

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 悬浮宠物服务入口。悬浮窗属于系统级覆盖层，只在用户显式开启后由设置页启动；
 * 服务自身不申请覆盖权限，也不读取账单数据。
 */
class PetFloatService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_HIDE = "com.meow.ledger.pet.hide"
    }
}
