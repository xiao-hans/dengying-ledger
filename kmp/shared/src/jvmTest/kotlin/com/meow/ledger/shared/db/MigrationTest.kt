package com.meow.ledger.shared.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SQLDelight 迁移回归：所有历史安装（v1 / v2 / 由 v1 升上来但没跑过迁移的 v2）
 * 都必须能升到当前 v3，且升级后的表结构与全新安装一致、核心账单数据不丢。
 *
 * 版本口径（来自 git 历史，口径见各 fixture 头注释）：
 *  - v1 = 76f82b2^ 的 .sq（28 表，sync_queue 4 列，无 version 声明）
 *  - v2 = 76f82b2 的 .sq（DDL 与 v1 相同，version = 2）
 *  - v3 = 当前 .sq（36 表，sync_queue 11 列，version = 3）
 *
 * SQLDelight 的 N.sqm = 「从版本 N 升到 N+1」，因此 1.sqm 在 1→2 / 1→3 跑，
 * 2.sqm 在 2→3 / 1→3 跑。migrate(x, 3) 会把这一路上所有适用的脚本按序执行。
 */
class MigrationTest {

    // ---------------------------------------------------------------- helpers

    private fun resource(name: String): String {
        val stream = requireNotNull(
            javaClass.classLoader.getResourceAsStream("migrations/$name")
        ) { "找不到 fixture migrations/$name" }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** fixture 里每条语句都以分号结尾，且字符串默认值里不含分号，可直接切分。 */
    private fun executeScript(driver: SqlDriver, sql: String) {
        sql.split(';').forEach { chunk ->
            // 去掉整行注释（fixture 头部与段落说明都是 -- 开头）。
            val code = chunk.lines()
                .filterNot { it.trim().startsWith("--") }
                .joinToString("\n")
                .trim()
            if (code.isNotEmpty()) driver.execute(null, code, 0)
        }
    }

    /** 从 fixture 建一个老版本库，并写入 bill / bill_book / category 各一行样本。 */
    private fun oldDatabase(fixture: String): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        executeScript(driver, resource(fixture))
        insertSampleRows(driver)
        return driver
    }

    private fun insertSampleRows(driver: SqlDriver) {
        // 只填 NOT NULL 且无默认值的列，其余走 DEFAULT；三行在升级前后都应存活。
        driver.execute(
            null,
            "INSERT INTO bill(member_id, client_id, money, day, desc) VALUES (7, 'mig-bill', 1234, 20261110, '迁移前账单')",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO bill_book(id, name) VALUES (7, '迁移前账本')",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO category(id, member_id, title) VALUES (7, 7, '迁移前分类')",
            0,
        )
    }

    /** sqlite_master 里的用户表 / 索引名（排除 sqlite_ 内部对象）。 */
    private fun objectNames(driver: SqlDriver, type: String): Set<String> {
        val out = mutableSetOf<String>()
        driver.executeQuery(
            null,
            "SELECT name FROM sqlite_master WHERE type = '$type' AND name NOT LIKE 'sqlite_%'",
            mapper = { cursor: SqlCursor ->
                while (cursor.next().value) out += cursor.getString(0)!!
                QueryResult.Value(Unit)
            },
            0,
        )
        return out
    }

    /** PRAGMA table_info：列名 -> 列定义；按列名键比较，忽略列序（SQLite ALTER 追加列会改变列序）。 */
    private fun columns(driver: SqlDriver, table: String): Map<String, Column> {
        val out = linkedMapOf<String, Column>()
        driver.executeQuery(
            null,
            "PRAGMA table_info($table)",
            mapper = { cursor: SqlCursor ->
                while (cursor.next().value) {
                    val name = cursor.getString(1)!!
                    out[name] = Column(
                        type = cursor.getString(2) ?: "",
                        notNull = cursor.getLong(3) ?: 0L,
                        default = cursor.getString(4),
                        pk = cursor.getLong(5) ?: 0L,
                    )
                }
                QueryResult.Value(Unit)
            },
            0,
        )
        return out
    }

    private data class Column(
        val type: String,
        val notNull: Long,
        val default: String?,
        val pk: Long,
    )

    private fun schemaSnapshot(driver: SqlDriver): Map<String, Map<String, Column>> =
        objectNames(driver, "table").associateWith { columns(driver, it) }

    private fun scalarLong(driver: SqlDriver, sql: String): Long {
        var result = -1L
        driver.executeQuery(null, sql, mapper = { cursor: SqlCursor ->
            if (cursor.next().value) result = cursor.getLong(0) ?: -1L
            QueryResult.Value(Unit)
        }, 0)
        return result
    }

    private fun scalarString(driver: SqlDriver, sql: String): String? {
        var result: String? = null
        driver.executeQuery(null, sql, mapper = { cursor: SqlCursor ->
            if (cursor.next().value) result = cursor.getString(0)
            QueryResult.Value(Unit)
        }, 0)
        return result
    }

    // ---------------------------------------------------------------- tests

    /** 全新安装的 v3 schema，作为所有升级路径的对照基准。 */
    private fun freshV3(): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MeowDatabase.Schema.create(driver)
        return driver
    }

    private fun assertUpgradesToV3(fixture: String, oldVersion: Int, label: String) {
        val driver = oldDatabase(fixture)

        // (a) 跑迁移；SQLDelight 用显式 oldVersion 参数决定执行哪些 .sqm。
        MeowDatabase.Schema.migrate(driver, oldVersion.toLong(), 3)

        // (b) 升级后的表集合、每张表的列名/类型/默认值/主键，与全新安装一致。
        val expected = schemaSnapshot(freshV3())
        val actual = schemaSnapshot(driver)
        assertEquals(
            expected.keys.sorted(),
            actual.keys.sorted(),
            "$label：升级后的表集合应与全新安装一致",
        )
        expected.forEach { (table, expectedCols) ->
            val actualCols = actual[table] ?: error("$label：缺少表 $table")
            assertEquals(
                expectedCols,
                actualCols,
                "$label：表 $table 的列定义应与全新安装一致",
            )
        }
        // 索引集合也必须一致（sync_queue_status 是 v3 新增的）。
        assertEquals(
            objectNames(freshV3(), "index").sorted(),
            objectNames(driver, "index").sorted(),
            "$label：升级后的索引集合应与全新安装一致",
        )

        // (c) 核心三行样本必须存活，且内容未被改写。
        assertEquals(1, scalarLong(driver, "SELECT count(*) FROM bill WHERE client_id = 'mig-bill'"))
        assertEquals(1234L, scalarLong(driver, "SELECT money FROM bill WHERE client_id = 'mig-bill'"))
        assertEquals("迁移前账单", scalarString(driver, "SELECT desc FROM bill WHERE client_id = 'mig-bill'"))
        assertEquals("迁移前账本", scalarString(driver, "SELECT name FROM bill_book WHERE id = 7"))
        assertEquals("迁移前分类", scalarString(driver, "SELECT title FROM category WHERE id = 7"))

        // (d) v3 新表 / 老表对应的生成查询都能跑通（不抛异常 = 表与列齐全、列序无关）。
        val db = MeowDatabase(driver)
        db.s2Queries.selectBillTags().executeAsList()
        db.s2Queries.selectTagsOfBill("mig-bill", 7).executeAsList()
        db.s2Queries.selectRemark("mig-bill", 7).executeAsOneOrNull()
        db.s2Queries.selectCateExtend(7, 7).executeAsList()
        db.s2Queries.selectMemberAssets(7).executeAsList()
        db.s2Queries.selectImages("mig-bill").executeAsList()
        db.s2Queries.selectPendingSync(Long.MAX_VALUE, 100L).executeAsList()
        assertEquals(0L, db.s2Queries.countPending().executeAsOne())
        assertEquals("迁移前账单", db.billQueries.selectOne(7, "mig-bill").executeAsOneOrNull()?.desc)
    }

    @Test
    fun v1UpgradesToV2RunsOnly1SqmAndStopsBeforeV3Changes() {
        val driver = oldDatabase("v1.sql")
        MeowDatabase.Schema.migrate(driver, 1, 2)

        // 1.sqm 只补那 7 张账号/小组件表（v1 已建过，IF NOT EXISTS 为幂等空操作），
        // 不该提前引入 v3 的 8 张新表，也不该动 sync_queue 的列。
        val tables = objectNames(driver, "table")
        assertEquals(28, tables.size)
        assertTrue("bill_tag" !in tables)
        assertTrue("widget_base" !in tables)
        assertEquals(
            setOf("id", "path", "payload", "create_time"),
            columns(driver, "sync_queue").keys,
            "1→2 不应改动 sync_queue（2.sqm 的 ALTER 被版本门禁挡住）",
        )
        // 样本数据仍在。
        assertEquals("迁移前账单", scalarString(driver, "SELECT desc FROM bill WHERE client_id = 'mig-bill'"))
    }

    @Test
    fun v1UpgradesToV3WithoutCrashAndPreservesData() =
        assertUpgradesToV3("v1.sql", 1, "v1→v3")

    @Test
    fun v2UpgradesToV3WithoutCrashAndPreservesData() =
        assertUpgradesToV3("v2.sql", 2, "v2→v3")

    @Test
    fun v2UpgradedFromV1WithoutMigrationStillUpgradesToV3() =
        assertUpgradesToV3("v2_from_v1_no_migration.sql", 2, "v2(由 v1 升级且未跑 1→2)→v3")

    @Test
    fun migratedSyncQueueColumnsHaveExpectedTypesAndDefaults() {
        val driver = oldDatabase("v1.sql")
        MeowDatabase.Schema.migrate(driver, 1, 3)
        val cols = columns(driver, "sync_queue")

        // 7 个 ALTER 追加的列，类型/默认值逐字对齐 S2.sq。
        assertEquals(Column("TEXT", 1, "'POST'", 0), cols["method"])
        assertEquals(Column("TEXT", 1, "''", 0), cols["client_id"])
        assertEquals(Column("INTEGER", 1, "0", 0), cols["status"])
        assertEquals(Column("INTEGER", 1, "0", 0), cols["retry_count"])
        assertEquals(Column("INTEGER", 1, "0", 0), cols["next_attempt"])
        assertEquals(Column("INTEGER", 1, "0", 0), cols["update_time"])
        assertEquals(Column("TEXT", 1, "''", 0), cols["error"])
        // 原有 4 列仍在，共 11 列；队列里待重放的行没被 DROP 掉。
        assertEquals(11, cols.size)
        assertEquals(
            setOf("id", "path", "payload", "create_time"),
            cols.keys - setOf("method", "client_id", "status", "retry_count", "next_attempt", "update_time", "error"),
        )
    }

    @Test
    fun syncQueueRowsSurviveMigration() {
        val driver = oldDatabase("v1.sql")
        driver.execute(
            null,
            "INSERT INTO sync_queue(id, path, payload, create_time) VALUES ('q1', 'Bills/Create', '{}', 100)",
            0,
        )
        MeowDatabase.Schema.migrate(driver, 1, 3)

        // 用生成查询读回：能按列名取到，且新列走 DEFAULT，说明没有 DROP 重建。
        val row = MeowDatabase(driver).s2Queries
            .selectPendingSync(Long.MAX_VALUE, 100L).executeAsList().single()
        assertEquals("q1", row.id)
        assertEquals("Bills/Create", row.path)
        assertEquals("{}", row.payload)
        assertEquals(100L, row.create_time)
        assertEquals("POST", row.method)
        assertEquals(0L, row.status)
        assertTrue(row.error.isEmpty())
    }
}
