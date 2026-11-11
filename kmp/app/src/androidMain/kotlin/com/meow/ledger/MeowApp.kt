package com.meow.ledger

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.ui.AppGraph

class MeowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val driver = AndroidSqliteDriver(MeowDatabase.Schema, this, "meow_ledger.db")
        AppGraph.database = MeowDatabase(driver)
    }
}
