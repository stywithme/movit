package com.movit.feature.trainingdebug.lab

import com.movit.core.training.geometry.ElbowAngleEstimator
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseLandmarkIndices
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * WP-26 / M-E — the measurement gate.
 *
 * Runs every candidate elbow method over the same recorded landmark streams and reports the
 * numbers that decide which one ships. This exists because the decision must be made with
 * data: every method here is defensible on paper, and paper is exactly what has been wrong
 * about this problem before.
 *
 * **The headline metric is [ElbowMethodMetrics.crossCameraSpread]**, not MAE. A method that is
 * 10° off but reports the *same* 10° from every camera position is a calibration problem — one
 * you fix once, in the exercise thresholds. A method whose answer swings 40° when the user
 * turns their phone is unusable no matter how good its average looks, because the user has no
 * way to know which reading they are getting.
 *
 * Note on the baseline: the pre-WP-21 arbiter cannot be replayed here — it was replaced, not
 * kept behind a flag. Comparisons are against the raw signals, which are the meaningful floor
 * anyway: a method that cannot beat `RAW_2D` has earned nothing.
 */
object ElbowMethodComparator {

    fun compare(recordings: List<ElbowLabRecording>): ElbowComparisonReport {
        val methods = ElbowLabMethod.entries.map { method ->
            method to recordings.map { recording -> runMethod(method, recording) }
        }
        return ElbowComparisonReport(
            recordingCount = recordings.size,
            frameCount = recordings.sumOf { it.frames.size },
            metrics = methods.associate { (method, runs) -> method to summarise(runs) },
        )
    }

    /** Replays one recording through one method, returning the per-frame outputs. */
    fun runMethod(method: ElbowLabMethod, recording: ElbowLabRecording): ElbowLabRun {
        val estimator = when (method) {
            ElbowLabMethod.ARBITER -> ElbowAngleEstimator()
            ElbowLabMethod.ARBITER_WITH_RECONSTRUCTION ->
                ElbowAngleEstimator().apply { reconstructionEnabled = true }
            else -> null
        }
        val aspectYScale = PoseFrameAssembler.aspectYScale(
            recording.analysisImageWidth,
            recording.analysisImageHeight,
        )
        val angles = mutableListOf<Double>()
        val confidences = mutableListOf<Float>()
        for (frame in recording.frames) {
            val raw = PoseFrameAssembler.calculateAngles(
                landmarks = frame.normalized,
                worldLandmarks = frame.world,
                aspectYScale = aspectYScale,
            )
            val value = when (method) {
                ElbowLabMethod.RAW_2D -> angle2D(frame, recording.side, aspectYScale)
                ElbowLabMethod.RAW_3D -> sideAngle(raw, recording.side)
                ElbowLabMethod.ARBITER,
                ElbowLabMethod.ARBITER_WITH_RECONSTRUCTION,
                -> {
                    val corrected = estimator!!.correct(
                        angles = raw,
                        worldLandmarks = frame.world,
                        normLandmarks = frame.normalized,
                        timestampMs = frame.timestampMs,
                        aspectYScale = aspectYScale,
                    )
                    confidences += estimator.lastConfidence[recording.side.index]
                    sideAngle(corrected, recording.side)
                }
            }
            if (value != null && !value.isNaN()) angles += value
        }
        return ElbowLabRun(
            method = method,
            recordingId = recording.id,
            cameraPosition = recording.cameraPosition,
            trueAngleDeg = recording.trueAngleDeg,
            angles = angles,
            meanConfidence = confidences.takeIf { it.isNotEmpty() }?.let { list ->
                list.fold(0f) { acc, v -> acc + if (v.isNaN()) 0f else v } / list.size
            },
        )
    }

    private fun summarise(runs: List<ElbowLabRun>): ElbowMethodMetrics {
        val errors = runs.flatMap { run -> run.angles.map { abs(it - run.trueAngleDeg) } }
        val byPose = runs.groupBy { it.trueAngleDeg }
        val spreads = byPose.values.mapNotNull { poseRuns ->
            // Same pose, different camera positions: how far apart do the answers land?
            val perCameraMeans = poseRuns
                .filter { it.angles.isNotEmpty() }
                .map { run -> run.angles.average() }
            if (perCameraMeans.size < 2) null else standardDeviation(perCameraMeans)
        }
        return ElbowMethodMetrics(
            samples = errors.size,
            meanAbsoluteErrorDeg = errors.averageOrZero(),
            p95AbsoluteErrorDeg = errors.percentile(0.95),
            maxAbsoluteErrorDeg = errors.maxOrNull() ?: 0.0,
            crossCameraSpread = spreads.averageOrZero(),
            meanConfidence = runs.mapNotNull { it.meanConfidence }
                .takeIf { it.isNotEmpty() }
                ?.let { list -> list.sum() / list.size },
        )
    }

    private fun angle2D(frame: ElbowLabFrame, side: ElbowLabSide, aspectYScale: Float): Double? {
        val shoulder = frame.normalized.getOrNull(side.shoulderIndex) ?: return null
        val elbow = frame.normalized.getOrNull(side.elbowIndex) ?: return null
        val wrist = frame.normalized.getOrNull(side.wristIndex) ?: return null
        val ax = shoulder.x - elbow.x
        val ay = (shoulder.y - elbow.y) * aspectYScale
        val bx = wrist.x - elbow.x
        val by = (wrist.y - elbow.y) * aspectYScale
        val magA = sqrt(ax * ax + ay * ay)
        val magB = sqrt(bx * bx + by * by)
        if (magA < 1e-7f || magB < 1e-7f) return null
        val cos = ((ax * bx + ay * by) / (magA * magB)).coerceIn(-1f, 1f)
        return kotlin.math.acos(cos.toDouble()) * 180.0 / kotlin.math.PI
    }

    private fun sideAngle(angles: JointAngles, side: ElbowLabSide): Double? =
        if (side == ElbowLabSide.LEFT) angles.leftElbow else angles.rightElbow

    private fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

    private fun List<Double>.percentile(fraction: Double): Double {
        if (isEmpty()) return 0.0
        val sorted = sorted()
        val index = ((sorted.size - 1) * fraction).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}

enum class ElbowLabMethod {
    /** Screen-space projection only — the floor any method must beat. */
    RAW_2D,

    /** MediaPipe world landmarks, uncorrected. */
    RAW_3D,

    /** Production arbiter after WP-21/22. */
    ARBITER,

    /** Arbiter with the WP-25 bone-length reconstruction enabled. */
    ARBITER_WITH_RECONSTRUCTION,
}

enum class ElbowLabSide(val index: Int) {
    LEFT(0),
    RIGHT(1),
    ;

    val shoulderIndex: Int
        get() = if (this == LEFT) PoseLandmarkIndices.LEFT_SHOULDER else PoseLandmarkIndices.RIGHT_SHOULDER
    val elbowIndex: Int
        get() = if (this == LEFT) PoseLandmarkIndices.LEFT_ELBOW else PoseLandmarkIndices.RIGHT_ELBOW
    val wristIndex: Int
        get() = if (this == LEFT) PoseLandmarkIndices.LEFT_WRIST else PoseLandmarkIndices.RIGHT_WRIST
}

/**
 * One capture: a fixed, protractor-measured elbow angle held while the camera sits at a known
 * position. Several recordings of the *same* [trueAngleDeg] from different [cameraPosition]s
 * are what make [ElbowMethodMetrics.crossCameraSpread] meaningful.
 */
data class ElbowLabRecording(
    val id: String,
    val trueAngleDeg: Double,
    val cameraPosition: String,
    val side: ElbowLabSide,
    val analysisImageWidth: Int,
    val analysisImageHeight: Int,
    val frames: List<ElbowLabFrame>,
)

data class ElbowLabFrame(
    val timestampMs: Long,
    val normalized: List<Landmark>,
    val world: List<Landmark>,
)

data class ElbowLabRun(
    val method: ElbowLabMethod,
    val recordingId: String,
    val cameraPosition: String,
    val trueAngleDeg: Double,
    val angles: List<Double>,
    val meanConfidence: Float?,
)

data class ElbowMethodMetrics(
    val samples: Int,
    val meanAbsoluteErrorDeg: Double,
    val p95AbsoluteErrorDeg: Double,
    val maxAbsoluteErrorDeg: Double,
    /**
     * Average standard deviation of the reported angle for one pose across camera positions.
     * **The gate metric** — the definition of the problem this whole track exists to fix.
     */
    val crossCameraSpread: Double,
    val meanConfidence: Float?,
)

data class ElbowComparisonReport(
    val recordingCount: Int,
    val frameCount: Int,
    val metrics: Map<ElbowLabMethod, ElbowMethodMetrics>,
) {
    /** Fixed-width table for the debug lab / clipboard export. */
    fun formatTable(): String = buildString {
        appendLine("recordings=$recordingCount frames=$frameCount")
        appendLine("method                       MAE     P95     MAX   xCam   conf")
        for (method in ElbowLabMethod.entries) {
            val m = metrics[method] ?: continue
            append(method.name.padEnd(28))
            append(format(m.meanAbsoluteErrorDeg).padStart(6))
            append(format(m.p95AbsoluteErrorDeg).padStart(8))
            append(format(m.maxAbsoluteErrorDeg).padStart(8))
            append(format(m.crossCameraSpread).padStart(7))
            append((m.meanConfidence?.let { format(it.toDouble()) } ?: "-").padStart(7))
            appendLine()
        }
    }

    private fun format(value: Double): String {
        val scaled = kotlin.math.round(value * 10.0) / 10.0
        return scaled.toString()
    }
}
