package com.meow.ledger.shared.manage

import com.meow.ledger.shared.db.Behavior_event
import com.meow.ledger.shared.db.Contract_info
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Member_import_bill_file
import com.meow.ledger.shared.db.Member_order
import com.meow.ledger.shared.db.Member_profile
import kotlin.random.Random

/** 本机账号，只有一行。 */
const val PROFILE_ID = 0L

/** 自动续费合约，只有一行。 */
const val CONTRACT_ID = 1L

const val FEEDBACK_KIND = "feedback"

/** 会员套餐，对应 React 的 order_config。 */
data class VipPlan(val id: String, val name: String, val money: Long, val days: Int)

val VIP_PLANS = listOf(
    VipPlan("month", "月卡", 600, 30),
    VipPlan("quarter", "季卡", 1500, 90),
    VipPlan("year", "年卡", 4800, 365),
)

/** 远程开关的默认值。数值项存数字，开关项存 1 或 0。 */
val REMOTE_DEFAULTS = mapOf(
    "auto_bill_enabled" to "1",
    "vip_banner" to "1",
    "feedback_enabled" to "1",
    "max_books" to "20",
)

/** 账号、导入记录、会员订单、合约与反馈。 */
class AccountRepository(private val db: MeowDatabase) {

    fun profile(): Member_profile =
        db.accountQueries.selectProfile(PROFILE_ID).executeAsOneOrNull()
            ?: Member_profile(PROFILE_ID, "本地用户", "", "", "", "", 0, 0)

    /** 手机号必须是 1 开头的 11 位。验证码留在本机并随结果返回。 */
    fun sendCode(phone: String): String? {
        if (!phone.matches(Regex("^1\\d{10}$"))) return null
        val code = Random.nextInt(100000, 999999).toString()
        db.accountQueries.upsertCode(
            com.meow.ledger.shared.db.Sms_code(phone, code, currentTimeMillis() + 300_000),
        )
        return code
    }

    /** 验证码不对时返回 null，对了就写入登录态。 */
    fun loginByPhone(phone: String, code: String): Member_profile? {
        val stored = db.accountQueries.selectCode(phone).executeAsOneOrNull()
        if (stored == null || stored.code != code || stored.expires < currentTimeMillis()) return null
        return save(profile().copy(phone = phone, login_type = "phone", bind_type = "phone", bind_id = phone))
    }

    fun loginByDevice(): Member_profile = save(profile().copy(login_type = "device"))

    fun loginThird(provider: String, openId: String): Member_profile =
        save(profile().copy(login_type = provider, bind_type = provider, bind_id = openId))

    fun updateNickname(nickname: String): Member_profile? {
        val text = nickname.trim()
        if (text.isEmpty()) return null
        return save(profile().copy(nickname = text))
    }

    /** 解绑：清掉绑定关系，登录态保留。 */
    fun unbind(): Member_profile = save(profile().copy(bind_type = "", bind_id = ""))

    private fun save(row: Member_profile): Member_profile {
        db.accountQueries.upsertProfile(row)
        return row
    }

    fun imports(): List<Member_import_bill_file> = db.accountQueries.selectImports().executeAsList()

    /** 记一次导入。账单由调用方写入，这里只留记录。 */
    fun recordImport(name: String, count: Int, content: String): Member_import_bill_file {
        val row = Member_import_bill_file(
            id = "im${currentTimeMillis()}${Random.nextInt(1000, 9999)}",
            name = name.ifBlank { "导入" },
            type = "text",
            count = count.toLong(),
            status = 1,
            create_time = currentTimeMillis(),
            content = content,
        )
        db.accountQueries.upsertImport(row)
        return row
    }

    /** 删除导入记录，不删账单。 */
    fun deleteImport(id: String) {
        db.accountQueries.deleteImport(id)
    }

    fun orders(): List<Member_order> = db.accountQueries.selectOrders().executeAsList()

    fun order(id: String): Member_order? = db.accountQueries.selectOrder(id).executeAsOneOrNull()

    /** 下单即视为本地支付成功，并延长会员到期时间。 */
    fun createOrder(planId: String): Member_order? {
        val plan = VIP_PLANS.firstOrNull { it.id == planId } ?: return null
        val now = currentTimeMillis()
        val row = Member_order(
            id = "o$now${Random.nextInt(1000, 9999)}",
            money = plan.money,
            method = "local",
            type = 1,
            product_id = plan.id,
            product_name = plan.name,
            status = 1,
            create_time = now,
        )
        db.accountQueries.upsertOrder(row)
        val current = profile()
        val base = maxOf(current.vip_expire, now)
        save(current.copy(vip = 1, vip_expire = base + plan.days.toLong() * 86_400_000))
        return row
    }

    fun contract(): Contract_info =
        db.accountQueries.selectContract(CONTRACT_ID).executeAsOneOrNull()
            ?: Contract_info(CONTRACT_ID, 0, "", "", 0, "")

    fun signContract(provider: String, productName: String, money: Long, nextDate: String): Contract_info {
        val row = Contract_info(CONTRACT_ID, 1, provider, productName, money, nextDate)
        db.accountQueries.upsertContract(row)
        return row
    }

    /** 没有生效中的合约时返回 false。 */
    fun unsignContract(): Boolean {
        if (contract().signed != 1L) return false
        db.accountQueries.upsertContract(Contract_info(CONTRACT_ID, 0, "", "", 0, ""))
        return true
    }

    fun feedback(): List<Behavior_event> = db.accountQueries.selectEvents(FEEDBACK_KIND).executeAsList()

    /** 内容少于 5 个字时不收。 */
    fun addFeedback(content: String, contact: String = ""): Behavior_event? {
        val text = content.trim()
        if (text.length < 5) return null
        val row = Behavior_event(
            id = "fb${currentTimeMillis()}${Random.nextInt(1000, 9999)}",
            kind = FEEDBACK_KIND,
            content = text,
            contact = contact,
            create_time = currentTimeMillis(),
        )
        db.accountQueries.upsertEvent(row)
        return row
    }

    fun remote(key: String): String =
        db.configQueries.selectOne(key).executeAsOneOrNull()?.value_ ?: REMOTE_DEFAULTS[key] ?: ""

    fun remoteAll(): Map<String, String> = REMOTE_DEFAULTS.mapValues { remote(it.key) }

    fun setRemote(key: String, value: String) {
        db.configQueries.upsert(
            com.meow.ledger.shared.db.Remote_config(key, value, currentTimeMillis()),
        )
    }
}

private fun currentTimeMillis(): Long = System.currentTimeMillis()
