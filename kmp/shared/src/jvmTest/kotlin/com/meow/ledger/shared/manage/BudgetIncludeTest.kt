package com.meow.ledger.shared.manage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M7：预算包含/排除视图与复制上月（isCopy 语义）。 */
class BudgetIncludeTest {

    private fun repo(): Pair<BudgetRepository, MeowDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        val db = MeowDatabase(driver)
        return BudgetRepository(db) to db
    }

    @Test
    fun copyPreviousClonesBudgetAndCateRows() {
        val (repo, db) = repo()
        repo.save(202611, 100000, remind = true, remindRate = 80)
        repo.saveCategory(202611, 9, 30000)
        val copied = repo.copyPrevious(202612)!!
        assertEquals(100000, copied.money)
        assertEquals(1, copied.is_copy)
        assertEquals(30000, db.budgetQueries.selectByMonth(1, 202612).executeAsList().first().money)
        // 上月原样保留
        assertEquals(100000, db.budgetQueries.selectOne(1, 202611).executeAsOneOrNull()!!.money)
        assertEquals(0, db.budgetQueries.selectOne(1, 202611).executeAsOneOrNull()!!.is_copy)
    }

    @Test
    fun copyPreviousCrossesYear() {
        val (repo, db) = repo()
        repo.save(202612, 50000, remind = false, remindRate = 80)
        assertEquals(50000, repo.copyPrevious(202701)!!.money)
        assertEquals(50000, db.budgetQueries.selectOne(1, 202701).executeAsOneOrNull()!!.money)
    }

    @Test
    fun copyWithoutPreviousReturnsNull() {
        val (repo, db) = repo()
        assertNull(repo.copyPrevious(202611))
        assertEquals(0, db.budgetQueries.selectByMonth(1, 202611).executeAsList().size)
        assertNull(db.budgetQueries.selectOne(1, 202611).executeAsOneOrNull())
    }

    @Test
    fun viewSplitsIncludedAndExcluded() {
        val (repo, db) = repo()
        val catRepo = com.meow.ledger.shared.bills.CategoryRepository(db)
        val food = catRepo.create("餐饮", com.meow.ledger.shared.bills.TYPE_EXPENSE, 0)
        val drink = catRepo.create("饮料", com.meow.ledger.shared.bills.TYPE_EXPENSE, 0)
        repo.save(202611, 100000, remind = false, remindRate = 80)
        repo.saveCategory(202611, food.id, 30000)
        val view = repo.view(202611)
        val included = view.categories.filter { it.money > 0 }
        val excluded = view.categories.filter { it.money == 0L }
        assertEquals(1, included.size)
        assertEquals("餐饮", included.first().title)
        assertTrue(excluded.any { it.categoryId == drink.id })
        assertTrue(excluded.none { it.categoryId == food.id })
    }
}
