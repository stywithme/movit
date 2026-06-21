package com.movit.core.training.position

import com.movit.core.training.config.PoseVariant

fun PoseVariant.resolveSceneExpectation(): PoseSceneExpectation =
    if (expectedPostures != null) {
        PoseSceneExpectation.fromJson(expectedPostures, expectedDirections, expectedRegions)
    } else {
        val code = posePosition ?: cameraPosition ?: "standing_side"
        PoseSceneExpectation.fromLegacyCode(code)
    }
