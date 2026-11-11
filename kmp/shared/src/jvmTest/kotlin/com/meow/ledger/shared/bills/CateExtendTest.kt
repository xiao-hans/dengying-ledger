package com.meow.ledger.shared.bills

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S2 业务层：分类扩展（排序与隐藏）。 */
class CateExtendTest {

    private fun db(): MeowDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        val database = MeowDatabase(driver)
        seedCategoriesIfEmpty(database)
        return database
    }

    @Test
    fun hiddenCategoriesDisappearFromList() {
        val cate = CategoryRepository(db())
        assertEquals(9, cate.listWithExtend(TYPE_EXPENSE, bookId = 1).size)
        cate.setExtend(1L, bookId = 1, memberId = 0, sort = 0, hidden = 1)
        assertEquals(8, cate.listWithExtend(TYPE_EXPENSE, bookId = 1).size)
        assertTrue(cate.listWithExtend(TYPE_EXPENSE, bookId = 1).none { it.id == 1L })
    }

    @Test
    fun extendSortOverridesDefaultOrder() {
        val cate = CategoryRepository(db())
        cate.setExtend(9L, bookId = 1, memberId = 0, sort = -1, hidden = 0)
        assertEquals(9L, cate.listWithExtend(TYPE_EXPENSE, bookId = 1).first().id)
    }

    @Test
    fun ofTypeKeepsDefaultBehaviour() {
        val cate = CategoryRepository(db())
        assertEquals(9, cate.ofType(TYPE_EXPENSE).size)
        cate.setExtend(1L, bookId = 1, memberId = 0, sort = 0, hidden = 1)
        // ofType 不看扩展表，保持首页/记账页的口径不变。
        assertEquals(9, cate.ofType(TYPE_EXPENSE).size)
    }
}
