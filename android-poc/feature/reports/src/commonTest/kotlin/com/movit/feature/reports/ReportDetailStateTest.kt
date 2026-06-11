package com.movit.feature.reports

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ReportDetailStateTest {

    @Test
    fun loadPreview_populatesReport() = runBlocking {
        val viewModel = ReportDetailViewModel(
            reportId = "preview",
            repository = DefaultReportDetailRepository(),
        )
        viewModel.load()
        val report = viewModel.state.value.report
        assertNotNull(report)
        assertEquals(92, report.formScore)
        assertEquals(3, report.joints.size)
    }

    @Test
    fun pageSelected_updatesState() {
        val viewModel = ReportDetailViewModel("preview")
        viewModel.onPageSelected(ReportDetailPage.Form)
        assertEquals(ReportDetailPage.Form, viewModel.state.value.selectedPage)
    }

    @Test
    fun previewData_isLocalizedForArabic() = runBlocking {
        val report = ReportDetailPreviewData.forId("preview", language = "ar")
        assertNotNull(report)
        assertEquals("الركبتان", report.joints.first().label)
        assertEquals("أفضل إنجاز شخصي", report.badgeLabel)
    }
}
