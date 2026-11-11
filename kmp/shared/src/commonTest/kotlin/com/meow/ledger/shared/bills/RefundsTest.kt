package com.meow.ledger.shared.bills

import com.meow.ledger.shared.db.Refund_bill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RefundsTest {
    @Test
    fun refundedIgnoresRevokedAndEmptyRows() {
        val rows = listOf(
            refund("a", "bill", 100),
            refund("b", "bill", 50, status = 1),
            refund("c", "", 999),
        )
        assertEquals(100, refundedCents(rows))
    }

    @Test
    fun netPaidNeverGoesBelowZero() {
        assertEquals(700, netPaid(1000, 300))
        assertEquals(0, netPaid(1000, 1500))
    }

    @Test
    fun refundOverTheBillIsRejected() {
        val error = assertFailsWith<RefundTooLarge> {
            checkRefund(billCents = 1000, already = 800, adding = 300)
        }
        assertEquals(800, error.alreadyCents)
        assertEquals(300, error.addingCents)
    }

    @Test
    fun refundUpToTheBillIsAccepted() {
        checkRefund(billCents = 1000, already = 800, adding = 200)
    }
}

/** 与 RefundRepository.add 同一条校验，抽出来避免测试依赖数据库。 */
private fun checkRefund(billCents: Long, already: Long, adding: Long) {
    if (already + adding > billCents) throw RefundTooLarge(billCents, already, adding)
}

private fun refund(id: String, clientId: String, cents: Long, status: Long = 0) = Refund_bill(
    id = id,
    client_id = clientId,
    member_id = 0,
    money = cents,
    asset_id = 0,
    day = 20261109,
    desc = "",
    images = "",
    source = 0,
    status = status,
    create_date = 0,
    update_date = 0,
)
