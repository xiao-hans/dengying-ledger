package com.meow.ledger.shared.manage

import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.bills.TYPE_TRANSFER
import com.meow.ledger.shared.bills.centsToYuan
import com.meow.ledger.shared.bills.toCents
import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.random.Random

const val SOURCE_IMPORT = 4L
const val SOURCE_OCR = 3L

/** 一次导入的结果。 */
data class ImportOutcome(
    val created: Int,
    val duplicate: Int,
    val skipped: Int,
)

/** 一条可导入的账单，金额是元的文本。 */
data class ImportRow(
    val day: Long,
    val type: Long,
    val category: String,
    val money: String,
    val desc: String,
)

class ExchangeRepository(private val db: MeowDatabase) {

    /** 全部账单，导出和搜索都从这里取。 */
    fun all(): List<Bill> = db.billQueries.selectAll().executeAsList()

    /** 按备注、分类名、账单号、金额、日期筛选。query 为空时只按类型筛。 */
    fun search(query: String, type: Long = 0): List<Bill> {
        val needle = query.trim()
        val names = categoryNames()
        return all().filter { bill ->
            (type == 0L || bill.type == type) &&
                (needle.isEmpty() || hit(bill, needle, names[bill.member_cate_id] ?: ""))
        }
    }

    /** 备注、分类名、账单号、金额（元）、日期里任意一个包含关键字就算命中。 */
    private fun hit(bill: Bill, needle: String, category: String): Boolean {
        val fields = listOf(bill.desc, category, bill.client_id, centsToYuan(bill.money), bill.day.toString())
        return fields.any { it.contains(needle, ignoreCase = true) }
    }

    /** 账单导出成 CSV 文本，带 UTF-8 BOM，Excel 直接打开不乱码。 */
    fun csv(): String {
        val names = categoryNames()
        val books = db.bookQueries.selectAll().executeAsList().associate { it.id to it.name }
        val rows = all().joinToString("\n") { bill ->
            listOf(
                dayText(bill.day),
                typeText(bill.type),
                names[bill.member_cate_id] ?: "未分类",
                centsToYuan(bill.money),
                bill.desc,
                books[bill.book_id] ?: "",
                "",
                bill.tag_ids,
                sourceText(bill.source),
                bill.client_id,
            ).joinToString(",") { cell(it) }
        }
        return "﻿日期,类型,分类,金额(元),备注,账本,账户,标签,来源,账单编号\n$rows"
    }

    /**
     * 解析 CSV 并写入账单。表头行自动跳过；重复按「备注 + 金额 + 日期」判断；
     * 金额、日期解析不了的行计入 skipped。
     */
    fun importCsv(text: String, source: Long = SOURCE_IMPORT, today: Long = 0): ImportOutcome {
        val rows = parseCsv(text)
        return importRows(rows, source, today)
    }

    fun importRows(rows: List<ImportRow>, source: Long, today: Long): ImportOutcome {
        var created = 0
        var duplicate = 0
        var skipped = 0
        val seen = existingKeys()
        db.transaction {
            rows.forEach { row ->
                val cents = toCents(row.money)
                if (cents == null || cents == 0L || row.day <= 0) {
                    skipped += 1
                    return@forEach
                }
                val key = dedupKey(row.desc, cents, row.day)
                if (key in seen) {
                    duplicate += 1
                    return@forEach
                }
                seen += key
                db.billQueries.upsert(billOf(row, cents, source, today))
                created += 1
            }
        }
        return ImportOutcome(created, duplicate, skipped)
    }

    private fun existingKeys(): MutableSet<String> =
        all().map { dedupKey(it.desc, it.money, it.day) }.toMutableSet()

    private fun billOf(row: ImportRow, cents: Long, source: Long, today: Long): Bill {
        val type = if (row.type == TYPE_INCOME || row.type == TYPE_TRANSFER) row.type else TYPE_EXPENSE
        val categoryId = categoryIdOf(row.category, type)
        return Bill(
            member_id = 0,
            client_id = "import-${row.day}-${Random.nextLong()}",
            money = cents, refund_money = 0, type = type, exchange = 0,
            book_id = DEFAULT_BOOK_ID, member_cate_id = categoryId, currency = "CNY",
            day = row.day, desc = row.desc.trim(), not_in_budget = 0, images = "",
            update_date = today, create_date = today, status = 0,
            to_assets_id = 0, from_assets_id = 0, interest = 0, interest_reckon_type = 0,
            tag_ids = "", source = source, import_id = 0, repayment_day = 0,
            local_create_time = today, local_update_time = today,
            installment_plan_id = "", installment_num = 0, installment_money = 0, installment_interest = 0,
        )
    }

    /** 按名称找已有分类，找不到就用该类型的第一个。 */
    private fun categoryIdOf(name: String, type: Long): Long {
        val categories = db.categoryQueries.selectAllOfType(type).executeAsList()
        return categories.firstOrNull { it.title == name.trim() }?.id ?: categories.firstOrNull()?.id ?: 1
    }

    private fun categoryNames(): Map<Long, String> =
        (db.categoryQueries.selectAllOfType(TYPE_EXPENSE).executeAsList() +
            db.categoryQueries.selectAllOfType(TYPE_INCOME).executeAsList())
            .associate { it.id to it.title }
}

/** 去重键：备注 + 金额分 + 日期。 */
internal fun dedupKey(desc: String, cents: Long, day: Long): String = "${desc.trim()}|$cents|$day"

/** 解析 CSV。跳过空行和表头，列顺序与导出一致。 */
internal fun parseCsv(text: String): List<ImportRow> {
    val lines = text.removePrefix("﻿").split('\n').map { it.trimEnd('\r') }.filter { it.isNotBlank() }
    return lines.mapNotNull { line ->
        val cells = splitCsv(line)
        if (cells.isEmpty() || cells[0] == "日期") return@mapNotNull null
        val day = cells.getOrNull(0)?.filter { it.isDigit() }?.toLongOrNull() ?: return@mapNotNull null
        ImportRow(
            day = day,
            type = typeOf(cells.getOrElse(1) { "" }),
            category = cells.getOrElse(2) { "" },
            money = cells.getOrElse(3) { "" },
            desc = cells.getOrElse(4) { "" },
        )
    }
}

private fun splitCsv(line: String): List<String> {
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    line.forEach { char ->
        when {
            char == '"' -> quoted = !quoted
            char == ',' && !quoted -> {
                cells += current.toString()
                current.clear()
            }
            else -> current.append(char)
        }
    }
    cells += current.toString()
    return cells
}

private fun cell(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value

private fun typeOf(text: String): Long = when (text.trim()) {
    "收入", "2" -> TYPE_INCOME
    "转账", "3" -> TYPE_TRANSFER
    else -> TYPE_EXPENSE
}

private fun typeText(type: Long): String = when (type) {
    TYPE_INCOME -> "收入"
    TYPE_TRANSFER -> "转账"
    else -> "支出"
}

private fun sourceText(source: Long): String = when (source) {
    1L -> "通知"
    2L -> "截屏"
    SOURCE_OCR -> "识别"
    SOURCE_IMPORT -> "导入"
    5L -> "周期"
    else -> "手工"
}

private fun dayText(day: Long): String {
    val text = day.toString()
    return if (text.length == 8) "${text.substring(0, 4)}-${text.substring(4, 6)}-${text.substring(6, 8)}" else text
}
