package com.meow.ledger.shared.manage

import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.netPaid
import com.meow.ledger.shared.db.Budget
import com.meow.ledger.shared.db.Cate_budget
import com.meow.ledger.shared.db.MeowDatabase

/** 一个分类在当月的预算与已用，金额都是分。 */
data class CategoryBudget(
    val categoryId: Long,
    val title: String,
    val money: Long,
    val spent: Long,
)

/** 一个月的预算全貌。没设过预算时 budget 为空。 */
data class BudgetView(
    val month: Long,
    val budget: Budget?,
    val spent: Long,
    val categories: List<CategoryBudget>,
)

class BudgetRepository(private val db: MeowDatabase) {

    /** yearMonth 形如 202611。已用只算支出，并扣掉退款，跳过不计入预算的账单。 */
    fun view(yearMonth: Long, bookId: Long = DEFAULT_BOOK_ID): BudgetView {
        val budget = db.budgetQueries.selectOne(bookId, yearMonth).executeAsOneOrNull()
        val cateBudgets = db.budgetQueries.selectByMonth(bookId, yearMonth).executeAsList()
            .associateBy { it.member_cate_id }
        val spent = spentByCategory(yearMonth, bookId)
        val categories = db.categoryQueries.selectAllOfType(TYPE_EXPENSE).executeAsList()
            .map { category ->
                CategoryBudget(
                    categoryId = category.id,
                    title = category.title,
                    money = cateBudgets[category.id]?.money ?: 0,
                    spent = spent[category.id] ?: 0,
                )
            }
        return BudgetView(
            month = yearMonth,
            budget = budget,
            spent = spent.values.sum(),
            categories = categories,
        )
    }

    /** 设置月预算。money 为 0 时清掉这个月的预算和分类预算。 */
    fun save(yearMonth: Long, money: Long, remind: Boolean, remindRate: Long, bookId: Long = DEFAULT_BOOK_ID) {
        if (money <= 0) {
            clear(yearMonth, bookId)
            return
        }
        val current = db.budgetQueries.selectOne(bookId, yearMonth).executeAsOneOrNull()
        db.budgetQueries.upsert(
            Budget(
                book_id = bookId,
                day = yearMonth,
                money = money,
                acf_money = current?.acf_money ?: 0,
                is_remind = if (remind) 1 else 0,
                remind_rate = remindRate.coerceIn(1, 100),
                carry_down = current?.carry_down ?: 0,
                is_copy = current?.is_copy ?: 0,
            ),
        )
    }

    /** 设置一个分类的预算。money 为 0 时清掉这个分类。 */
    fun saveCategory(yearMonth: Long, categoryId: Long, money: Long, bookId: Long = DEFAULT_BOOK_ID) {
        db.budgetQueries.upsertCate(
            Cate_budget(
                book_id = bookId,
                day = yearMonth,
                member_cate_id = categoryId,
                money = money.coerceAtLeast(0),
                acf_money = 0,
            ),
        )
    }

    /** 删掉一个月的预算，含分类预算。 */
    fun clear(yearMonth: Long, bookId: Long = DEFAULT_BOOK_ID) {
        db.budgetQueries.deleteCatesOfMonth(bookId, yearMonth)
        db.budgetQueries.deleteOne(bookId, yearMonth)
    }

    /** 删掉一个月里某个分类的预算（Budget/DeleteBudgetCate）。 */
    fun removeCategory(yearMonth: Long, categoryId: Long, bookId: Long = DEFAULT_BOOK_ID) {
        db.budgetQueries.deleteCateOne(bookId, yearMonth, categoryId)
    }

    /** 复制上月预算到本月（Budget/CreateBudget 的 isCopy 语义）。上月没设预算返回 null。 */
    fun copyPrevious(yearMonth: Long, bookId: Long = DEFAULT_BOOK_ID): Budget? {
        val year = (yearMonth / 100).toInt()
        val month = (yearMonth % 100).toInt()
        val previous = if (month == 1) (year - 1) * 100L + 12 else yearMonth - 1
        val last = db.budgetQueries.selectOne(bookId, previous).executeAsOneOrNull() ?: return null
        db.budgetQueries.upsert(last.copy(day = yearMonth, is_copy = 1))
        db.budgetQueries.selectByMonth(bookId, previous).executeAsList().forEach { cate ->
            db.budgetQueries.upsertCate(cate.copy(day = yearMonth))
        }
        return db.budgetQueries.selectOne(bookId, yearMonth).executeAsOneOrNull()
    }

    private fun spentByCategory(yearMonth: Long, bookId: Long): Map<Long, Long> {
        val from = yearMonth * 100 + 1
        val to = yearMonth * 100 + 31
        val bills = db.billQueries.selectByRange(bookId, from, to).executeAsList()
            .filter { it.type == TYPE_EXPENSE && it.not_in_budget == 0L }
        return bills.groupBy { it.member_cate_id }.mapValues { (_, items) ->
            items.sumOf { bill ->
                val refunded = db.refundQueries.selectByBill(bill.client_id).executeAsList().sumOf { it.money }
                netPaid(bill.money, refunded)
            }
        }
    }
}
