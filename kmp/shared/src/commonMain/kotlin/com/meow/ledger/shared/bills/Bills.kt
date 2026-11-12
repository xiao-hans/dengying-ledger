package com.meow.ledger.shared.bills

import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.network.SyncEngine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

const val TYPE_EXPENSE = 1L
const val TYPE_INCOME = 2L
const val TYPE_TRANSFER = 3L

/** 一个月的收支汇总，金额全部整数分。 */
data class MonthSummary(
    val expense: Long,
    val income: Long,
    val count: Int,
)

/** 按天分组后的一天账单。day 为 yyyyMMdd。 */
data class DayGroup(
    val day: Long,
    val bills: List<Bill>,
    val expense: Long,
    val income: Long,
    /** 每笔已退的分，列表用它显示实付而不是原价。 */
    val refunded: Map<String, Long> = emptyMap(),
)

/** 当月账单的展示结果。 */
data class MonthView(
    val summary: MonthSummary,
    val groups: List<DayGroup>,
)

/**
 * 账单仓储。`sync` 可选注入：null 时是纯本地模式（测试默认），
 * 非 null 时先落库再入 `sync_queue`，由 SyncScheduler 异步送达，云端不可达不阻塞 UI。
 */
class BillsRepository(private val db: MeowDatabase, private val sync: SyncEngine? = null) {

    /** 取一个月的账单并汇总。yearMonth 形如 202611，bookId 为 0 时不限账本。 */
    fun month(yearMonth: Int, bookId: Long = 0): MonthView {
        val from = yearMonth.toLong() * 100 + 1
        val to = yearMonth.toLong() * 100 + 31
        val bills = db.billQueries.selectByRange(bookId, from, to).executeAsList()
        val refunded = refundsOf(bills)
        return viewOf(bills, refunded)
    }

    /** 每个账单已退的分，只算 status 为 0 的退款。 */
    private fun refundsOf(bills: List<Bill>): Map<String, Long> =
        bills.associate { bill ->
            bill.client_id to db.refundQueries.selectByBill(bill.client_id).executeAsList().sumOf { it.money }
        }

    fun get(memberId: Long, clientId: String): Bill? =
        db.billQueries.selectOne(memberId, clientId).executeAsOneOrNull()

    fun save(bill: Bill) {
        db.billQueries.upsert(bill)
    }

    /** 记一笔并联动资产余额：支出扣 from 卡、收入加 to 卡、转账两头走。 */
    fun saveWithBalance(bill: Bill) {
        // 本地优先：先落库，再异步入队。云端不可达不影响这一步的结果。
        // 同 client_id 已存在说明是编辑，走 Bills/Update；新账单走 Bills/Create。
        val existed = db.billQueries.selectOne(bill.member_id, bill.client_id).executeAsOneOrNull() != null
        db.billQueries.upsert(bill)
        applyBalance(bill)
        track("ac_add_bill", "${bill.client_id}|${bill.money}|${bill.type}")
        enqueueBillWrite(if (existed) "Bills/Update" else "Bills/Create", bill)
    }

    /**
     * `Items<BillItem>` 口径：单笔写操作包一层 `items`，client_id 作幂等键。
     * `saveWithBalance` 与 `batchUpdate` 共用这个 payload builder，保证每笔入队都带
     * 完整的 BillItem，而不是只带 `memberCateId`。
     */
    private fun enqueueBillWrite(path: String, bill: Bill) {
        val engine = sync ?: return
        engine.enqueue(path, "POST", billItemPayload(bill), bill.client_id)
    }

    /** 一笔完整 BillItem 的 `{"items":[...]}` 请求体；字段名与样本 BillItem 一致。 */
    private fun billItemPayload(bill: Bill): JsonObject = buildJsonObject {
        putJsonArray("items") {
            add(buildJsonObject {
                put("memberId", bill.member_id.toString())
                put("clientId", bill.client_id)
                put("money", bill.money.toString())
                put("billType", bill.type.toString())
                put("day", bill.day.toString())
                put("desc", bill.desc)
                put("memberCateId", bill.member_cate_id.toString())
                put("billBookId", bill.book_id.toString())
            })
        }
    }

    /** `Items<BillDelInfo>` 口径：删除只带幂等键。 */
    private fun enqueueBillDelete(memberId: Long, clientId: String) {
        val engine = sync ?: return
        val payload = buildJsonObject {
            putJsonArray("items") {
                add(buildJsonObject {
                    put("memberId", memberId.toString())
                    put("clientId", clientId)
                })
            }
        }
        engine.enqueue("Bills/Delete", "POST", payload, clientId)
    }

    /** 埋点：与 AccountRepository.track 同表，就地写避免循环依赖。 */
    private fun track(kind: String, content: String) {
        val nowMillis = now()
        db.accountQueries.upsertEvent(
            com.meow.ledger.shared.db.Behavior_event(
                id = "b$nowMillis${(0..999).random()}",
                kind = kind,
                content = content.take(500),
                contact = "",
                create_time = nowMillis,
            ),
        )
    }

    /** 删一笔并回滚资产余额，与 [saveWithBalance] 对称。 */
    fun deleteWithBalance(memberId: Long, clientId: String) {
        val bill = db.billQueries.selectOne(memberId, clientId).executeAsOneOrNull() ?: return
        db.billQueries.deleteOne(memberId, clientId)
        // 用负金额复用同一套联动逻辑，收入/支出/转账的回滚方向自动对称。
        applyBalance(bill.copy(money = -bill.money))
        enqueueBillDelete(memberId, clientId)
    }

    private fun applyBalance(bill: Bill) {
        val from = if (bill.from_assets_id == 0L) null else db.assetQueries.selectOne(bill.from_assets_id).executeAsOneOrNull()
        val to = if (bill.to_assets_id == 0L) null else db.assetQueries.selectOne(bill.to_assets_id).executeAsOneOrNull()
        when (bill.type) {
            TYPE_EXPENSE -> from?.let { db.assetQueries.upsert(it.copy(balance_cents = it.balance_cents - bill.money)) }
            TYPE_INCOME -> to?.let { db.assetQueries.upsert(it.copy(balance_cents = it.balance_cents + bill.money)) }
            TYPE_TRANSFER -> {
                from?.let { db.assetQueries.upsert(it.copy(balance_cents = it.balance_cents - bill.money)) }
                to?.let { db.assetQueries.upsert(it.copy(balance_cents = it.balance_cents + bill.money)) }
            }
        }
    }

    fun delete(memberId: Long, clientId: String) {
        db.billQueries.deleteOne(memberId, clientId)
        enqueueBillDelete(memberId, clientId)
    }

    /**
     * 批量改分类：逐笔落库，再逐笔入队 `Bills/Update`。
     * 每笔都用 [billItemPayload] 发完整的 `Items<BillItem>`（与 [saveWithBalance] 同形状），
     * 只把 `memberCateId` 改成新值，不带残缺 item。返回实际改动条数。
     */
    fun batchUpdate(memberId: Long, clientIds: List<String>, cateId: Long): Int {
        val changed = clientIds.mapNotNull { clientId ->
            val bill = db.billQueries.selectOne(memberId, clientId).executeAsOneOrNull() ?: return@mapNotNull null
            bill.copy(member_cate_id = cateId, local_update_time = now())
        }
        changed.forEach { updated ->
            db.billQueries.upsert(updated)
            enqueueBillWrite("Bills/Update", updated)
        }
        return changed.size
    }

    /** 按备注关键字搜账单，bookId 为 0 时不限账本。 */
    fun search(keyword: String, bookId: Long = 0): List<Bill> =
        db.billQueries.selectAll().executeAsList()
            .filter { bill -> bookId == 0L || bill.book_id == bookId }
            .filter { bill -> bill.desc.contains(keyword) }
            .sortedByDescending { it.local_create_time }

    /** 分页取账单，page 从 1 开始。 */
    fun page(page: Int, size: Int, bookId: Long = 0): List<Bill> =
        search("", bookId).drop((page - 1).coerceAtLeast(0) * size).take(size)

    /** 批量导入。同 member_id + client_id 再次导入会覆盖而非重复，幂等键语义。 */
    fun importAll(bills: List<Bill>): Int {
        bills.forEach { db.billQueries.upsert(it) }
        return bills.size
    }

    /** 某一天的收支合计。 */
    fun daySum(day: Long): MonthSummary {
        val bills = db.billQueries.selectByRange(0, day, day).executeAsList()
        return MonthSummary(
            expense = bills.filter { it.type == TYPE_EXPENSE }.sumOf { it.money },
            income = bills.filter { it.type == TYPE_INCOME }.sumOf { it.money },
            count = bills.size,
        )
    }

    /** 带图片的账单数。 */
    fun hasPicCount(): Int =
        db.billQueries.selectAll().executeAsList().count { it.images.isNotBlank() }
}

/** 幂等键：时间戳 + 计数，进程内唯一。 */
private var clientIdSeq = 0L

fun newClientId(): String {
    clientIdSeq += 1
    return "%d%03d".format(System.currentTimeMillis(), clientIdSeq % 1000)
}

internal fun now(): Long = System.currentTimeMillis()


internal fun viewOf(bills: List<Bill>, refunded: Map<String, Long> = emptyMap()): MonthView {
    val active = bills.filter { it.status == 0L }
    val groups = active.groupBy { it.day }
        .entries
        .sortedByDescending { it.key }
        .map { (day, items) ->
            DayGroup(
                day = day,
                bills = items,
                expense = items.filter { it.type == TYPE_EXPENSE }.sumOf { netPaid(it.money, refunded[it.client_id] ?: 0) },
                income = items.filter { it.type == TYPE_INCOME }.sumOf { netPaid(it.money, refunded[it.client_id] ?: 0) },
                refunded = items.associate { it.client_id to (refunded[it.client_id] ?: 0) },
            )
        }
    return MonthView(
        summary = MonthSummary(
            expense = groups.sumOf { it.expense },
            income = groups.sumOf { it.income },
            count = active.size,
        ),
        groups = groups,
    )
}
