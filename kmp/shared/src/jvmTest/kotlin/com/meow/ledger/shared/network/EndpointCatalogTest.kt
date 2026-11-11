package com.meow.ledger.shared.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EndpointCatalogTest {
    @Test
    fun `catalog has 112 annotations and 111 unique paths`() {
        assertEquals(112, EndpointRegistry.all.size)
        assertEquals(111, EndpointRegistry.uniquePaths().size)
        assertEquals(20, EndpointRegistry.byGroup().size)
    }

    @Test
    fun `groups match authoritative counts`() {
        val groups = EndpointRegistry.byGroup().mapValues { it.value.size }
        assertEquals(12, groups["Bills"])
        assertEquals(6, groups["Cate"])
        assertEquals(4, groups["MemberTag"])
        assertEquals(10, groups["BillsBook"])
        assertEquals(9, groups["Budget"])
        assertEquals(14, groups["SavePlan"])
        assertEquals(1, groups["Config"])
    }

    @Test
    fun `two import overloads and config host are retained`() {
        val imports = EndpointRegistry.byPath("Bills/ImportBills")
        assertEquals(2, imports.size)
        assertEquals(setOf("ImportBillsReq", "ImportOcrBillsReq"), imports.map { it.body }.toSet())
        assertEquals(HttpConfig.CONFIG_BASE, HttpConfig.baseFor("Config/GetV2"))
        assertEquals(HttpConfig.MAIN_BASE, HttpConfig.baseFor("Bills/PageList"))
    }

    @Test
    fun `member tag create keeps source field case`() {
        val endpoint = EndpointRegistry.byPath("MemberTag/Create").single()
        assertEquals(listOf("BillBookId", "name"), endpoint.fields)
    }

    @Test
    fun `envelope unwraps data and preserves trace id on error`() {
        val json = Json { ignoreUnknownKeys = true }
        val ok = json.decodeFromString<Envelope<JsonElement>>(
            """{"code":0,"message":"","data":{"value":1},"trace_id":"t-1"}""",
        )
        assertEquals("t-1", ok.traceId)
        assertEquals(0, ok.code)
        assertTrue(ok.unwrap("Bills/PageList") != null)

        val failed = json.decodeFromString<Envelope<JsonElement>>(
            """{"code":42,"message":"denied","data":null,"trace_id":"t-2"}""",
        )
        val error = assertFailsWith<ApiException> { failed.unwrap("Bills/PageList") }
        assertEquals(42, error.code)
        assertEquals("t-2", error.traceId)
        assertEquals("denied", error.message)
    }
}
