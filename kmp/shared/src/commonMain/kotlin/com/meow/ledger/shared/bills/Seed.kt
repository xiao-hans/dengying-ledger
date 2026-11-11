package com.meow.ledger.shared.bills

import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.MeowDatabase

/** 库为空时写入几笔示例账单，方便第一次打开就能看到首页的样子。 */
fun seedIfEmpty(db: MeowDatabase, today: Long) {
    if (db.billQueries.selectByRange(0, 0, 99999999).executeAsList().isNotEmpty()) return
    val month = today / 100 * 100
    listOf(
        sample(month + 2, TYPE_EXPENSE, 3200, "午餐"),
        sample(month + 2, TYPE_EXPENSE, 1580, "地铁"),
        sample(month + 1, TYPE_INCOME, 2000000, "工资"),
        sample(today, TYPE_EXPENSE, 4590, "超市"),
    ).forEach { db.billQueries.upsert(it) }
}

private fun sample(day: Long, type: Long, money: Long, desc: String) = Bill(
    member_id = 0, client_id = "seed-$day-$desc", money = money, refund_money = 0,
    type = type, exchange = 0, book_id = 1, member_cate_id = 1, currency = "CNY",
    day = day, desc = desc, not_in_budget = 0, images = "", update_date = 0, create_date = 0,
    status = 0, to_assets_id = 0, from_assets_id = 0, interest = 0, interest_reckon_type = 0,
    tag_ids = "", source = 0, import_id = 0, repayment_day = 0, local_create_time = 0,
    local_update_time = 0, installment_plan_id = "", installment_num = 0,
    installment_money = 0, installment_interest = 0,
)
