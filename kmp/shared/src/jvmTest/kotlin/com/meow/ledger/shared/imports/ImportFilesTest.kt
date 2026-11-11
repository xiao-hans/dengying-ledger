package com.meow.ledger.shared.imports

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 导入文件登记：list/open/remove/record，大内容截断。 */
class ImportFilesTest {
    private val db = MeowDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply { MeowDatabase.Schema.create(this) })
    private val repo = ImportFileRepository(db)

    @Test
    fun recordListOpenRemove() {
        repo.record("1", "招商银行账单.csv", "csv", 12, "a,b,c")
        repo.record("2", "微信账单.csv", "csv", 8, "x,y")
        assertEquals(2, repo.list().size)
        val one = repo.open("1")!!
        assertEquals("招商银行账单.csv", one.name)
        assertEquals(12L, one.count)
        repo.remove("1")
        assertNull(repo.open("1"))
        assertEquals(1, repo.list().size)
    }

    @Test
    fun sameIdOverwrites() {
        repo.record("1", "旧.csv", "csv", 3, "old")
        repo.record("1", "新.csv", "csv", 9, "new")
        assertEquals(1, repo.list().size)
        assertEquals("新.csv", repo.open("1")!!.name)
        assertEquals(9L, repo.open("1")!!.count)
    }

    @Test
    fun bigContentIsTruncated() {
        val big = "x".repeat(80 * 1024)
        val row = repo.record("big", "大文件.csv", "csv", 1, big)
        assertTrue(row.content.length <= 64 * 1024)
    }
}
