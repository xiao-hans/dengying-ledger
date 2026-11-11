package com.meow.ledger.shared.bills

import com.meow.ledger.shared.db.Category
import com.meow.ledger.shared.db.MeowDatabase

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
}
