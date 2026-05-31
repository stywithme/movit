package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.PoseApp
import com.trainingvalidator.poc.analysis.SmoothedLandmark

/**
 * Unified 3-axis scene detector: camera direction + body posture + visible region.
 *
 * Wraps [StableCameraDetector], [BodyPostureDetector], and [VisibleRegionDetector]
 * with independent majority-vote smoothing per axis, yielding a stable
 * [PoseSceneResult] that doesn't flicker frame to frame.
 *
 * **Warm-up**: while fewer than [requiredMajority] frames have been collected,
 * the raw single-frame result is trusted directly so the first camera frame gets
 * an immediate real value rather than UNKNOWN.
 *
 * Create one instance per training session / validator lifetime.
 */
class PoseSceneDetector(
    private val windowSize: Int = 7,
    private val requiredMajority: Int = 5
) {

    companion object {
        /** Below this softmax max-probability, fall back to [BodyPostureDetector]. */
        private const val MIN_MLP_CONFIDENCE = 0.45f
    }
    private val cameraDetector = StableCameraDetector(windowSize, requiredMajority)

    private val postureWindow = ArrayDeque<BodyPosture>(windowSize)
    private val regionWindow = ArrayDeque<VisibleRegion>(windowSize)

    private var stablePosture: BodyPosture = BodyPosture.UNKNOWN
    private var stableRegion: VisibleRegion = VisibleRegion.UNKNOWN

    private var lastRawPosture: BodyPostureDetector.PostureResult? = null
    private var lastRawRegion: VisibleRegionDetector.RegionResult? = null

    fun detect(landmarks: List<SmoothedLandmark>, isFrontCamera: Boolean = false): PoseSceneResult {
        val camResult = cameraDetector.detect(landmarks, isFrontCamera)

        val rawPosture = resolveCoarsePosture(landmarks)
        lastRawPosture = rawPosture
        stablePosture = pushAndResolve(postureWindow, rawPosture.posture, stablePosture)

        val rawRegion = VisibleRegionDetector.detect(landmarks)
        lastRawRegion = rawRegion
        stableRegion = pushAndResolve(regionWindow, rawRegion.region, stableRegion)

        return PoseSceneResult(
            direction = camResult.position,
            directionConfidence = camResult.confidence,
            facing = camResult.facingDirection,
            closerSide = camResult.closerSide,
            posture = stablePosture,
            postureConfidence = rawPosture.confidence,
            bodyAxisAngleDeg = rawPosture.bodyAxisAngleDeg,
            region = stableRegion,
            upperScore = rawRegion.upperScore,
            coreScore = rawRegion.coreScore,
            lowerScore = rawRegion.lowerScore,
            depthInfo = camResult.depthInfo
        )
    }

    /**
     * Push a value into the window and return the resolved stable value.
     * During warm-up (fewer than [requiredMajority] frames), the raw value
     * is trusted directly.
     */
    private fun <T> pushAndResolve(window: ArrayDeque<T>, rawValue: T, currentStable: T): T {
        if (window.size >= windowSize) window.removeFirst()
        window.addLast(rawValue)

        if (window.size < requiredMajority) return rawValue

        val counts = window.groupingBy { it }.eachCount()
        val (best, count) = counts.maxByOrNull { it.value } ?: return currentStable
        return if (count >= requiredMajority) best else currentStable
    }

    // ── Accessors ───────────────────────────────────────────────────────

    fun getLastRawPosture(): BodyPostureDetector.PostureResult? = lastRawPosture
    fun getLastRawRegion(): VisibleRegionDetector.RegionResult? = lastRawRegion
    fun getStableCameraDetector(): StableCameraDetector = cameraDetector

    fun reset() {
        cameraDetector.reset()
        postureWindow.clear()
        regionWindow.clear()
        stablePosture = BodyPosture.UNKNOWN
        stableRegion = VisibleRegion.UNKNOWN
        lastRawPosture = null
        lastRawRegion = null
    }

    /**
     * Uses [PostureMlpClassifier] when `posture_mlp.tflite` + `posture_mlp_norm.json` exist in assets;
     * otherwise [BodyPostureDetector]. Lying class (2) is refined with geometric sub-types when possible.
     */
    private fun resolveCoarsePosture(landmarks: List<SmoothedLandmark>): BodyPostureDetector.PostureResult {
        val mlp = runCatching { PostureMlpClassifier.getOrNull(PoseApp.instance) }.getOrNull()
            ?: return BodyPostureDetector.detect(landmarks)
        val pred = mlp.predictFromLandmarks(landmarks)
            ?: return BodyPostureDetector.detect(landmarks)
        if (pred.confidence < MIN_MLP_CONFIDENCE) {
            return BodyPostureDetector.detect(landmarks)
        }
        val angleDeg = PostureMlpFeatureExtractor.computeBodyAxisAngleDeg(landmarks)
        val posture = when (pred.classIndex) {
            0 -> BodyPosture.STANDING
            1 -> BodyPosture.SITTING
            else -> refineLyingSubtype(landmarks)
        }
        return BodyPostureDetector.PostureResult(posture, pred.confidence, angleDeg)
    }

    private fun refineLyingSubtype(landmarks: List<SmoothedLandmark>): BodyPosture {
        val geo = BodyPostureDetector.detect(landmarks)
        return when (geo.posture) {
            BodyPosture.LYING_PRONE, BodyPosture.LYING_SUPINE, BodyPosture.LYING_SIDE -> geo.posture
            else -> BodyPosture.LYING_SUPINE
        }
    }
}

/**
 * Full 3-axis detection result: direction, posture, and visible region.
 */
data class PoseSceneResult(
    val direction: CameraPositionDetector.DetectedCameraPosition,
    val directionConfidence: Float,
    val facing: CameraPositionDetector.DetectedFacing,
    val closerSide: CameraPositionDetector.BodySide,

    val posture: BodyPosture,
    val postureConfidence: Float,
    val bodyAxisAngleDeg: Float,

    val region: VisibleRegion,
    val upperScore: Float,
    val coreScore: Float,
    val lowerScore: Float,

    val depthInfo: CameraPositionDetector.DepthInfo
)
