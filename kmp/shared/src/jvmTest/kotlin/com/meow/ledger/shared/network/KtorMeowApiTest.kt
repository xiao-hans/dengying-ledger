package com.meow.ledger.shared.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 用 ktor-client-mock 验证真实 HTTP 形状：方法、host、path、请求头、GET query、
 * POST 明文 JSON 形状、信封解包、HTTP 500 与两个动态 @Url 方法。
 */
class KtorMeowApiTest {

    private val okEnvelope = """{"code":0,"message":"","data":{"value":1},"trace_id":"t-1"}"""

    private class Harness(
        val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        val headers: HeaderProvider = testHeaders(),
        val baseUrlProvider: BaseUrlProvider = SpecBaseUrlProvider,
    ) {
        val requests = mutableListOf<HttpRequestData>()
        val api: KtorMeowApi

        init {
            val engine = MockEngine { request ->
                requests += request
                handler(request)
            }
            api = KtorMeowApi(
                client = io.ktor.client.HttpClient(engine),
                headerProvider = headers,
                baseUrlProvider = baseUrlProvider,
            )
        }
    }

    companion object {
        fun testHeaders(): HeaderProvider = DefaultHeaderProvider(
            token = { "tk-123" },
            deviceId = { "device-9" },
            appVersion = { "1.8.8" },
            clock = { 1_700_000_000_000L },
        )
    }

    private fun respondOk(text: String = okEnvelope): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respond(text, headers = headersOf(HttpHeaders.ContentType, "application/json")) }

    /** 表驱动：取一组代表性端点（含 9 个 GET 全部）、@Field、@Body、Config 域逐条核对。 */
    @Test
    fun `representative requests match method host path headers and body`() {
        val harness = Harness(respondOk())
        val cases = listOf(
            // GET 9 条：字段进 query、无请求体
            EndpointRequest("Budget/GetBudgetList", method = "GET", fields = mapOf("page" to "2")),
            EndpointRequest("VipGroupBuy/List", method = "GET"),
            EndpointRequest("SavePlan/Wishes", method = "GET"),
            EndpointRequest("SavePlan/GetFileSavePlans", method = "GET"),
            EndpointRequest("Member/GetMemberContractInfo", method = "GET"),
            EndpointRequest("SavePlan/GetSavePlans", method = "GET"),
            EndpointRequest("SavePlan/GetIcon", method = "GET"),
            EndpointRequest("SavePlan/GetSavePlanType", method = "GET"),
            EndpointRequest("PersonCenter/GetPersonCenterIcons", method = "GET"),
            // POST @Field 端点：fields 是顶层 JSON object
            EndpointRequest("Bills/PageList", fields = mapOf("updateDate" to "2026-11-11", "pageNum" to "1")),
            EndpointRequest("Cate/Create", fields = mapOf("title" to "午餐", "type" to "1", "iconId" to "i")),
            // POST @Body 端点：body 整个作为请求体
            EndpointRequest("Bills/Create", body = buildJsonObject { put("items", "x") }),
            EndpointRequest("Bills/ImportBills", body = buildJsonObject { put("ocrText", "x") }),
            // 无字段无体：空 JSON object
            EndpointRequest("Account/LoginByDevice"),
            // 配置域：显式 urlType
            EndpointRequest("Config/GetV2", fields = mapOf("appVersion" to "1.8.8"), urlType = "configManage"),
        )

        runBlocking {
            cases.forEach { request -> harness.api.call(request) }
        }
        assertEquals(cases.size, harness.requests.size)
        val catalog = EndpointCatalog.associate { it.path to it.method }

        cases.forEachIndexed { index, request ->
            val sent = harness.requests[index]
            val expectedBase = if (request.path == "Config/GetV2") HttpConfig.CONFIG_BASE else HttpConfig.MAIN_BASE
            val expectedMethod = HttpMethod.parse(catalog.getValue(request.path))
            assertEquals(expectedMethod, sent.method, "method for ${request.path}")
            assertTrue(sent.url.toString().startsWith(expectedBase + request.path), "url prefix for ${request.path}")
            // 通用请求头逐个在场
            assertEquals("Bearer tk-123", sent.headers[ApiHeaderNames.AUTHORIZATION])
            assertEquals("device-9", sent.headers[ApiHeaderNames.DEVICE_ID])
            assertEquals("1.8.8", sent.headers[ApiHeaderNames.APP_VERSION])
            assertEquals("1700000000000", sent.headers[ApiHeaderNames.CLIENT_TIME])

            val bodyText = (sent.body as? io.ktor.http.content.TextContent)?.text
            val parsedBody = bodyText?.let { Json.decodeFromString<JsonObject>(it) }
            if (request.method == "GET") {
                // GET：无请求体、字段在 query 里
                assertNull(bodyText, "GET ${request.path} must not send a body")
                request.fields.forEach { (key, value) -> assertEquals(value, sent.url.parameters[key], "query $key") }
            } else {
                // POST：明文 JSON；@Field 是顶层字段，@Body 不包一层
                assertEquals("application/json", sent.body.contentType?.toString())
                assertNotNull(parsedBody, "POST ${request.path} must send JSON")
                if (request.body != null) {
                    assertEquals(request.body.toString(), parsedBody.toString(), "body passthrough for ${request.path}")
                } else {
                    request.fields.forEach { (key, value) ->
                        assertEquals(JsonPrimitive(value), parsedBody[key], "field $key for ${request.path}")
                    }
                }
            }
        }
    }

    @Test
    fun `all 112 catalog paths resolve to expected method and host`() {
        val harness = Harness(respondOk())
        runBlocking {
            EndpointCatalog.forEach { spec ->
                harness.api.call(EndpointRequest(spec.path, method = spec.method))
            }
        }
        assertEquals(112, harness.requests.size)
        EndpointCatalog.forEachIndexed { index, spec ->
            val sent = harness.requests[index]
            assertEquals(HttpMethod.parse(spec.method), sent.method, spec.path)
            val base = if (spec.path == HttpConfig.CONFIG_PATH) HttpConfig.CONFIG_BASE else HttpConfig.MAIN_BASE
            assertEquals(base + spec.path, sent.url.toString(), spec.path)
            // 每条请求都带四个通用头；GET 不发 body。
            assertEquals("Bearer tk-123", sent.headers[ApiHeaderNames.AUTHORIZATION], spec.path)
            assertEquals("device-9", sent.headers[ApiHeaderNames.DEVICE_ID], spec.path)
            assertEquals("1.8.8", sent.headers[ApiHeaderNames.APP_VERSION], spec.path)
            assertEquals("1700000000000", sent.headers[ApiHeaderNames.CLIENT_TIME], spec.path)
            if (spec.method == "GET") {
                assertNull((sent.body as? io.ktor.http.content.TextContent)?.text, "GET ${spec.path} must not send a body")
            }
        }
    }

    @Test
    fun `field and body are mutually exclusive`() {
        val harness = Harness(respondOk())
        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                harness.api.call(
                    EndpointRequest(
                        "Bills/PageList",
                        fields = mapOf("updateDate" to "x"),
                        body = buildJsonObject { put("a", "b") },
                    ),
                )
            }
        }
        assertEquals(0, harness.requests.size)
    }

    @Test
    fun `code not zero throws ApiException via unwrap`() {
        val harness = Harness(
            handler = {
                respond(
                    """{"code":42,"message":"denied","data":null,"trace_id":"t-2"}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val error = assertFailsWith<ApiException> {
            runBlocking { harness.api.call(EndpointRequest("Bills/Create")) }
        }
        assertEquals(42, error.code)
        assertEquals("denied", error.message)
        assertEquals("t-2", error.traceId)
        assertEquals("Bills/Create", error.path)
    }

    @Test
    fun `non 2xx http status throws`() {
        val harness = Harness(handler = { respondError(HttpStatusCode.InternalServerError, "boom") })
        assertFailsWith<IllegalStateException> {
            runBlocking { harness.api.call(EndpointRequest("Bills/Create")) }
        }
    }

    @Test
    fun `valid envelope is returned with data and trace id`() {
        val harness = Harness(respondOk())
        val envelope = runBlocking { harness.api.call(EndpointRequest("Bills/Create")) }
        assertEquals(0, envelope.code)
        assertEquals("t-1", envelope.traceId)
        assertNotNull(envelope.unwrap("Bills/Create"))
    }

    @Test
    fun `invalid envelope throws`() {
        val harness = Harness(handler = { respond("not json") })
        assertFailsWith<IllegalArgumentException> {
            runBlocking { harness.api.call(EndpointRequest("Bills/Create")) }
        }
    }

    @Test
    fun `missing token omits Authorization header`() {
        val harness = Harness(
            handler = respondOk(),
            headers = DefaultHeaderProvider(
                token = { "" },
                deviceId = { "device-9" },
                appVersion = { "1.8.8" },
                clock = { 1L },
            ),
        )
        runBlocking { harness.api.call(EndpointRequest("Bills/Create")) }
        assertNull(harness.requests.single().headers[ApiHeaderNames.AUTHORIZATION])
        assertNotNull(harness.requests.single().headers[ApiHeaderNames.DEVICE_ID])
    }

    @Test
    fun `dynamic download streams bytes in chunks`() {
        val content = ByteArray(20_000) { (it % 251).toByte() }
        val harness = Harness(handler = { respond(content) })
        val chunks = mutableListOf<ByteArray>()
        runBlocking { harness.api.download("https://oss.example.com/file.bin") { chunks += it } }
        assertEquals(HttpMethod.Get, harness.requests.single().method)
        assertEquals("https://oss.example.com/file.bin", harness.requests.single().url.toString())
        assertNoAppHeaders(harness.requests.single())
        assertTrue(chunks.size > 1, "expected chunked read, got ${chunks.size} chunk(s)")
        assertEquals(content.toList(), chunks.flatMap { it.toList() })
    }

    @Test
    fun `dynamic download streams a body larger than the buffer`() {
        // 100 KiB > 8 KiB buffer：必须产生多个 chunk，且不能整体缓冲成一个大数组。
        val content = ByteArray(100 * 1024) { (it % 251).toByte() }
        val harness = Harness(handler = { respond(content) })
        val chunks = mutableListOf<ByteArray>()
        runBlocking { harness.api.download("https://cdn.example.com/big.bin") { chunks += it } }
        assertTrue(chunks.size > 1, "expected multiple chunks, got ${chunks.size}")
        assertTrue(chunks.all { it.size <= 8 * 1024 }, "no chunk may exceed the 8 KiB buffer")
        assertEquals(content.size, chunks.sumOf { it.size })
        assertEquals(content.toList(), chunks.flatMap { it.toList() })
    }

    @Test
    fun `dynamic download accepts explicit caller headers only`() {
        val harness = Harness(handler = { respond(ByteArray(16)) })
        runBlocking {
            harness.api.download(
                url = "https://oss.example.com/signed",
                headers = mapOf("x-oss-signature" to "sig-1"),
            ) { }
        }
        val sent = harness.requests.single()
        assertEquals("sig-1", sent.headers["x-oss-signature"])
        assertNoAppHeaders(sent)
    }

    /** 动态 @Url 是第三方 host：Authorization / 设备号 / 版本 / 时间一个都不能带。 */
    private fun assertNoAppHeaders(sent: HttpRequestData) {
        assertNull(sent.headers[ApiHeaderNames.AUTHORIZATION])
        assertNull(sent.headers[ApiHeaderNames.DEVICE_ID])
        assertNull(sent.headers[ApiHeaderNames.APP_VERSION])
        assertNull(sent.headers[ApiHeaderNames.CLIENT_TIME])
    }

    @Test
    fun `dynamic uploadOss sends multipart with fields and returns data`() {
        val harness = Harness(
            handler = {
                respond(
                    """{"code":0,"message":"","data":{"key":"oss/a.jpg","uploaded":1},"trace_id":"t-3"}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val result = runBlocking {
            harness.api.uploadOss(
                url = "https://oss.example.com/upload",
                fields = mapOf("key" to "oss/a.jpg", "policy" to "p1"),
                fileName = "a.jpg",
                bytes = byteArrayOf(1, 2, 3),
                contentType = "image/jpeg",
            )
        }
        val sent = harness.requests.single()
        assertEquals(HttpMethod.Post, sent.method)
        assertEquals("https://oss.example.com/upload", sent.url.toString())
        assertNoAppHeaders(sent)
        val multipart = sent.body as? io.ktor.client.request.forms.MultiPartFormDataContent
        assertNotNull(multipart, "body must be multipart/form-data")
        assertTrue(
            multipart.contentType.toString().startsWith("multipart/form-data"),
            "Content-Type must be multipart",
        )
        // 直接检查 MockEngine 收到的原始 multipart 文本，而不是只做子串包含：
        // 逐 part 拆开，确保字段名、文件名与 Content-Disposition 数量都符合 RFC 7578。
        val text = runBlocking { multipart.toByteArray().decodeToString() }
        val boundary = assertNotNull(multipart.contentType.parameter("boundary"), "missing multipart boundary")
        val parts = text.split("--$boundary").filter { it.contains("Content-Disposition:") }
        fun part(named: String): String = parts.firstOrNull { it.contains("name=\"$named\"") }
            ?: error("missing part $named in:\n$text")
        fun dispositionLines(part: String): List<String> =
            part.lines().filter { it.startsWith("Content-Disposition:", ignoreCase = true) }

        // 每个字段 part 恰好一条 Content-Disposition，且带正确的 name 与值。
        listOf("key" to "oss/a.jpg", "policy" to "p1").forEach { (name, value) ->
            val part = part(name)
            assertEquals(1, dispositionLines(part).size, "$name must have exactly one Content-Disposition")
            assertTrue(dispositionLines(part).single().contains("name=\"$name\""), "missing $name disposition")
            assertTrue(part.contains(value), "missing $name value")
        }

        // file part：只能有一条 Content-Disposition，必须同时含 name="file" 与 filename="a.jpg"，
        // 不能再用 `file; filename=...` 额外拼一条（那会与 Ktor 自带的 form-data 头重复）。
        val filePart = part("file")
        val fileDispositions = dispositionLines(filePart)
        assertEquals(1, fileDispositions.size, "file part must have exactly one Content-Disposition:\n$filePart")
        assertTrue(fileDispositions.single().contains("name=\"file\""), "missing file part name")
        assertTrue(fileDispositions.single().contains("filename=\"a.jpg\""), "missing/quoted filename")
        assertTrue(filePart.contains("image/jpeg"), "missing file content type")
        val data = assertNotNull(result).let { Json.decodeFromString<JsonObject>(it.toString()) }
        assertEquals(1, data["uploaded"].toString().toInt())
        assertEquals("oss/a.jpg", data["key"].toString().trim('"'))
    }

    @Test
    fun `dynamic uploadOss propagates business error`() {
        val harness = Harness(
            handler = {
                respond(
                    """{"code":7,"message":"oss denied","data":null,"trace_id":"t-4"}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val error = assertFailsWith<ApiException> {
            runBlocking { harness.api.uploadOss("https://oss.example.com/u", emptyMap(), "a", byteArrayOf(), "text/plain") }
        }
        assertEquals(7, error.code)
        assertEquals("@Url:POST:uploadOss", error.path)
    }

    @Test
    fun `dynamic download rejects non 2xx`() {
        val harness = Harness(handler = { respondError(HttpStatusCode.NotFound) })
        assertFailsWith<IllegalStateException> {
            runBlocking { harness.api.download("https://oss.example.com/missing") { } }
        }
    }

    @Test
    fun `dynamic uploadOss accepts explicit caller headers only`() {
        val harness = Harness(handler = respondOk())
        runBlocking {
            harness.api.uploadOss(
                url = "https://oss.example.com/signed",
                fields = emptyMap(),
                fileName = "a",
                bytes = byteArrayOf(1),
                contentType = "text/plain",
                headers = mapOf("x-oss-security-token" to "sts-1"),
            )
        }
        val sent = harness.requests.single()
        assertEquals("sts-1", sent.headers["x-oss-security-token"])
        assertNoAppHeaders(sent)
    }

    @Test
    fun `dynamic ids are registered but keep the 112 count`() {
        assertEquals(setOf("@Url:GET:download", "@Url:POST:uploadOss"), DynamicEndpoints.ids)
        assertEquals(2, DynamicEndpoints.all.size)
        assertEquals(112, EndpointRegistry.all.size)
        assertEquals(111, EndpointRegistry.uniquePaths().size)
        assertTrue(EndpointRegistry.isKnown(DynamicEndpoints.DOWNLOAD))
        assertTrue(EndpointRegistry.isKnown(DynamicEndpoints.UPLOAD_OSS))
        assertFalse(EndpointRegistry.isKnown("Bills/BatchUpdate"))
    }

    @Test
    fun `configured base url sends main and config requests to that host`() {
        val custom = BaseUrlProvider { isConfig ->
            if (isConfig) "https://cfg.self-hosted.test/api" else "https://api.self-hosted.test/v1"
        }
        val harness = Harness(handler = respondOk(), baseUrlProvider = custom)
        runBlocking {
            harness.api.call(EndpointRequest("Bills/Create"))
            harness.api.call(EndpointRequest("Config/GetV2", fields = mapOf("appVersion" to "1"), urlType = "configManage"))
        }
        assertEquals("https://api.self-hosted.test/v1/Bills/Create", harness.requests[0].url.toString())
        assertEquals("https://cfg.self-hosted.test/api/Config/GetV2", harness.requests[1].url.toString())
    }

    @Test
    fun `config base falls back to main base when blank`() {
        // sync_config_base_url 留空时，Config/GetV2 复用主域。
        val custom = BaseUrlProvider { "https://api.self-hosted.test/v1" }
        val harness = Harness(handler = respondOk(), baseUrlProvider = custom)
        runBlocking {
            harness.api.call(EndpointRequest("Config/GetV2", urlType = "configManage"))
        }
        assertEquals("https://api.self-hosted.test/v1/Config/GetV2", harness.requests.single().url.toString())
    }

    @Test
    fun `blank base url disables call without sending anything`() {
        val harness = Harness(handler = respondOk(), baseUrlProvider = BaseUrlProvider { null })
        assertFailsWith<IllegalStateException> {
            runBlocking { harness.api.call(EndpointRequest("Bills/Create")) }
        }
        assertEquals(0, harness.requests.size, "no request may leave the device")
    }

    @Test
    fun `settings base url provider gates on sync_base_url`() {
        val driver = app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver(
            app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.IN_MEMORY,
        )
        com.meow.ledger.shared.db.MeowDatabase.Schema.create(driver)
        val db = com.meow.ledger.shared.db.MeowDatabase(driver)
        val settings = com.meow.ledger.shared.auto.SettingsStore(db)
        val provider = SettingsBaseUrlProvider(settings)

        // 空白：禁用（主域、配置域都返回 null），且 remoteSyncEnabled 为 false。
        assertNull(provider.base(false))
        assertNull(provider.base(true))
        assertFalse(remoteSyncEnabled(db))

        settings.setText(com.meow.ledger.shared.auto.KEY_SYNC_BASE_URL, "https://self.test/api")
        assertTrue(remoteSyncEnabled(db))
        assertEquals("https://self.test/api", provider.base(false))
        assertEquals("https://self.test/api", provider.base(true), "config 空白时回退主域")

        settings.setText(com.meow.ledger.shared.auto.KEY_SYNC_CONFIG_BASE_URL, "https://cfg.test/")
        assertEquals("https://cfg.test/", provider.base(true))
    }

    @Test
    fun `server token comes only from auth_token setting`() {
        val driver = app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver(
            app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.IN_MEMORY,
        )
        com.meow.ledger.shared.db.MeowDatabase.Schema.create(driver)
        val db = com.meow.ledger.shared.db.MeowDatabase(driver)
        val settings = com.meow.ledger.shared.auto.SettingsStore(db)
        // 本机 refreshLogin 的假令牌不该被 serverToken 取到。
        com.meow.ledger.shared.manage.AccountRepository(db).refreshLogin()
        assertEquals("", serverToken(db))
        settings.setText(com.meow.ledger.shared.auto.KEY_AUTH_TOKEN, "real-server-token")
        assertEquals("real-server-token", serverToken(db))
    }
}
