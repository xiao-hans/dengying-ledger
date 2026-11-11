package com.meow.ledger.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class EndpointRequest(
    val path: String,
    val method: String = "POST",
    val fields: Map<String, String> = emptyMap(),
    val body: JsonElement? = null,
    val urlType: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

interface MeowApi {
    suspend fun call(request: EndpointRequest): JsonEnvelope
}

/** Ktor 实现：统一 host、超时、明文 JSON 与 {code,message,data,trace_id} 信封。 */
class KtorMeowApi(
    private val client: HttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : MeowApi {
    override suspend fun call(request: EndpointRequest): JsonEnvelope {
        val url = HttpConfig.baseFor(request.path, request.urlType) + request.path
        val response = client.request(url) {
            method = HttpMethod.parse(request.method)
            contentType(ContentType.Application.Json)
            request.headers.forEach { (key, value) -> headers.append(key, value) }
            val payload = buildJsonObject {
                request.fields.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
                if (request.body is JsonObject) {
                    request.body.forEach { (key, value) -> put(key, value) }
                } else if (request.body != null) {
                    put("data", request.body)
                }
            }
            setBody(payload)
        }
        val text = response.bodyAsText()
        return try {
            json.decodeFromString<JsonEnvelope>(text)
        } catch (error: Exception) {
            throw IllegalArgumentException("${request.path} returned invalid envelope: ${text.take(160)}", error)
        }
    }

    companion object {
        fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                connectTimeoutMillis = HttpConfig.CONNECT_TIMEOUT_MS
                requestTimeoutMillis = HttpConfig.REQUEST_TIMEOUT_MS
                socketTimeoutMillis = HttpConfig.SOCKET_TIMEOUT_MS
            }
        }
    }
}

/** 32 个 S2 契约方法以同名扩展封装，避免手写路径和字段名漂移。 */
class BillsContracts(private val api: MeowApi) {
    suspend fun pageList(updateDate: String, pageNum: String) =
        api.call(EndpointRequest("Bills/PageList", fields = mapOf("updateDate" to updateDate, "pageNum" to pageNum)))

    suspend fun create(body: JsonElement) = api.call(EndpointRequest("Bills/Create", body = body))

    suspend fun update(body: JsonElement) = api.call(EndpointRequest("Bills/Update", body = body))

    suspend fun delete(body: JsonElement) = api.call(EndpointRequest("Bills/Delete", body = body))

    suspend fun billsCountOfHasPic() = api.call(EndpointRequest("Bills/BillsCountOfHasPic"))

    suspend fun autoBillsCallBack(type: String) =
        api.call(EndpointRequest("Bills/AutoBillsCallBack", fields = mapOf("type" to type)))

    suspend fun deleteFixedBill(id: String) =
        api.call(EndpointRequest("Bills/DeleteFixedBill", fields = mapOf("id" to id)))

    suspend fun updateFixedBill(fields: Map<String, String>) =
        api.call(EndpointRequest("Bills/UpdateFixedBill", fields = fields))

    suspend fun getFixedBills() = api.call(EndpointRequest("Bills/GetFixedBills"))

    suspend fun createFixedBill(fields: Map<String, String>) =
        api.call(EndpointRequest("Bills/CreateFixedBill", fields = fields))

    suspend fun importBills(req: JsonElement) = api.call(EndpointRequest("Bills/ImportBills", body = req))

    suspend fun importOcrBills(req: JsonElement) = api.call(EndpointRequest("Bills/ImportBills", body = req))
}

class CateContracts(private val api: MeowApi) {
    suspend fun create(title: String, type: String, iconId: String) = api.call(
        EndpointRequest("Cate/Create", fields = mapOf("title" to title, "type" to type, "iconId" to iconId)),
    )

    suspend fun update(fields: Map<String, String>) = api.call(EndpointRequest("Cate/Update", fields = fields))

    suspend fun delete(id: String) = api.call(EndpointRequest("Cate/Delete", fields = mapOf("id" to id)))

    suspend fun getMainCates() = api.call(EndpointRequest("Cate/GetMainCates"))

    suspend fun getCateIcons() = api.call(EndpointRequest("Cate/GetCateIcons"))

    suspend fun updateExtend(body: JsonElement) = api.call(EndpointRequest("Cate/UpdateExtend", body = body))
}

class MemberTagContracts(private val api: MeowApi) {
    suspend fun create(billsBookId: String, name: String) = api.call(
        EndpointRequest("MemberTag/Create", fields = mapOf("BillBookId" to billsBookId, "name" to name)),
    )

    suspend fun update(tagId: String, name: String) = api.call(
        EndpointRequest("MemberTag/Update", fields = mapOf("tagId" to tagId, "name" to name)),
    )

    suspend fun delete(tagId: String) =
        api.call(EndpointRequest("MemberTag/Delete", fields = mapOf("tagId" to tagId)))

    suspend fun updateSort(body: JsonElement) = api.call(EndpointRequest("MemberTag/UpdateSort", body = body))
}

class BillsBookContracts(private val api: MeowApi) {
    suspend fun create(name: String, iconId: String, monthStartDate: String) = api.call(
        EndpointRequest(
            "BillsBook/Create",
            fields = mapOf("name" to name, "iconId" to iconId, "monthStartDate" to monthStartDate),
        ),
    )

    suspend fun update(fields: Map<String, String>) = api.call(EndpointRequest("BillsBook/Update", fields = fields))

    suspend fun delete(billsBookId: String) =
        api.call(EndpointRequest("BillsBook/Delete", fields = mapOf("billsBookId" to billsBookId)))

    suspend fun getList() = api.call(EndpointRequest("BillsBook/GetList"))

    suspend fun getBillsBookIcons() = api.call(EndpointRequest("BillsBook/GetBillsBookIcons"))

    suspend fun join(billsBookId: String) =
        api.call(EndpointRequest("BillsBook/Join", fields = mapOf("billsBookId" to billsBookId)))

    suspend fun getInfoByCode(code: String) =
        api.call(EndpointRequest("BillsBook/GetInfoByCode", fields = mapOf("code" to code)))

    suspend fun createInviteCode(billsBookId: String) =
        api.call(EndpointRequest("BillsBook/CreateInviteCode", fields = mapOf("billsBookId" to billsBookId)))

    suspend fun removeMember(memberId: String, billsBookId: String) = api.call(
        EndpointRequest(
            "BillsBook/RemoveMember",
            fields = mapOf("memberId" to memberId, "billsBookId" to billsBookId),
        ),
    )

    suspend fun exit(billsBookId: String) =
        api.call(EndpointRequest("BillsBook/Exit", fields = mapOf("billsBookId" to billsBookId)))
}

/** Budget 9 个契约方法（字段名与 EndpointCatalog 一致）。 */
class BudgetContracts(private val api: MeowApi) {
    suspend fun createBudget(fields: Map<String, String>) =
        api.call(EndpointRequest("Budget/CreateBudget", fields = fields))

    suspend fun getYearlyBudgets(year: String) =
        api.call(EndpointRequest("Budget/GetYearlyBudgets", fields = mapOf("year" to year)))

    suspend fun getBudget(billsBookId: String, day: String) = api.call(
        EndpointRequest("Budget/GetBudget", fields = mapOf("billsBookId" to billsBookId, "day" to day)),
    )

    suspend fun deleteBudget(billsBookId: String, day: String) = api.call(
        EndpointRequest("Budget/DeleteBudget", fields = mapOf("billsBookId" to billsBookId, "day" to day)),
    )

    suspend fun deleteBudgetCate(billsBookId: String, day: String, cateId: String) = api.call(
        EndpointRequest(
            "Budget/DeleteBudgetCate",
            fields = mapOf("billsBookId" to billsBookId, "day" to day, "cateId" to cateId),
        ),
    )

    suspend fun updateBudgetCate(billsBookId: String, day: String, cateId: String, money: String) = api.call(
        EndpointRequest(
            "Budget/UpdateBudgetCate",
            fields = mapOf("billsBookId" to billsBookId, "day" to day, "cateId" to cateId, "money" to money),
        ),
    )

    suspend fun updateBudget(fields: Map<String, String>) =
        api.call(EndpointRequest("Budget/UpdateBudget", fields = fields))

    suspend fun createBudgetCate(billsBookId: String, day: String, cateId: String, money: String) = api.call(
        EndpointRequest(
            "Budget/CreateBudgetCate",
            fields = mapOf("billsBookId" to billsBookId, "day" to day, "cateId" to cateId, "money" to money),
        ),
    )

    suspend fun getBudgetList() = api.call(EndpointRequest("Budget/GetBudgetList", method = "GET"))
}

/** KeyWordMatchingRule 4 个契约：匹配规则的云端 CRUD。 */
class RuleContracts(private val api: MeowApi) {
    suspend fun create(fields: Map<String, String>) =
        api.call(EndpointRequest("KeyWordMatchingRule/Create", fields = fields))

    suspend fun list() = api.call(EndpointRequest("KeyWordMatchingRule/List"))

    suspend fun update(fields: Map<String, String>) =
        api.call(EndpointRequest("KeyWordMatchingRule/Update", fields = fields))

    suspend fun delete(id: String) =
        api.call(EndpointRequest("KeyWordMatchingRule/Delete", fields = mapOf("id" to id)))
}

/** MemberImportBillFile 2 个契约：导入文件记录的云端列表与删除。 */
class ImportFileContracts(private val api: MeowApi) {
    suspend fun list() = api.call(EndpointRequest("MemberImportBillFile/List"))

    suspend fun delete(id: String) =
        api.call(EndpointRequest("MemberImportBillFile/Delete", fields = mapOf("id" to id)))
}

/**
 * Upload 3 个契约：票据图与账单文件的直传。
 * multipart 契约按样本口径传文件体，本地仓库没有云端时仅登记调用意图。
 */
class UploadContracts(private val api: MeowApi) {
    suspend fun uploadImages(fileName: String) =
        api.call(EndpointRequest("Upload/UploadImages", fields = mapOf("fileName" to fileName)))

    suspend fun uploadImg(imgType: String) =
        api.call(EndpointRequest("Upload/UploadImg", fields = mapOf("imgType" to imgType)))

    suspend fun uploadBills(fileName: String) =
        api.call(EndpointRequest("Upload/UploadBills", fields = mapOf("fileName" to fileName)))
}

/** FundAccount 6 个契约：资产账户的增删改查与欠款单（字段名与 EndpointCatalog 一致）。 */
class FundAccountContracts(private val api: MeowApi) {
    suspend fun create(fields: Map<String, String>) =
        api.call(EndpointRequest("FundAccount/Create", fields = fields))

    suspend fun update(fields: Map<String, String>) =
        api.call(EndpointRequest("FundAccount/Update", fields = fields))

    suspend fun delete(id: String, isSaveBill: String) = api.call(
        EndpointRequest("FundAccount/Delete", fields = mapOf("id" to id, "isSaveBill" to isSaveBill)),
    )

    suspend fun updateStatus(id: String, status: String) = api.call(
        EndpointRequest("FundAccount/UpdateStatus", fields = mapOf("id" to id, "status" to status)),
    )

    suspend fun getIOU(status: String) =
        api.call(EndpointRequest("FundAccount/GetIOU", fields = mapOf("status" to status)))

    suspend fun getFundAccountTypes() =
        api.call(EndpointRequest("FundAccount/GetFundAccountTypes"))
}

/** SavePlan 14 个契约：存钱计划与逐期存入（字段名与 EndpointCatalog 一致，混淆 @Body 的明细字段由仓库自定）。 */
class SavePlanContracts(private val api: MeowApi) {
    suspend fun createSavePlan(fields: Map<String, String>) =
        api.call(EndpointRequest("SavePlan/CreateSavePlan", fields = fields))

    suspend fun updateSavePlan(fields: Map<String, String>) =
        api.call(EndpointRequest("SavePlan/UpdateSavePlan", fields = fields))

    suspend fun updateSavePlanItem(fields: Map<String, String>) =
        api.call(EndpointRequest("SavePlan/UpdateSavePlanItem", fields = fields))

    suspend fun updateSavePlanItemStatus(fields: Map<String, String>) =
        api.call(EndpointRequest("SavePlan/UpdateSavePlanItemStatus", fields = fields))

    suspend fun updateSavePlanStatus(fields: Map<String, String>) =
        api.call(EndpointRequest("SavePlan/UpdateSavePlanStatus", fields = fields))

    suspend fun createSavePlanItem(fields: Map<String, String>) =
        api.call(EndpointRequest("SavePlan/CreateSavePlanItem", fields = fields))

    suspend fun savePlanItemsById(fields: Map<String, String>) =
        api.call(EndpointRequest("SavePlan/SavePlanItemsById", fields = fields))

    suspend fun getSavePlans() =
        api.call(EndpointRequest("SavePlan/GetSavePlans"))

    suspend fun getSavePlansByQuery(fields: Map<String, String>) =
        api.call(EndpointRequest("SavePlan/GetSavePlansByQuery", fields = fields))

    suspend fun getSavePlansByType(type: String) =
        api.call(EndpointRequest("SavePlan/GetSavePlansByType", fields = mapOf("type" to type)))

    suspend fun getFileSavePlans() =
        api.call(EndpointRequest("SavePlan/GetFileSavePlans"))

    suspend fun wishes() =
        api.call(EndpointRequest("SavePlan/Wishes"))

    suspend fun getIcon() =
        api.call(EndpointRequest("SavePlan/GetIcon"))

    suspend fun getSavePlanType() =
        api.call(EndpointRequest("SavePlan/GetSavePlanType"))
}

/** ConsumptionInstallment 6 个契约：分期计划的创建、查询、结清与删除。 */
class InstallmentContracts(private val api: MeowApi) {
    suspend fun create(fields: Map<String, String>) =
        api.call(EndpointRequest("ConsumptionInstallment/Create", fields = fields))

    suspend fun allPlan() =
        api.call(EndpointRequest("ConsumptionInstallment/AllPlan"))

    suspend fun getByPlanId(planId: String) =
        api.call(EndpointRequest("ConsumptionInstallment/GetByPlanId", fields = mapOf("planId" to planId)))

    suspend fun getByClientId(clientId: String) =
        api.call(EndpointRequest("ConsumptionInstallment/GetByClientId", fields = mapOf("clientId" to clientId)))

    suspend fun payOff(fields: Map<String, String>) =
        api.call(EndpointRequest("ConsumptionInstallment/PayOff", fields = fields))

    suspend fun delete(planId: String, isSaveBill: String) = api.call(
        EndpointRequest(
            "ConsumptionInstallment/Delete",
            fields = mapOf("planId" to planId, "isSaveBill" to isSaveBill),
        ),
    )
}

/** BillRefund 3 个契约：退款记录的建立、更新与删除。 */
class RefundContracts(private val api: MeowApi) {
    suspend fun create(fields: Map<String, String>) =
        api.call(EndpointRequest("BillRefund/Create", fields = fields))

    suspend fun update(fields: Map<String, String>) =
        api.call(EndpointRequest("BillRefund/Update", fields = fields))

    suspend fun delete(fields: Map<String, String>) =
        api.call(EndpointRequest("BillRefund/Delete", fields = fields))
}
