package com.meow.ledger.shared.manage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogTest {
    private fun repo(): CatalogRepository {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return CatalogRepository(MeowDatabase(driver)).also { it.ensureSeed() }
    }

    @Test
    fun builtinCategoryCannotBeDeletedButCanBeHidden() {
        val repo = repo()
        assertEquals(9, repo.categories(TYPE_EXPENSE).size)
        assertFalse(repo.deleteCategory(1))
        repo.setHidden(1, true)
        assertEquals(8, repo.visibleCategories(TYPE_EXPENSE).size)
        assertEquals(9, repo.categories(TYPE_EXPENSE).size)
        repo.setHidden(1, false)
        assertEquals(9, repo.visibleCategories(TYPE_EXPENSE).size)
    }

    @Test
    fun customCategoryCanBeEditedAndDeleted() {
        val repo = repo()
        val coffee = repo.saveCategory("咖啡", TYPE_EXPENSE, "coin")
        assertNotNull(coffee)
        assertEquals(1L, coffee.is_custom)
        val renamed = repo.saveCategory("外卖咖啡", TYPE_EXPENSE, "pet", coffee.id)
        assertNotNull(renamed)
        assertEquals("外卖咖啡", renamed.title)
        assertTrue(repo.deleteCategory(coffee.id))
        assertNull(repo.category(coffee.id))
        assertEquals(4, repo.categories(TYPE_INCOME).size)
    }

    @Test
    fun duplicateTagNameIsRejected() {
        val repo = repo()
        val first = repo.saveTag("报销")
        assertNotNull(first)
        assertNull(repo.saveTag("报销"))
        assertNotNull(repo.saveTag("差旅"))
        val renamed = repo.saveTag("报销", tagId = first.tag_id)
        assertNotNull(renamed)
        assertEquals("报销", renamed.name)
    }

    @Test
    fun sortingTagsRenumbersFromOne() {
        val repo = repo()
        val a = repo.saveTag("甲")
        val b = repo.saveTag("乙")
        val c = repo.saveTag("丙")
        assertNotNull(a)
        assertNotNull(b)
        assertNotNull(c)
        repo.sortTags(listOf(c.tag_id, a.tag_id, b.tag_id))
        val order = repo.tags().map { it.name }
        assertEquals(listOf("丙", "甲", "乙"), order)
        repo.deleteTag(b.tag_id)
        assertEquals(2, repo.tags().size)
    }
}
