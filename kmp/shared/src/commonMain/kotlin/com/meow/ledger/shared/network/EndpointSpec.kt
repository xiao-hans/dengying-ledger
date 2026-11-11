package com.meow.ledger.shared.network

data class EndpointSpec(
    val method: String,
    val path: String,
    val fields: List<String> = emptyList(),
    val body: String? = null,
    val ret: String = "",
) {
    val group: String get() = path.substringBefore('/')
}

object EndpointRegistry {
    val all: List<EndpointSpec> get() = EndpointCatalog

    fun byPath(path: String): List<EndpointSpec> = all.filter { it.path == path }

    fun uniquePaths(): Set<String> = all.map { it.path }.toSet()

    fun byGroup(): Map<String, List<EndpointSpec>> = all.groupBy { it.group }
}
