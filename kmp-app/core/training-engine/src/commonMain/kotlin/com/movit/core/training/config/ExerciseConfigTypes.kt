package com.movit.core.training.config

import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.ZoneType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocalizedText(
    val ar: String = "",
    val en: String = "",
    val audioAr: String? = null,
    val audioEn: String? = null,
) {
    fun get(language: String = "en"): String = if (language == "ar") ar else en

    fun getAudioUrl(language: String = "en"): String? = if (language == "ar") audioAr else audioEn
}

@Serializable
data class CategoryInfo(
    val code: String = "",
    val name: LocalizedText = LocalizedText(),
)

@Serializable
data class AngleRange(
    val min: Double = 0.0,
    val max: Double = 0.0,
) {
    fun contains(angle: Double): Boolean = angle >= min && angle <= max
}

enum class OutwardDirection {
    TOWARDS_HIGH,
    TOWARDS_LOW,
}

@Serializable
data class StateRanges(
    val perfect: AngleRange,
    val normal: AngleRange? = null,
    val pad: AngleRange? = null,
    val warning: AngleRange? = null,
    val danger: AngleRange? = null,
) {
    val effectiveMin: Double
        get() {
            var min = perfect.min
            normal?.min?.let { if (it < min) min = it }
            pad?.min?.let { if (it < min) min = it }
            return min
        }

    val effectiveMax: Double
        get() {
            var max = perfect.max
            normal?.max?.let { if (it > max) max = it }
            pad?.max?.let { if (it > max) max = it }
            return max
        }

    val outermostMin: Double
        get() {
            var min = perfect.min
            normal?.min?.let { if (it < min) min = it }
            pad?.min?.let { if (it < min) min = it }
            warning?.min?.let { if (it < min) min = it }
            danger?.min?.let { if (it < min) min = it }
            return min
        }

    val outermostMax: Double
        get() {
            var max = perfect.max
            normal?.max?.let { if (it > max) max = it }
            pad?.max?.let { if (it > max) max = it }
            warning?.max?.let { if (it > max) max = it }
            danger?.max?.let { if (it > max) max = it }
            return max
        }

    fun determineState(angle: Double, outwardDirection: OutwardDirection? = null): JointState {
        if (perfect.contains(angle)) return JointState.PERFECT
        if (normal != null && normal.contains(angle)) return JointState.NORMAL
        if (pad != null && pad.contains(angle)) return JointState.PAD
        if (danger != null && danger.contains(angle)) return JointState.DANGER
        if (warning != null && warning.contains(angle)) return JointState.WARNING
        if (outwardDirection != null) return getOutermostState(outwardDirection)
        return JointState.WARNING
    }

    private fun getOutermostState(direction: OutwardDirection): JointState = when (direction) {
        OutwardDirection.TOWARDS_HIGH -> {
            var best = JointState.PERFECT
            var bestVal = perfect.max
            normal?.let { if (it.max > bestVal) { bestVal = it.max; best = JointState.NORMAL } }
            pad?.let { if (it.max > bestVal) { bestVal = it.max; best = JointState.PAD } }
            warning?.let { if (it.max > bestVal) { bestVal = it.max; best = JointState.WARNING } }
            danger?.let { if (it.max > bestVal) { bestVal = it.max; best = JointState.DANGER } }
            best
        }
        OutwardDirection.TOWARDS_LOW -> {
            var best = JointState.PERFECT
            var bestVal = perfect.min
            normal?.let { if (it.min < bestVal) { bestVal = it.min; best = JointState.NORMAL } }
            pad?.let { if (it.min < bestVal) { bestVal = it.min; best = JointState.PAD } }
            warning?.let { if (it.min < bestVal) { bestVal = it.min; best = JointState.WARNING } }
            danger?.let { if (it.min < bestVal) { bestVal = it.min; best = JointState.DANGER } }
            best
        }
    }
}

@Serializable(with = StateMessageValueSerializer::class)
sealed class StateMessageValue {
    abstract fun getMessage(zone: ZoneType): LocalizedText?

    @Serializable
    data class Single(val message: LocalizedText) : StateMessageValue() {
        override fun getMessage(zone: ZoneType): LocalizedText = message
    }

    @Serializable
    data class ZoneSpecific(
        val up: LocalizedText? = null,
        val down: LocalizedText? = null,
    ) : StateMessageValue() {
        override fun getMessage(zone: ZoneType): LocalizedText? = when (zone) {
            ZoneType.UP_ZONE -> up
            ZoneType.DOWN_ZONE -> down
            ZoneType.TRANSITION -> null
        }
    }
}

@Serializable
data class StateMessages(
    val perfect: StateMessageValue? = null,
    val normal: StateMessageValue? = null,
    val pad: StateMessageValue? = null,
    val warning: StateMessageValue? = null,
    val danger: StateMessageValue? = null,
) {
    fun getMessage(state: JointState, zone: ZoneType): LocalizedText? {
        val value = when (state) {
            JointState.PERFECT -> perfect
            JointState.NORMAL -> normal
            JointState.PAD -> pad
            JointState.WARNING -> warning
            JointState.DANGER -> danger
            JointState.TRANSITION -> null
        }
        return value?.getMessage(zone)
    }
}

@Serializable
enum class JointRole {
    @SerialName("primary")
    PRIMARY,

    @SerialName("secondary")
    SECONDARY,
}

@Serializable
enum class TrackingMode {
    @SerialName("two_sides")
    TWO_SIDES,

    @SerialName("any_side")
    ANY_SIDE,
}

@Serializable
enum class MetricCode {
    FORM_SCORE,
    REP_COUNT,
    DURATION,
    ROM,
    SYMMETRY,
    STABILITY,
    TEMPO,
    TUT,
    HOLD_DURATION,
    ALIGNMENT,
    FORM_CONSISTENCY,
    FATIGUE_INDEX,
    TEMPO_CONSISTENCY,
    VELOCITY,
    VELOCITY_LOSS,
    WEIGHT,
    VOLUME,
    EST_1RM,
}

@Serializable
data class ReportMetricsConfig(
    val primary: List<MetricCode> = listOf(MetricCode.FORM_SCORE),
    val optional: List<MetricCode> = emptyList(),
    val excluded: List<MetricCode> = emptyList(),
) {
    fun shouldShow(metric: MetricCode): Boolean = !excluded.contains(metric)
}

@Serializable
enum class BilateralSwitchMode {
    @SerialName("every_rep")
    EVERY_REP,

    @SerialName("after_all_reps")
    AFTER_ALL_REPS,
}

@Serializable
data class BilateralConfig(
    val switchMode: BilateralSwitchMode? = null,
    val switchEvery: Int? = 1,
    val startSide: String = "right",
)

@Serializable
data class RepCountingConfig(
    val reps: Int = 12,
    val duration: Int? = null,
    val gracePeriodMs: Long? = null,
    val minRepIntervalMs: Long? = null,
    val maxRepIntervalMs: Long? = null,
) {
    fun getMinRepInterval(default: Long): Long = minRepIntervalMs ?: default

    fun getMaxRepInterval(default: Long): Long = maxRepIntervalMs ?: default

    fun calculateMinPhaseDuration(numberOfPhases: Int, defaultMinPhaseDuration: Long): Long {
        val minInterval = minRepIntervalMs ?: return defaultMinPhaseDuration
        if (numberOfPhases <= 0) return defaultMinPhaseDuration
        return minInterval / numberOfPhases
    }
}

@Serializable
enum class PositionCheckType {
    @SerialName("forward_comparison")
    FORWARD_COMPARISON,

    @SerialName("vertical_comparison")
    VERTICAL_COMPARISON,

    @SerialName("sideways_comparison")
    SIDEWAYS_COMPARISON,

    @SerialName("distance_ratio")
    DISTANCE_RATIO,

    @SerialName("horizontal_alignment")
    HORIZONTAL_ALIGNMENT,

    @SerialName("vertical_alignment")
    VERTICAL_ALIGNMENT,

    @SerialName("depth_alignment")
    DEPTH_ALIGNMENT,
}

@Serializable
enum class PositionOperator {
    @SerialName("should_not_exceed")
    SHOULD_NOT_EXCEED,

    @SerialName("should_exceed")
    SHOULD_EXCEED,

    @SerialName("approximately_equal")
    APPROXIMATELY_EQUAL,

    @SerialName("greater_than_ratio")
    GREATER_THAN_RATIO,

    @SerialName("less_than_ratio")
    LESS_THAN_RATIO,
}

@Serializable
enum class CheckSeverity {
    @SerialName("error")
    ERROR,

    @SerialName("warning")
    WARNING,

    @SerialName("tip")
    TIP,
}

@Serializable
data class LandmarkGroup(
    val primary: String = "",
    val secondary: String = "",
    val tertiary: String? = null,
    val quaternary: String? = null,
)

@Serializable
data class PositionCondition(
    val operator: PositionOperator = PositionOperator.APPROXIMATELY_EQUAL,
    val threshold: Double = 0.0,
)

@Serializable
data class PositionCheck(
    val id: String = "",
    val type: PositionCheckType = PositionCheckType.FORWARD_COMPARISON,
    val landmarks: LandmarkGroup = LandmarkGroup(),
    val condition: PositionCondition = PositionCondition(),
    val activePhases: List<String> = emptyList(),
    val errorMessage: LocalizedText = LocalizedText(),
    val severity: CheckSeverity = CheckSeverity.WARNING,
    val cooldownMs: Long = 2_000,
    val minErrorFrames: Int = 3,
)

@Serializable
data class FeedbackMessages(
    val motivational: List<LocalizedText> = emptyList(),
    @SerialName("tips")
    val tips: List<LocalizedText> = emptyList(),
)

@Serializable
data class MessageAssignment(
    val messageId: String = "",
    val target: String = "",
    val context: String? = null,
    val jointCode: String? = null,
    val zone: String? = null,
    val checkId: String? = null,
    val sortOrder: Int = 0,
)
