package com.movit.core.posecapture.android

import android.content.Context
import com.movit.core.posecapture.boundary.trainingdebug.PoseModelType
import com.movit.core.posecapture.boundary.trainingdebug.PoseModelTypePort
import com.movit.core.posecapture.boundary.trainingdebug.ResolvedPoseModel

/**
 * D9 Android actual for [PoseModelTypePort].
 * Persists through pose-capture owned prefs bridge (not app module / not direct UI writes).
 */
class AndroidPoseModelTypePort(
    private val context: Context,
) : PoseModelTypePort {
    override fun getSelectedModel(): PoseModelType =
        PoseModelType.fromPreference(PoseModelTypePreference.getModelType(context))

    override fun setSelectedModel(model: PoseModelType) {
        PoseModelTypePreference.setModelType(context, PoseModelType.toPreference(model))
    }

    override fun resolveForInitialization(requested: PoseModelType?): ResolvedPoseModel {
        val model = requested ?: getSelectedModel()
        val baseBuilder = com.google.mediapipe.tasks.core.BaseOptions.builder()
        return PoseModelResolver.resolveForDebug(
            context = context,
            baseBuilder = baseBuilder,
            modelPort = this,
            requested = model,
        )
    }
}
