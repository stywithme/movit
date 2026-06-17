package com.movit.core.network.contract

/**
 * Parses legacy Retrofit interfaces under app module for @GET/@POST/… path annotations.
 */
internal object RetrofitContractPathExtractor {

    // Legacy Retrofit fully removed (WS-D/B8): auth + subscriptions are now KMP-native
    // (MovitMobileApi / MovitBillingApi). No Retrofit interface files remain to extract.
    private val interfaceFiles = emptyList<String>()

    private val annotationRegex = Regex(
        """@(GET|POST|PUT|DELETE|PATCH)\("([^"]+)"\)""",
    )

    fun extractAll(): Set<String> {
        val paths = mutableSetOf<String>()
        for (fileName in interfaceFiles) {
            val source = readRetrofitSource(fileName)
            annotationRegex.findAll(source).forEach { match ->
                val method = match.groupValues[1]
                val path = normalizePath(match.groupValues[2])
                if (path.isNotEmpty()) {
                    paths += "$method $path"
                }
            }
        }
        return paths
    }

    private fun normalizePath(path: String): String =
        when {
            path.startsWith("http") -> "" // @Url streaming — excluded
            else -> path
        }

    private fun readRetrofitSource(fileName: String): String {
        val resourcePath = "legacy-retrofit/$fileName"
        javaClass.classLoader.getResource(resourcePath)?.readText()?.let { return it }

        val candidates = listOf(
            "app/src/main/java/com/movit/network/$fileName",
            "../app/src/main/java/com/movit/network/$fileName",
            "../../app/src/main/java/com/movit/network/$fileName",
        )
        for (relative in candidates) {
            val file = java.io.File(relative)
            if (file.isFile) return file.readText()
        }
        error("Cannot locate $fileName for Retrofit contract extraction")
    }
}
