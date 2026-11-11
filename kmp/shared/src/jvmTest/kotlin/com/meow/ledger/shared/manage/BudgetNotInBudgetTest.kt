package com.meow.ledger.shared.manage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.newClientId
import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

/** S3 预算：not_in_budget 标记的支出不计入预算消耗。 */
class BudgetNotInBudgetTest {

    private fun repo(): Pair<BudgetRepository, MeowDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        val db = MeowDatabase(driver)
        return BudgetRepository(db) to db
    }

    private fun bill(money: Long, notInBudget: Long) = Bill(
        member_id = 0, client_id = newClientId(), money = money, refund_money = 0, type = TYPE_EXPENSE,
        exchange = 0, book_id = 1, member_cate_id = 1, currency = "CNY", day = 20261101, desc = "x",
        not_in_budget = notInBudget, images = "", update_date = 0, create_date = 0, status = 0,
        to_assets_id = 0, from_assets_id = 0, interest = 0, interest_reckon_type = 0, tag_ids = "",
        source = 0, import_id = 0L, repayment_day = 0, local_create_time = 0, local_update_time = 0,
        installment_plan_id = "", installment_num = 0, installment_money = 0, installment_interest = 0,
    )

    @Test
    fun notInBudgetBillsDoNotConsumeBudget() {
        val (repo, db) = repo()
        val bills = BillsRepoForBudget(db)
        bills.save(bill(3000, 0)) // 计入
        bills.save(bill(2000, 1)) // 不计入
        repo.save(202611, 10000, remind = false, remindRate = 80)
        val view = repo.view(202611)
        assertEquals(3000, view.spent)
        assertEquals(7000, view.budget!!.money - view.spent)
    }

    /** 复用账单仓储的最小封装，避免测试里直接碰 SQL。 */
    private class BillsRepoForBudget(private val db: MeowDatabase) {
        private val repo = com.meow.ledger.shared.bills.BillsRepository(db)
        fun save(bill: Bill) = repo.save(bill)
    }
}
