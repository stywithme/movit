package com.movit.core.training.testing

data class FrameFixture(
    val name: String,
    val isFrontCamera: Boolean = true,
    val frames: List<FrameFixtureFrame>,
) {
    companion object {
        fun fromAngleSequence(
            name: String,
            isFrontCamera: Boolean = true,
            anglesByFrame: List<Map<String, Double>>,
            startTimestampMs: Long = 1_000L,
            stepMs: Long = 200L,
        ): FrameFixture = FrameFixture(
            name = name,
            isFrontCamera = isFrontCamera,
            frames = anglesByFrame.mapIndexed { index, angles ->
                FrameFixtureFrame(
                    index = index,
                    timestampMs = startTimestampMs + stepMs * index,
                    angles = angles,
                )
            },
        )
    }
}

data class FrameFixtureFrame(
    val index: Int,
    val timestampMs: Long,
    val angles: Map<String, Double> = emptyMap(),
    val isFrontCamera: Boolean? = null,
)
