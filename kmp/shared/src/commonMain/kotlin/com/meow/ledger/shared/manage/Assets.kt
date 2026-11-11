package com.meow.ledger.shared.manage

import com.meow.ledger.shared.db.Assets
import com.meow.ledger.shared.db.MeowDatabase

/** 账户类型，对齐 React 的 assetTypes 兜底列表。 */
data class AssetType(val id: Long, val key: String, val title: String)

/**
 * 资金账户。
 *
 * status 与 React 一致：1 可见，0 隐藏。默认现金账户（id = 1）不可删除。
 * 借据是 type = 6 的账户，还清即隐藏，不单独建表。
 */
class AssetRepository(private val db: MeowDatabase) {
    private val assets get() = db.assetQueries

    fun types(): List<AssetType> = ASSET_TYPES

    fun all(): List<Assets> = assets.selectEvery().executeAsList()

    fun visible(): List<Assets> = assets.selectAll().executeAsList()

    fun one(id: Long): Assets? = assets.selectOne(id).executeAsOneOrNull()

    fun ensureDefault() {
        if (one(DEFAULT_ASSET_ID) != null) return
        assets.upsert(
            Assets(
                id = DEFAULT_ASSET_ID,
                type_id = 1,
                type = 1,
                type_name = "cash",
                type_title = "现金",
                name = "现金",
                desc = "",
                icon = "wallet",
                currency = "CNY",
                create_date = currentTimeMillis(),
                update_date = currentTimeMillis(),
                credit_line = 0,
                repayment_date = 0,
                statement_date = 0,
                is_remind = 0,
                is_total_assets = 1,
                is_next_bills = 0,
                status = VISIBLE,
                balance_cents = 0,
            ),
        )
    }

    fun save(
        name: String,
        typeId: Long,
        balanceCents: Long,
        creditLine: Long = 0,
        repaymentDate: Long = 0,
        statementDate: Long = 0,
        desc: String = "",
        isTotalAssets: Boolean = true,
        isRemind: Boolean = false,
        id: Long = 0,
    ): Assets? {
        val type = ASSET_TYPES.firstOrNull { it.id == typeId } ?: ASSET_TYPES.first()
        val now = currentTimeMillis()
        if (id == 0L) {
            val next = (assets.selectEvery().executeAsList().maxOfOrNull { it.id } ?: 0L) + 1
            val row = Assets(
                id = next,
                type_id = type.id,
                type = type.id,
                type_name = type.key,
                type_title = type.title,
                name = name,
                desc = desc,
                icon = "wallet",
                currency = "CNY",
                create_date = now,
                update_date = now,
                credit_line = creditLine,
                repayment_date = repaymentDate,
                statement_date = statementDate,
                is_remind = if (isRemind) 1 else 0,
                is_total_assets = if (isTotalAssets) 1 else 0,
                is_next_bills = 0,
                status = VISIBLE,
                balance_cents = balanceCents,
            )
            assets.upsert(row)
            return row
        }
        val old = one(id) ?: return null
        val row = old.copy(
            type_id = type.id,
            type = type.id,
            type_name = type.key,
            type_title = type.title,
            name = name,
            desc = desc,
            update_date = now,
            credit_line = creditLine,
            repayment_date = repaymentDate,
            statement_date = statementDate,
            is_remind = if (isRemind) 1 else 0,
            is_total_assets = if (isTotalAssets) 1 else 0,
            balance_cents = balanceCents,
        )
        assets.upsert(row)
        return row
    }

    /** 隐藏或恢复。 */
    fun setVisible(id: Long, visible: Boolean) {
        val old = one(id) ?: return
        assets.upsert(old.copy(status = if (visible) VISIBLE else HIDDEN, update_date = currentTimeMillis()))
    }

    /** 硬删除。默认现金账户不可删除，已有账单保留。 */
    fun delete(id: Long): Boolean {
        if (id == DEFAULT_ASSET_ID || one(id) == null) return false
        assets.deleteOne(id)
        return true
    }

    /** 借据名单，逗号拼接，对齐 FundAccount/GetIOU。 */
    fun iouNames(): String = all()
        .filter { it.type == IOU_TYPE && it.status == VISIBLE }
        .joinToString(",") { it.name }
}

private fun currentTimeMillis(): Long = System.currentTimeMillis()

const val DEFAULT_ASSET_ID = 1L
private const val VISIBLE = 1L
private const val HIDDEN = 0L
private const val IOU_TYPE = 6L

val ASSET_TYPES = listOf(
    AssetType(1, "cash", "现金"),
    AssetType(2, "bank", "银行卡"),
    AssetType(3, "credit", "信用卡"),
    AssetType(4, "alipay", "支付宝"),
    AssetType(5, "wechat", "微信"),
    AssetType(6, "iou", "借条"),
)
