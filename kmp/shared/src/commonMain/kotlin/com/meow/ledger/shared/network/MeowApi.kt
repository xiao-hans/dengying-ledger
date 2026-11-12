package com.meow.ledger.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 一次业务请求的描述。
 *
 * 字段名与 `spec/endpoints.json` 的 Retrofit `@Field` / `@Body` 声明一致。
 * `method` 必须与 `EndpointCatalog` 一致；`urlType` 对应样本 `@Headers({"urlType:..."})`。
 */
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

/** 非 2xx 的 HTTP 响应：携带真实 HTTP 状态码，供 [SyncEngine] 区分可重试与永久拒绝。 */
class HttpStatusCodeException(
    val path: String,
    val statusCode: Int,
) : IllegalStateException("$path returned HTTP $statusCode")

/**
 * Ktor 实现：统一 host、超时、明文 JSON 与 `{code,message,data,trace_id}` 信封。
 *
 * ## 请求体契约（对齐 AGENTS.md §2 / 04 §5）
 *
 * 自建后端使用**明文 JSON**，不复现样本 so 里的 `data=<native 加密串>` 私有加密。
 * 一个端点在 `api/a.java` 里要么用 `@FieldMap`/`@Field`，要么用 `@Body`，二者不混用：
 *
 * - **`@Field` 端点**：[EndpointRequest.fields] 逐字段放到一个 JSON object 里作为请求体；
 *   `key` 就是样本字段名，值是字符串。
 * - **`@Body` 端点**：[EndpointRequest.body] 整个作为请求体发送，不额外包一层、不与
 *   `fields` 合并。`Bills/ImportBills` 两个重载正是靠 body 内的字段区分。
 * - **GET 端点**：`fields` 只作为 URL query 参数发送，**不发送请求体**。
 *
 * 若某端点同时给了 `fields` 与 `body`（样本里不存在，属于调用错误），直接抛
 * [IllegalArgumentException]，避免悄悄拼出不符合契约的形状。
 *
 * ## 后端 host 与隐私门禁
 *
 * [baseUrlProvider] 每次请求解析 base URL（默认 [SpecBaseUrlProvider]，仅供测试 /
 * 契约自检）。生产装配注入 [SettingsBaseUrlProvider]：只读用户配置的 `sync_base_url`
 * 等自建后端键，返回 `null` 表示同步禁用，此时 `call` 直接抛异常而不发请求
 * （`SyncEngine` 更进一步：未启用时连 flush 都不发）。**绝不把样本原 App 的
 * 第三方生产域名当生产默认值**，账单/设备号/令牌不会发到那里。
 * `Config/GetV2` 用配置域（或回退主域），其余用主域。
 *
 * ## 通用请求头
 *
 * [headerProvider] 在每次请求时求值并注入 `Authorization`（未登录则省略）、
 * `X-Device-Id`、`X-App-Version`、`X-Client-Time`。请求自带的 `headers` 后写，
 * 可覆盖同名头。**仅用于宿主后端业务请求**；两个动态 `@Url` 方法面对的是
 * OSS/CDN 预签名绝对地址，**不注入这些 app 头**（多余头会破坏签名校验），
 * 由调用方按需传 [EndpointRequest.headers] 等价的可选 `headers` 参数。
 *
 * ## 响应
 *
 * 非 2xx → 抛 [HttpStatusCodeException]（`IllegalStateException` 子类，携带 HTTP 状态码）；
 * `code != 0` → [Envelope.unwrap] 抛 [ApiException]；信封解析失败 →
 * [IllegalArgumentException]。所有异常留给 [SyncEngine.flush] 分类处理（退避重试或终态丢弃），
 * 绝不会阻塞已经落库的 UI 写操作。
 */
class KtorMeowApi(
    private val client: HttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val headerProvider: HeaderProvider = EmptyHeaderProvider,
    private val baseUrlProvider: BaseUrlProvider = SpecBaseUrlProvider,
) : MeowApi {

    override suspend fun call(request: EndpointRequest): JsonEnvelope {
        val isConfig = HttpConfig.isConfigRequest(request.path, request.urlType)
        val base = baseUrlProvider.base(isConfig)
            ?: throw IllegalStateException("remote sync disabled: no sync_base_url configured (${request.path})")
        val url = HttpConfig.compose(base, request.path)
        val response = client.request(url) {
            method = HttpMethod.parse(request.method)
            headerProvider.headers().forEach { (key, value) -> headers.append(key, value) }
            request.headers.forEach { (key, value) -> headers.append(key, value) }
            if (method == HttpMethod.Get) {
                // GET：字段进 query，无请求体、也不带 Content-Type。
                url { request.fields.forEach { (key, value) -> parameters.append(key, value) } }
            } else {
                // 直接发序列化后的明文 JSON 字符串，不依赖 ContentNegotiation 的反射。
                setBody(TextContent(json.encodeToString(requestBody(request)), ContentType.Application.Json))
            }
        }
        return decodeEnvelope(response, request.path)
    }

    /**
     * `@Streaming @GET @Url`：用 `prepareRequest(...).execute` 真正流式读取绝对 URL
     * 的响应体，按 8 KiB 分块逐块交给 [sink]，**不把整个文件缓冲进内存**。
     * 100 KiB 以上的文件会产生多个 chunk。失败（含非 2xx）直接抛出，由调用方决定重试。
     *
     * 这个 URL 通常是 OSS/CDN 预签名地址（第三方 host）：**不注入 app 通用头**
     * （`Authorization` / `X-Device-Id` / `X-App-Version` / `X-Client-Time`），
     * 多余头会破坏签名校验。调用方需要携带的签名/自定义头通过 [headers] 显式传入。
     */
    suspend fun download(
        url: String,
        headers: Map<String, String> = emptyMap(),
        sink: (ByteArray) -> Unit,
    ) {
        val requestHeaders = headers
        client.prepareRequest(url) {
            method = HttpMethod.Get
            requestHeaders.forEach { (key, value) -> this.headers.append(key, value) }
        }.execute { response ->
            require2xx(response, DynamicEndpoints.DOWNLOAD)
            streamToSink(response.bodyAsChannel(), sink)
        }
    }

    /** 逐块读，直到 channel 关闭；单个 chunk 最多 [DOWNLOAD_CHUNK_BYTES]。 */
    private suspend fun streamToSink(channel: ByteReadChannel, sink: (ByteArray) -> Unit) {
        val buffer = ByteArray(DOWNLOAD_CHUNK_BYTES)
        while (true) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read <= 0) break
            sink(buffer.copyOf(read))
        }
    }

    /**
     * `@POST @Url + MultipartBody`：OSS 直传。`fields` 作为表单字段，`bytes` 作为文件
     * part（part 名 `file`，文件名 [fileName]，类型 [contentType]）。
     * 返回 `UploadOssData`，即信封里的 `data`；`code != 0` 抛 [ApiException]。
     *
     * 与 [download] 同理：预签名第三方地址**不注入 app 通用头**，需要额外头时走 [headers]。
     */
    suspend fun uploadOss(
        url: String,
        fields: Map<String, String>,
        fileName: String,
        bytes: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ): JsonElement? {
        val requestHeaders = headers
        val response = client.submitFormWithBinaryData(
            url = url,
            formData = formData {
                fields.forEach { (key, value) -> append(key, value) }
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            },
        ) {
            requestHeaders.forEach { (key, value) -> this.headers.append(key, value) }
        }
        require2xx(response, DynamicEndpoints.UPLOAD_OSS)
        return decodeEnvelope(response, DynamicEndpoints.UPLOAD_OSS)
            .unwrap(DynamicEndpoints.UPLOAD_OSS)
    }

    /** POST 请求体：fields 与 body 互斥，形状显式、不合并。 */
    private fun requestBody(request: EndpointRequest): JsonElement {
        if (request.fields.isNotEmpty() && request.body != null) {
            throw IllegalArgumentException("${request.path}: @Field 与 @Body 不能同时出现在一次请求里")
        }
        if (request.body != null) return request.body
        return buildJsonObject {
            request.fields.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
        }
    }

    private suspend fun decodeEnvelope(response: HttpResponse, path: String): JsonEnvelope {
        require2xx(response, path)
        val text = response.bodyAsText()
        val envelope = try {
            json.decodeFromString<JsonEnvelope>(text)
        } catch (error: Exception) {
            throw IllegalArgumentException("$path returned invalid envelope: ${text.take(160)}", error)
        }
        envelope.unwrap(path)
        return envelope
    }

    private fun require2xx(response: HttpResponse, path: String) {
        if (!response.status.isSuccess()) {
            throw HttpStatusCodeException(path, response.status.value)
        }
    }

    companion object {
        /** 流式下载的分块大小；大于它的文件必然产生多个 chunk。 */
        const val DOWNLOAD_CHUNK_BYTES = 8 * 1024

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
        api.call(EndpointRequest("SavePlan/GetSavePlans", method = "GET"))

    suspend fun getSavePlansByQuery(fields: Map<String, String>) =
        api.call(EndpointRequest("SavePlan/GetSavePlansByQuery", fields = fields))

    suspend fun getSavePlansByType(type: String) =
        api.call(EndpointRequest("SavePlan/GetSavePlansByType", fields = mapOf("type" to type)))

    suspend fun getFileSavePlans() =
        api.call(EndpointRequest("SavePlan/GetFileSavePlans", method = "GET"))

    suspend fun wishes() =
        api.call(EndpointRequest("SavePlan/Wishes", method = "GET"))

    suspend fun getIcon() =
        api.call(EndpointRequest("SavePlan/GetIcon", method = "GET"))

    suspend fun getSavePlanType() =
        api.call(EndpointRequest("SavePlan/GetSavePlanType", method = "GET"))
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


/** Account 10 个契约：手机验证码、三方绑定与设备登录。 */
class AccountContracts(private val api: MeowApi) {
    suspend fun sendCode(phone: String) =
        api.call(EndpointRequest("Account/SendCode", fields = mapOf("phone" to phone)))

    suspend fun loginByPhone(phone: String, code: String) = api.call(
        EndpointRequest("Account/LoginByPhone", fields = mapOf("phone" to phone, "code" to code)),
    )

    suspend fun loginByDevice() = api.call(EndpointRequest("Account/LoginByDevice"))

    suspend fun activateV3(distinctId: String) =
        api.call(EndpointRequest("Account/ActivateV3", fields = mapOf("distinctId" to distinctId)))

    suspend fun bindPhone(phone: String, code: String, oldPhone: String) = api.call(
        EndpointRequest(
            "Account/BindPhone",
            fields = mapOf("phone" to phone, "code" to code, "oldPhone" to oldPhone),
        ),
    )

    suspend fun unbind() = api.call(EndpointRequest("Account/Unbind"))

    suspend fun bindQQ(fields: Map<String, String>) =
        api.call(EndpointRequest("Account/BindQQ", fields = fields))

    suspend fun bindWechat(fields: Map<String, String>) =
        api.call(EndpointRequest("Account/BindWechat", fields = fields))

    suspend fun loginByQQ(fields: Map<String, String>) =
        api.call(EndpointRequest("Account/LoginByQQ", fields = fields))

    suspend fun loginByWechat(fields: Map<String, String>) =
        api.call(EndpointRequest("Account/LoginByWechat", fields = fields))
}

/** Member 10 个契约：资料、设置、消息推送提醒。 */
class MemberContracts(private val api: MeowApi) {
    suspend fun getInfo() = api.call(EndpointRequest("Member/GetInfo"))

    suspend fun getContractInfo() = api.call(EndpointRequest("Member/GetMemberContractInfo", method = "GET"))

    suspend fun getSetting() = api.call(EndpointRequest("Member/GetSetting"))

    suspend fun updateSetting(key: String, value: String) = api.call(
        EndpointRequest("Member/UpdateSetting", fields = mapOf("key" to key, "value" to value)),
    )

    suspend fun updateNickname(nickname: String) =
        api.call(EndpointRequest("Member/UpdateNickname", fields = mapOf("nickname" to nickname)))

    suspend fun refreshToken(gtCid: String) =
        api.call(EndpointRequest("Member/RefreshToken", fields = mapOf("gtCid" to gtCid)))

    suspend fun createReminder(fields: Map<String, String>) =
        api.call(EndpointRequest("Member/CreateReminder", fields = fields))

    suspend fun updateReminder(fields: Map<String, String>) =
        api.call(EndpointRequest("Member/UpdateReminder", fields = fields))

    suspend fun reminderList() = api.call(EndpointRequest("Member/MemberReminderList"))

    suspend fun initSSOldUserInfo() = api.call(EndpointRequest("Member/InitSSOldUserInfo"))
}

/** Order 5 个契约：VIP 下单与支付状态回查。 */
class OrderContracts(private val api: MeowApi) {
    suspend fun createOrder(fields: Map<String, String>) =
        api.call(EndpointRequest("Order/v2/CreateOrder", fields = fields))

    suspend fun vipConfigs() = api.call(EndpointRequest("Order/v2/VipConfigs"))

    suspend fun checkStatus(orderId: String) =
        api.call(EndpointRequest("Order/CheckStatus", fields = mapOf("orderId" to orderId)))

    suspend fun rechargeDiscountConfig() =
        api.call(EndpointRequest("Order/GetRechargeDiscountConfig"))

    suspend fun alipayUnSign() = api.call(EndpointRequest("Order/AlipayUnSign"))
}

/** VipGroupBuy 3 个契约：拼团展示码、列表与绑定。 */
class VipGroupBuyContracts(private val api: MeowApi) {
    suspend fun list() = api.call(EndpointRequest("VipGroupBuy/List", method = "GET"))

    suspend fun bind() = api.call(EndpointRequest("VipGroupBuy/Bind"))

    suspend fun getByShowId(showId: String) =
        api.call(EndpointRequest("VipGroupBuy/GetByShowId", fields = mapOf("showId" to showId)))
}

/** S7 平台契约 5 个：运营弹窗、更新提醒、埋点、远程配置与个人中心图标。 */
class PlatformContracts(private val api: MeowApi) {
    suspend fun getDialog() = api.call(EndpointRequest("Operation/GetDialog"))

    suspend fun getUpdateRemind() = api.call(EndpointRequest("Operation/GetUpdateRemind"))

    suspend fun behaviorAdd() = api.call(EndpointRequest("Behavior/Add"))

    /** 样本带 `@Headers({"urlType:configManage"})`，显式传 urlType，不靠路径兜底。 */
    suspend fun configGetV2(fields: Map<String, String>) =
        api.call(EndpointRequest("Config/GetV2", fields = fields, urlType = "configManage"))

    suspend fun personCenterIcons() =
        api.call(EndpointRequest("PersonCenter/GetPersonCenterIcons", method = "GET"))
}
