package com.meow.ledger.shared.manage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

/** M14：桌面小组件额外行配置存取。 */
class WidgetConfigTest {

    private fun repo(): WidgetRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return WidgetRepository(MeowDatabase(driver))
    }

    @Test
    fun emptyByDefault() {
        assertEquals(emptySet(), repo().extraRows())
    }

    @Test
    fun roundTripSorted() {
        val repo = repo()
        repo.setExtraRows(setOf(WIDGET_ROW_REFUND, WIDGET_ROW_BUDGET))
        assertEquals(setOf(WIDGET_ROW_BUDGET, WIDGET_ROW_REFUND), repo.extraRows())
        repo.setExtraRows(setOf(WIDGET_ROW_PLAN))
        assertEquals(setOf(WIDGET_ROW_PLAN), repo.extraRows())
        repo.setExtraRows(emptySet())
        assertEquals(emptySet(), repo.extraRows())
    }
}
