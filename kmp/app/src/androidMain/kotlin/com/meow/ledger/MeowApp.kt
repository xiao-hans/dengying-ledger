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
    }
}
