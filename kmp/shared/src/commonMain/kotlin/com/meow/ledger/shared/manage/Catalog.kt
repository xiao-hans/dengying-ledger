package com.meow.ledger.shared.manage

import com.meow.ledger.shared.bills.DEFAULT_CATEGORIES
import com.meow.ledger.shared.bills.seedCategoriesIfEmpty
import com.meow.ledger.shared.db.Category
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Tag

/** 内置分类编号：支出 1-9、收入 101-104，与 DEFAULT_CATEGORIES 一致。 */
private val BUILTIN_CATEGORY_IDS: Set<Long> = DEFAULT_CATEGORIES.map { it.first }.toSet()

/** 分类图标选择器的兜底图标，对齐 CateEdit 的 FALLBACK_ICONS。 */
data class CategoryIcon(val id: String, val name: String)

val CATEGORY_ICONS: List<CategoryIcon> = listOf(
    CategoryIcon("grid", "通用"),
    CategoryIcon("wallet", "钱包"),
    CategoryIcon("book", "账簿"),
    CategoryIcon("star", "心愿"),
    CategoryIcon("heart", "健康"),
    CategoryIcon("coin", "收入"),
    CategoryIcon("pet", "宠物"),
    CategoryIcon("note", "记录"),
    CategoryIcon("image", "票据"),
)

/**
 * 分类与标签。内置分类只能隐藏不能删除；自定义分类可编辑可删除。
 * 删除分类或标签都不改动已有账单。
 */
class CatalogRepository(private val db: MeowDatabase) {
    fun ensureSeed() = seedCategoriesIfEmpty(db)

    /** 某类型的全部分类，含已隐藏。 */
    fun categories(type: Long): List<Category> =
        db.categoryQueries.selectAllOfType(type).executeAsList()

    /** 某类型的可见分类，供记一笔的分类选择器使用。 */
    fun visibleCategories(type: Long): List<Category> =
        db.categoryQueries.selectByType(type).executeAsList()

    fun category(id: Long): Category? =
        db.categoryQueries.selectOne(id, LOCAL_MEMBER_ID).executeAsOneOrNull()

    fun isBuiltin(id: Long): Boolean = id in BUILTIN_CATEGORY_IDS

    /** 新建或更新分类。id 为 0 时新建。更新不存在的分类返回 null。 */
    fun saveCategory(title: String, type: Long, icon: String, id: Long = 0): Category? {
        val name = title.trim().ifBlank { "新分类" }
        val now = if (id == 0L) {
            (db.categoryQueries.selectAllOfType(type).executeAsList().maxOfOrNull { it.id } ?: 0L) + 1
        } else id
        val old = if (id == 0L) null else (category(id) ?: return null)
        val row = Category(
            id = now,
            member_id = LOCAL_MEMBER_ID,
            cate_id = now,
            type = type,
            title = name,
            icon_id = old?.icon_id ?: now,
            base_icon = icon,
            icon = icon,
            is_custom = if (id == 0L) 1 else (old?.is_custom ?: 1),
            status = old?.status ?: 1,
            sort = old?.sort ?: now,
            hidden = old?.hidden ?: 0,
        )
        db.categoryQueries.upsert(row)
        return row
    }

    /** 隐藏或恢复一个分类。已记账单不受影响。 */
    fun setHidden(id: Long, hidden: Boolean) {
        val old = category(id) ?: return
        db.categoryQueries.upsert(old.copy(hidden = if (hidden) 1 else 0))
    }

    /** 删除自定义分类。内置分类拒绝删除并返回 false。 */
    fun deleteCategory(id: Long): Boolean {
        if (isBuiltin(id)) return false
        if (category(id) == null) return false
        db.categoryQueries.deleteOne(id, LOCAL_MEMBER_ID)
        return true
    }

    fun tags(bookId: Long = DEFAULT_BOOK_ID): List<Tag> =
        db.tagQueries.selectByBook(bookId).executeAsList()

    /** 新建或重命名标签。tagId 为 0 时新建。同名标签拒绝并返回 null。 */
    fun saveTag(name: String, bookId: Long = DEFAULT_BOOK_ID, tagId: Long = 0): Tag? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val existing = tags(bookId)
        if (existing.any { it.name == trimmed && it.tag_id != tagId }) return null
        val now = currentTimeMillis()
        val row = if (tagId == 0L) {
            val next = (existing.maxOfOrNull { it.tag_id } ?: 0L) + 1
            Tag(next, bookId, LOCAL_MEMBER_ID, trimmed, next, now, now)
        } else {
            val old = existing.firstOrNull { it.tag_id == tagId } ?: return null
            old.copy(name = trimmed, update_date = now)
        }
        db.tagQueries.upsert(row)
        return row
    }

    /** 按给定顺序重排标签，sort 从 1 开始。 */
    fun sortTags(tagIds: List<Long>, bookId: Long = DEFAULT_BOOK_ID) {
        val now = currentTimeMillis()
        val byId = tags(bookId).associateBy { it.tag_id }
        tagIds.forEachIndexed { index, id ->
            val old = byId[id] ?: return@forEachIndexed
            db.tagQueries.upsert(old.copy(sort = index.toLong() + 1, update_date = now))
        }
    }

    /** 删除标签。已有账单保留，只是不再带此标签。 */
    fun deleteTag(tagId: Long) {
        db.tagQueries.deleteOne(tagId)
    }
}

private fun currentTimeMillis(): Long = System.currentTimeMillis()
