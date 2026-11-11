package com.meow.ledger.shared.manage

import com.meow.ledger.shared.db.Installment_item
import com.meow.ledger.shared.db.Installment_plan
import com.meow.ledger.shared.db.MeowDatabase
import com.meow.ledger.shared.db.Save_plan
import com.meow.ledger.shared.db.Save_plan_item
import kotlin.random.Random

/** 分期未结清 / 已还清。 */
const val INSTALLMENT_ACTIVE = 1L
const val INSTALLMENT_PAID_OFF = 2L

/** 存钱计划进行中。 */
const val SAVE_PLAN_ACTIVE = 1L

/** 存钱类型，对应 React 的 plan_type 表。 */
data class PlanType(val id: Long, val name: String)

val PLAN_TYPES = listOf(
    PlanType(1, "目标存钱"),
    PlanType(2, "定期存款"),
    PlanType(3, "自由存钱"),
)

/** 存钱封面，对应 React 的 plan_cover 表。 */
val PLAN_ICONS = listOf("wallet", "star", "heart", "coin", "book", "note")

/** 分期与存钱计划。本地没有网络，行为对齐 React 版的 mock。 */
class PlanRepository(private val db: MeowDatabase) {

    fun installments(): List<Installment_plan> = db.planQueries.selectAll().executeAsList()

    fun installment(id: String): Installment_plan? = db.planQueries.selectPlan(id).executeAsOneOrNull()

    fun installmentItems(planId: String): List<Installment_item> =
        db.planQueries.selectInstallmentItems(planId).executeAsList()

    /** 新建一笔分期，并按期数平分生成每一期。 */
    fun createInstallment(
        name: String,
        money: Long,
        count: Int = 12,
        interest: Long = 0,
    ): Installment_plan {
        val now = currentTimeMillis()
        val periods = count.coerceIn(1, 360)
        val row = Installment_plan(
            id = newId("i"),
            member_id = LOCAL_MEMBER_ID,
            name = name.ifBlank { "消费分期" },
            deduction_asset_id = DEFAULT_ASSET_ID,
            start_date = now,
            money = money,
            total_count = periods.toLong(),
            executed_count = 0,
            interest = interest,
            interest_type = 0,
            remainder_process_type = 0,
            remaining_money = money,
            remaining_interest = interest,
            next_execute_date = now,
            book_id = DEFAULT_BOOK_ID,
            member_cate_id = 9,
            not_in_budget = 0,
            desc = "",
            images = "",
            tag_ids = "",
            status = INSTALLMENT_ACTIVE,
            create_time = now,
            update_time = now,
        )
        db.planQueries.upsert(row)
        val perPeriod = money / periods
        val perInterest = interest / periods
        repeat(periods) { index ->
            db.planQueries.upsertInstallmentItem(
                Installment_item(
                    id = "${row.id}_${index + 1}",
                    plan_id = row.id,
                    num = (index + 1).toLong(),
                    money = perPeriod,
                    interest = perInterest,
                    status = 0,
                    execute_date = 0,
                ),
            )
        }
        return row
    }

    /** 还清：状态改为已结清，剩余金额与利息归零。 */
    fun payOff(id: String): Installment_plan? {
        val old = installment(id) ?: return null
        val row = old.copy(
            status = INSTALLMENT_PAID_OFF,
            remaining_money = 0,
            remaining_interest = 0,
            executed_count = old.total_count,
            update_time = currentTimeMillis(),
        )
        db.planQueries.upsert(row)
        return row
    }

    /** 删除分期计划与其每一期。已入账的账单保留。 */
    fun deleteInstallment(id: String) {
        db.planQueries.deleteInstallmentItems(id)
        db.planQueries.deletePlan(id)
    }

    fun savePlans(): List<Save_plan> = db.planQueries.selectSavePlans().executeAsList()

    fun savePlan(id: String): Save_plan? = db.planQueries.selectSavePlan(id).executeAsOneOrNull()

    fun saveItems(planId: String): List<Save_plan_item> =
        db.planQueries.selectSaveItems(planId).executeAsList()

    /** 新建或修改存钱计划。修改不存在的计划返回 null。 */
    fun saveSavePlan(
        name: String,
        targetMoney: Long,
        typeId: Long = 1,
        icon: String = "wallet",
        id: String = "",
    ): Save_plan? {
        val now = currentTimeMillis()
        val old = if (id.isEmpty()) null else savePlan(id) ?: return null
        val row = Save_plan(
            id = old?.id ?: newId("s"),
            name = name.ifBlank { "存钱计划" },
            icon = icon,
            type_id = typeId,
            target_money = targetMoney,
            saved_money = old?.saved_money ?: 0,
            status = SAVE_PLAN_ACTIVE,
            archived = old?.archived ?: 0,
            desc = old?.desc ?: "",
            create_time = old?.create_time ?: now,
            update_time = now,
        )
        db.planQueries.upsertSavePlan(row)
        return row
    }

    /** 归档：不再出现在进行中列表。 */
    fun archiveSavePlan(id: String): Save_plan? {
        val old = savePlan(id) ?: return null
        val row = old.copy(archived = 1, update_time = currentTimeMillis())
        db.planQueries.upsertSavePlan(row)
        return row
    }

    fun deleteSavePlan(id: String) {
        db.planQueries.deleteSaveItemsOf(id)
        db.planQueries.deleteSavePlan(id)
    }

    /** 记一笔存入，并把金额累加到计划的已存金额。 */
    fun addSaveItem(planId: String, money: Long, remark: String = ""): Save_plan_item? {
        val plan = savePlan(planId) ?: return null
        val now = currentTimeMillis()
        val next = (saveItems(planId).maxOfOrNull { it.num } ?: 0L) + 1
        val item = Save_plan_item(
            id = newId("si"),
            plan_id = planId,
            num = next,
            money = money,
            status = 1,
            remark = remark,
            create_time = now,
            update_time = now,
        )
        db.planQueries.upsertSaveItem(item)
        db.planQueries.upsertSavePlan(plan.copy(saved_money = plan.saved_money + money, update_time = now))
        return item
    }

    private fun newId(prefix: String): String = "$prefix${currentTimeMillis()}${Random.nextInt(1000, 9999)}"
}

private fun currentTimeMillis(): Long = System.currentTimeMillis()
