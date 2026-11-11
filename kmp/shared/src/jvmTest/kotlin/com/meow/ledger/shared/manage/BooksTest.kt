package com.meow.ledger.shared.manage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BooksTest {
    private fun repo(): BookRepository {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return BookRepository(MeowDatabase(driver))
    }

    @Test
    fun ensureDefaultCreatesTheDailyBookOnce() {
        val repo = repo()
        repo.ensureDefault()
        repo.ensureDefault()
        val books = repo.books()
        assertEquals(1, books.size)
        assertEquals("日常账本", books.single().name)
        assertEquals(DEFAULT_BOOK_ID, books.single().id)
    }

    @Test
    fun createUpdateAndDeleteKeepTheDefaultBook() {
        val repo = repo()
        repo.ensureDefault()
        val created = repo.create("旅行", 4, 5)
        assertEquals(2L, created.id)
        assertEquals(5L, created.month_start_date)

        val updated = repo.update(created.id, "日本行", 4, 99)
        assertNotNull(updated)
        assertEquals("日本行", updated.name)
        assertEquals(28L, updated.month_start_date)

        assertFalse(repo.delete(DEFAULT_BOOK_ID))
        assertTrue(repo.delete(created.id))
        assertNull(repo.book(created.id))
        assertEquals(1, repo.books().size)
    }

    @Test
    fun inviteCodeRoundTripsAndJoinCopiesTheBook() {
        val repo = repo()
        repo.ensureDefault()
        val code = repo.inviteCode(DEFAULT_BOOK_ID)
        assertEquals(DEFAULT_BOOK_ID, repo.infoByCode(code).id)

        val joined = repo.join("DYtravel")
        assertEquals("共享账本", joined.name)
        assertEquals(ROLE_MEMBER, repo.members(joined.id).single { it.member_id == LOCAL_MEMBER_ID }.role)
        assertEquals(joined.id, repo.join("DYtravel").id)
        assertEquals(2, repo.books().size)
    }

    @Test
    fun ownerAndSelfCannotBeRemovedAndDefaultCannotExit() {
        val repo = repo()
        repo.ensureDefault()
        assertFalse(repo.exit(DEFAULT_BOOK_ID))
        assertFalse(repo.removeMember(DEFAULT_BOOK_ID, LOCAL_MEMBER_ID))

        val book = repo.create("家庭", 5, 1)
        repo.members(book.id)
        val db = repo
        assertFalse(db.removeMember(book.id, LOCAL_MEMBER_ID))
        assertTrue(repo.exit(book.id))
        assertTrue(repo.members(book.id).none { it.member_id == LOCAL_MEMBER_ID })
    }
}
