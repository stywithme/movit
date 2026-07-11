package com.movit.core.training.geometry

import com.movit.core.training.engine.policy.VisibilityDefaults
import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame
import com.movit.core.training.model.PoseLandmarkIndices

/**
 * Builds [PoseFrame] from platform-neutral landmarks using shared angle math.
 *
 * Sticky 3D/2D state is **per-owner** via [AngleModeStickyState] (same pattern as
 * [ElbowAngleEstimator]) — do not share one instance across concurrent frame sources.
 */
object PoseFrameAssembler {
    fun assemble(
        landmarks: List<Landmark>,
        timestampMs: Long,
        isFrontCamera: Boolean,
        worldLandmarks: List<Landmark>? = null,
        analysisImageWidth: Int = 0,
        analysisImageHeight: Int = 0,
        visibilityThreshold: Float = VisibilityDefaults.ANGLE_GATE,
        applyElbowCorrection: Boolean = true,
        collectElbowDiagnostics: Boolean = false,
        estimator: ElbowAngleEstimator? = null,
        stickyState: AngleModeStickyState? = null,
    ): PoseFrame {
        val resolvedLandmarks = VirtualLandmarks.ensureAppended(landmarks)
        val aspectYScale = aspectYScale(analysisImageWidth, analysisImageHeight)
        val switched = mutableSetOf<String>()
        var angles = calculateAngles(
            landmarks = resolvedLandmarks,
            visibilityThreshold = visibilityThreshold,
            worldLandmarks = worldLandmarks,
            modeSwitchedOut = switched,
            stickyState = stickyState,
            aspectYScale = aspectYScale,
        )
        if (applyElbowCorrection && worldLandmarks != null && worldLandmarks.size >= 33) {
            val elbowEstimator = estimator ?: ElbowAngleEstimator()
            angles = elbowEstimator.correct(
                angles = angles,
                worldLandmarks = worldLandmarks,
                normLandmarks = resolvedLandmarks,
                timestampMs = timestampMs,
                aspectYScale = aspectYScale,
                collectDiagnostics = collectElbowDiagnostics,
            )
        }
        return PoseFrame(
            angles = angles,
            landmarks = resolvedLandmarks,
            worldLandmarks = worldLandmarks,
            isFrontCamera = isFrontCamera,
            timestampMs = timestampMs,
            analysisImageWidth = analysisImageWidth,
            analysisImageHeight = analysisImageHeight,
            angleModeSwitchedJointCodes = switched,
        )
    }

    fun calculateAngles(
        landmarks: List<Landmark>,
        visibilityThreshold: Float = VisibilityDefaults.ANGLE_GATE,
        worldLandmarks: List<Landmark>? = null,
        modeSwitchedOut: MutableSet<String>? = null,
        stickyState: AngleModeStickyState? = null,
        aspectYScale: Float = 1f,
    ): JointAngles {
        // WP-02 (final): exercises are authored against 3D poses — 3D is the only mode.
        // Gate on raw MediaPipe world size (33), not landmarks.size (35 after virtual append).
        val world = worldLandmarks?.takeIf { it.size >= 33 }
        val switched = modeSwitchedOut ?: mutableSetOf()
        // Ephemeral sticky when caller omits one — no cross-frame hysteresis (tests / one-shots).
        val sticky = stickyState ?: AngleModeStickyState()
        val vis = visibilityThreshold
        val yScale = aspectYScale

        fun stickyAngle(code: String, a: Int, b: Int, c: Int): Double? =
            angleAt3D(world, landmarks, a, b, c, vis, code, sticky, switched, yScale)

        // WP-18: elbows skip sticky — final values come from ElbowAngleEstimator when enabled.
        fun elbowAngle(a: Int, b: Int, c: Int): Double? =
            angleAt3DDirect(world, landmarks, a, b, c, vis, yScale)

        return JointAngles(
            leftElbow = elbowAngle(
                PoseLandmarkIndices.LEFT_SHOULDER, PoseLandmarkIndices.LEFT_ELBOW, PoseLandmarkIndices.LEFT_WRIST,
            ),
            rightElbow = elbowAngle(
                PoseLandmarkIndices.RIGHT_SHOULDER, PoseLandmarkIndices.RIGHT_ELBOW, PoseLandmarkIndices.RIGHT_WRIST,
            ),
            leftShoulder = stickyAngle(
                "left_shoulder",
                PoseLandmarkIndices.LEFT_ELBOW, PoseLandmarkIndices.LEFT_SHOULDER, PoseLandmarkIndices.LEFT_HIP,
            ),
            rightShoulder = stickyAngle(
                "right_shoulder",
                PoseLandmarkIndices.RIGHT_ELBOW, PoseLandmarkIndices.RIGHT_SHOULDER, PoseLandmarkIndices.RIGHT_HIP,
            ),
            leftShoulderCross = stickyAngle(
                "left_shoulder_cross",
                PoseLandmarkIndices.LEFT_ELBOW, PoseLandmarkIndices.LEFT_SHOULDER, PoseLandmarkIndices.RIGHT_SHOULDER,
            ),
            rightShoulderCross = stickyAngle(
                "right_shoulder_cross",
                PoseLandmarkIndices.RIGHT_ELBOW, PoseLandmarkIndices.RIGHT_SHOULDER, PoseLandmarkIndices.LEFT_SHOULDER,
            ),
            leftWrist = stickyAngle(
                "left_wrist",
                PoseLandmarkIndices.LEFT_ELBOW, PoseLandmarkIndices.LEFT_WRIST, 19,
            ),
            rightWrist = stickyAngle(
                "right_wrist",
                PoseLandmarkIndices.RIGHT_ELBOW, PoseLandmarkIndices.RIGHT_WRIST, 20,
            ),
            leftHip = stickyAngle(
                "left_hip",
                PoseLandmarkIndices.LEFT_SHOULDER, PoseLandmarkIndices.LEFT_HIP, PoseLandmarkIndices.LEFT_KNEE,
            ),
            rightHip = stickyAngle(
                "right_hip",
                PoseLandmarkIndices.RIGHT_SHOULDER, PoseLandmarkIndices.RIGHT_HIP, PoseLandmarkIndices.RIGHT_KNEE,
            ),
            leftHipCross = stickyAngle(
                "left_hip_cross",
                PoseLandmarkIndices.LEFT_KNEE, PoseLandmarkIndices.LEFT_HIP, PoseLandmarkIndices.RIGHT_HIP,
            ),
            rightHipCross = stickyAngle(
                "right_hip_cross",
                PoseLandmarkIndices.RIGHT_KNEE, PoseLandmarkIndices.RIGHT_HIP, PoseLandmarkIndices.LEFT_HIP,
            ),
            neckLeft = stickyAngle(
                "neck_left",
                PoseLandmarkIndices.LEFT_SHOULDER, VirtualLandmarks.NECK, PoseLandmarkIndices.NOSE,
            ),
            neckRight = stickyAngle(
                "neck_right",
                PoseLandmarkIndices.RIGHT_SHOULDER, VirtualLandmarks.NECK, PoseLandmarkIndices.NOSE,
            ),
            neckSpine = stickyAngle(
                "neck_spine",
                VirtualLandmarks.SPINE, VirtualLandmarks.NECK, PoseLandmarkIndices.NOSE,
            ),
            spine = spineAngle(world, landmarks, vis, sticky, switched, yScale),
            leftKnee = stickyAngle(
                "left_knee",
                PoseLandmarkIndices.LEFT_HIP, PoseLandmarkIndices.LEFT_KNEE, PoseLandmarkIndices.LEFT_ANKLE,
            ),
            rightKnee = stickyAngle(
                "right_knee",
                PoseLandmarkIndices.RIGHT_HIP, PoseLandmarkIndices.RIGHT_KNEE, PoseLandmarkIndices.RIGHT_ANKLE,
            ),
            leftAnkle = stickyAngle(
                "left_ankle",
                PoseLandmarkIndices.LEFT_KNEE, PoseLandmarkIndices.LEFT_ANKLE, PoseLandmarkIndices.LEFT_FOOT_INDEX,
            ),
            rightAnkle = stickyAngle(
                "right_ankle",
                PoseLandmarkIndices.RIGHT_KNEE, PoseLandmarkIndices.RIGHT_ANKLE, PoseLandmarkIndices.RIGHT_FOOT_INDEX,
            ),
        )
    }

    /** Aspect fix for normalized 2D: stretch y so x/y share the same metric (F3). */
    fun aspectYScale(analysisWidth: Int, analysisHeight: Int): Float {
        if (analysisWidth <= 0 || analysisHeight <= 0) return 1f
        return analysisHeight.toFloat() / analysisWidth.toFloat()
    }

    private fun spineAngle(
        world: List<Landmark>?,
        landmarks: List<Landmark>,
        visibilityThreshold: Float,
        sticky: AngleModeStickyState,
        switched: MutableSet<String>,
        aspectYScale: Float,
    ): Double? {
        // Prefer left knee as third point; fall back to right knee (legacy).
        val withLeft = angleAt3D(
            world, landmarks,
            VirtualLandmarks.NECK, VirtualLandmarks.SPINE, PoseLandmarkIndices.LEFT_KNEE,
            visibilityThreshold, "spine", sticky, switched, aspectYScale,
        )
        if (withLeft != null) return withLeft
        return angleAt3D(
            world, landmarks,
            VirtualLandmarks.NECK, VirtualLandmarks.SPINE, PoseLandmarkIndices.RIGHT_KNEE,
            visibilityThreshold, "spine", sticky, switched, aspectYScale,
        )
    }

    private fun angleAt3D(
        world: List<Landmark>?,
        landmarks: List<Landmark>,
        indexA: Int,
        indexB: Int,
        indexC: Int,
        visibilityThreshold: Float,
        jointCode: String,
        sticky: AngleModeStickyState?,
        modeSwitchedOut: MutableSet<String>,
        aspectYScale: Float,
    ): Double? {
        val angle3d = tryAngleDegrees3D(world, landmarks, indexA, indexB, indexC, visibilityThreshold)
        val use3d = if (sticky != null) {
            sticky.resolveUse3d(jointCode, wants3d = angle3d != null, modeSwitchedOut)
        } else {
            angle3d != null
        }
        if (use3d && angle3d != null) return angle3d
        return angleAt(landmarks, indexA, indexB, indexC, visibilityThreshold, aspectYScale)
    }

    private fun angleAt3DDirect(
        world: List<Landmark>?,
        landmarks: List<Landmark>,
        indexA: Int,
        indexB: Int,
        indexC: Int,
        visibilityThreshold: Float,
        aspectYScale: Float,
    ): Double? {
        val angle3d = tryAngleDegrees3D(world, landmarks, indexA, indexB, indexC, visibilityThreshold)
        if (angle3d != null) return angle3d
        return angleAt(landmarks, indexA, indexB, indexC, visibilityThreshold, aspectYScale)
    }

    /**
     * F2: gate 3D on **normalized** visibility (real model signal); use world xyz for geometry.
     * Virtual neck/spine (33/34) are midpoints from world shoulders/hips — not in the 33-point list.
     */
    private fun tryAngleDegrees3D(
        world: List<Landmark>?,
        norm: List<Landmark>,
        indexA: Int,
        indexB: Int,
        indexC: Int,
        visibilityThreshold: Float,
    ): Double? {
        if (world == null) return null
        val a = worldLandmark(world, indexA) ?: return null
        val b = worldLandmark(world, indexB) ?: return null
        val c = worldLandmark(world, indexC) ?: return null
        val na = normLandmark(norm, indexA) ?: return null
        val nb = normLandmark(norm, indexB) ?: return null
        val nc = normLandmark(norm, indexC) ?: return null
        if (!na.isVisible(visibilityThreshold) || !nb.isVisible(visibilityThreshold) || !nc.isVisible(visibilityThreshold)) {
            return null
        }
        return JointAngleCalculator.angleDegrees3D(
            PosePoint3D(a.x, a.y, a.z),
            PosePoint3D(b.x, b.y, b.z),
            PosePoint3D(c.x, c.y, c.z),
        )
    }

    private fun angleAt(
        landmarks: List<Landmark>,
        indexA: Int,
        indexB: Int,
        indexC: Int,
        visibilityThreshold: Float,
        aspectYScale: Float,
    ): Double? {
        val a = normLandmark(landmarks, indexA) ?: return null
        val b = normLandmark(landmarks, indexB) ?: return null
        val c = normLandmark(landmarks, indexC) ?: return null
        if (!a.isVisible(visibilityThreshold) || !b.isVisible(visibilityThreshold) || !c.isVisible(visibilityThreshold)) {
            return null
        }
        return JointAngleCalculator.angleDegrees(
            pointA = PosePoint2D(a.x, a.y * aspectYScale),
            pointB = PosePoint2D(b.x, b.y * aspectYScale),
            pointC = PosePoint2D(c.x, c.y * aspectYScale),
        )
    }

    private fun worldLandmark(world: List<Landmark>, index: Int): Landmark? {
        if (index < world.size) return world[index]
        if (world.size < 33) return null
        return when (index) {
            VirtualLandmarks.NECK -> midpoint(
                world[PoseLandmarkIndices.LEFT_SHOULDER],
                world[PoseLandmarkIndices.RIGHT_SHOULDER],
            )
            VirtualLandmarks.SPINE -> midpoint(
                world[PoseLandmarkIndices.LEFT_HIP],
                world[PoseLandmarkIndices.RIGHT_HIP],
            )
            else -> null
        }
    }

    private fun normLandmark(landmarks: List<Landmark>, index: Int): Landmark? {
        if (index < landmarks.size) return landmarks[index]
        return null
    }

    private fun midpoint(a: Landmark, b: Landmark): Landmark = Landmark(
        x = (a.x + b.x) / 2f,
        y = (a.y + b.y) / 2f,
        z = (a.z + b.z) / 2f,
        visibility = minOf(a.visibility, b.visibility),
        presence = minOf(a.presence, b.presence),
    )
}
