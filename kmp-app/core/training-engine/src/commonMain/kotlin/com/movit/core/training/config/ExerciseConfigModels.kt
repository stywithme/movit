@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.movit.core.training.config

import com.movit.core.training.engine.CountingMethod
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.ZoneType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class TrackedJoint(
    val joint: String = "",
    val role: JointRole = JointRole.PRIMARY,
    val startPose: AngleRange = AngleRange(0.0, 180.0),
    @JsonNames("upRange", "stateUpRange")
    val upRange: StateRanges? = null,
    @JsonNames("downRange", "stateDownRange")
    val downRange: StateRanges? = null,
    @JsonNames("range", "stateRange")
    val range: StateRanges? = null,
    val stateMessages: StateMessages? = null,
    val pairedWith: String? = null,
    val invertIndicator: Boolean = false,
    val trackingMode: TrackingMode = TrackingMode.TWO_SIDES,
    val phaseRanges: Map<String, StateRanges>? = null,
    val phaseStateMessages: Map<String, StateMessages>? = null,
) {
    fun hasStateUpDownRanges(): Boolean = upRange != null && downRange != null

    fun hasStateHoldRange(): Boolean = range != null

    fun getStateUpRange(): StateRanges =
        upRange ?: error("upRange required for PRIMARY joint $joint")

    fun getStateDownRange(): StateRanges =
        downRange ?: error("downRange required for PRIMARY joint $joint")

    fun getTransitionZone(): Pair<Double, Double>? {
        if (!hasStateUpDownRanges()) return null
        val transitionMin = getStateDownRange().effectiveMax
        val transitionMax = getStateUpRange().effectiveMin
        return if (transitionMin < transitionMax) transitionMin to transitionMax else null
    }

    fun determineZoneType(angle: Double): ZoneType {
        if (!hasStateUpDownRanges()) return ZoneType.UP_ZONE
        val transition = getTransitionZone()
        return when {
            transition != null && angle > transition.first && angle < transition.second -> ZoneType.TRANSITION
            angle >= getStateUpRange().effectiveMin -> ZoneType.UP_ZONE
            angle <= getStateDownRange().effectiveMax -> ZoneType.DOWN_ZONE
            else -> ZoneType.TRANSITION
        }
    }

    fun determineState(angle: Double): JointState = when {
        hasStateHoldRange() -> range!!.determineState(angle)
        hasStateUpDownRanges() -> {
            when (val zone = determineZoneType(angle)) {
                ZoneType.TRANSITION -> JointState.TRANSITION
                ZoneType.UP_ZONE -> getStateUpRange().determineState(angle, OutwardDirection.TOWARDS_HIGH)
                ZoneType.DOWN_ZONE -> getStateDownRange().determineState(angle, OutwardDirection.TOWARDS_LOW)
            }
        }
        else -> JointState.WARNING
    }
}

@Serializable
data class PoseVariant(
    val name: LocalizedText = LocalizedText(),
    val posePosition: String? = null,
    val cameraPosition: String? = null,
    val positionImageUrl: String? = null,
    val expectedPostures: List<String>? = null,
    val expectedDirections: List<String>? = null,
    val expectedRegions: List<String>? = null,
    val trackedJoints: List<TrackedJoint> = emptyList(),
    val positionChecks: List<PositionCheck> = emptyList(),
    val feedbackMessages: FeedbackMessages = FeedbackMessages(),
    val messageAssignments: List<MessageAssignment> = emptyList(),
)

@Serializable
data class ExerciseConfig(
    val name: LocalizedText = LocalizedText(),
    val description: LocalizedText? = null,
    val instructions: LocalizedText? = null,
    val imageUrl: String? = null,
    val category: CategoryInfo = CategoryInfo(),
    val countingMethod: CountingMethod = CountingMethod.UP_DOWN,
    val muscles: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val poseVariants: List<PoseVariant> = emptyList(),
    val repCountingConfig: RepCountingConfig = RepCountingConfig(),
    val supportsWeight: Boolean = false,
    val minWeight: Float? = null,
    val maxWeight: Float? = null,
    val defaultWeight: Float? = null,
    val reportMetrics: ReportMetricsConfig? = null,
    val hasPositionChecks: Boolean = false,
    val isBilateral: Boolean = false,
    val bilateralConfig: BilateralConfig? = null,
) {
    fun getPoseVariant(index: Int = 0): PoseVariant? = poseVariants.getOrNull(index)

    fun getPrimaryJoints(variantIndex: Int = 0): List<TrackedJoint> =
        poseVariants.getOrNull(variantIndex)
            ?.trackedJoints
            ?.filter { it.role == JointRole.PRIMARY }
            ?: emptyList()

    fun getTrackedJoints(variantIndex: Int = 0): List<TrackedJoint> =
        poseVariants.getOrNull(variantIndex)?.trackedJoints ?: emptyList()

    fun isHoldExercise(): Boolean = countingMethod == CountingMethod.HOLD

    fun hasAnyPositionChecks(variantIndex: Int = 0): Boolean =
        poseVariants.getOrNull(variantIndex)?.positionChecks?.isNotEmpty() == true || hasPositionChecks

    fun displayName(language: String = "en"): String = name.get(language).ifBlank { name.en }

    fun defaultTargetReps(): Int = repCountingConfig.reps

    fun validationIssues(poseVariantIndex: Int = 0): List<String> {
        val out = mutableListOf<String>()
        if (name.ar.isBlank() && name.en.isBlank()) out += "exercise name is empty"
        if (poseVariants.isEmpty()) out += "poseVariants is empty"
        val variant = poseVariants.getOrNull(poseVariantIndex)
        if (variant == null) {
            out += "pose variant $poseVariantIndex is missing"
        } else {
            if (variant.trackedJoints.isEmpty()) out += "no trackedJoints in variant $poseVariantIndex"
            if (variant.trackedJoints.none { it.role == JointRole.PRIMARY }) {
                out += "no primary joints in variant $poseVariantIndex"
            }
        }
        return out
    }
}

@Serializable
data class ExerciseConfigRecord(
    val id: String = "",
    val slug: String = "",
    val updatedAt: String = "",
    val config: ExerciseConfig = ExerciseConfig(),
    /** Library fingerprint stamped when message assignments were last resolved. Empty = never stamped. */
    val messageLibraryFingerprint: String = "",
) {
    fun withSanitizedConfig(): ExerciseConfigRecord = copy(config = config.sanitizeDefaults())

    companion object {
        fun fromConfig(
            id: String,
            slug: String,
            updatedAt: String,
            config: ExerciseConfig,
            messageLibraryFingerprint: String = "",
        ): ExerciseConfigRecord = ExerciseConfigRecord(
            id = id,
            slug = slug,
            updatedAt = updatedAt,
            config = config.sanitizeDefaults(),
            messageLibraryFingerprint = messageLibraryFingerprint,
        )
    }
}

fun ExerciseConfig.sanitizeDefaults(): ExerciseConfig = copy(
    poseVariants = poseVariants.map { variant ->
        variant.copy(
            trackedJoints = variant.trackedJoints,
            positionChecks = variant.positionChecks,
            feedbackMessages = variant.feedbackMessages,
            messageAssignments = variant.messageAssignments,
        )
    },
    muscles = muscles,
    equipment = equipment,
    tags = tags,
)
