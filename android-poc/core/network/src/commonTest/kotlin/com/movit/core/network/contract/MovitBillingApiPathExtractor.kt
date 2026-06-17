package com.movit.core.network.contract

/** Parses [com.movit.core.network.MovitBillingApi] source for base("…") HTTP paths. */
internal object MovitBillingApiPathExtractor {

    private val sourcePath = "src/commonMain/kotlin/com/movit/core/network/MovitBillingApi.kt"

    fun extractFromSource(): Set<String> = MovitMobileApiPathExtractor.extractPaths(readSource())

    private fun readSource(): String {
        javaClass.classLoader.getResource(sourcePath)?.readText()?.let { return it }
        val candidates = listOf(
            "core/network/$sourcePath",
            "../core/network/$sourcePath",
            "../../core/network/$sourcePath",
        )
        for (relative in candidates) {
            val file = java.io.File(relative)
            if (file.isFile) return file.readText()
        }
        error("Cannot locate MovitBillingApi.kt for contract extraction")
    }
}
