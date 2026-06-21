package com.movit.core.posecapture

import com.movit.core.posecapture.boundary.NoOpPoseRefiner

/**
 * iOS v1 pose refinement — intentionally [NoOpPoseRefiner].
 *
 * Android wires [com.movit.core.posecapture.android.AndroidPoseRefiner] (LiteRT MLP placeholder; same
 * no-op until assets ship). Geometric landmark smoothing via [PoseLandmarkSmoother] is the active
 * cross-platform path. MLP refinement is deferred on iOS (no LiteRT); landmarks pass through unchanged.
 */
typealias IosPoseRefiner = NoOpPoseRefiner
