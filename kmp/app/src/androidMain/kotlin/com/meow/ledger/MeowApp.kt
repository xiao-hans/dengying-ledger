package com.meow.ledger

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.network.SyncEngine
import com.meow.ledger.ui.AppGraph

class MeowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val driver = AndroidSqliteDriver(MeowDatabase.Schema, this, "meow_ledger.db")
        AppGraph.database = MeowDatabase(driver)
        // 纯本地模式：写操作入 sync_queue，接真实后端后由 ApiGate 送达。
        AppGraph.sync = SyncEngine(AppGraph.database)
        // 文件桥与采集桥都需要进程级上下文。
        com.meow.ledger.ui.AndroidHost.appContext = this
        com.meow.ledger.platform.CaptureBridge.start(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default))
        // 补齐错过的周期账账期（source=5 自动入账，幂等），再重排提醒与小组件。
        com.meow.ledger.shared.manage.FixedBillRepository(AppGraph.database).generateDueBills()
        // 周期提醒随进程启动重排，桌面小组件同步今日收支。
        com.meow.ledger.platform.RemindScheduler.scheduleAll(this)
        com.meow.ledger.platform.AppWidgetReceiver.refresh(this)
    }
}
