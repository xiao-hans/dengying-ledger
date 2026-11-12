package com.meow.ledger.shared.bills

import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Refund_bill
import kotlin.random.Random

/** 退款合计不得超过原账单。超过则拒绝，不写入。 */
class RefundTooLarge(val billCents: Long, val alreadyCents: Long, val addingCents: Long) :
    IllegalArgumentException("退款合计不能超过原账单金额")

/**
 * 退款不改原账单：每笔退款是一行，报表按实付 = max(0, 原金额 − 已退) 统计。
 * 这与 React 版账单详情的规则一致：合计超过原金额的退款被拒绝。
 */
class RefundRepository(private val db: MeowDatabase) {
    fun ofBill(clientId: String): List<Refund_bill> = db.refundQueries.selectByBill(clientId).executeAsList()

    /** 全部退款记录，按创建时间倒序。统计下钻用。 */
    fun all(): List<Refund_bill> = db.refundQueries.selectAll().executeAsList().sortedByDescending { it.create_date }

    fun add(
        billClientId: String,
        billCents: Long,
        cents: Long,
        day: Long,
        desc: String = "退款",
        memberId: Long = 0,
    ): Refund_bill {
        val already = ofBill(billClientId).sumOf { it.money }
        if (already + cents > billCents) throw RefundTooLarge(billCents, already, cents)
        val now = day * 1000000
        val row = Refund_bill(
            id = "refund-$billClientId-${Random.nextLong()}",
            client_id = billClientId,
            member_id = memberId,
            money = cents,
            asset_id = 0,
            day = day,
            desc = desc,
            images = "",
            source = 0,
            status = 0,
            create_date = now,
            update_date = now,
        )
        db.refundQueries.upsert(row)
        return row
    }

    /** 修改退款金额：本账单全部退款合计不能超过原账单金额。 */
    fun update(old: Refund_bill, cents: Long, billCents: Long): Refund_bill {
        val others = ofBill(old.client_id).filter { it.id != old.id && it.status == 0L }.sumOf { it.money }
        if (others + cents > billCents) throw RefundTooLarge(billCents, others, cents)
        val row = old.copy(money = cents, update_date = old.day * 1000000)
        db.refundQueries.upsert(row)
        return row
    }

    fun remove(id: String) {
        db.refundQueries.deleteOne(id)
    }
}

/** 已退金额。只计 status = 0 的行，空 client_id 不计。 */
fun refundedCents(refunds: List<Refund_bill>): Long =
    refunds.filter { it.status == 0L && it.client_id.isNotEmpty() }.sumOf { it.money }

/** 实付，不会低于 0。 */
fun netPaid(billCents: Long, refunded: Long): Long = (billCents - refunded).coerceAtLeast(0)
