package com.meow.ledger.shared.auto

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 20 条真实感支付文本：识别率 ≥ 90%、重复 0（04 §4.3 去重是自动记账的生命线）。 */
class AutoCaptureTest {
    private val db = MeowDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply { MeowDatabase.Schema.create(this) })
    private val repo = AutoRepository(db)

    private val samples = listOf(
        "【微信支付】支付成功 ¥23.50 商户：瑞幸咖啡",
        "在 盒马鲜生 消费 ¥128.40",
        "向 美团外卖 付款 ¥36.80 完成",
        "商户：永辉超市 交易金额：￥89.20",
        "支付宝支出 45.00元 打车出行",
        "扣款 ¥12.00 顺丰速运",
        "收款 ¥200.00 商户：闲鱼买家张三",
        "到账 1500.00元 工资代发",
        "消费 ¥9.90 711便利店",
        "支付 ¥66.60 商户：肯德基",
        "退款 ¥18.50 商户：京东自营",
        "红包已领 88.88元",
        "转入 300.00 招商银行",
        "付款给 老王水果店 ¥35.70",
        "店铺：优衣库 消费 ¥299.00",
        "在 老娘舅 消费26.00元",
        "交易对方：滴滴出行 金额 ¥21.30",
        "已付款 ¥108.00 山姆会员店",
        "支出 320.00元 国网电费",
        "扫码支付 ¥4.50 商户：地铁便利",
    )

    @Test
    fun recognitionRateAtLeast90Percent() {
        var created = 0
        for (text in samples) {
            val r = repo.capture(text, "notification")
            created += r.created
        }
        assertTrue(created * 10 >= samples.size * 9, "识别 $created / ${samples.size}")
        assertEquals(created, repo.pending().size)
    }

    @Test
    fun duplicateNeverReachesQueue() {
        val first = repo.capture(samples[0], "notification")
        assertEquals(1, first.created)
        val again = repo.capture(samples[0], "notification")
        assertEquals(0, again.created)
        assertEquals(1, again.duplicate)
        assertEquals(1, repo.pending().size)
    }

    @Test
    fun sameSourceSameMinuteIsDuplicateNextMinuteIsNot() {
        val t = "在 瑞幸咖啡 消费 ¥23.50"
        val base = 1_700_000_000_000L
        repo.capture(t, "notification", occurredAt = base)
        val sameMinute = repo.capture(t, "notification", occurredAt = base + 30_000)
        assertEquals(0, sameMinute.created)
        assertEquals(1, sameMinute.duplicate)
        val nextMinute = repo.capture(t, "notification", occurredAt = base + 90_000)
        assertEquals(1, nextMinute.created)
        assertEquals(0, nextMinute.duplicate)
    }

    @Test
    fun garbageIsUnsupportedNotQueued() {
        val r = repo.capture("今天天气不错", "notification")
        assertEquals(0, r.created)
        assertEquals(1, r.unsupported)
        assertTrue(repo.pending().isEmpty())
    }
}

/** 票据整图：ML Kit 多行文本一次解析，日期数字不误判成金额，商户取全段。 */
class AutoTicketTest {
    private val db = MeowDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply { MeowDatabase.Schema.create(this) })
    private val repo = AutoRepository(db)

    @Test
    fun ticketTextProducesOneEventWithMerchant() {
        val text = "微信支付\n¥42.50\n瑞幸咖啡\n2026年11月12日 01:45"
        val result = repo.captureTicket(text, "ocr")
        assertEquals(1, result.created)
        assertEquals(0, result.unsupported)
        val events = repo.pending()
        assertEquals(1, events.size)
        val event = events.first()
        assertEquals(4250L, event.money)
        assertEquals("瑞幸咖啡", event.merchant)
        assertEquals(9L, event.member_cate_id)
    }

    @Test
    fun plainDateTextIsNotMoney() {
        val result = repo.captureTicket("2026年11月12日 01:45", "ocr")
        assertEquals(0, result.created)
        assertEquals(1, result.unsupported)
    }

    @Test
    fun duplicateTicketIsFolded() {
        val text = "支付成功 ¥15.90 美团外卖"
        repo.captureTicket(text, "ocr")
        val again = repo.captureTicket(text, "ocr")
        assertEquals(0, again.created)
        assertEquals(1, again.duplicate)
    }
}
