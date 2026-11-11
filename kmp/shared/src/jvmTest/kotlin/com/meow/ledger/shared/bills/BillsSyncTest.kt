package com.meow.ledger.shared.bills

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S2 业务层：批量改分类、搜索、导入幂等、日合计与幂等键。 */
class BillsSyncTest {

    private fun repo(): Pair<BillsRepository, MeowDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        val db = MeowDatabase(driver)
        return BillsRepository(db) to db
    }

    private fun bill(clientId: String, money: Long = 1000L, cate: Long = 1L, day: Long = 20261101L) =
        Bill(
            member_id = 0, client_id = clientId, money = money, refund_money = 0, type = TYPE_EXPENSE,
            exchange = 0, book_id = 1, member_cate_id = cate, currency = "CNY", day = day, desc = "备注$clientId",
            not_in_budget = 0, images = "", update_date = 0, create_date = 0, status = 0,
            to_assets_id = 0, from_assets_id = 0, interest = 0, interest_reckon_type = 0, tag_ids = "",
            source = 0, import_id = 0L, repayment_day = 0, local_create_time = day * 1000,
            local_update_time = day * 1000, installment_plan_id = "", installment_num = 0,
            installment_money = 0, installment_interest = 0,
        )

    @Test
    fun batchUpdateMovesCategoryOnlyForExistingBills() {
        val (repo, _) = repo()
        repo.save(bill("a"))
        repo.save(bill("b"))
        assertEquals(2, repo.batchUpdate(0, listOf("a", "b", "ghost"), 7L))
        assertEquals(7L, repo.get(0, "a")!!.member_cate_id)
        assertEquals(7L, repo.get(0, "b")!!.member_cate_id)
        assertNull(repo.get(0, "ghost"))
    }

    @Test
    fun searchFindsByRemarkAndFiltersBook() {
        val (repo, _) = repo()
        repo.save(bill("a", day = 20261101L)) // book 1
        repo.save(bill("b").copy(book_id = 2))
        assertEquals(2, repo.search("备注").size)
        assertEquals(1, repo.search("备注", bookId = 1).size)
        assertEquals(0, repo.search("不存在").size)
    }

    @Test
    fun importIsIdempotentOnSameClientId() {
        val (repo, _) = repo()
        val one = bill("dup")
        assertEquals(1, repo.importAll(listOf(one)))
        assertEquals(1, repo.importAll(listOf(one.copy(money = 2500L))))
        val loaded = repo.get(0, "dup")!!
        assertEquals(2500L, loaded.money)
        assertEquals(1, repo.month(202611).summary.count)
    }

    @Test
    fun daySumAddsExpenseAndIncome() {
        val (repo, _) = repo()
        repo.save(bill("a", money = 1200L, day = 20261101))
        repo.save(bill("b", money = 3000L, day = 20261101).copy(type = TYPE_INCOME))
        val sum = repo.daySum(20261101)
        assertEquals(1200L, sum.expense)
        assertEquals(3000L, sum.income)
        assertEquals(2, sum.count)
    }

    @Test
    fun hasPicCountsOnlyImages() {
        val (repo, _) = repo()
        repo.save(bill("a"))
        repo.save(bill("b").copy(images = "oss://p1.jpg"))
        assertEquals(1, repo.hasPicCount())
    }

    @Test
    fun clientIdIsUniqueWithinProcess() {
        val first = newClientId()
        val second = newClientId()
        assertNotEquals(first, second)
        assertTrue(first.isNotEmpty())
    }
}
