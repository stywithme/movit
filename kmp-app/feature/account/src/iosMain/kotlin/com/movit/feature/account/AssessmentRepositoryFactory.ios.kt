package com.movit.feature.account

actual fun defaultAssessmentRepository(): AssessmentRepository = SharedAssessmentRepository()
