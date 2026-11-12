package com.meow.ledger.shared.manage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.bills.TYPE_TRANSFER
import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

/** M9：资产流水（逐笔变动 + 余额推算）。 */
class AssetFlowTest {

    private fun repo(): Pair<AssetRepository, MeowDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        val db = MeowDatabase(driver)
        db.assetQueries.upsert(
            com.meow.ledger.shared.db.Assets(
                id = 1, type_id = 1, type = 1, type_name = "cash", type_title = "现金",
                name = "钱包", desc = "", icon = "", currency = "CNY",
                create_date = 0, update_date = 0, credit_line = 0, repayment_date = 0,
                statement_date = 0, is_remind = 0, is_total_assets = 1, is_next_bills = 0,
                status = 1, balance_cents = 8000,
            ),
        )
        return AssetRepository(db) to db
    }

    private fun bill(clientId: String, type: Long, money: Long, from: Long, to: Long, day: Long = 20261110L) = Bill(
        member_id = 0, client_id = clientId, money = money, refund_money = 0, type = type,
        exchange = 0, book_id = 1, member_cate_id = 1, currency = "CNY", day = day, desc = clientId,
        not_in_budget = 0, images = "", update_date = 0, create_date = 0, status = 0,
        to_assets_id = to, from_assets_id = from, interest = 0, interest_reckon_type = 0, tag_ids = "",
        source = 0, import_id = 0L, repayment_day = 0, local_create_time = 0, local_update_time = 0,
        installment_plan_id = "", installment_num = 0, installment_money = 0, installment_interest = 0,
    )

    @Test
    fun expenseIncomeTransferAllAppear() {
        val (assets, db) = repo()
        val bills = com.meow.ledger.shared.bills.BillsRepository(db)
        bills.save(bill("e1", TYPE_EXPENSE, 3000, from = 1, to = 0))
        bills.save(bill("i1", TYPE_INCOME, 5000, from = 0, to = 1))
        bills.save(bill("t1", TYPE_TRANSFER, 2000, from = 1, to = 2))
        val rows = assets.flow(1)
        assertEquals(3, rows.size)
        assertEquals(-3000, rows.first { it.clientId == "e1" }.cents)
        assertEquals(5000, rows.first { it.clientId == "i1" }.cents)
        assertEquals(-2000, rows.first { it.clientId == "t1" }.cents)
    }

    @Test
    fun lastBalanceAfterMatchesCurrentBalance() {
        val (assets, db) = repo()
        val bills = com.meow.ledger.shared.bills.BillsRepository(db)
        bills.save(bill("i1", TYPE_INCOME, 10000, from = 0, to = 1, day = 20261101L))
        bills.save(bill("e1", TYPE_EXPENSE, 7000, from = 1, to = 0, day = 20261109L))
        val rows = assets.flow(1)
        assertEquals(8000, rows.first().balanceAfter) // 最新一笔之后 = 当前余额
        assertEquals(15000, rows.last().balanceAfter) // 期初 5000，首笔收入 10000 后 = 15000
    }

    @Test
    fun otherAssetsBillsAreExcluded() {
        val (assets, db) = repo()
        val bills = com.meow.ledger.shared.bills.BillsRepository(db)
        bills.save(bill("other", TYPE_EXPENSE, 999, from = 2, to = 0))
        assertEquals(0, assets.flow(1).size)
    }
}
