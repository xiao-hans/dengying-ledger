package com.meow.ledger.shared.manage

import com.meow.ledger.shared.db.Bill_book
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Member_book
import kotlin.math.abs

private fun currentTimeMillis(): Long = System.currentTimeMillis()

/** 本地登录用户。样本没有独立成员表，成员名一律显示为「成员 #id」。 */
const val LOCAL_MEMBER_ID = 0L

const val DEFAULT_BOOK_ID = 1L

const val ROLE_MEMBER = 0L
const val ROLE_OWNER = 1L

/** 与 React 版 FALLBACK 图标一致：1 账簿 / 2 钱包 / 3 餐饮 / 4 旅行 / 5 家庭 / 6 心愿。 */
data class BookIcon(val id: Long, val name: String)

val BOOK_ICONS = listOf(
    BookIcon(1, "账簿"),
    BookIcon(2, "钱包"),
    BookIcon(3, "餐饮"),
    BookIcon(4, "旅行"),
    BookIcon(5, "家庭"),
    BookIcon(6, "心愿"),
)

/** 邀请码指向的账本。本地版不做真实远程协作，加入只在本机生成一本共享账本副本。 */
data class SharedBook(val id: Long, val name: String, val code: String)

class BookRepository(private val db: MeowDatabase) {

    fun books(): List<Bill_book> = db.bookQueries.selectAll().executeAsList()

    fun book(id: Long): Bill_book? = db.bookQueries.selectOne(id).executeAsOneOrNull()

    fun members(bookId: Long): List<Member_book> = db.memberQueries.selectMembers(bookId).executeAsList()

    /** 没有账本时补一本默认账本，保证示例账单的 book_id = 1 有归属。 */
    fun ensureDefault() {
        if (book(DEFAULT_BOOK_ID) != null) return
        create(id = DEFAULT_BOOK_ID, name = "日常账本", iconId = 1, monthStartDate = 1)
    }

    /** 指定 id 时用该 id（默认账本、按邀请码加入的账本），否则取当前最大 id + 1。 */
    fun create(name: String, iconId: Long, monthStartDate: Int, id: Long = 0): Bill_book {
        val bookId = if (id > 0) id else (books().maxOfOrNull { it.id } ?: 0L) + 1
        val role = if (bookId == DEFAULT_BOOK_ID) ROLE_OWNER else roleOf(bookId)
        val row = Bill_book(
            id = bookId,
            name = name.ifBlank { "新账本" },
            icon_id = iconId,
            icon = "book",
            tag = "",
            create_date = currentTimeMillis(),
            month_start_date = monthStartDate.coerceIn(1, 28).toLong(),
        )
        db.bookQueries.upsert(row)
        if (db.memberQueries.selectMember(LOCAL_MEMBER_ID, bookId).executeAsOneOrNull() == null) {
            db.memberQueries.upsertMember(Member_book(LOCAL_MEMBER_ID, bookId, role))
        }
        return row
    }

    fun update(id: Long, name: String, iconId: Long, monthStartDate: Int): Bill_book? {
        val old = book(id) ?: return null
        val row = old.copy(
            name = name.ifBlank { old.name },
            icon_id = iconId,
            month_start_date = monthStartDate.coerceIn(1, 28).toLong(),
        )
        db.bookQueries.upsert(row)
        return row
    }

    /** 默认账本不可删除，避免误删全部账单；删除账本不删除其账单。 */
    fun delete(id: Long): Boolean {
        if (id == DEFAULT_BOOK_ID || book(id) == null) return false
        db.bookQueries.deleteOne(id)
        return true
    }

    /** 邀请码稳定可复现：`DY` + 账本 id 的 36 进制。本地版不需要随机段也能互认。 */
    fun inviteCode(bookId: Long): String = "DY" + bookId.toString(36).uppercase()

    /** 解析邀请码。解不出 id 时按码的哈希生成一个新账本 id，行为对齐 React 的 hashCode 兜底。 */
    fun infoByCode(code: String): SharedBook {
        val body = code.trim().removePrefix("DY").removePrefix("dy")
        val parsed = body.takeWhile { it.isDigit() || it in 'a'..'z' || it in 'A'..'Z' }.toLongOrNull(36)
        val id = if (parsed != null && parsed > 0) parsed else (abs(code.hashCode().toLong()) % 100000).coerceAtLeast(2)
        return SharedBook(id, book(id)?.name ?: "共享账本", code.trim())
    }

    /** 加入共享账本：账本不存在就建一本副本，并把本地用户记为成员。 */
    fun join(code: String): Bill_book {
        val info = infoByCode(code)
        val existing = book(info.id)
        if (existing == null) {
            db.bookQueries.upsert(
                Bill_book(info.id, info.name, 1, "book", "", currentTimeMillis(), 1),
            )
        }
        db.memberQueries.upsertMember(Member_book(LOCAL_MEMBER_ID, info.id, ROLE_MEMBER))
        return book(info.id)!!
    }

    /** 默认账本不能退出。 */
    fun exit(bookId: Long): Boolean {
        if (bookId == DEFAULT_BOOK_ID) return false
        db.memberQueries.deleteMember(LOCAL_MEMBER_ID, bookId)
        return true
    }

    /** 创建者与本地用户本人不能被移除。 */
    fun removeMember(bookId: Long, memberId: Long): Boolean {
        val row = db.memberQueries.selectMember(memberId, bookId).executeAsOneOrNull() ?: return false
        if (row.role == ROLE_OWNER || memberId == LOCAL_MEMBER_ID) return false
        db.memberQueries.deleteMember(memberId, bookId)
        return true
    }

    fun roleOf(bookId: Long): Long =
        db.memberQueries.selectMember(LOCAL_MEMBER_ID, bookId).executeAsOneOrNull()?.role ?: ROLE_OWNER

}
