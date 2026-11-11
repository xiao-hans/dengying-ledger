package com.meow.ledger.shared.manage

import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.bills.TYPE_TRANSFER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExchangeTest {

    @Test
    fun csvRoundTripKeepsRowsAndSkipsHeader() {
        val text = "﻿日期,类型,分类,金额(元),备注\n2026-11-03,支出,餐饮,32.50,午餐\n2026-11-04,收入,工资,8000,\n"
        val rows = parseCsv(text)
        assertEquals(2, rows.size)
        assertEquals(20261103, rows[0].day)
        assertEquals(TYPE_EXPENSE, rows[0].type)
        assertEquals("餐饮", rows[0].category)
        assertEquals("32.50", rows[0].money)
        assertEquals(TYPE_INCOME, rows[1].type)
    }

    @Test
    fun quotedCommaStaysInsideOneCell() {
        val rows = parseCsv("2026-11-05,转账,账户,100,\"转给,家用\"\n")
        assertEquals(1, rows.size)
        assertEquals(TYPE_TRANSFER, rows[0].type)
        assertEquals("转给,家用", rows[0].desc)
    }

    @Test
    fun dedupKeyIgnoresSurroundingSpaces() {
        assertEquals(dedupKey(" 午餐 ", 3250, 20261103), dedupKey("午餐", 3250, 20261103))
        assertTrue(dedupKey("午餐", 3250, 20261103) != dedupKey("午餐", 3250, 20261104))
    }
}
