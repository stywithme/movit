package com.movit.core.training.position

import com.movit.core.training.model.Landmark

/**
 * Unified 3-axis scene detector: camera direction + body posture + visible region.
 *
 * Wraps [StableCameraDetector], [BodyPostureDetector], and [VisibleRegionDetector]
 * with independent majority-vote smoothing per axis.
 *
 * MLP posture refinement (legacy [PostureMlpClassifier]) is optional via [PoseRefiner] in WS-3;
 * v1 uses geometric [BodyPostureDetector] only (B-3).
 */
class PoseSceneDetector(
    private val windowSize: Int = 7,
    private val requiredMajority: Int = 5,
    private val postureRefiner: PosePostureRefiner = PosePostureRefiner.GeometricOnly,
) {
    private val cameraDetector = StableCameraDetector(windowSize, requiredMajority)

    private val postureWindow = ArrayDeque<BodyPosture>(windowSize)
    private val regionWindow = ArrayDeque<VisibleRegion>(windowSize)

    private var stablePosture: BodyPosture = BodyPosture.UNKNOWN
    private var stableRegion: VisibleRegion = VisibleRegion.UNKNOWN

    private var lastRawPosture: BodyPostureDetector.PostureResult? = null
    private var lastRawRegion: VisibleRegionDetector.RegionResult? = null

    fun detect(landmarks: List<Landmark>, isFrontCamera: Boolean = false): PoseSceneResult {
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
            depthInfo = camResult.depthInfo,
        )
    }

    private fun resolveCoarsePosture(landmarks: List<Landmark>): BodyPostureDetector.PostureResult {
        val refined = postureRefiner.refinePosture(landmarks)
        if (refined != null) return refined
        return BodyPostureDetector.detect(landmarks)
    }

    private fun <T> pushAndResolve(window: ArrayDeque<T>, rawValue: T, currentStable: T): T {
        if (window.size >= windowSize) window.removeFirst()
        window.addLast(rawValue)
        if (window.size < requiredMajority) return rawValue
        val counts = window.groupingBy { it }.eachCount()
        val (best, count) = counts.maxByOrNull { it.value } ?: return currentStable
        return if (count >= requiredMajority) best else currentStable
    }

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
}

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
    val depthInfo: CameraPositionDetector.DepthInfo,
)

/**
 * Optional posture refinement hook (D5). Geometric fallback is always available.
 */
fun interface PosePostureRefiner {
    fun refinePosture(landmarks: List<Landmark>): BodyPostureDetector.PostureResult?

    companion object {
        val GeometricOnly: PosePostureRefiner = PosePostureRefiner { null }
    }
}
