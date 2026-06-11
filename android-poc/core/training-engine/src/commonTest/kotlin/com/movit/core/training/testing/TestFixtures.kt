package com.movit.core.training.testing

internal fun readExerciseFixture(name: String): String {
    val resourcePath = "fixtures/exercises/$name"
    val loader = Thread.currentThread().contextClassLoader
    loader?.getResource(resourcePath)?.readText()?.let { return it }
    val candidates = listOf(
        "src/commonTest/resources/$resourcePath",
        "core/training-engine/src/commonTest/resources/$resourcePath",
    )
    for (relative in candidates) {
        val file = java.io.File(relative)
        if (file.isFile) return file.readText()
    }
    error("Missing fixture: $resourcePath")
}
