package com.meow.ledger.shared.auto

import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.bills.toCents
import com.meow.ledger.shared.db.Auto_event
import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Rule
import kotlin.random.Random

/** 通知识别、无障碍识别、截屏 OCR 三路采集。Web 没有系统级权限，输入是用户粘贴的文本。 */
const val SOURCE_NOTIFICATION = "notification"
const val SOURCE_ACCESSIBILITY = "accessibility"
const val SOURCE_OCR = "ocr"

/** bill.source：0 手工 / 1 通知 / 2 截屏 / 3 OCR / 4 导入 / 5 周期。 */
fun billSourceOf(source: String): Long = when (source) {
    SOURCE_NOTIFICATION -> 1L
    SOURCE_ACCESSIBILITY -> 2L
    SOURCE_OCR -> 3L
    else -> 0L
}

fun sourceLabel(source: String): String = when (source) {
    SOURCE_NOTIFICATION -> "通知识别"
    SOURCE_ACCESSIBILITY -> "无障碍识别"
    SOURCE_OCR -> "截屏 OCR"
    else -> "未知来源"
}

/** 监听白名单。从未保存过时默认只开前三个。 */
data class WatchApp(val pkg: String, val name: String)

val WATCH_APPS = listOf(
    WatchApp("com.eg.android.AlipayGphone", "支付宝"),
    WatchApp("com.tencent.mm", "微信"),
    WatchApp("com.tencent.mobileqq", "QQ"),
    WatchApp("com.unionpay", "云闪付"),
    WatchApp("com.chinamobile.bank", "银行 App"),
    WatchApp("com.smile.gifmaker", "快手"),
    WatchApp("com.kuaishou.nebula", "快手极速版"),
)

val DEFAULT_WATCH_APPS = WATCH_APPS.take(3).map { it.pkg }

/** 引导页五步，对齐 GuideAutoBillActivity 的权限漏斗。 */
data class GuideStep(val title: String, val body: String)

val GUIDE_STEPS = listOf(
    GuideStep("认识自动记账", "只在支付发生后读取你主动提供的通知、页面或截图文本，提取金额与商户；不会替你付款，也不会读取通讯录。"),
    GuideStep("选择识别方式", "通知识别、无障碍文本、截屏 OCR 三路可单独开关；不需要全部开启，也可以随时关闭。"),
    GuideStep("选择监听的 App", "默认列出支付宝、微信、QQ、云闪付、银行与快手。白名单之外的文本不会被识别。"),
    GuideStep("设置匹配规则", "按商户关键词自动带入分类、账户与标签；没有命中规则时，由你在确认页手动选择。"),
    GuideStep("确认后才入账", "识别结果先进入待确认队列，重复事件会折叠。你可以逐条修改或批量通过，绝不会静默写进账本。"),
)

/**
 * 金额正则：带货币符号、带单位、或带小数点的数字才算金额。
 * 裸整数（如「2026年」「100分」里的数字）不是金额；日期、时间与折扣紧跟字会被否定前瞻排除。
 */
private val MONEY_RE = Regex("""(?:[¥￥]|RMB|人民币)\s*(-?\d+(?:\.\d{1,2})?|-?\d+\s*(?:元|块))|-?\d+\.\d{1,2}(?!\d)|-?\d+\s*(?:元|块)(?!\s*[年月日时分秒号折%])""")

private val NOISE_RE = Regex("[\u652f\u4ed8\u6210\u529f\u4ed8\u6b3e\u6210\u529f\u5df2\u652f\u4ed8\u6536\u6b3e\u6210\u529f\u5df2\u6536\u6b3e\u4ea4\u6613\u6210\u529f\u6d88\u8d39\u652f\u51fa\u6536\u5165\u5230\u8d26\u8f6c\u8d26\u8ba2\u5355\u8d26\u5355\u60a8\u4f60\u5df2\u6210\u529f\u91d1\u989d\u4f59\u989d\u672c\u6b21\u5b8c\u6210\u4eba\u6c11\u5e01\u5143\u6574\uff1a:\u00b7,\uff0c\u3002!\uff01\\s]")

/** 解析结果。没有有效金额时为 null。 */
data class Parsed(
    val money: Long,
    val merchant: String,
    val type: Long,
    val sourceApp: String,
)

/** 从原始文本提取金额、商户与收支方向。带货币符号或单位的金额优先。 */
fun parseRaw(raw: String, source: String, preferAfterMoney: Boolean = false): Parsed? {
    val text = raw.replace(Regex("\\s+"), " ").trim()
    if (text.isEmpty()) return null
    var best: Pair<Long, Int>? = null
    for (m in MONEY_RE.findAll(text)) {
        val digits = Regex("""-?\d+(?:\.\d{1,2})?""").find(m.value)?.value ?: continue
        val cents = toCents(digits) ?: continue
        if (kotlin.math.abs(cents) < 1) continue
        val marked = m.value.contains('¥') || m.value.contains('￥') || m.value.contains('元') || m.value.contains('块')
        val score = (if (marked) 1000 else 0) + kotlin.math.abs(cents).toInt()
        if (best == null || score > best.second) best = kotlin.math.abs(cents) to score
    }
    val money = best?.first ?: return null
    val income = Regex("收款|到账|已收|转入|退款|收入|红包已领|入账").containsMatchIn(text)
    val expense = Regex("付款|支付|消费|支出|转出|扣款|已付").containsMatchIn(text)
    val type = if (income && !expense) TYPE_INCOME else TYPE_EXPENSE
    return Parsed(money, extractMerchant(text, preferAfterMoney), type, sourceLabel(source))
}

private fun extractMerchant(text: String, preferAfterMoney: Boolean = false): String {
    if (preferAfterMoney) {
        // 票据版式里商户名紧跟金额行；从最后一个金额之后取第一个像名字的词。
        val last = MONEY_RE.findAll(text).lastOrNull()
        if (last != null) {
            val rest = text.substring(last.range.last + 1)
            val token = rest.split(Regex("[\\s,，。;.；:：|/年月日时分秒]+"))
                .firstOrNull { it.length in 2..18 && !it.all(Char::isDigit) }
            if (token != null) return token
        }
    }
    val labeled = Regex("(?:商户|商家|收款方|付款给|收款人|店铺|店名|交易对方)\\s*[：:为]?\\s*([^\\s,\uff0c\u3002;\uff1b:\uff1a\u00a5\uffe5\\d]{2,18})")
        .find(text)
    if (labeled != null) return labeled.groupValues[1].trim()
    val paid = Regex("(?:在|向|给)\\s*([^\\s,\uff0c\u3002;\uff1b:\uff1a\u00a5\uffe5\\d]{2,18})\\s*(?:消费|付款|支付|完成)")
        .find(text)
    if (paid != null) return paid.groupValues[1].trim()
    val cleaned = text.replace(MONEY_RE, " ").replace(NOISE_RE, " ").replace(Regex("[\\[\\]()\uff08\uff09\u3010\u3011\"'`]") , " ").trim()
    return cleaned.split(Regex("[\\s,\uff0c\u3002;\uff1b|/]+"))
        .firstOrNull { it.length in 2..18 && !it.all(Char::isDigit) }
        ?: "未识别商户"
}

/** 最长关键词优先。没有命中返回 null。 */
fun matchRule(rules: List<Rule>, merchant: String, text: String): Rule? {
    val hay = "$merchant $text".lowercase()
    return rules
        .filter { it.key_word.trim().isNotEmpty() && hay.contains(it.key_word.trim().lowercase()) }
        .maxByOrNull { it.key_word.length }
}

/** 三路采集共用的去重键：同一分钟内同一商户同一金额只入队一次。 */
fun dedupKey(sourceApp: String, money: Long, occurredAt: Long, merchant: String): String {
    val bucket = occurredAt / 60_000
    return digest("$sourceApp|$money|$bucket|$merchant")
}

private const val MASK = 0xffffffffL

/** FNV-1a 拼接，只要求确定性，不要求与 MD5 一致。乘法在 32 位掩码内完成，避免溢出成负值。 */
fun digest(text: String): String {
    var h1 = 0x811c9dc5L
    var h2 = 0x01000193L
    var h3 = 0x9e3779b9L
    var h4 = 0x85ebca6bL
    text.forEachIndexed { i, ch ->
        val c = ch.code.toLong()
        h1 = mul32(h1 xor c, 16777619)
        h2 = mul32(h2 + c + i, 2246822519)
        h3 = mul32(h3 xor (c shl 1), 3266489917)
        h4 = mul32(h4 xor (c ushr 1), 668265263)
    }
    return listOf(h1, h2, h3, h4).joinToString("") { it.toString(16).padStart(8, '0') }
}

private fun mul32(a: Long, b: Long): Long {
    val x = a and MASK
    val y = b and MASK
    val lo = (x and 0xffff) * y
    val hi = (x ushr 16) * y
    return ((hi ushr 16) + (hi shl 16) + lo) and MASK
}

/** 一次采集的结果。重复事件不重新入队。 */
data class CaptureResult(val created: Int, val duplicate: Int, val unsupported: Int)

/** 待确认队列里用户可修改的字段。金额用整数分；备注为空时确认仍回退到商户名。 */
data class PendingEdit(
    val money: Long,
    val desc: String,
    val memberCateId: Long,
)

/**
 * 采集入口：原始文本 → 解析 → 规则 → 去重 → 写 auto_event。
 * 结果只进待确认队列，绝不静默入账。
 */
class AutoRepository(private val db: MeowDatabase) {
    fun rules(): List<Rule> = db.ruleQueries.selectAll().executeAsList()

    fun pending(): List<Auto_event> = db.autoQueries.selectPending().executeAsList()

    fun capture(
        rawText: String,
        source: String,
        occurredAt: Long = currentTimeMillis(),
        sourceApp: String = sourceLabel(source),
    ): CaptureResult {
        val lines = rawText.split(Regex("[\\r\\n]+")) .map { it.trim() }.filter { it.isNotEmpty() }
        val segments = if (lines.size > 1) lines else listOf(rawText)
        var created = 0
        var duplicate = 0
        var unsupported = 0
        val rules = rules()
        for (seg in segments) {
            val parsed = parseRaw(seg, source)
            if (parsed == null) { unsupported++; continue }
            val full = parsed.copy(sourceApp = sourceApp)
            val hash = dedupKey(full.sourceApp, full.money, occurredAt, full.merchant)
            if (db.autoQueries.selectByHash(hash).executeAsOneOrNull() != null) { duplicate++; continue }
            val rule = matchRule(rules, full.merchant, seg)
            val now = occurredAt / 1000
            db.autoQueries.upsert(
                Auto_event(
                    id = hash,
                    hash = hash,
                    source = source,
                    source_app = full.sourceApp,
                    merchant = full.merchant,
                    money = full.money,
                    type = full.type,
                    occurred_at = occurredAt,
                    raw_text = seg,
                    status = 0,
                    member_cate_id = rule?.member_cate_id ?: if (full.type == TYPE_INCOME) 101 else 9,
                    assets_id = rule?.assets_id ?: 1,
                    tag_ids = rule?.tag_ids ?: "",
                    book_id = rule?.book_id ?: 0,
                    desc = "${full.merchant} · ${sourceLabel(source)}",
                    rule_id = rule?.id ?: 0,
                    create_time = now,
                ),
            )
            created++
            track("ac_auto_capture", "${full.merchant}|${full.money}|$source")
        }
        return CaptureResult(created, duplicate, unsupported)
    }

    /**
     * 票据 OCR 专用：整图文本一次解析，只产出一条候选。
     * 不按行拆分——金额行和商户行在票据里是分开的，拆开会把商户丢成「未识别商户」。
     */
    fun captureTicket(
        rawText: String,
        source: String,
        occurredAt: Long = currentTimeMillis(),
        sourceApp: String = sourceLabel(source),
    ): CaptureResult {
        val parsed = parseRaw(rawText, source, preferAfterMoney = true)
        if (parsed == null) return CaptureResult(0, 0, 1)
        val full = parsed.copy(sourceApp = sourceApp)
        val hash = dedupKey(full.sourceApp, full.money, occurredAt, full.merchant)
        if (db.autoQueries.selectByHash(hash).executeAsOneOrNull() != null) return CaptureResult(0, 1, 0)
        val rules = rules()
        val rule = matchRule(rules, full.merchant, rawText)
        val now = occurredAt / 1000
        db.autoQueries.upsert(
            Auto_event(
                id = hash,
                hash = hash,
                source = source,
                source_app = full.sourceApp,
                merchant = full.merchant,
                money = full.money,
                type = full.type,
                occurred_at = occurredAt,
                raw_text = rawText.take(200),
                status = 0,
                member_cate_id = rule?.member_cate_id ?: if (full.type == TYPE_INCOME) 101 else 9,
                assets_id = rule?.assets_id ?: 1,
                tag_ids = rule?.tag_ids ?: "",
                book_id = rule?.book_id ?: 0,
                desc = "${full.merchant} · ${sourceLabel(source)}",
                rule_id = rule?.id ?: 0,
                create_time = now,
            ),
        )
        track("ac_auto_capture", "${full.merchant}|${full.money}|$source")
        return CaptureResult(1, 0, 0)
    }

    /** 埋点：与 AccountRepository.track 同表，AutoRepository 只拿得到 db，就地写。 */
    private fun track(kind: String, content: String) {
        val now = currentTimeMillis()
        db.accountQueries.upsertEvent(
            com.meow.ledger.shared.db.Behavior_event(
                id = "b$now${(0..999).random()}",
                kind = kind,
                content = content.take(500),
                contact = "",
                create_time = now,
            ),
        )
    }

    /**
     * 确认入账：写一笔账单，再把事件标成已入账。
     * 只允许由用户显式动作调用；批量通过走 [confirmAll]，绝不自动调用。
     * edit 非空时用用户改过的金额/备注/分类覆盖识别结果。
     */
    fun confirm(event: Auto_event, memberId: Long = 0, edit: PendingEdit? = null): Bill {
        val money = edit?.money ?: event.money
        val desc = (edit?.desc ?: event.desc).ifBlank { event.merchant }
        val cateId = edit?.memberCateId ?: event.member_cate_id
        val day = dayOf(event.occurred_at)
        val bill = Bill(
            member_id = memberId,
            client_id = "auto-${event.hash}",
            money = money,
            refund_money = 0,
            type = event.type,
            exchange = 0,
            book_id = event.book_id,
            member_cate_id = cateId,
            currency = "CNY",
            day = day,
            desc = desc,
            not_in_budget = 0,
            images = "",
            update_date = event.occurred_at,
            create_date = event.occurred_at,
            status = 0,
            to_assets_id = 0,
            from_assets_id = event.assets_id,
            interest = 0,
            interest_reckon_type = 0,
            tag_ids = event.tag_ids,
            source = billSourceOf(event.source),
            import_id = 0,
            repayment_day = 0,
            local_create_time = event.occurred_at,
            local_update_time = event.occurred_at,
            installment_plan_id = "",
            installment_num = 0,
            installment_money = 0,
            installment_interest = 0,
        )
        db.billQueries.upsert(bill)
        db.autoQueries.upsert(event.copy(status = 1))
        track("ac_auto_confirm", "$desc|$money")
        return bill
    }

    /** 忽略：不写账单，事件留着供查。 */
    fun ignore(event: Auto_event) {
        db.autoQueries.upsert(event.copy(status = 2))
    }

    /** 按 id 取当前仍待确认的事件，已入账/已忽略的不会被重复处理。 */
    fun pendingById(id: String): Auto_event? =
        db.autoQueries.selectByHash(id).executeAsOneOrNull()?.takeIf { it.status == 0L }

    /**
     * 批量通过：只处理传入且仍处于待确认状态的 id。[edits] 里带的用户修改优先于识别结果。
     * 整批在一个事务里完成，返回实际入账笔数。
     */
    fun confirmAll(ids: Collection<String>, edits: Map<String, PendingEdit> = emptyMap(), memberId: Long = 0): Int {
        var count = 0
        db.transaction {
            ids.distinct().forEach { id ->
                val event = pendingById(id) ?: return@forEach
                confirm(event, memberId, edits[id])
                count++
            }
        }
        return count
    }

    /** 批量忽略：只处理仍待确认的 id，不写账单，返回实际忽略条数。 */
    fun ignoreAll(ids: Collection<String>): Int {
        var count = 0
        db.transaction {
            ids.distinct().forEach { id ->
                val event = pendingById(id) ?: return@forEach
                ignore(event)
                count++
            }
        }
        return count
    }

    fun remove(id: String) {
        db.autoQueries.deleteOne(id)
    }

    fun saveRule(rule: Rule) {
        db.ruleQueries.upsert(rule)
    }

    fun nextRuleId(): Long = (rules().maxOfOrNull { it.id } ?: 0) + 1

    fun deleteRule(id: Long) {
        db.ruleQueries.deleteOne(id)
    }
}

private fun dayOf(millis: Long): Long {
    val day = millis / 86_400_000
    val z = day + 719468
    val era = if (z >= 0) z else z - 146096
    val doe = (era - era / 146097 * 146097).toInt()
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + (era / 146097).toInt() * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + if (mp < 10) 3 else -9
    val year = y + if (m <= 2) 1 else 0
    return year * 10000L + m * 100L + d
}

private fun currentTimeMillis(): Long = System.currentTimeMillis()
