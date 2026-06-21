package com.movit.core.training.journal

import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.JointStateInfo
import com.movit.core.training.engine.Phase
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField

/** Phase codes for compact storage (1 byte). */
object PhaseCode {
    const val IDLE: Byte = 0
    const val ECCENTRIC: Byte = 1
    const val ISOMETRIC: Byte = 2
    const val CONCENTRIC: Byte = 3

    fun fromPhase(phase: Phase): Byte = when (phase) {
        Phase.IDLE, Phase.START -> IDLE
        Phase.DOWN -> ECCENTRIC
        Phase.BOTTOM, Phase.COUNT -> ISOMETRIC
        Phase.UP -> CONCENTRIC
    }
}

/** Sentinel in [FrameSample.angles] when a joint was skipped this frame. */
const val JOINT_SKIPPED_ANGLE_SENTINEL: Short = Short.MIN_VALUE

object StateCode {
    const val PERFECT: Byte = 0
    const val NORMAL: Byte = 1
    const val PAD: Byte = 2
    const val WARNING: Byte = 3
    const val DANGER: Byte = 4

    fun fromJointState(state: JointState): Byte = when (state) {
        JointState.PERFECT -> PERFECT
        JointState.NORMAL -> NORMAL
        JointState.PAD -> PAD
        JointState.WARNING -> WARNING
        JointState.DANGER -> DANGER
        JointState.TRANSITION -> NORMAL
    }

    fun isGood(code: Byte): Boolean = code <= NORMAL
}

@Serializable
data class FrameSample(
    val t: Int,
    val phase: Byte,
    @JvmField val angles: ShortArray,
    @JvmField val states: ByteArray? = null,
) {
    fun isJointAngleValid(jointIndex: Int): Boolean {
        val value = angles.getOrNull(jointIndex) ?: return false
        return value != JOINT_SKIPPED_ANGLE_SENTINEL
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FrameSample) return false
        if (t != other.t || phase != other.phase) return false
        if (!angles.contentEquals(other.angles)) return false
        return when {
            states == null && other.states == null -> true
            states != null && other.states != null -> states.contentEquals(other.states)
            else -> false
        }
    }

    override fun hashCode(): Int {
        var result = t
        result = 31 * result + phase
        result = 31 * result + angles.contentHashCode()
        result = 31 * result + (states?.contentHashCode() ?: 0)
        return result
    }
}

@Serializable
data class RepRecord(
    val num: Int,
    val startT: Int,
    val endT: Int,
    val frames: List<FrameSample>,
    val phases: List<Int>,
    val worstState: Byte,
    val score: Short,
    val weightKg: Float? = null,
) {
    val durationMs: Int get() = endT - startT

    fun isCounted(): Boolean = worstState <= StateCode.PAD
}

@Serializable
data class RepMetrics(
    val rom: Short,
    val symmetry: Short?,
    val stability: Short,
    val tempo: List<Int>,
    val velocity: Short?,
    val formScore: Short,
    val alignmentAccuracy: Short,
    val velocityLoss: Short? = null,
)

@Serializable
data class WorkoutExecutionMetrics(
    val avgRom: Short,
    val avgSymmetry: Short?,
    val avgStability: Short,
    val avgTempo: List<Int>,
    val avgVelocity: Short?,
    val avgFormScore: Short,
    val avgAlignmentAccuracy: Short,
    val totalTUT: Int,
    val totalVolume: Float?,
    val maxWeight: Float?,
    val est1RM: Float?,
    val formConsistency: Short? = null,
    val fatigueIndex: Short? = null,
    val velocityLoss: Short? = null,
    val tempoConsistency: Short? = null,
)

@Serializable
data class RepMetricsData(
    val num: Int,
    val durationMs: Int,
    val worstState: Byte,
    val score: Short,
    val weightKg: Float? = null,
    val side: String? = null,
    val metrics: RepMetrics,
)

@Serializable
data class WorkoutUpload(
    val id: String,
    val exerciseId: String,
    val timestamp: Long,
    val durationMs: Int,
    val totalReps: Int,
    val countedReps: Int,
    val invalidReps: Int,
    val weightKg: Float? = null,
    val weightUnit: String = "kg",
    val repMetrics: List<RepMetricsData> = emptyList(),
    val executionMetrics: WorkoutExecutionMetrics,
) {
    fun averageScorePercent(): Float = executionMetrics.avgFormScore / 10f
}

/** Durable checkpoint payload (I-22) — metrics only, no raw frames. */
@Serializable
data class SessionJournalSnapshot(
    val sessionId: String,
    val exerciseId: String,
    val trackedJoints: List<String>,
    val recordingStartMs: Long,
    val defaultWeightKg: Float? = null,
    val weightUnit: String = "kg",
    val completedRepMetrics: List<RepMetricsData> = emptyList(),
    val isAssessmentMode: Boolean = false,
    val framesOffered: Int = 0,
    val framesRecorded: Int = 0,
    val framesDropped: Int = 0,
    val jointCoverageNumerator: Long = 0,
    val jointCoverageDenominator: Long = 0,
)

fun jointStatesToByteArray(
    trackedJoints: List<String>,
    states: Map<String, JointStateInfo>?,
): ByteArray? {
    if (states == null) return null
    return ByteArray(trackedJoints.size) { index ->
        val jointName = trackedJoints[index]
        val stateInfo = states[jointName]
        if (stateInfo != null) {
            StateCode.fromJointState(stateInfo.state)
        } else {
            StateCode.NORMAL
        }
    }
}

fun anglesToShortArray(
    trackedJoints: List<String>,
    angles: Map<String, Double>,
    skippedJointCodes: Set<String>,
): ShortArray = ShortArray(trackedJoints.size) { index ->
    val jointName = trackedJoints[index]
    if (jointName in skippedJointCodes) {
        JOINT_SKIPPED_ANGLE_SENTINEL
    } else {
        val angle = angles[jointName] ?: 0.0
        (angle * 10).toInt().coerceIn(0, 1800).toShort()
    }
}
