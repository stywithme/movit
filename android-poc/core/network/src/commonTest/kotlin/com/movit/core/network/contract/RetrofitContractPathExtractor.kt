package com.movit.core.network.contract

/**
 * Parses legacy Retrofit interfaces under app module for @GET/@POST/… path annotations.
 */
internal object RetrofitContractPathExtractor {

    private val interfaceFiles = listOf(
        "AuthApi.kt",
        "SubscriptionApi.kt",
    )

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
            "app/src/main/java/com/trainingvalidator/poc/network/$fileName",
            "../app/src/main/java/com/trainingvalidator/poc/network/$fileName",
            "../../app/src/main/java/com/trainingvalidator/poc/network/$fileName",
            "feature/billing/src/main/kotlin/com/movit/billing/network/$fileName",
            "../feature/billing/src/main/kotlin/com/movit/billing/network/$fileName",
            "../../feature/billing/src/main/kotlin/com/movit/billing/network/$fileName",
        )
        for (relative in candidates) {
            val file = java.io.File(relative)
            if (file.isFile) return file.readText()
        }
        error("Cannot locate $fileName for Retrofit contract extraction")
    }
}
