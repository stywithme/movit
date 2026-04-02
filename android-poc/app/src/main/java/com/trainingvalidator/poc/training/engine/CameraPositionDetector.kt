package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.BodyLandmarks
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * CameraPositionDetector — classifies camera direction and body facing from pose landmarks.
 *
 * **Rotation-invariant**: all primary signals are based on Euclidean 2D distances and
 * the 2D cross-product of the spine vector × limb-pair vectors. This means detection
 * works identically whether the person is standing, lying, or at any angle.
 *
 * Primary signals (pure 2D):
 *   • **widthRatio** — body's perceived 2D width (Euclidean) relative to torso length.
 *     High → front/back family; low → side family (no diagonal ambiguity).
 *   • **crossScore** — 2D cross-product of spine vs shoulder/hip/knee pair vectors.
 *     Positive → facing camera (front); negative → facing away (back).
 *
 * Secondary (Z-based, confidence only):
 *   • X/(X+Z) ratio as a confidence boost/penalty.
 *
 * Raw [detect] is stateless (single-frame).
 * For production use, wrap with [StableCameraDetector] for temporal smoothing.
 */
object CameraPositionDetector {

    // ── Width ratio thresholds (primary 2D signal) ───────────────────────
    // widthRatio = avg(shoulderWidth2D/torsoLen, hipWidth2D/torsoLen)
    //   High → front/back family;  Low → side family.
    // Single boundary with hysteresis — no diagonal gap.

    private const val WIDTH_BOUNDARY_ENTER = 0.58f
    private const val WIDTH_BOUNDARY_EXIT  = 0.48f

    // Z-based ratio thresholds (secondary — confidence boost/penalty only)
    private const val Z_CONFIRMS_FRONT = 0.55f
    private const val Z_CONFIRMS_SIDE = 0.45f

    // Minimum torso length (normalised) to trust detection at all
    private const val MIN_TORSO_LENGTH = 0.05f

    // Face visibility thresholds (tertiary fallback for front/back)
    private const val FACE_FRONT_THRESHOLD = 0.50f
    private const val FACE_BACK_THRESHOLD = 0.25f

    // Cross-product score: spine × limb-pair, normalised by torsoLength².
    // Positive → anatomical left is "clockwise" from spine → facing camera.
    private const val CROSS_FRONT_THRESHOLD = 0.12f
    private const val CROSS_BACK_THRESHOLD = -0.12f

    // Minimum Z difference to determine closer body side
    private const val CLOSER_SIDE_Z_MIN = 0.03f

    // Minimum visibility differential to use as tiebreaker for side L/R
    private const val CLOSER_SIDE_VIS_MIN = 0.08f

    // ── Data classes & enums ────────────────────────────────────────────

    data class CameraDetectionResult(
        val position: DetectedCameraPosition,
        val confidence: Float,
        val facingDirection: DetectedFacing,
        val closerSide: BodySide,
        val depthInfo: DepthInfo
    )

    enum class DetectedCameraPosition {
        FRONT_VIEW, BACK_VIEW,
        SIDE_VIEW_LEFT, SIDE_VIEW_RIGHT,
        DIAGONAL, UNKNOWN
    }

    enum class DetectedFacing {
        FACING_RIGHT, FACING_LEFT,
        FACING_CAMERA, FACING_AWAY,
        FACING_UP, FACING_DOWN,
        UNKNOWN
    }

    enum class BodySide { LEFT, RIGHT, BOTH_EQUAL, UNKNOWN }

    data class DepthInfo(
        val leftShoulderZ: Float, val rightShoulderZ: Float,
        val leftHipZ: Float, val rightHipZ: Float,
        val leftKneeZ: Float, val rightKneeZ: Float,
        val averageBodyZ: Float, val bodyDepthRange: Float
    )

    /**
     * Intermediate metrics — computed once and shared between the stateless
     * [detect] and the hysteresis-aware [StableCameraDetector].
     *
     * All "primary" fields are rotation-invariant (Euclidean 2D + cross-product).
     */
    internal data class DetectionMetrics(
        val widthRatio: Float,
        val crossScore: Float,
        val combinedRatio: Float,
        val xzRatio: Float,
        val hipXZRatio: Float,
        val faceScore: Float,
        val visibilityDiff: Float,
        val sideZDiff: Float,
        val torsoLength: Float,
        val shoulderWidth2D: Float,
        val hipWidth2D: Float,
        val shoulderZDiff: Float
    )

    // ── Stateless single-frame detection ────────────────────────────────

    fun detect(landmarks: List<SmoothedLandmark>): CameraDetectionResult {
        val metrics = computeMetrics(landmarks) ?: return createUnknownResult()
        val (position, confidence) = classifyPosition(metrics, previousPosition = null)
        val closerSide = classifyCloserSide(metrics)
        val facing = classifyFacing(position, closerSide, metrics)
        return CameraDetectionResult(position, confidence, facing, closerSide, computeDepthInfo(landmarks))
    }

    // ── Metrics (rotation-invariant) ─────────────────────────────────────

    internal fun computeMetrics(landmarks: List<SmoothedLandmark>): DetectionMetrics? {
        if (landmarks.size < 33) return null

        val lShoulder = landmarks[BodyLandmarks.LEFT_SHOULDER]
        val rShoulder = landmarks[BodyLandmarks.RIGHT_SHOULDER]
        val lHip = landmarks[BodyLandmarks.LEFT_HIP]
        val rHip = landmarks[BodyLandmarks.RIGHT_HIP]
        val lKnee = landmarks[BodyLandmarks.LEFT_KNEE]
        val rKnee = landmarks[BodyLandmarks.RIGHT_KNEE]

        // Spine vector: shoulder-midpoint → hip-midpoint
        val shoulderCx = (lShoulder.x + rShoulder.x) / 2f
        val shoulderCy = (lShoulder.y + rShoulder.y) / 2f
        val hipCx = (lHip.x + rHip.x) / 2f
        val hipCy = (lHip.y + rHip.y) / 2f
        val spineX = hipCx - shoulderCx
        val spineY = hipCy - shoulderCy
        val torsoLength = sqrt(spineX * spineX + spineY * spineY)
        if (torsoLength < MIN_TORSO_LENGTH) return null

        // Limb-pair vectors (left − right)
        val shoulderDx = lShoulder.x - rShoulder.x
        val shoulderDy = lShoulder.y - rShoulder.y
        val hipDx = lHip.x - rHip.x
        val hipDy = lHip.y - rHip.y
        val kneeDx = lKnee.x - rKnee.x
        val kneeDy = lKnee.y - rKnee.y

        // ── Primary 2D: Euclidean widths ──
        val shoulderWidth2D = sqrt(shoulderDx * shoulderDx + shoulderDy * shoulderDy)
        val hipWidth2D = sqrt(hipDx * hipDx + hipDy * hipDy)
        val widthRatio = (shoulderWidth2D / torsoLength + hipWidth2D / torsoLength) / 2f

        // ── Primary 2D: cross-product for front/back ──
        // cross = spine.dy * pair.dx − spine.dx * pair.dy
        // Positive → facing camera, Negative → facing away (rotation-invariant)
        val shoulderCross = spineY * shoulderDx - spineX * shoulderDy
        val hipCross = spineY * hipDx - spineX * hipDy
        val kneeCross = spineY * kneeDx - spineX * kneeDy
        val tSq = torsoLength * torsoLength
        val crossScore = (shoulderCross * 0.4f + hipCross * 0.4f + kneeCross * 0.2f) / tSq

        // ── Secondary Z-based signals ──
        val shoulderZDiff = abs(lShoulder.z - rShoulder.z)
        val hipZDiff = abs(lHip.z - rHip.z)
        val eps = 0.001f
        val xzRatio = shoulderWidth2D / (shoulderWidth2D + shoulderZDiff + eps)
        val hipXZRatio = hipWidth2D / (hipWidth2D + hipZDiff + eps)

        // Z-depth difference for side L/R
        val avgLeftZ = (lShoulder.z + lHip.z + lKnee.z) / 3f
        val avgRightZ = (rShoulder.z + rHip.z + rKnee.z) / 3f

        // Visibility differential for side L/R support
        val leftVis = (lShoulder.visibility + lHip.visibility + lKnee.visibility) / 3f
        val rightVis = (rShoulder.visibility + rHip.visibility + rKnee.visibility) / 3f

        return DetectionMetrics(
            widthRatio = widthRatio,
            crossScore = crossScore,
            combinedRatio = (xzRatio + hipXZRatio) / 2f,
            xzRatio = xzRatio,
            hipXZRatio = hipXZRatio,
            faceScore = computeFaceVisibilityScore(landmarks),
            visibilityDiff = leftVis - rightVis,
            sideZDiff = avgLeftZ - avgRightZ,
            torsoLength = torsoLength,
            shoulderWidth2D = shoulderWidth2D,
            hipWidth2D = hipWidth2D,
            shoulderZDiff = shoulderZDiff
        )
    }

    // ── Classification ──────────────────────────────────────────────────

    internal fun classifyPosition(
        metrics: DetectionMetrics,
        previousPosition: DetectedCameraPosition?
    ): Pair<DetectedCameraPosition, Float> {
        val width = metrics.widthRatio

        val wasFrontBack = previousPosition == DetectedCameraPosition.FRONT_VIEW ||
                previousPosition == DetectedCameraPosition.BACK_VIEW

        // Hysteresis: harder to enter front/back, easier to stay
        val threshold = if (wasFrontBack) WIDTH_BOUNDARY_EXIT else WIDTH_BOUNDARY_ENTER

        return if (width >= threshold) {
            // ── Front / Back family ──
            // Confidence: 0.5 at the boundary, 1.0 at width=1.0
            val range = (1f - threshold).coerceAtLeast(0.01f)
            val baseConf = 0.5f + 0.5f * ((width - threshold) / range).coerceIn(0f, 1f)
            val conf = if (metrics.combinedRatio > Z_CONFIRMS_FRONT) baseConf
                       else (baseConf * 0.90f).coerceAtLeast(0.5f)

            when {
                metrics.crossScore > CROSS_FRONT_THRESHOLD ->
                    DetectedCameraPosition.FRONT_VIEW to conf
                metrics.crossScore < CROSS_BACK_THRESHOLD ->
                    DetectedCameraPosition.BACK_VIEW to conf
                metrics.faceScore > FACE_FRONT_THRESHOLD ->
                    DetectedCameraPosition.FRONT_VIEW to (conf * 0.85f)
                metrics.faceScore < FACE_BACK_THRESHOLD ->
                    DetectedCameraPosition.BACK_VIEW to (conf * 0.85f)
                previousPosition == DetectedCameraPosition.BACK_VIEW ->
                    DetectedCameraPosition.BACK_VIEW to 0.50f
                else ->
                    DetectedCameraPosition.FRONT_VIEW to 0.50f
            }
        } else {
            // ── Side family ──
            // Confidence: 0.5 at the boundary, 1.0 at width=0
            val baseConf = 0.5f + 0.5f * (1f - width / threshold.coerceAtLeast(0.01f))
            val conf = if (metrics.combinedRatio < Z_CONFIRMS_SIDE) baseConf
                       else (baseConf * 0.90f).coerceAtLeast(0.5f)

            // Determine L/R using visibility (reliable) then Z-depth (noisy),
            // with dead zones to prevent flickering on ambiguous frames.
            val side = classifySideDirection(metrics, previousPosition)
            side to conf
        }
    }

    /**
     * Determine SIDE_VIEW_LEFT vs SIDE_VIEW_RIGHT using a multi-signal
     * approach with dead zones:
     *   1. Visibility diff (most reliable for side views — the visible side
     *      has much higher landmark visibility than the occluded side)
     *   2. Z-depth diff (secondary, only trusted above CLOSER_SIDE_Z_MIN)
     *   3. Hold previous position when both signals are ambiguous
     */
    private fun classifySideDirection(
        metrics: DetectionMetrics,
        previousPosition: DetectedCameraPosition?
    ): DetectedCameraPosition {
        // 1) Visibility: the side facing the camera has higher visibility
        if (abs(metrics.visibilityDiff) >= CLOSER_SIDE_VIS_MIN) {
            return if (metrics.visibilityDiff > 0)
                DetectedCameraPosition.SIDE_VIEW_LEFT
            else
                DetectedCameraPosition.SIDE_VIEW_RIGHT
        }

        // 2) Z-depth with dead zone
        if (abs(metrics.sideZDiff) >= CLOSER_SIDE_Z_MIN) {
            return if (metrics.sideZDiff < 0)
                DetectedCameraPosition.SIDE_VIEW_LEFT
            else
                DetectedCameraPosition.SIDE_VIEW_RIGHT
        }

        // 3) Ambiguous — hold previous side to prevent flickering
        return when (previousPosition) {
            DetectedCameraPosition.SIDE_VIEW_LEFT -> DetectedCameraPosition.SIDE_VIEW_LEFT
            DetectedCameraPosition.SIDE_VIEW_RIGHT -> DetectedCameraPosition.SIDE_VIEW_RIGHT
            else -> DetectedCameraPosition.SIDE_VIEW_RIGHT
        }
    }

    internal fun classifyCloserSide(metrics: DetectionMetrics): BodySide {
        if (abs(metrics.sideZDiff) >= CLOSER_SIDE_Z_MIN) {
            return if (metrics.sideZDiff < 0) BodySide.LEFT else BodySide.RIGHT
        }
        if (abs(metrics.visibilityDiff) >= CLOSER_SIDE_VIS_MIN) {
            return if (metrics.visibilityDiff > 0) BodySide.LEFT else BodySide.RIGHT
        }
        return BodySide.BOTH_EQUAL
    }

    internal fun classifyFacing(
        position: DetectedCameraPosition,
        closerSide: BodySide,
        metrics: DetectionMetrics
    ): DetectedFacing = when (position) {
        DetectedCameraPosition.FRONT_VIEW -> DetectedFacing.FACING_CAMERA
        DetectedCameraPosition.BACK_VIEW -> DetectedFacing.FACING_AWAY

        DetectedCameraPosition.SIDE_VIEW_LEFT,
        DetectedCameraPosition.SIDE_VIEW_RIGHT -> {
            // For side views, the crossScore tells us whether the body's
            // anatomical front faces toward or away from the camera.
            // Strong cross → body is partly rotated → FACING_UP (supine-like)
            // or FACING_DOWN (prone-like). Weak cross → pure side view.
            when {
                metrics.crossScore > CROSS_FRONT_THRESHOLD -> DetectedFacing.FACING_UP
                metrics.crossScore < CROSS_BACK_THRESHOLD -> DetectedFacing.FACING_DOWN
                closerSide == BodySide.LEFT -> DetectedFacing.FACING_RIGHT
                closerSide == BodySide.RIGHT -> DetectedFacing.FACING_LEFT
                else -> DetectedFacing.UNKNOWN
            }
        }

        else -> DetectedFacing.UNKNOWN
    }

    // ── Face visibility (public — used by BodyPostureDetector & Debug) ──

    fun computeFaceVisibilityScore(landmarks: List<SmoothedLandmark>): Float {
        val nose = landmarks.getOrNull(BodyLandmarks.NOSE)?.visibility ?: 0f
        val leftEye = landmarks.getOrNull(BodyLandmarks.LEFT_EYE)?.visibility ?: 0f
        val rightEye = landmarks.getOrNull(BodyLandmarks.RIGHT_EYE)?.visibility ?: 0f
        val leftEar = landmarks.getOrNull(BodyLandmarks.LEFT_EAR)?.visibility ?: 0f
        val rightEar = landmarks.getOrNull(BodyLandmarks.RIGHT_EAR)?.visibility ?: 0f
        return nose * 0.30f + leftEye * 0.20f + rightEye * 0.20f +
                leftEar * 0.15f + rightEar * 0.15f
    }

    // ── Depth info ──────────────────────────────────────────────────────

    internal fun computeDepthInfo(landmarks: List<SmoothedLandmark>): DepthInfo {
        val lS = landmarks[BodyLandmarks.LEFT_SHOULDER]
        val rS = landmarks[BodyLandmarks.RIGHT_SHOULDER]
        val lH = landmarks[BodyLandmarks.LEFT_HIP]
        val rH = landmarks[BodyLandmarks.RIGHT_HIP]
        val lK = landmarks[BodyLandmarks.LEFT_KNEE]
        val rK = landmarks[BodyLandmarks.RIGHT_KNEE]

        val indices = intArrayOf(
            BodyLandmarks.LEFT_SHOULDER, BodyLandmarks.RIGHT_SHOULDER,
            BodyLandmarks.LEFT_HIP, BodyLandmarks.RIGHT_HIP,
            BodyLandmarks.LEFT_KNEE, BodyLandmarks.RIGHT_KNEE
        )
        var minZ = Float.MAX_VALUE; var maxZ = Float.MIN_VALUE
        for (i in indices) {
            val z = landmarks.getOrNull(i)?.z ?: continue
            if (z < minZ) minZ = z
            if (z > maxZ) maxZ = z
        }

        return DepthInfo(
            leftShoulderZ = lS.z, rightShoulderZ = rS.z,
            leftHipZ = lH.z, rightHipZ = rH.z,
            leftKneeZ = lK.z, rightKneeZ = rK.z,
            averageBodyZ = (lS.z + rS.z + lH.z + rH.z) / 4f,
            bodyDepthRange = if (minZ <= maxZ) maxZ - minZ else 0f
        )
    }

    // ── Utilities ───────────────────────────────────────────────────────

    internal fun createUnknownResult() = CameraDetectionResult(
        DetectedCameraPosition.UNKNOWN, 0f,
        DetectedFacing.UNKNOWN, BodySide.UNKNOWN,
        DepthInfo(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    )

    fun toJsonCameraPosition(detected: DetectedCameraPosition): String = when (detected) {
        DetectedCameraPosition.FRONT_VIEW -> "front_view"
        DetectedCameraPosition.BACK_VIEW -> "back_view"
        DetectedCameraPosition.SIDE_VIEW_LEFT,
        DetectedCameraPosition.SIDE_VIEW_RIGHT -> "side_view"
        DetectedCameraPosition.DIAGONAL -> "diagonal"
        DetectedCameraPosition.UNKNOWN -> "unknown"
    }

    fun matchesExpected(detected: DetectedCameraPosition, expected: String): Boolean = when (expected) {
        "side_view" -> detected == DetectedCameraPosition.SIDE_VIEW_LEFT ||
                detected == DetectedCameraPosition.SIDE_VIEW_RIGHT
        "front_view" -> detected == DetectedCameraPosition.FRONT_VIEW
        "back_view" -> detected == DetectedCameraPosition.BACK_VIEW
        else -> true
    }
}

// ═════════════════════════════════════════════════════════════════════════
// StableCameraDetector — temporal smoothing wrapper
// ═════════════════════════════════════════════════════════════════════════

/**
 * Wraps [CameraPositionDetector] with a rolling-majority window and
 * per-frame hysteresis to produce a stable camera position that doesn't flicker.
 *
 * **Warm-up**: while fewer than [requiredMajority] frames are collected,
 * the raw detection is trusted directly so that the very first frame
 * (image mode, or start of video) returns a real value instead of UNKNOWN.
 *
 * Create one instance per training session / validator lifetime.
 */
class StableCameraDetector(
    private val windowSize: Int = 7,
    private val requiredMajority: Int = 5
) {
    private val positionWindow = ArrayDeque<CameraPositionDetector.DetectedCameraPosition>(windowSize)
    private val facingWindow = ArrayDeque<CameraPositionDetector.DetectedFacing>(windowSize)

    private var stablePosition = CameraPositionDetector.DetectedCameraPosition.UNKNOWN
    private var stableFacing = CameraPositionDetector.DetectedFacing.UNKNOWN
    private var lastRawResult: CameraPositionDetector.CameraDetectionResult? = null

    fun detect(landmarks: List<SmoothedLandmark>, isFrontCamera: Boolean = false): CameraPositionDetector.CameraDetectionResult {
        val metrics = CameraPositionDetector.computeMetrics(landmarks)
            ?: return CameraPositionDetector.createUnknownResult()

        val (rawPos, confidence) = CameraPositionDetector.classifyPosition(
            metrics, previousPosition = stablePosition
        )
        val rawCloserSide = CameraPositionDetector.classifyCloserSide(metrics)

        // Front camera mirrors the image horizontally: MediaPipe's LEFT landmarks map to
        // the person's anatomical RIGHT side and vice versa. Flip only the left/right side
        // labels; front/back are unaffected by horizontal mirroring.
        val correctedPos = if (isFrontCamera) flipSide(rawPos) else rawPos
        val correctedCloserSide = if (isFrontCamera) flipBodySide(rawCloserSide) else rawCloserSide

        val facing = CameraPositionDetector.classifyFacing(correctedPos, correctedCloserSide, metrics)
        val depthInfo = CameraPositionDetector.computeDepthInfo(landmarks)

        val rawResult = CameraPositionDetector.CameraDetectionResult(
            correctedPos, confidence, facing, correctedCloserSide, depthInfo
        )
        lastRawResult = rawResult

        pushWindow(positionWindow, correctedPos)
        pushWindow(facingWindow, facing)

        stablePosition = resolveStable(positionWindow, stablePosition, correctedPos)
        stableFacing = resolveStable(facingWindow, stableFacing, facing)

        return CameraPositionDetector.CameraDetectionResult(
            stablePosition, confidence, stableFacing, correctedCloserSide, depthInfo
        )
    }

    private fun flipSide(pos: CameraPositionDetector.DetectedCameraPosition) = when (pos) {
        CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT ->
            CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT
        CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_RIGHT ->
            CameraPositionDetector.DetectedCameraPosition.SIDE_VIEW_LEFT
        else -> pos
    }

    private fun flipBodySide(side: CameraPositionDetector.BodySide) = when (side) {
        CameraPositionDetector.BodySide.LEFT -> CameraPositionDetector.BodySide.RIGHT
        CameraPositionDetector.BodySide.RIGHT -> CameraPositionDetector.BodySide.LEFT
        else -> side
    }

    private fun <T> pushWindow(window: ArrayDeque<T>, value: T) {
        if (window.size >= windowSize) window.removeFirst()
        window.addLast(value)
    }

    /**
     * Warm-up: trust raw value when the window has fewer than [requiredMajority]
     * frames. Otherwise require a majority vote to change.
     */
    private fun <T> resolveStable(window: ArrayDeque<T>, current: T, rawValue: T): T {
        if (window.size < requiredMajority) return rawValue

        val counts = window.groupingBy { it }.eachCount()
        val (best, count) = counts.maxByOrNull { it.value } ?: return current
        return if (count >= requiredMajority) best else current
    }

    fun getLastRawResult(): CameraPositionDetector.CameraDetectionResult? = lastRawResult
    fun getStablePosition(): CameraPositionDetector.DetectedCameraPosition = stablePosition
    fun getStableFacing(): CameraPositionDetector.DetectedFacing = stableFacing

    fun reset() {
        positionWindow.clear()
        facingWindow.clear()
        stablePosition = CameraPositionDetector.DetectedCameraPosition.UNKNOWN
        stableFacing = CameraPositionDetector.DetectedFacing.UNKNOWN
        lastRawResult = null
    }
}
