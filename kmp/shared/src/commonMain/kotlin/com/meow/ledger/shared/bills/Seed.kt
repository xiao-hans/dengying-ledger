package com.meow.ledger.shared.bills

import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.manage.AssetRepository
import com.meow.ledger.shared.manage.BookRepository

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

/**
 * 清空本机全部用户数据：账单、账本、分类、资产、预算、标签、规则、周期账、计划、
 * 小组件、账号、待确认队列、设置与同步队列等，逐表删除且不可恢复。
 * 只保留表结构；删完重建内置默认值（默认账本、现金账户、内置分类、示例账单），
 * 让 App 仍处于可记账状态。整批在一个事务里完成。
 * 仅由设置页「清空本机数据」的二次确认回调调用，绝不自动执行。
 */
fun clearLocalData(db: MeowDatabase, today: Long) {
    db.transaction {
        val q = db.clearQueries
        q.deleteAllBills()
        q.deleteAllRefunds()
        q.deleteAllBooks()
        q.deleteAllMembers()
        q.deleteAllCategories()
        q.deleteAllCateExtend()
        q.deleteAllBudgets()
        q.deleteAllCateBudgets()
        q.deleteAllAssets()
        q.deleteAllMemberAssets()
        q.deleteAllTags()
        q.deleteAllBillTags()
        q.deleteAllBillRemarks()
        q.deleteAllRules()
        q.deleteAllAutoEvents()
        q.deleteAllSettings()
        q.deleteAllRemoteConfig()
        q.deleteAllFixedBills()
        q.deleteAllReminds()
        q.deleteAllImages()
        q.deleteAllWidgetBase()
        q.deleteAllWidgetDesktop()
        q.deleteAllWidgetHot()
        q.deleteAllWidgetUser()
        q.deleteAllSyncQueue()
        q.deleteAllInstallmentPlans()
        q.deleteAllInstallmentItems()
        q.deleteAllSavePlans()
        q.deleteAllSavePlanItems()
        q.deleteAllProfiles()
        q.deleteAllSmsCodes()
        q.deleteAllImports()
        q.deleteAllOrders()
        q.deleteAllContracts()
        q.deleteAllBehaviorEvents()
        q.deleteAllGroups()
    }
    // 重建默认值：MainApp 每次组合也会调 seedIfEmpty，但清空后账单表为空，
    // 这里直接补齐，保证危险操作后 App 立刻可用。
    db.transaction {
        BookRepository(db).ensureDefault()
        AssetRepository(db).ensureDefault()
        seedCategoriesIfEmpty(db)
        seedIfEmpty(db, today)
    }
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
