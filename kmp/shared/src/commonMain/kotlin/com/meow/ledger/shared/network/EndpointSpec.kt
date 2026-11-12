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
    /** 只含 112 条注解路径；动态 @Url 走 [DynamicEndpoints]。 */
    val all: List<EndpointSpec> get() = EndpointCatalog

    fun byPath(path: String): List<EndpointSpec> = all.filter { it.path == path }

    fun uniquePaths(): Set<String> = all.map { it.path }.toSet()

    /** 校验 path 是否在权威清单里（112 条注解路径或 2 个动态 @Url id）。 */
    fun isKnown(path: String): Boolean = path in uniquePaths() || path in DynamicEndpoints.ids

    fun byGroup(): Map<String, List<EndpointSpec>> = all.groupBy { it.group }
}
