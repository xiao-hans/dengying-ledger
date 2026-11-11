package com.meow.ledger.shared.manage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssetsTest {
    private fun db(): MeowDatabase {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return MeowDatabase(driver)
    }

    @Test
    fun defaultAssetIsVisibleCash() {
        val repo = AssetRepository(db())
        repo.ensureDefault()
        repo.ensureDefault()
        val cash = repo.one(DEFAULT_ASSET_ID)
        assertNotNull(cash)
        assertEquals("现金", cash.name)
        assertEquals(1L, cash.status)
        assertEquals(1, repo.visible().size)
    }

    @Test
    fun hideKeepsTheRowOutOfTheVisibleList() {
        val repo = AssetRepository(db())
        val card = repo.save("工资卡", 2, 500_00)
        assertNotNull(card)
        repo.setVisible(card.id, false)
        assertEquals(0, repo.visible().size)
        assertEquals(1, repo.all().size)
        repo.setVisible(card.id, true)
        assertEquals(1, repo.visible().size)
    }

    @Test
    fun defaultAssetCannotBeDeleted() {
        val repo = AssetRepository(db())
        repo.ensureDefault()
        assertFalse(repo.delete(DEFAULT_ASSET_ID))
        assertNotNull(repo.one(DEFAULT_ASSET_ID))
        val card = repo.save("备用卡", 2, 0)
        assertNotNull(card)
        assertTrue(repo.delete(card.id))
        assertNull(repo.one(card.id))
    }

    @Test
    fun iouNamesListsOnlyVisibleIous() {
        val repo = AssetRepository(db())
        val owed = repo.save("张三", 6, 200_00, isTotalAssets = false)
        repo.save("李四", 6, 100_00, isTotalAssets = false)
        assertNotNull(owed)
        repo.setVisible(owed.id, false)
        assertEquals("李四", repo.iouNames())
    }
}
