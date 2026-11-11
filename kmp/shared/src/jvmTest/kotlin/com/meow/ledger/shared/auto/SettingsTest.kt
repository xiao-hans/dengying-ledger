package com.meow.ledger.shared.auto

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsTest {
    private fun store(): SettingsStore {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return SettingsStore(MeowDatabase(driver))
    }

    @Test
    fun flagFallsBackToTheDefaultUntilStored() {
        val store = store()
        assertFalse(store.flag(KEY_AUTO_BILL, false))
        assertTrue(store.flag(KEY_NOTIFICATION, true))
        store.setFlag(KEY_AUTO_BILL, true)
        assertTrue(store.flag(KEY_AUTO_BILL, false))
        store.setFlag(KEY_AUTO_BILL, false)
        assertFalse(store.flag(KEY_AUTO_BILL, true))
    }

    @Test
    fun watchAppsDefaultToTheFirstThreeAndEmptyIsEmpty() {
        val store = store()
        assertEquals(DEFAULT_WATCH_APPS.toSet(), store.watchApps())
        store.setWatchApps(emptySet())
        assertEquals(emptySet(), store.watchApps())
        store.setWatchApps(setOf("com.tencent.mm"))
        assertEquals(setOf("com.tencent.mm"), store.watchApps())
    }

    @Test
    fun textRoundTrips() {
        val store = store()
        assertEquals(THEME_SYSTEM, store.text(KEY_THEME, THEME_SYSTEM))
        store.setText(KEY_THEME, THEME_DARK)
        assertEquals(THEME_DARK, store.text(KEY_THEME, THEME_SYSTEM))
    }
}
