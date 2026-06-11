package com.movit.core.network.contract

/**
 * Parses [com.movit.core.network.MovitMobileApi] source for base("…") HTTP paths.
 */
internal object MovitMobileApiPathExtractor {

    private val sourcePath = "src/commonMain/kotlin/com/movit/core/network/MovitMobileApi.kt"

    fun extractFromSource(): Set<String> = extractPaths(readSource())

    fun extractPaths(source: String): Set<String> {
        val paths = mutableSetOf<String>()
        val baseRegex = Regex("""base\("([^"]+)"\)""")
        val methodRegex = Regex(
            """suspend fun \w+\([^)]*\):\s*Result<[^>]+>\s*=\s*runCatching\s*\{\s*\n\s*val response = client\.(get|post|put|patch|delete)\(""",
            RegexOption.MULTILINE,
        )

        val lines = source.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val methodMatch = Regex("""client\.(get|post|put|patch|delete)\(""")
                .find(line)
            if (methodMatch != null) {
                val method = methodMatch.groupValues[1].uppercase()
                val window = lines.subList(i, minOf(i + 3, lines.size)).joinToString("\n")
                val pathMatch = baseRegex.find(window)
                if (pathMatch != null) {
                    paths += "$method ${normalizePath(pathMatch.groupValues[1])}"
                }
            }
            i++
        }
        return paths
    }

    private fun normalizePath(path: String): String =
        path
            .replace("\$programId", "{id}")
            .replace("\$userProgramId", "{id}")
            .replace("\$workoutTemplateId", "{id}")
            .replace("\$workoutId", "{workoutId}")
            .replace("\$overrideId", "{overrideId}")
            .replace("\$exerciseId", "{exerciseId}")
            .replace("\$slug", "{slug}")
            .replace("\$exerciseSlug", "{slug}")
            .replace("\$sessionId", "{sessionId}")

    private fun readSource(): String {
        val fromModule = javaClass.classLoader.getResource(sourcePath)?.readText()
        if (fromModule != null) return fromModule

        val candidates = listOf(
            "core/network/$sourcePath",
            "../core/network/$sourcePath",
            "../../core/network/$sourcePath",
        )
        for (relative in candidates) {
            val file = java.io.File(relative)
            if (file.isFile) return file.readText()
        }
        error("Cannot locate MovitMobileApi.kt for contract extraction")
    }
}
