package com.movit.core.training.testing

import com.movit.core.training.config.ExerciseConfigParser

internal fun readParityFixture(name: String): String {
    val resourcePath = "fixtures/parity/$name"
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
    error("Missing parity fixture: $resourcePath")
}

internal fun parseParityConfig(fixtureName: String) =
    ExerciseConfigParser.parseConfigJson(readParityFixture(fixtureName))
