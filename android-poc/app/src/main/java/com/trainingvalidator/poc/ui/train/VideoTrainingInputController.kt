package com.trainingvalidator.poc.ui.train

import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import android.widget.SeekBar
import android.widget.Toast
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.training.session.SessionState
import com.trainingvalidator.poc.ui.components.GlassmorphicMessageView
import com.trainingvalidator.poc.ui.training.VideoModeController
import com.trainingvalidator.poc.video.VideoManager

/**
 * Video texture pipeline: [VideoModeController], seek/save UI, and frame→ViewModel delivery.
 * Camera / ML Kit pose is not used; poses come from the video path inside [VideoModeController].
 */
class VideoTrainingInputController(
    private val host: TrainingActivity
) {
    private val tag: String
        get() = TrainingActivity.TAG

    /**
     * Called after [com.trainingvalidator.poc.databinding.ActivityTrainingBinding] and
     * [com.trainingvalidator.poc.analysis.LandmarkSmoother] are ready.
     */
    fun setupVideoMode() {
        Log.d(tag, "Setting up VIDEO mode with URI: ${host.videoUri}")

        val b = host.binding
        b.previewView.visibility = View.GONE
        b.videoTextureView.visibility = View.VISIBLE
        b.btnSwitchCamera.visibility = View.GONE
        b.videoControlsPanel.visibility = View.VISIBLE
        b.setupPosePanel.visibility = View.GONE
        b.setupIndicatorBar.visibility = View.GONE
        b.countdownPanel.visibility = View.GONE
        b.bottomStatsBar.visibility = View.VISIBLE
        b.btnSaveResults.visibility = View.VISIBLE
        host.updatePlayPauseIcon(isPlaying = false)

        // Keep the video analysis path on the legacy FILL_START mapping.
        b.skeletonOverlay.setScaleMode(fitCenter = false)
        b.skeletonOverlay.setFillCenterMode(enabled = false)

        host.videoModeController = VideoModeController(host, host.lifecycleScope)
        host.videoModeController?.setListener(createVideoModeListener())

        val uri = host.videoUri ?: return
        host.videoModeController?.initialize(b.videoTextureView, uri, host.landmarkSmoother)

        setupVideoControls()
    }

    private fun createVideoModeListener(): VideoModeController.VideoModeListener {
        return object : VideoModeController.VideoModeListener {
            override fun onVideoReady(durationMs: Long) {
                val b = host.binding
                b.tvVideoDuration.text = host.formatTimeMs(durationMs)
                b.glassmorphicMessage.showMessage(
                    "Video ready. Press play to start.",
                    GlassmorphicMessageView.TYPE_INFO
                )
            }

            override fun onPlaybackStateChanged(state: VideoManager.PlaybackState) {
                when (state) {
                    VideoManager.PlaybackState.PLAYING -> {
                        host.updatePlayPauseIcon(isPlaying = true)
                        if (host.viewModel.supervisor.state.value != SessionState.TRAINING &&
                            host.viewModel.supervisor.state.value != SessionState.COMPLETED) {
                            startVideoTraining()
                        }
                    }
                    VideoManager.PlaybackState.PAUSED -> {
                        host.updatePlayPauseIcon(isPlaying = false)
                    }
                    VideoManager.PlaybackState.ENDED -> {
                        host.updatePlayPauseIcon(isPlaying = false)
                        host.viewModel.onVideoEnded()
                    }
                    VideoManager.PlaybackState.ERROR -> {
                        host.binding.glassmorphicMessage.showError(host.getString(R.string.error_video_playback))
                    }
                    else -> {}
                }
            }

            override fun onProgressChanged(currentMs: Long, durationMs: Long) {
                val b = host.binding
                b.tvVideoCurrentTime.text = host.formatTimeMs(currentMs)
                if (durationMs > 0) {
                    val progress = (currentMs.toFloat() / durationMs.toFloat() * 100).toInt()
                    b.videoSeekBar.progress = progress
                }
            }

            override fun onSeekPerformed() {
                host.viewModel.onVideoSeeked()
                host.binding.glassmorphicMessage.showMessage("Analysis reset", GlassmorphicMessageView.TYPE_INFO)
            }

            override fun onVideoEnded() {
                host.viewModel.onVideoEnded()
            }

            override fun onFrameProcessed(
                angles: JointAngles,
                smoothedLandmarks: List<SmoothedLandmark>,
                imageWidth: Int,
                imageHeight: Int,
                timestampMs: Long
            ) {
                host.wasPoseDetectedLastFrame = true
                host.viewModel.onPoseFrame(
                    angles, smoothedLandmarks, false, timestampMs,
                    imageWidth = imageWidth, imageHeight = imageHeight
                )

                val stateInfos = host.viewModel.trainingEngine?.jointStateInfos?.value ?: emptyMap()
                val positionErrors = host.viewModel.trainingEngine?.positionErrors?.value ?: emptyList()
                val bilateralFlipped = host.viewModel.trainingEngine?.isBilateralFlipped ?: false
                val b = host.binding
                b.skeletonOverlay.updateWithStateInfos(
                    smoothedLandmarks = smoothedLandmarks,
                    inputImageWidth = imageWidth,
                    inputImageHeight = imageHeight,
                    angles = angles,
                    stateInfos = stateInfos,
                    positionErrors = positionErrors,
                    bilateralFlipped = bilateralFlipped,
                    anySideDimmedJointCodes = host.viewModel.trainingEngine?.anySideDimmedJointCodes?.value
                        ?: emptySet()
                )
            }

            override fun onNoPoseDetected(timestampMs: Long) {
                host.binding.skeletonOverlay.clear()
                if (host.wasPoseDetectedLastFrame && host.viewModel.supervisor.state.value == SessionState.TRAINING) {
                    host.binding.glassmorphicMessage.hide()
                    host.binding.vignetteOverlay.clear()
                }
                host.wasPoseDetectedLastFrame = false
                host.viewModel.onNoPoseDetected(timestampMs)
            }

            override fun onResultsSaved(success: Boolean) {
                if (success) {
                    host.binding.glassmorphicMessage.showMotivation(host.getString(R.string.results_saved))
                    host.binding.btnSaveResults.isEnabled = false
                    host.binding.btnSaveResults.text = host.getString(R.string.btn_saved)
                } else {
                    host.binding.glassmorphicMessage.showError(host.getString(R.string.error_save_failed))
                }
            }

            override fun onError(message: String) {
                Toast.makeText(host, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupVideoControls() {
        val b = host.binding
        b.videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            private var wasPlaying = false

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = host.videoModeController?.getDuration() ?: 0L
                    val position = (progress / 100f * duration).toLong()
                    b.tvVideoCurrentTime.text = host.formatTimeMs(position)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                wasPlaying = host.videoModeController?.isPlaying() ?: false
                host.videoModeController?.pause()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 0
                val duration = host.videoModeController?.getDuration() ?: 0L
                val position = (progress / 100f * duration).toLong()
                host.videoModeController?.seekTo(position)
                if (wasPlaying) {
                    host.videoModeController?.play()
                }
            }
        })

        b.btnSaveResults.setOnClickListener {
            val exerciseConfig = host.viewModel.exerciseConfig.value ?: return@setOnClickListener
            host.videoModeController?.saveResults(
                host.viewModel.trainingEngine ?: return@setOnClickListener,
                exerciseConfig
            )
        }
    }

    private fun startVideoTraining() {
        host.viewModel.requestVideoStart()
        val trackedIndices = host.viewModel.getTrackedLandmarkIndices()
        val b = host.binding
        b.skeletonOverlay.setTrainingMode(true, trackedIndices, useFrontCamera = false)
        host.observeTrainingEngineState()
        Log.d(tag, "Video training started")
    }

    /**
     * Release player resources. Safe to call multiple times.
     */
    fun release() {
        host.videoModeController?.release()
    }
}
