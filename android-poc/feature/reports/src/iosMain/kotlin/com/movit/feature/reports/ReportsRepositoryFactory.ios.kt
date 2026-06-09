package com.movit.feature.reports

actual fun defaultReportsRepository(): ReportsRepository = SharedReportsRepository()
