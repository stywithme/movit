package com.movit.core.training.session

import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.ExerciseConfigParser

/**
 * Default squat-knee setup probe used when Debug Lab has no exercise slug selected.
 * Mirrors the legacy debug setup gate exercise shape.
 */
object SetupProbeDefaults {
    val exerciseConfig: ExerciseConfig by lazy {
        ExerciseConfigParser.parseConfigJson(SETUP_PROBE_JSON)
    }

    private const val SETUP_PROBE_JSON = """
        {
          "name": {"ar": "اختبار", "en": "Setup probe"},
          "countingMethod": "up_down",
          "poseVariants": [{
            "cameraPosition": "front_view",
            "trackedJoints": [
              {
                "joint": "left_knee",
                "role": "primary",
                "startPose": {"min": 150, "max": 180},
                "pairedWith": "right_knee",
                "upRange": {"perfect": {"min": 130, "max": 180}},
                "downRange": {"perfect": {"min": 60, "max": 100}}
              },
              {
                "joint": "right_knee",
                "role": "primary",
                "startPose": {"min": 150, "max": 180},
                "pairedWith": "left_knee",
                "upRange": {"perfect": {"min": 130, "max": 180}},
                "downRange": {"perfect": {"min": 60, "max": 100}}
              }
            ]
          }]
        }
    """
}
