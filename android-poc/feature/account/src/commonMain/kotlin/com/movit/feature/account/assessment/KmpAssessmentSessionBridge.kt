package com.movit.feature.account.assessment

import com.movit.core.training.model.PoseFrame

interface KmpAssessmentSession : AutoCloseable {
    fun bindPreview(surface: Any)
    fun start()
    override fun close()
}

fun interface KmpAssessmentSessionFactory {
    fun create(
        hostContext: Any,
        lifecycleOwner: Any,
        onPoseFrame: (PoseFrame?) -> Unit,
        onError: (String) -> Unit,
    ): KmpAssessmentSession?
}

object KmpAssessmentSessionBridge {
    var factory: KmpAssessmentSessionFactory? = null
}

