package com.trainingvalidator.poc.training.engine

import com.google.gson.annotations.SerializedName

/**
 * Expected camera direction axis — what direction the camera should see the person from.
 */
enum class ExpectedDirection(val code: String) {
    FRONT("front"),
    BACK("back"),
    SIDE_ANY("side"),
    SIDE_LEFT("side_left"),
    SIDE_RIGHT("side_right"),
    DIAGONAL("diagonal"),
    ANY("any");

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): ExpectedDirection = byCode[code] ?: ANY
    }
}

// ── Per-axis match result ───────────────────────────────────────────────

data class AxisMatchResult(
    val postureMatch: Boolean,
    val directionMatch: Boolean,
    val regionMatch: Boolean
) {
    val allMatch: Boolean get() = postureMatch && directionMatch && regionMatch
}

// ── Multi-value scene expectation (new system) ──────────────────────────

/**
 * Holds the expected values for each of the 3 axes. Each axis can accept
 * one or more valid values, or "any" to accept everything.
 *
 * Built from backend JSON arrays (`expectedPostures`, `expectedDirections`,
 * `expectedRegions`) or from a legacy [PosePosition] code.
 */
data class PoseSceneExpectation(
    val postures: List<BodyPosture>,
    val directions: List<ExpectedDirection>,
    val regions: List<VisibleRegion>
) {
    private val anyPosture = postures.isEmpty() || postures.any { it == BodyPosture.UNKNOWN }
    private val anyDirection = directions.isEmpty() || directions.any { it == ExpectedDirection.ANY }
    private val anyRegion = regions.isEmpty() || regions.any { it == VisibleRegion.UNKNOWN }

    fun matchesPosture(detected: BodyPosture): Boolean {
        if (anyPosture) return true
        return postures.contains(detected)
    }

    fun matchesDirection(detected: CameraPositionDetector.DetectedCameraPosition): Boolean {
        if (anyDirection) return true
        return directions.any { directionSatisfies(it, detected) }
    }

    fun matchesRegion(detected: VisibleRegion): Boolean {
        if (detected == VisibleRegion.UNKNOWN) return false
        if (anyRegion) return true
        if (detected == VisibleRegion.FULL_BODY) return true
        return regions.contains(detected)
    }

    fun matchesScene(scene: PoseSceneResult): AxisMatchResult = AxisMatchResult(
        postureMatch = matchesPosture(scene.posture),
        directionMatch = matchesDirection(scene.direction),
        regionMatch = matchesRegion(scene.region)
    )

    fun postureLabel(): String = if (anyPosture) "any" else postures.joinToString(", ") { it.name }
    fun directionLabel(): String = if (anyDirection) "any" else directions.joinToString(", ") { it.code }
    fun regionLabel(): String = if (anyRegion) "any" else regions.joinToString(", ") { it.name }

    companion object {
        fun fromJson(
            postures: List<String>?,
            directions: List<String>?,
            regions: List<String>?
        ): PoseSceneExpectation {
            val p = postures?.map { parsePosture(it) } ?: listOf(BodyPosture.UNKNOWN)
            val d = directions?.map { ExpectedDirection.fromCode(it) } ?: listOf(ExpectedDirection.ANY)
            val r = regions?.map { parseRegion(it) } ?: listOf(VisibleRegion.UNKNOWN)
            return PoseSceneExpectation(p, d, r)
        }

        fun fromLegacyCode(code: String): PoseSceneExpectation {
            val pos = PosePosition.fromCode(code) ?: PosePosition.fromLegacy(code)
            return PoseSceneExpectation(
                postures = listOf(pos.expectedPosture),
                directions = listOf(pos.expectedDirection),
                regions = listOf(pos.expectedRegion)
            )
        }

        private fun parsePosture(code: String): BodyPosture = when (code) {
            "standing" -> BodyPosture.STANDING
            "lying_prone" -> BodyPosture.LYING_PRONE
            "lying_supine" -> BodyPosture.LYING_SUPINE
            "lying_side" -> BodyPosture.LYING_SIDE
            "sitting" -> BodyPosture.SITTING
            "any" -> BodyPosture.UNKNOWN
            else -> BodyPosture.UNKNOWN
        }

        private fun parseRegion(code: String): VisibleRegion = when (code) {
            "full_body" -> VisibleRegion.FULL_BODY
            "upper_body" -> VisibleRegion.UPPER_BODY
            "lower_body" -> VisibleRegion.LOWER_BODY
            "any" -> VisibleRegion.UNKNOWN
            else -> VisibleRegion.UNKNOWN
        }

        private fun directionSatisfies(
            expected: ExpectedDirection,
            detected: CameraPositionDetector.DetectedCameraPosition
        ): Boolean = when (expected) {
            ExpectedDirection.FRONT -> detected == CameraPositionDetector.DetectedCameraPosition.FRONT_VIEW
            ExpectedDirection.BACK -> detected == CameraPositionDetector.DetectedCameraPosition.BACK_VIEW
            ExpectedDirection.SIDE_ANY ->
                detected == CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT ||
                        detected == CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT
            ExpectedDirection.SIDE_LEFT -> detected == CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT
            ExpectedDirection.SIDE_RIGHT -> detected == CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT
            ExpectedDirection.DIAGONAL ->
                detected == CameraPositionDetector.DetectedCameraPosition.FRONT_VIEW ||
                        detected == CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT ||
                        detected == CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT
            ExpectedDirection.ANY -> true
        }
    }
}

// ── Legacy PosePosition enum (kept for backward compatibility) ──────────

/**
 * All 17 pose positions that can be configured in the backend.
 * @deprecated Use [PoseSceneExpectation] for matching. This enum is kept only
 * for [fromCode]/[fromLegacy] fallback when the backend doesn't send the
 * 3-axis arrays.
 */
enum class PosePosition(
    val code: String,
    val expectedPosture: BodyPosture,
    val expectedDirection: ExpectedDirection,
    val expectedRegion: VisibleRegion
) {
    @SerializedName("standing_front")
    STANDING_FRONT("standing_front", BodyPosture.STANDING, ExpectedDirection.FRONT, VisibleRegion.FULL_BODY),

    @SerializedName("standing_back")
    STANDING_BACK("standing_back", BodyPosture.STANDING, ExpectedDirection.BACK, VisibleRegion.FULL_BODY),

    @SerializedName("standing_side")
    STANDING_SIDE("standing_side", BodyPosture.STANDING, ExpectedDirection.SIDE_ANY, VisibleRegion.FULL_BODY),

    @SerializedName("standing_side_left")
    STANDING_SIDE_LEFT("standing_side_left", BodyPosture.STANDING, ExpectedDirection.SIDE_LEFT, VisibleRegion.FULL_BODY),

    @SerializedName("standing_side_right")
    STANDING_SIDE_RIGHT("standing_side_right", BodyPosture.STANDING, ExpectedDirection.SIDE_RIGHT, VisibleRegion.FULL_BODY),

    @SerializedName("standing_diagonal")
    STANDING_DIAGONAL("standing_diagonal", BodyPosture.STANDING, ExpectedDirection.DIAGONAL, VisibleRegion.FULL_BODY),

    @SerializedName("standing_front_upper")
    STANDING_FRONT_UPPER("standing_front_upper", BodyPosture.STANDING, ExpectedDirection.FRONT, VisibleRegion.UPPER_BODY),

    @SerializedName("standing_back_upper")
    STANDING_BACK_UPPER("standing_back_upper", BodyPosture.STANDING, ExpectedDirection.BACK, VisibleRegion.UPPER_BODY),

    @SerializedName("standing_side_upper")
    STANDING_SIDE_UPPER("standing_side_upper", BodyPosture.STANDING, ExpectedDirection.SIDE_ANY, VisibleRegion.UPPER_BODY),

    @SerializedName("standing_front_lower")
    STANDING_FRONT_LOWER("standing_front_lower", BodyPosture.STANDING, ExpectedDirection.FRONT, VisibleRegion.LOWER_BODY),

    @SerializedName("standing_back_lower")
    STANDING_BACK_LOWER("standing_back_lower", BodyPosture.STANDING, ExpectedDirection.BACK, VisibleRegion.LOWER_BODY),

    @SerializedName("standing_side_lower")
    STANDING_SIDE_LOWER("standing_side_lower", BodyPosture.STANDING, ExpectedDirection.SIDE_ANY, VisibleRegion.LOWER_BODY),

    @SerializedName("prone_side")
    PRONE_SIDE("prone_side", BodyPosture.LYING_PRONE, ExpectedDirection.SIDE_ANY, VisibleRegion.FULL_BODY),

    @SerializedName("prone_front")
    PRONE_FRONT("prone_front", BodyPosture.LYING_PRONE, ExpectedDirection.FRONT, VisibleRegion.FULL_BODY),

    @SerializedName("supine_side")
    SUPINE_SIDE("supine_side", BodyPosture.LYING_SUPINE, ExpectedDirection.SIDE_ANY, VisibleRegion.FULL_BODY),

    @SerializedName("supine_front")
    SUPINE_FRONT("supine_front", BodyPosture.LYING_SUPINE, ExpectedDirection.FRONT, VisibleRegion.FULL_BODY),

    @SerializedName("side_lying")
    SIDE_LYING("side_lying", BodyPosture.LYING_SIDE, ExpectedDirection.SIDE_ANY, VisibleRegion.FULL_BODY);

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: String?): PosePosition? = code?.let { byCode[it] }

        fun fromLegacy(cameraPosition: String?): PosePosition = when (cameraPosition) {
            "side_view" -> STANDING_SIDE
            "front_view" -> STANDING_FRONT
            "back_view" -> STANDING_BACK
            else -> STANDING_SIDE
        }
    }
}
