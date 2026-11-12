package com.meow.ledger.shared.bills

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.auto.AutoRepository
import com.meow.ledger.shared.auto.DEFAULT_WATCH_APPS
import com.meow.ledger.shared.auto.KEY_APP_LOCK
import com.meow.ledger.shared.auto.KEY_AUTO_BILL
import com.meow.ledger.shared.auto.KEY_KEEP_ALIVE
import com.meow.ledger.shared.auto.KEY_THEME
import com.meow.ledger.shared.auto.SettingsStore
import com.meow.ledger.shared.auto.THEME_DARK
import com.meow.ledger.shared.auto.THEME_SYSTEM
import com.meow.ledger.shared.auto.snapshot
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Tag
import com.meow.ledger.shared.manage.AssetRepository
import com.meow.ledger.shared.manage.BookRepository
import com.meow.ledger.shared.manage.BudgetRepository
import com.meow.ledger.shared.manage.FixedBillRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 「清空本机数据」必须名副其实：删掉账单、账本、分类自定义、资产、预算、标签、规则、
 * 待确认队列、周期账、设置等，再重建内置默认值让 App 仍可记账（AGENTS.md §6.1）。
 */
class ClearLocalDataTest {
    private val today = 20261112L

    private fun setup(): MeowDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return MeowDatabase(driver)
    }

    private fun populate(db: MeowDatabase) {
        seedIfEmpty(db, today)
        seedCategoriesIfEmpty(db)
        BookRepository(db).ensureDefault()
        BookRepository(db).create("旅行账本", 4, 1)
        AssetRepository(db).ensureDefault()
        BudgetRepository(db).save(202611, 100000, remind = false, remindRate = 0)
        BudgetRepository(db).saveCategory(202611, 9, 30000)
        CatalogSeed(db).addCustomCategoryAndTag()
        FixedBillRepository(db).save("房租", 250000, 1, 1, 5)
        AutoRepository(db).capture("在 瑞幸咖啡 消费 ¥23.50", "notification", occurredAt = 1_700_000_000_000L)
        val settings = SettingsStore(db)
        settings.setText(KEY_THEME, THEME_DARK)
        settings.setFlag(KEY_AUTO_BILL, true)
        settings.setFlag(KEY_KEEP_ALIVE, true)
        settings.setWatchApps(setOf("com.tencent.mm"))
        settings.setText(KEY_APP_LOCK, "2580")
    }

    @Test
    fun clearsAllUserTablesButKeepsDefaultsUsable() {
        val db = setup()
        populate(db)
        // 清空前确实有用户数据。
        assertTrue(db.billQueries.selectAll().executeAsList().size >= 4)
        assertEquals(2, BookRepository(db).books().size)
        assertFalse(SettingsStore(db).text(KEY_THEME).isEmpty())
        assertEquals(1, AutoRepository(db).pending().size)

        clearLocalData(db, today)

        // 账单只剩重建的示例账单，账本/资产/分类恢复为内置默认值。
        assertEquals(4, db.billQueries.selectAll().executeAsList().size)
        assertEquals(1, BookRepository(db).books().size)
        assertEquals(1, db.memberQueries.selectMembers(1).executeAsList().size)
        assertEquals(1, db.assetQueries.selectAll().executeAsList().size)
        assertEquals(9, db.categoryQueries.selectAllOfType(TYPE_EXPENSE).executeAsList().size)
        assertEquals(4, db.categoryQueries.selectAllOfType(TYPE_INCOME).executeAsList().size)
        assertEquals(0, db.categoryQueries.selectAllOfType(TYPE_EXPENSE).executeAsList().count { it.is_custom == 1L })
        // 各业务表已经清空。
        assertEquals(0, db.tagQueries.selectAllTags().executeAsList().size)
        assertEquals(0, db.budgetQueries.selectByMonth(1, 202611).executeAsList().size)
        assertEquals(0, db.ruleQueries.selectAll().executeAsList().size)
        assertEquals(0, AutoRepository(db).pending().size)
        assertEquals(0, db.fixedQueries.selectAll().executeAsList().size)
        assertEquals(0, db.s2Queries.selectBillTags().executeAsList().size)
        assertEquals(0, db.s2Queries.countPending().executeAsOne().toInt())
        // 设置与账号一并清空，回到未设置状态。
        assertEquals("", SettingsStore(db).text(KEY_THEME))
        assertEquals(0, db.accountQueries.selectEvents("ac_auto_capture").executeAsList().size)
    }

    /** 清库后 UI 用来重置内存状态的那份快照，必须整体回到缺省值（§6.8）。 */
    @Test
    fun settingsSnapshotReturnsDefaultsAfterClear() {
        val db = setup()
        populate(db)
        val before = SettingsStore(db).snapshot()
        // 清空前确实偏离了缺省值。
        assertEquals(THEME_DARK, before.theme)
        assertTrue(before.autoBill)
        assertTrue(before.keepAlive)
        assertEquals(setOf("com.tencent.mm"), before.watchApps)
        assertEquals("2580", before.lockPin)

        clearLocalData(db, today)

        val after = SettingsStore(db).snapshot()
        assertEquals(THEME_SYSTEM, after.theme)
        assertFalse(after.autoBill)
        assertFalse(after.keepAlive)
        assertEquals(DEFAULT_WATCH_APPS.toSet(), after.watchApps)
        assertEquals("", after.lockPin)
    }

    @Test
    fun canBeCalledRepeatedlyAndRepopulateAfterClear() {
        val db = setup()
        populate(db)
        clearLocalData(db, today)
        clearLocalData(db, today)
        assertEquals(4, db.billQueries.selectAll().executeAsList().size)
        assertEquals(1, BookRepository(db).books().size)

        // 清空后仍可正常记账，不会因缺默认分类/账本而卡住。
        val repo = BillsRepository(db)
        val bill = sampleBill(today)
        repo.saveWithBalance(bill)
        assertEquals(5, db.billQueries.selectAll().executeAsList().size)
    }

    private fun sampleBill(day: Long) = com.meow.ledger.shared.db.Bill(
        member_id = 0, client_id = "after-clear", money = 1234, refund_money = 0,
        type = TYPE_EXPENSE, exchange = 0, book_id = 1, member_cate_id = 1, currency = "CNY",
        day = day, desc = "清空后记一笔", not_in_budget = 0, images = "", update_date = 0, create_date = 0,
        status = 0, to_assets_id = 0, from_assets_id = 0, interest = 0, interest_reckon_type = 0,
        tag_ids = "", source = 0, import_id = 0, repayment_day = 0, local_create_time = 0,
        local_update_time = 0, installment_plan_id = "", installment_num = 0,
        installment_money = 0, installment_interest = 0,
    )
}

/** 直接写表造「用户自定义」数据，避免依赖具体仓储 API 的签名。 */
private class CatalogSeed(private val db: MeowDatabase) {
    fun addCustomCategoryAndTag() {
        db.categoryQueries.upsert(
            com.meow.ledger.shared.db.Category(
                id = 200, member_id = 0, cate_id = 200, type = TYPE_EXPENSE, title = "自定义分类",
                icon_id = 200, base_icon = "", icon = "", is_custom = 1, status = 1, sort = 200, hidden = 0,
            ),
        )
        db.tagQueries.upsert(Tag(1, 1, 0, "自定义标签", 1, 0, 0))
    }
}
