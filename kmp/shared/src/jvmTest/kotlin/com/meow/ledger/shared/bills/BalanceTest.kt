package com.meow.ledger.shared.bills

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.Assets
import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

/** S5：记账/删账与资产余额联动。 */
class BalanceTest {
    private fun db(): MeowDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return MeowDatabase(driver)
    }

    private fun asset(id: Long, balance: Long) = Assets(
        id = id, type_id = 1, type = 1, type_name = "bank", type_title = "储蓄卡",
        name = "卡$id", desc = "", icon = "", currency = "CNY",
        create_date = 0, update_date = 0, credit_line = 0, repayment_date = 0,
        statement_date = 0, is_remind = 0, is_total_assets = 1, is_next_bills = 0,
        status = 1, balance_cents = balance,
    )

    private fun bill(type: Long, money: Long, from: Long, to: Long) = Bill(
        member_id = 0, client_id = "t$type$money$from$to", money = money, refund_money = 0,
        type = type, exchange = 0, book_id = 1, member_cate_id = 1, currency = "CNY",
        day = 20261101L, desc = "", not_in_budget = 0, images = "", update_date = 0,
        create_date = 0, status = 0, to_assets_id = to, from_assets_id = from,
        interest = 0, interest_reckon_type = 0, tag_ids = "", source = 0, import_id = 0L,
        repayment_day = 0, local_create_time = 0, local_update_time = 0,
        installment_plan_id = "", installment_num = 0, installment_money = 0,
        installment_interest = 0,
    )

    @Test
    fun expenseDeductsFromCard() {
        val d = db()
        d.assetQueries.upsert(asset(1, 10000))
        BillsRepository(d).saveWithBalance(bill(TYPE_EXPENSE, 2500, from = 1, to = 0))
        assertEquals(7500, d.assetQueries.selectOne(1).executeAsOne().balance_cents)
    }

    @Test
    fun incomeAddsToCard() {
        val d = db()
        d.assetQueries.upsert(asset(1, 10000))
        BillsRepository(d).saveWithBalance(bill(TYPE_INCOME, 3000, from = 0, to = 1))
        assertEquals(13000, d.assetQueries.selectOne(1).executeAsOne().balance_cents)
    }

    @Test
    fun transferMovesBetweenCards() {
        val d = db()
        d.assetQueries.upsert(asset(1, 10000))
        d.assetQueries.upsert(asset(2, 100))
        BillsRepository(d).saveWithBalance(bill(TYPE_TRANSFER, 4000, from = 1, to = 2))
        assertEquals(6000, d.assetQueries.selectOne(1).executeAsOne().balance_cents)
        assertEquals(4100, d.assetQueries.selectOne(2).executeAsOne().balance_cents)
    }

    @Test
    fun deleteRollsBalanceBack() {
        val d = db()
        d.assetQueries.upsert(asset(1, 10000))
        val repo = BillsRepository(d)
        val b = bill(TYPE_EXPENSE, 2500, from = 1, to = 0)
        repo.saveWithBalance(b)
        repo.deleteWithBalance(0, b.client_id)
        assertEquals(10000, d.assetQueries.selectOne(1).executeAsOne().balance_cents)
    }
}
