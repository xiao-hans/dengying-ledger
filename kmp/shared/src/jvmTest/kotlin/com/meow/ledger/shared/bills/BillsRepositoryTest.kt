package com.meow.ledger.shared.bills

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BillsRepositoryTest {
    private fun database(): MeowDatabase {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return MeowDatabase(driver)
    }

    private fun bill(clientId: String, money: Long, type: Long, day: Long, desc: String) =
        com.meow.ledger.shared.db.Bill(
            member_id = 0, client_id = clientId, money = money, refund_money = 0,
            type = type, exchange = 0, book_id = 1, member_cate_id = 1, currency = "CNY",
            day = day, desc = desc, not_in_budget = 0, images = "", update_date = 0,
            create_date = day, status = 0, to_assets_id = 0, from_assets_id = 0,
            interest = 0, interest_reckon_type = 0, tag_ids = "", source = 0, import_id = 0,
            repayment_day = 0, local_create_time = day, local_update_time = day,
            installment_plan_id = "", installment_num = 0, installment_money = 0,
            installment_interest = 0,
        )

    @Test
    fun saveThenReadBackTheMonth() {
        val db = database()
        val repository = BillsRepository(db)
        repository.save(bill("a", 1500, TYPE_EXPENSE, 20261110, "咖啡"))
        repository.save(bill("b", 200000, TYPE_INCOME, 20261110, "报销"))

        val view = repository.month(202611)

        assertEquals(1500, view.summary.expense)
        assertEquals(200000, view.summary.income)
        assertEquals(2, view.summary.count)
        assertEquals(listOf("报销", "咖啡"), view.groups.single().bills.map { it.desc })
    }

    @Test
    fun savingTheSameClientIdUpdatesInsteadOfDuplicating() {
        val db = database()
        val repository = BillsRepository(db)
        repository.save(bill("a", 1500, TYPE_EXPENSE, 20261110, "咖啡"))
        repository.save(bill("a", 1800, TYPE_EXPENSE, 20261110, "拿铁"))

        val view = repository.month(202611)

        assertEquals(1, view.summary.count)
        assertEquals(1800, view.summary.expense)
        assertEquals("拿铁", view.groups.single().bills.single().desc)
    }

    @Test
    fun seedCategoriesWritesTheBuiltInSetOnce() {
        val db = database()
        seedCategoriesIfEmpty(db)
        seedCategoriesIfEmpty(db)

        val categories = CategoryRepository(db)
        assertEquals(9, categories.ofType(TYPE_EXPENSE).size)
        assertEquals(listOf("工资", "奖金", "理财", "其他收入"), categories.ofType(TYPE_INCOME).map { it.title })
        assertTrue(categories.ofType(TYPE_EXPENSE).first().title == "餐饮")
    }
}
