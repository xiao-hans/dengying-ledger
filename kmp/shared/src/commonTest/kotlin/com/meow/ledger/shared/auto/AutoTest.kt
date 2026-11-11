package com.meow.ledger.shared.auto

import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.db.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoTest {
    @Test
    fun parsesMarkedAmountAndMerchant() {
        val parsed = parseRaw("在星巴克消费 ¥35.50 支付成功", SOURCE_NOTIFICATION)
        assertEquals(3550, parsed?.money)
        assertEquals("星巴克", parsed?.merchant)
        assertEquals(TYPE_EXPENSE, parsed?.type)
    }

    @Test
    fun incomeWhenOnlyIncomeWords() {
        val parsed = parseRaw("收款成功 ¥20.00", SOURCE_NOTIFICATION)
        assertEquals(TYPE_INCOME, parsed?.type)
        assertEquals(2000, parsed?.money)
    }

    @Test
    fun noAmountReturnsNull() {
        assertNull(parseRaw("没有金额的通知", SOURCE_OCR))
    }

    @Test
    fun longestKeywordWins() {
        val rules = listOf(rule(1, "星巴"), rule(2, "星巴克"))
        assertEquals(2, matchRule(rules, "星巴克", "")?.id)
    }

    @Test
    fun noKeywordReturnsNull() {
        assertNull(matchRule(listOf(rule(1, "美团")), "星巴克", ""))
    }

    @Test
    fun sameMinuteSameMerchantCollides() {
        val a = dedupKey("通知识别", 3550, 1_700_000_000_000, "星巴克")
        val b = dedupKey("通知识别", 3550, 1_700_000_020_000, "星巴克")
        assertEquals(a, b)
    }

    @Test
    fun differentAmountDoesNotCollide() {
        val a = dedupKey("通知识别", 3550, 1_700_000_030_000, "星巴克")
        val b = dedupKey("通知识别", 3600, 1_700_000_030_000, "星巴克")
        assertTrue(a != b)
    }

    private fun rule(id: Long, keyword: String) = Rule(
        id = id, key_word = keyword, source = 0, member_id = 0, type = TYPE_EXPENSE,
        book_id = 0, member_cate_id = 1, assets_id = 1, tag_ids = "",
        create_date = 0, update_date = 0,
    )
}
