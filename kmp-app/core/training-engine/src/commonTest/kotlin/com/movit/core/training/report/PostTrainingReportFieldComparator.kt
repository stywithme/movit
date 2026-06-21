package com.movit.core.training.report

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object PostTrainingReportFieldComparator {
    fun assertParityFields(expected: JsonObject, actual: JsonObject) {
        comparePrimitive(expected, actual, "summary.totalReps")
        comparePrimitive(expected, actual, "summary.durationMs")
        comparePrimitive(expected, actual, "summary.rating")
        comparePrimitive(expected, actual, "summary.countedReps")
        comparePrimitive(expected, actual, "summary.invalidatedReps")
        compareFloat(expected, actual, "summary.averageScore", tolerance = 0.01f)
        compareFloat(expected, actual, "summary.countedRatio", tolerance = 0.01f)
        comparePrimitive(expected, actual, "summary.stateBreakdown.perfectCount")
        comparePrimitive(expected, actual, "summary.stateBreakdown.normalCount")
        comparePrimitive(expected, actual, "summary.stateBreakdown.padCount")
        comparePrimitive(expected, actual, "summary.stateBreakdown.warningCount")
        comparePrimitive(expected, actual, "summary.stateBreakdown.dangerCount")
        comparePrimitive(expected, actual, "executionQuality.visibilityPauseCount")
        comparePrimitive(expected, actual, "executionQuality.cameraWarningCount")
        comparePrimitive(expected, actual, "executionQuality.overallQuality")
    }

    private fun comparePrimitive(expected: JsonObject, actual: JsonObject, dottedPath: String) {
        val expectedValue = resolve(expected, dottedPath)?.jsonPrimitive?.content
        val actualValue = resolve(actual, dottedPath)?.jsonPrimitive?.content
        if (expectedValue != actualValue) {
            error("Field $dottedPath expected=$expectedValue actual=$actualValue")
        }
    }

    private fun compareFloat(
        expected: JsonObject,
        actual: JsonObject,
        dottedPath: String,
        tolerance: Float,
    ) {
        val expectedValue = resolve(expected, dottedPath)?.jsonPrimitive?.content?.toFloatOrNull()
        val actualValue = resolve(actual, dottedPath)?.jsonPrimitive?.content?.toFloatOrNull()
        if (expectedValue == null || actualValue == null) {
            error("Field $dottedPath missing or non-numeric expected=$expectedValue actual=$actualValue")
        }
        if (kotlin.math.abs(expectedValue - actualValue) > tolerance) {
            error("Field $dottedPath expected=$expectedValue actual=$actualValue")
        }
    }

    private fun resolve(root: JsonObject, dottedPath: String): JsonElement? {
        var current: JsonElement = root
        for (segment in dottedPath.split('.')) {
            current = (current as? JsonObject)?.get(segment) ?: return null
        }
        return current
    }
}
