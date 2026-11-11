package com.meow.ledger.shared.manage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S2 业务层：标签管理与账单打标。 */
class TagsTest {

    private fun db(): MeowDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return MeowDatabase(driver)
    }

    @Test
    fun createAssignsAscendingIdsPerBook() {
        val tags = TagRepository(db())
        assertEquals(1L, tags.create("聚餐", bookId = 1).tag_id)
        assertEquals(2L, tags.create("旅行", bookId = 1).tag_id)
        assertEquals(3L, tags.create("家用", bookId = 2).tag_id)
        assertEquals(2, tags.list(1).size)
        assertEquals(1, tags.list(2).size)
    }

    @Test
    fun updateAndRemoveRoundTrip() {
        val tags = TagRepository(db())
        val tag = tags.create("聚餐", bookId = 1)
        assertEquals("家庭聚餐", tags.update(tag.tag_id, "家庭聚餐")!!.name)
        assertTrue(tags.remove(tag.tag_id))
        assertTrue(tags.list(1).isEmpty())
        assertFalse(tags.remove(tag.tag_id))
        assertNull(tags.update(tag.tag_id, "x"))
    }

    @Test
    fun billTagsReplaceOnEachWrite() {
        val db = db()
        val tags = TagRepository(db)
        val one = tags.create("聚餐", bookId = 1)
        val two = tags.create("旅行", bookId = 1)

        tags.setBillTags("bill-1", LOCAL_MEMBER_ID, listOf(one.tag_id, two.tag_id))
        assertEquals(2, tags.tagsOfBill("bill-1").size)

        tags.setBillTags("bill-1", LOCAL_MEMBER_ID, listOf(two.tag_id))
        assertEquals(listOf(two.tag_id), tags.tagsOfBill("bill-1").map { it.tag_id })

        assertEquals(0, tags.tagsOfBill("bill-2").size)
    }
}

/** S2 业务层：默认账本与账本详情。 */
class BooksDefaultTest {

    @Test
    fun setDefaultPersistsAndFallsBack() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        val books = BookRepository(MeowDatabase(driver))
        assertEquals(DEFAULT_BOOK_ID, books.defaultBookId())
        books.ensureDefault()
        books.setDefault(9L)
        assertEquals(9L, books.defaultBookId())
    }
}
