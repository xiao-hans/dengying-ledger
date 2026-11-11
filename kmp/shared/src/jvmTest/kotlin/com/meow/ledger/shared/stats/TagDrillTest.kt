package com.meow.ledger.shared.stats

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.Bill_tag
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S3 统计下钻：按标签聚合、按标签取账单。 */
class TagDrillTest {

    private fun seed(): Pair<MeowDatabase, TagDrill> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        val db = MeowDatabase(driver)
        return db to TagDrill(db)
    }

    private fun bill(clientId: String, money: Long, day: Long) = Bill(
        member_id = 0, client_id = clientId, money = money, refund_money = 0, type = TYPE_EXPENSE,
        exchange = 0, book_id = 1, member_cate_id = 1, currency = "CNY", day = day, desc = clientId,
        not_in_budget = 0, images = "", update_date = 0, create_date = 0, status = 0,
        to_assets_id = 0, from_assets_id = 0, interest = 0, interest_reckon_type = 0, tag_ids = "",
        source = 0, import_id = 0L, repayment_day = 0, local_create_time = 0, local_update_time = 0,
        installment_plan_id = "", installment_num = 0, installment_money = 0, installment_interest = 0,
    )

    private fun insertBill(db: MeowDatabase, bill: Bill) {
        db.billQueries.upsert(bill)
    }

    private fun tag(id: Long, name: String) = Tag(
        tag_id = id, book_id = 1, member_id = 0, name = name, sort = id,
        create_date = 0, update_date = 0,
    )

    @Test
    fun byTagAggregatesNetMoneyPerTag() {
        val (db, drill) = seed()
        insertBill(db, bill("a", 5000, 20261101))
        insertBill(db, bill("b", 3000, 20261102))
        db.tagQueries.upsert(tag(1, "聚餐"))
        db.s2Queries.upsertBillTag(Bill_tag(client_id = "a", tag_id = 1, member_id = 0))
        db.s2Queries.upsertBillTag(Bill_tag(client_id = "b", tag_id = 1, member_id = 0))

        val rows = drill.byTag(20261101, 20261130, TYPE_EXPENSE)
        assertEquals(1, rows.size)
        assertEquals(1L, rows[0].key)
        assertEquals(8000, rows[0].cents)
        assertEquals(2, rows[0].count)
    }

    @Test
    fun billsOfTagReturnsOnlyLinkedBills() {
        val (db, drill) = seed()
        insertBill(db, bill("a", 5000, 20261101))
        insertBill(db, bill("b", 3000, 20261102))
        db.tagQueries.upsert(tag(1, "聚餐"))
        db.s2Queries.upsertBillTag(Bill_tag(client_id = "a", tag_id = 1, member_id = 0))

        val bills = drill.billsOfTag(20261101, 20261130, 1, TYPE_EXPENSE)
        assertEquals(1, bills.size)
        assertEquals("a", bills[0].client_id)
    }

    @Test
    fun drillIgnoresDeletedAndWrongType() {
        val (db, drill) = seed()
        val deleted = bill("c", 9000, 20261103).copy(status = 1)
        insertBill(db, deleted)
        insertBill(db, bill("d", 1000, 20261104).copy(type = 1)) // 收入
        val rows = drill.byTag(20261101, 20261130, TYPE_EXPENSE)
        assertTrue(rows.isEmpty())
    }
}
