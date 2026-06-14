package com.movit.feature.reports

import com.movit.core.training.report.MovitPeakCaptureType
import com.movit.core.training.report.MovitPeakFrameCapture
import com.movit.resources.strings.ReportDetailStrings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportFrameEvidenceMapperTest {

    @Test
    fun mapCaptures_buildsDisplayUris() = runBlocking {
        val captures = listOf(
            sampleCapture(MovitPeakCaptureType.DANGER_FRAME, rep = 2),
            sampleCapture(MovitPeakCaptureType.PEAK_FRAME, rep = 2),
        )
        val strings = ReportDetailStrings.load("en")
        val mapped = ReportFrameEvidenceMapper.mapCaptures(captures, strings)
        assertEquals(2, mapped.size)
        assertTrue(mapped.first().localPath.startsWith("file://"))
        assertEquals("Danger rep 2", mapped.first().label)
    }

    @Test
    fun mapCaptures_usesLocalizedLabels() = runBlocking {
        val capture = sampleCapture(MovitPeakCaptureType.BEST_REP, rep = 3)
        val en = ReportDetailStrings.load("en")
        val mapped = ReportFrameEvidenceMapper.mapCaptures(listOf(capture), en)
        assertEquals("Best rep 3", mapped.single().label)
    }

    @Test
    fun heroFramePath_prefersDangerThenBest() {
        val captures = listOf(
            sampleCapture(MovitPeakCaptureType.PEAK_FRAME, rep = 1, path = "/a.jpg"),
            sampleCapture(MovitPeakCaptureType.BEST_REP, rep = 2, path = "/b.jpg"),
            sampleCapture(MovitPeakCaptureType.DANGER_FRAME, rep = 3, path = "/c.jpg"),
        )
        assertEquals("file:///c.jpg.thumb", ReportFrameEvidenceMapper.heroFramePath(captures))
    }

    private fun sampleCapture(
        type: MovitPeakCaptureType,
        rep: Int,
        path: String = "/frame-$rep.jpg",
    ): MovitPeakFrameCapture = MovitPeakFrameCapture(
        id = "id-$rep-${type.name}",
        repNumber = rep,
        phaseCode = 3,
        capturedAtMs = 1L,
        captureType = type,
        localPath = path,
        thumbnailPath = "$path.thumb",
    )
}
