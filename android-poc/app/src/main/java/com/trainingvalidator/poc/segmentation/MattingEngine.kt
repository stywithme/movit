package com.trainingvalidator.poc.segmentation

enum class MattingEngine {
    MODNET,
    U2NET,
    MEDIAPIPE;

    companion object {
        fun fromSettings(value: String): MattingEngine {
            return when (value.trim().lowercase()) {
                "u2net", "u2-net", "u_2_net" -> U2NET
                "mediapipe", "selfie", "mp" -> MEDIAPIPE
                else -> MODNET
            }
        }

        fun defaultModelAsset(engine: MattingEngine): String {
            return when (engine) {
                MODNET -> "modnet_photographic.onnx"
                U2NET -> "u2net_human_seg.onnx"
                MEDIAPIPE -> "selfie_segmenter.tflite"
            }
        }

        fun defaultInputSize(engine: MattingEngine): Int {
            return when (engine) {
                MODNET -> 512
                U2NET -> 320
                MEDIAPIPE -> 256
            }
        }
    }
}
