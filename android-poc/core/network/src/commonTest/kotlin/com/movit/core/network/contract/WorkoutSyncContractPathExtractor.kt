package com.movit.core.network.contract

/**
 * Parses OkHttp-only legacy consumers (not declared on Retrofit interfaces).
 */
internal object WorkoutSyncContractPathExtractor {

    private val endpointConstantRegex = Regex(
        """private\s+const\s+val\s+ENDPOINT\s*=\s*"([^"]+)"""",
    )

    fun extractAll(): Set<String> {
        val source = readWorkoutSyncServiceSource()
        val match = endpointConstantRegex.find(source)
            ?: error("ENDPOINT constant not found in WorkoutSyncService.kt")
        val path = match.groupValues[1].trimStart('/')
        return setOf("POST $path")
    }

    private fun readWorkoutSyncServiceSource(): String {
        val candidates = listOf(
            "app/src/main/java/com/trainingvalidator/poc/network/WorkoutSyncService.kt",
            "../app/src/main/java/com/trainingvalidator/poc/network/WorkoutSyncService.kt",
            "../../app/src/main/java/com/trainingvalidator/poc/network/WorkoutSyncService.kt",
        )
        for (relative in candidates) {
            val file = java.io.File(relative)
            if (file.isFile) return file.readText()
        }
        error("Cannot locate WorkoutSyncService.kt for contract extraction")
    }
}
