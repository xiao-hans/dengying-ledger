package com.meow.ledger.shared.manage

import com.meow.ledger.shared.db.Group_vip
import com.meow.ledger.shared.db.MeowDatabase
import kotlin.random.Random

/** 展示码最长 32 个字，与 Web 端输入框一致。 */
const val SHOW_ID_MAX = 32

/** 按展示码回查的结果。没有记录时 isVip 为 0。 */
data class GroupCheck(val showId: String, val isVip: Long, val expireTime: Long)

/** 拼团只写本地记录，不发起分享，也不接支付渠道。 */
class GroupRepository(private val db: MeowDatabase) {

    fun list(): List<Group_vip> = db.groupQueries.selectGroups().executeAsList()

    /** 空展示码或超过 32 个字时不写。同一个码只保留第一条。 */
    fun bind(showId: String): Group_vip? {
        val code = showId.trim()
        if (code.isEmpty() || code.length > SHOW_ID_MAX) return null
        val existing = db.groupQueries.selectGroup(code).executeAsOneOrNull()
        if (existing != null) return existing
        val row = Group_vip(
            id = "g${System.currentTimeMillis()}${Random.nextInt(1000, 9999)}",
            show_id = code,
            status = 1,
            create_time = System.currentTimeMillis() / 1000,
        )
        db.groupQueries.upsertGroup(row)
        return row
    }

    /** 没有记录也返回结果，isVip 为 0。有记录时带上本机会员的到期时间。 */
    fun check(showId: String): GroupCheck {
        val row = db.groupQueries.selectGroup(showId).executeAsOneOrNull()
            ?: return GroupCheck(showId, 0, 0)
        val profile = db.accountQueries.selectProfile(PROFILE_ID).executeAsOneOrNull()
        return GroupCheck(row.show_id, profile?.vip ?: 0, profile?.vip_expire ?: 0)
    }
}
