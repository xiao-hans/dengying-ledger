package com.meow.ledger.shared.bills

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    @Test
    fun parsesWholeAndFractionalYuan() {
        assertEquals(0, toCents("0"))
        assertEquals(100, toCents("1"))
        assertEquals(150, toCents("1.5"))
        assertEquals(105, toCents("1.05"))
        assertEquals(100, toCents(" 1.00 "))
    }

    @Test
    fun rejectsInvalidInput() {
        // 空、非数字、负数、超过两位小数都不静默改值
        assertNull(toCents(""))
        assertNull(toCents("abc"))
        assertNull(toCents("-1"))
        assertNull(toCents("1.234"))
        assertNull(toCents("1.2.3"))
    }
}
