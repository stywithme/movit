package com.movit.feature.trainingdebug

import com.movit.core.training.geometry.JointAngleCalculator
import com.movit.core.training.geometry.JointLandmarkMapping
import com.movit.core.training.geometry.PoseLandmarkMirroring
import com.movit.core.training.geometry.PosePoint2D
import com.movit.core.training.geometry.PosePoint3D
import com.movit.core.training.geometry.VirtualLandmarks
import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import kotlin.math.abs
import kotlin.math.sqrt

object AngleDiagnosticsBuilder {
    fun buildAll(
        selectedJoints: Set<String>,
        angles: JointAngles,
        rawNorm: List<Landmark>,
        smoothedNorm: List<Landmark>,
        rawWorld: List<Landmark>?,
        smoothedWorld: List<Landmark>?,
        isFrontCamera: Boolean,
        elbowDiagnosticsPort: ElbowDiagnosticsPort = ElbowDiagnosticsPort.NoOp,
    ): List<AngleDiagnosticsData> {
        val resolvedRaw = VirtualLandmarks.ensureAppended(rawNorm)
        val resolvedSmooth = VirtualLandmarks.ensureAppended(smoothedNorm)
        val resolvedWorldRaw = rawWorld?.let(VirtualLandmarks::ensureAppended)
        val resolvedWorldSmooth = smoothedWorld?.let(VirtualLandmarks::ensureAppended)

        return selectedJoints.map { jointCode ->
            buildOne(
                jointCode = jointCode,
                angles = angles,
                rawNorm = resolvedRaw,
                smoothedNorm = resolvedSmooth,
                rawWorld = resolvedWorldRaw,
                smoothedWorld = resolvedWorldSmooth,
                isFrontCamera = isFrontCamera,
                elbowDiagnosticsPort = elbowDiagnosticsPort,
            )
        }
    }

    fun buildOne(
        jointCode: String,
        angles: JointAngles,
        rawNorm: List<Landmark>,
        smoothedNorm: List<Landmark>,
        rawWorld: List<Landmark>?,
        smoothedWorld: List<Landmark>?,
        isFrontCamera: Boolean,
        elbowDiagnosticsPort: ElbowDiagnosticsPort = ElbowDiagnosticsPort.NoOp,
    ): AngleDiagnosticsData {
        val sourceJointCode = mirrorJointCode(jointCode, isFrontCamera)
        val rawIndices = JointLandmarkMapping.getLandmarksForAngle(jointCode)
        val effectiveIndices = rawIndices.map { idx ->
            if (isFrontCamera) PoseLandmarkMirroring.mirroredIndex(idx) else idx
        }
        val displayedAngle = angles.getAngle(jointCode)
        val hasWorld = smoothedWorld != null && smoothedWorld.size >= 33
        val pipelineSourceLabel = if (hasWorld && displayedAngle != null) "World XYZ" else "Screen XY fallback"

        return AngleDiagnosticsData(
            displayJointCode = jointCode,
            sourceJointCode = sourceJointCode,
            effectiveIndices = effectiveIndices,
            displayedAngle = displayedAngle,
            pipelineSourceLabel = pipelineSourceLabel,
            normalizedRaw = buildFrame(rawNorm, effectiveIndices),
            normalizedSmoothed = buildFrame(smoothedNorm, effectiveIndices),
            worldRaw = rawWorld?.let { buildFrame(it, effectiveIndices) },
            worldSmoothed = smoothedWorld?.let { buildFrame(it, effectiveIndices) },
            elbowDiagnostics = if (jointCode.contains("elbow")) {
                elbowDiagnosticsPort.snapshotForJoint(jointCode)
            } else {
                null
            },
        )
    }

    private fun mirrorJointCode(jointCode: String, isFrontCamera: Boolean): String {
        if (!isFrontCamera) return jointCode
        return when (jointCode.lowercase()) {
            "left_elbow" -> "right_elbow"
            "right_elbow" -> "left_elbow"
            "left_knee" -> "right_knee"
            "right_knee" -> "left_knee"
            "left_shoulder" -> "right_shoulder"
            "right_shoulder" -> "left_shoulder"
            "left_hip" -> "right_hip"
            "right_hip" -> "left_hip"
            "left_ankle" -> "right_ankle"
            "right_ankle" -> "left_ankle"
            "left_wrist" -> "right_wrist"
            "right_wrist" -> "left_wrist"
            else -> jointCode
        }
    }

    private fun buildFrame(
        landmarks: List<Landmark>,
        indices: List<Int>,
    ): AngleDebugFrame? {
        if (indices.size < 3) return null
        val aIdx = indices[0]
        val bIdx = indices[1]
        val cIdx = indices[2]
        val a = landmarkAt(landmarks, aIdx) ?: return null
        val b = landmarkAt(landmarks, bIdx) ?: return null
        val c = landmarkAt(landmarks, cIdx) ?: return null

        val segBa = segmentMetrics(a, b)
        val segBc = segmentMetrics(b, c)

        return AngleDebugFrame(
            pointA = a,
            pointB = b,
            pointC = c,
            xyAngle = JointAngleCalculator.angleDegrees(
                PosePoint2D(a.x, a.y),
                PosePoint2D(b.x, b.y),
                PosePoint2D(c.x, c.y),
            ),
            xzAngle = angleInPlane(a, b, c, plane = Plane.XZ),
            yzAngle = angleInPlane(a, b, c, plane = Plane.YZ),
            xyzAngle = JointAngleCalculator.angleDegrees3D(
                PosePoint3D(a.x, a.y, a.z),
                PosePoint3D(b.x, b.y, b.z),
                PosePoint3D(c.x, c.y, c.z),
            ),
            segmentBA = segBa,
            segmentBC = segBc,
        )
    }

    private enum class Plane { XZ, YZ }

    private fun angleInPlane(a: AngleDebugPoint, b: AngleDebugPoint, c: AngleDebugPoint, plane: Plane): Double? {
        val coords = when (plane) {
            Plane.XZ -> floatArrayOf(a.x, a.z, b.x, b.z, c.x, c.z)
            Plane.YZ -> floatArrayOf(a.y, a.z, b.y, b.z, c.y, c.z)
        }
        return JointAngleCalculator.angleDegrees(
            PosePoint2D(coords[0], coords[1]),
            PosePoint2D(coords[2], coords[3]),
            PosePoint2D(coords[4], coords[5]),
        )
    }

    private fun segmentMetrics(a: AngleDebugPoint, b: AngleDebugPoint): AngleSegmentMetrics {
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        val dz = (b.z - a.z).toDouble()
        val length2D = sqrt(dx * dx + dy * dy)
        val length3D = sqrt(dx * dx + dy * dy + dz * dz)
        return AngleSegmentMetrics(dx, dy, dz, length2D, length3D)
    }

    private fun landmarkAt(
        landmarks: List<Landmark>,
        index: Int,
    ): AngleDebugPoint? {
        val lm = landmarks.getOrNull(index) ?: return null
        return AngleDebugPoint(
            index = index,
            name = TrainingDebugJointCatalog.landmarkNames.getOrNull(index) ?: "lm_$index",
            x = lm.x,
            y = lm.y,
            z = lm.z,
            visibility = lm.visibility,
            presence = lm.presence,
        )
    }
}
