package com.meow.ledger.shared.manage

import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Tag
import com.meow.ledger.shared.db.Bill_tag

/** 账单标签（MemberTag 4 端点的本地口径）。标签挂在账本下，bill_tag 关联账单。 */
class TagRepository(private val db: MeowDatabase) {

    fun list(bookId: Long): List<Tag> = db.tagQueries.selectByBook(bookId).executeAsList()

    fun create(name: String, bookId: Long, memberId: Long = 0): Tag {
        // tag_id 是全表主键，用全表 max+1；按账本局部 max 会撞已有 id 被 REPLACE 吞行。
        val nextId = (db.tagQueries.selectAllTags().executeAsList().maxOfOrNull { it.tag_id } ?: 0L) + 1
        val tag = Tag(
            tag_id = nextId, book_id = bookId, member_id = memberId, name = name,
            sort = nextId, create_date = System.currentTimeMillis(), update_date = System.currentTimeMillis(),
        )
        db.tagQueries.upsert(tag)
        return tag
    }

    fun update(tagId: Long, name: String): Tag? {
        val tag = db.tagQueries.selectAllTags().executeAsList().firstOrNull { it.tag_id == tagId }
            ?: return null
        val updated = tag.copy(name = name, update_date = System.currentTimeMillis())
        db.tagQueries.upsert(updated)
        return updated
    }

    fun remove(tagId: Long): Boolean {
        val all = db.tagQueries.selectAllTags().executeAsList()
        if (all.none { it.tag_id == tagId }) return false
        db.tagQueries.deleteOne(tagId)
        return true
    }

    /** 一笔账单挂的标签。 */
    fun tagsOfBill(clientId: String, memberId: Long = 0): List<Tag> {
        val all = db.tagQueries.selectAllTags().executeAsList().associateBy { it.tag_id }
        return db.s2Queries.selectTagsOfBill(clientId, memberId).executeAsList()
            .mapNotNull { all[it.tag_id] }
    }

    /** 重设一笔账单的标签集。 */
    fun setBillTags(clientId: String, memberId: Long, tagIds: List<Long>) {
        db.s2Queries.deleteBillTags(clientId, memberId)
        tagIds.forEach { db.s2Queries.upsertBillTag(Bill_tag(client_id = clientId, member_id = memberId, tag_id = it)) }
    }
}
