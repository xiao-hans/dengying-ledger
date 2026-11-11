package com.meow.ledger.shared.imports

import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Member_import_bill_file

/**
 * 导入文件记录：每次导入/粘贴识别都登记来源、条数和状态，
 * 对齐 MemberImportBillFile 的 List/Delete 契约与「导入页能看到历史」的交互。
 */
class ImportFileRepository(private val db: MeowDatabase) {
    fun list(): List<Member_import_bill_file> =
        db.accountQueries.selectImports().executeAsList()

    fun open(id: String): Member_import_bill_file? =
        db.accountQueries.selectImport(id).executeAsOneOrNull()

    fun remove(id: String) {
        db.accountQueries.deleteImport(id)
    }

    /** 记录一次导入。id 由调用方给（时间戳即可），同 id 覆盖。 */
    fun record(id: String, name: String, type: String, count: Int, content: String, status: Long = 1): Member_import_bill_file {
        val row = Member_import_bill_file(
            id = id,
            name = name,
            type = type,
            count = count.toLong(),
            status = status,
            create_time = currentTime(),
            content = content.take(64 * 1024),
        )
        db.accountQueries.upsertImport(row)
        return row
    }

    private fun currentTime(): Long = System.currentTimeMillis()
}
