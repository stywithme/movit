package com.movit.core.posecapture.boundary.trainingdebug

enum class PoseModelType {
    FULL,
    HEAVY,
    ;

    companion object {
        fun fromPreference(raw: String): PoseModelType =
            if (raw.equals("heavy", ignoreCase = true)) HEAVY else FULL

        fun toPreference(type: PoseModelType): String =
            when (type) {
                FULL -> "full"
                HEAVY -> "heavy"
            }
    }
}
