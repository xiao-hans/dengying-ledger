package com.meow.ledger.shared.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Envelope<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null,
    @SerialName("trace_id") val traceId: String = "",
)

class ApiException(
    val path: String,
    val code: Int,
    override val message: String,
    val traceId: String,
) : Exception("$path failed: code=$code message=$message trace_id=$traceId")

typealias JsonEnvelope = Envelope<JsonElement>

fun <T> Envelope<T>.unwrap(path: String): T? {
    if (code != 0) throw ApiException(path, code, message, traceId)
    return data
}
