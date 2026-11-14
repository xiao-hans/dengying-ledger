package com.meow.ledger.shared.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `spec/endpoints.json` 是全量接口登记簿：112 条注解路径 + 2 条 `dynamic: true` 的
 * 动态 `@Url` 条目。本测试从仓库根读该文件，保证它与 [EndpointCatalog]（仍只含
 * 112 条注解端点）和 [DynamicEndpoints]（2 条动态 id）不漂移。
 */
class EndpointsJsonRegistryTest {

    /** 从测试工作目录逐级向上找仓库根的 spec/endpoints.json。 */
    private fun specFile(): String {
        var dir = Paths.get("").toAbsolutePath()
        while (dir != null) {
            val f = dir.resolve("spec").resolve("endpoints.json")
            if (f.toFile().isFile) return f.toString()
            dir = dir.parent
        }
        error("spec/endpoints.json not found upward from ${Paths.get("").toAbsolutePath()}")
    }

    private val entries = Json.parseToJsonElement(File(specFile()).readText()).jsonArray.map { it.jsonObject }

    /**
     * JSON 里的 `body: null` 是 [JsonNull]，而 [JsonNull] 也是 JsonPrimitive，
     * `content` 会返回字符串 `"null"` 而不是 Kotlin null。必须与 EndpointCatalog
     * 的 `body: String?` 口径一致，否则逐条精确比对时所有空 body 条目的值都不相等。
     */
    private fun kotlinx.serialization.json.JsonObject.bodyOrNull(): String? {
        val el = this["body"] ?: return null
        return if (el is JsonNull) null else el.jsonPrimitive.content
    }

    // key 必须含 body：Bills/ImportBills 有两条仅 body 不同的重载（ImportBillsReq / ImportOcrBillsReq）
    private fun List<kotlinx.serialization.json.JsonObject>.tuples() =
        associate {
            val body = it.bodyOrNull()
            val key = "${it["method"]!!.jsonPrimitive.content} ${it["path"]!!.jsonPrimitive.content} $body"
            key to Triple(
                it["fields"]!!.jsonArray.map { f -> f.jsonPrimitive.content },
                body,
                it["ret"]!!.jsonPrimitive.content,
            )
        }

    @Test
    fun `json holds 112 annotated and 2 dynamic entries`() {
        val dynamic = entries.filter { it["dynamic"]?.jsonPrimitive?.booleanOrNull == true }
        val annotated = entries - dynamic.toSet()
        assertEquals(112, annotated.size)
        assertEquals(111, annotated.map { it["path"]!!.jsonPrimitive.content }.toSet().size)
        assertEquals(DynamicEndpoints.ids, dynamic.map { it["path"]!!.jsonPrimitive.content }.toSet())
    }

    @Test
    fun `annotated entries match EndpointCatalog exactly`() {
        val annotated = entries.filter { it["dynamic"]?.jsonPrimitive?.booleanOrNull != true }
        val tuples = annotated.tuples()
        // 112 条注解条目全部保留：若 key 漏掉 body，两个 ImportBills 重载会塌成一条。
        assertEquals(112, tuples.size)
        assertEquals(
            EndpointCatalog.associate { "${it.method} ${it.path} ${it.body}" to Triple(it.fields, it.body, it.ret) },
            tuples,
        )
    }

    @Test
    fun `registry count stays 112 excluding dynamic ids`() {
        assertEquals(112, EndpointRegistry.all.size)
        assertTrue(EndpointRegistry.all.none { it.path in DynamicEndpoints.ids })
        // isKnown 的合法 path 口径 = 112 条注解路径 + 2 个动态 id
        assertTrue(DynamicEndpoints.ids.all { EndpointRegistry.isKnown(it) })
    }
}
