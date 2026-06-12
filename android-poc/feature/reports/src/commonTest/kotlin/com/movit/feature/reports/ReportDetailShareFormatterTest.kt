package com.movit.feature.reports

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ReportDetailShareFormatterTest {

    @Test
    fun sharePayload_includesExerciseAndScore() = runBlocking {
        val report = ReportDetailPreviewData.squat("en")
        val payload = ReportDetailShareFormatter.sharePayload(report, language = "en", isExport = false)
        assertEquals("report_detail_share_chooser_title", payload.chooserTitleKey)
        assertContains(payload.text, "Barbell Squat")
        assertContains(payload.text, "92")
    }

    @Test
    fun exportPayload_includesStats() = runBlocking {
        val report = ReportDetailPreviewData.squat("en")
        val payload = ReportDetailShareFormatter.sharePayload(report, language = "en", isExport = true)
        assertEquals("report_detail_export_chooser_title", payload.chooserTitleKey)
        assertContains(payload.text, "Sets: 4")
        assertContains(payload.text, "Reps: 40")
    }
}
