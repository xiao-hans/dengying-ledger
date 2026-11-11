package com.meow.ledger.shared.bills

import com.meow.ledger.shared.db.Category
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Cate_extend

/** 内置分类，对齐 src/db/client.ts 的 seedDefaults：支出 1-9，收入 101-104。 */
val DEFAULT_CATEGORIES: List<Triple<Long, Long, String>> = listOf(
    Triple(1, TYPE_EXPENSE, "餐饮"),
    Triple(2, TYPE_EXPENSE, "交通"),
    Triple(3, TYPE_EXPENSE, "购物"),
    Triple(4, TYPE_EXPENSE, "居住"),
    Triple(5, TYPE_EXPENSE, "娱乐"),
    Triple(6, TYPE_EXPENSE, "医疗"),
    Triple(7, TYPE_EXPENSE, "教育"),
    Triple(8, TYPE_EXPENSE, "人情"),
    Triple(9, TYPE_EXPENSE, "其他支出"),
    Triple(101, TYPE_INCOME, "工资"),
    Triple(102, TYPE_INCOME, "奖金"),
    Triple(103, TYPE_INCOME, "理财"),
    Triple(104, TYPE_INCOME, "其他收入"),
)

/** 分类表为空时写入内置分类。用户改过的分类不会被覆盖。 */
fun seedCategoriesIfEmpty(db: MeowDatabase) {
    if (db.categoryQueries.selectByType(TYPE_EXPENSE).executeAsList().isNotEmpty()) return
    DEFAULT_CATEGORIES.forEach { (id, type, title) ->
        db.categoryQueries.upsert(
            Category(
                id = id, member_id = 0, cate_id = id, type = type, title = title,
                icon_id = id, base_icon = "", icon = "", is_custom = 0, status = 1,
                sort = id, hidden = 0,
            )
        )
    }
}

class CategoryRepository(private val db: MeowDatabase) {
    fun ofType(type: Long): List<Category> = db.categoryQueries.selectByType(type).executeAsList()

    fun detail(id: Long, memberId: Long = 0): Category? =
        db.categoryQueries.selectOne(id, memberId).executeAsOneOrNull()

    /** 自定义分类。id 取同类型现有最大值 + 1。 */
    fun create(title: String, type: Long, iconId: Long, memberId: Long = 0): Category {
        val nextId = (db.categoryQueries.selectAllOfType(type).executeAsList().maxOfOrNull { it.id } ?: 0L) + 1
        val cate = Category(
            id = nextId, member_id = memberId, cate_id = nextId, type = type, title = title,
            icon_id = iconId, base_icon = "", icon = "", is_custom = 1, status = 1,
            sort = nextId, hidden = 0,
        )
        db.categoryQueries.upsert(cate)
        return cate
    }

    fun update(id: Long, memberId: Long, title: String, iconId: Long): Category? {
        val cate = detail(id, memberId) ?: return null
        val updated = cate.copy(title = title, icon_id = iconId)
        db.categoryQueries.upsert(updated)
        return updated
    }

    fun remove(id: Long, memberId: Long = 0): Boolean {
        if (detail(id, memberId) == null) return false
        db.categoryQueries.deleteOne(id, memberId)
        return true
    }

    /** 合并分类与扩展表的排序/隐藏（Cate/GetCateExtend 的本地口径）。 */
    fun listWithExtend(type: Long, bookId: Long = 0, memberId: Long = 0): List<Category> {
        val extends = db.s2Queries.selectCateExtend(memberId, bookId).executeAsList()
            .associateBy { it.member_cate_id }
        return db.categoryQueries.selectAllOfType(type).executeAsList().mapNotNull { cate ->
            val ext = extends[cate.id]
            if (ext != null && ext.hidden == 1L) null
            else cate.copy(
                sort = ext?.sort ?: cate.sort,
                hidden = ext?.hidden ?: cate.hidden,
            )
        }.sortedWith(compareBy({ it.sort }, { it.id }))
    }

    /** 写某分类在某账本下的排序与隐藏。 */
    fun setExtend(memberCateId: Long, bookId: Long, memberId: Long, sort: Int, hidden: Int) {
        val cate = detail(memberCateId, memberId)
        val nowMs = System.currentTimeMillis()
        db.s2Queries.upsertCateExtend(
            Cate_extend(
                member_cate_id = memberCateId, book_id = bookId, member_id = memberId,
                type = cate?.type ?: 1L, sort = sort.toLong(), hidden = hidden.toLong(),
                status = 0L, create_date = nowMs, update_date = nowMs,
            )
        )
    }
}
