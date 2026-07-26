package com.movit.feature.trainingdebug.lab

import com.movit.core.training.model.Landmark
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * On-disk format for M-E reference recordings (WP-26).
 *
 * Landmarks are stored as flat `x, y, z, visibility, presence` runs — the same layout the
 * Swift↔Kotlin pose bridge already uses — so a capture can be dumped straight from the debug
 * lab and replayed byte-identically later.
 *
 * Capture protocol the files are expected to follow:
 * - a known elbow angle held still (protractor-measured) → `trueAngleDeg`;
 * - the **same** angle recorded from several `cameraPosition` values (front / front-side /
 *   side / front-behind), because cross-camera spread is the metric that decides the gate;
 * - repeated across users, since bone proportions drive the WP-25 calibration.
 */
object ElbowLabRecordingCodec {
    private const val FLOATS_PER_LANDMARK = 5

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun decode(text: String): List<ElbowLabRecording> =
        json.decodeFromString<List<ElbowLabRecordingDto>>(text).map { it.toModel() }

    fun encode(recordings: List<ElbowLabRecording>): String =
        json.encodeToString(recordings.map { it.toDto() })

    @Serializable
    private data class ElbowLabRecordingDto(
        val id: String,
        val trueAngleDeg: Double,
        val cameraPosition: String,
        val side: String,
        val analysisImageWidth: Int,
        val analysisImageHeight: Int,
        val frames: List<ElbowLabFrameDto>,
    )

    @Serializable
    private data class ElbowLabFrameDto(
        val timestampMs: Long,
        val normalized: List<Float>,
        val world: List<Float>,
    )

    private fun ElbowLabRecordingDto.toModel() = ElbowLabRecording(
        id = id,
        trueAngleDeg = trueAngleDeg,
        cameraPosition = cameraPosition,
        side = if (side.equals("right", ignoreCase = true)) ElbowLabSide.RIGHT else ElbowLabSide.LEFT,
        analysisImageWidth = analysisImageWidth,
        analysisImageHeight = analysisImageHeight,
        frames = frames.map { frame ->
            ElbowLabFrame(
                timestampMs = frame.timestampMs,
                normalized = frame.normalized.toLandmarks(),
                world = frame.world.toLandmarks(),
            )
        },
    )

    private fun ElbowLabRecording.toDto() = ElbowLabRecordingDto(
        id = id,
        trueAngleDeg = trueAngleDeg,
        cameraPosition = cameraPosition,
        side = side.name.lowercase(),
        analysisImageWidth = analysisImageWidth,
        analysisImageHeight = analysisImageHeight,
        frames = frames.map { frame ->
            ElbowLabFrameDto(
                timestampMs = frame.timestampMs,
                normalized = frame.normalized.toFloats(),
                world = frame.world.toFloats(),
            )
        },
    )

    private fun List<Float>.toLandmarks(): List<Landmark> =
        (0 until size / FLOATS_PER_LANDMARK).map { index ->
            val base = index * FLOATS_PER_LANDMARK
            Landmark(
                x = this[base],
                y = this[base + 1],
                z = this[base + 2],
                visibility = this[base + 3],
                presence = this[base + 4],
            )
        }

    private fun List<Landmark>.toFloats(): List<Float> = buildList(size * FLOATS_PER_LANDMARK) {
        for (landmark in this@toFloats) {
            add(landmark.x)
            add(landmark.y)
            add(landmark.z)
            add(landmark.visibility)
            add(landmark.presence)
        }
    }
}
