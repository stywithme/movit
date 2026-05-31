package com.trainingvalidator.poc.ui.report.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.training.report.BestWorstReplayPipeline
import com.trainingvalidator.poc.training.report.FrameCapture
import com.trainingvalidator.poc.training.report.RepReplayClip
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight image-sequence player for temporary rep replays.
 *
 * It intentionally avoids introducing a media-encoder dependency and falls back to the
 * still image or placeholder if replay files are not available anymore.
 */
class RepReplayPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "RepReplayPlayerView"
        private const val DEBUG_PREFIX = "REPLAY_DEBUG_AI"
        private const val DEFAULT_PLAYBACK_FRAME_DELAY_MS = 120L
        private const val MIN_PLAYBACK_FRAME_DELAY_MS = 100L
        private const val MAX_PLAYBACK_FRAME_DELAY_MS = 160L
        private const val START_FRAME_HOLD_MS = 200L
        private const val END_FRAME_HOLD_MS = 280L
        private const val LOOP_RESTART_DELAY_MS = 250L
    }

    private data class LoadedReplayFrame(
        val bitmap: Bitmap,
        val path: String
    )

    private val imageView = ImageView(context).apply {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private val statusView = TextView(context).apply {
        layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = dp(10)
        }
        setPadding(dp(10), dp(6), dp(10), dp(6))
        textSize = 11f
        setTextColor(0xFFFFFFFF.toInt())
    }

    /** Shown when still decode fails and [placeholderHint] was provided (e.g. report screen). */
    private val missingLabel = TextView(context).apply {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = dp(44)
            marginStart = dp(8)
            marginEnd = dp(8)
        }
        gravity = Gravity.CENTER_HORIZONTAL
        visibility = View.GONE
        textSize = 11f
        setTextColor(0xAAFFFFFF.toInt())
        maxLines = 2
    }

    private var clip: RepReplayClip? = null
    private var fallbackFrame: FrameCapture? = null
    private var playableFrames: List<String> = emptyList()
    private var frameOffsetsMs: List<Long> = emptyList()
    private var loadedFrames: List<LoadedReplayFrame> = emptyList()
    private var currentFrameIndex = 0
    private var isArabic = false
    private var isPlaying = false
    private var startFrameHoldMs = START_FRAME_HOLD_MS
    private var playbackFrameDelayMs = DEFAULT_PLAYBACK_FRAME_DELAY_MS
    private var loadGeneration = 0
    private var loadJob: Job? = null
    private var resumePlaybackOnAttach = false

    /** Bitmap decoded for fallback still (not part of [loadedFrames]); recycled on next bind. */
    private var decodedFallbackBitmap: Bitmap? = null
    private var placeholderHint: String? = null

    private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Two Runnable tokens forward to methods so Kotlin never infers types across a mutual
    // object : Runnable { ... } cycle (compiler: "recursive problem").
    private val restartPlaybackRunnable: Runnable = Runnable { runRestartPlaybackStep() }

    private val playbackRunnable: Runnable = Runnable { runPlaybackStep() }

    private fun runRestartPlaybackStep() {
        if (!isPlaying || loadedFrames.size < 2) {
            return
        }

        currentFrameIndex = 0
        if (!renderLoadedFrame(currentFrameIndex)) {
            stopPlayback(showStatus = true)
            showFallbackStill()
            return
        }

        postDelayed(playbackRunnable, startFrameHoldMs)
    }

    private fun runPlaybackStep() {
        if (!isPlaying || loadedFrames.size < 2) {
            return
        }

        if (currentFrameIndex >= loadedFrames.lastIndex) {
            postDelayed(restartPlaybackRunnable, LOOP_RESTART_DELAY_MS)
            return
        }

        currentFrameIndex += 1
        if (!renderLoadedFrame(currentFrameIndex)) {
            stopPlayback(showStatus = true)
            showFallbackStill()
            return
        }

        if (currentFrameIndex >= loadedFrames.lastIndex) {
            postDelayed(restartPlaybackRunnable, END_FRAME_HOLD_MS)
        } else {
            postDelayed(playbackRunnable, playbackFrameDelayMs)
        }
    }

    init {
        addView(imageView)
        addView(missingLabel)
        addView(statusView)
        isClickable = true
        isFocusable = true
        setOnClickListener { togglePlayback() }
    }

    fun bind(
        replayClip: RepReplayClip?,
        fallbackFrame: FrameCapture?,
        accentColor: Int,
        isArabic: Boolean,
        placeholderHint: String? = null
    ) {
        stopPlayback(showStatus = false, resumeOnAttach = false)
        loadJob?.cancel()
        releaseLoadedFrames()
        releaseDecodedFallbackBitmap()

        this.clip = replayClip
        this.fallbackFrame = fallbackFrame
        this.isArabic = isArabic
        this.placeholderHint = placeholderHint
        missingLabel.isVisible = false
        val availableFrames = replayClip?.frames
            ?.filter { File(it.frameUri).exists() }
            ?: emptyList()
        val framePaths = availableFrames.map { it.frameUri }
        this.playableFrames = framePaths
        this.frameOffsetsMs = availableFrames.map { it.offsetMs }
        this.currentFrameIndex = 0

        val totalClipFrames = replayClip?.frames?.size ?: 0
        val missingClipFrames = totalClipFrames - availableFrames.size
        Log.d(
            BestWorstReplayPipeline.LOG_TAG,
            "ui_bind clipRep=${replayClip?.repNumber} totalClipFrames=$totalClipFrames " +
                "playableFrames=${playableFrames.size} missingClipFrames=$missingClipFrames " +
                "fallbackFrame=${fallbackFrame != null} " +
                "fallbackFramePath=${!fallbackFrame?.frameUri.isNullOrEmpty()} " +
                "fallbackThumbPath=${!fallbackFrame?.thumbnailUri.isNullOrEmpty()}"
        )

        statusView.background = GradientDrawable().apply {
            setColor(0x66000000)
            cornerRadius = dp(999).toFloat()
            setStroke(dp(1), accentColor)
        }

        if (playableFrames.isEmpty()) {
            val generation = ++loadGeneration
            loadJob = viewScope.launch {
                val bitmap = decodeFallbackBitmapOnIo(fallbackFrame)
                if (generation != loadGeneration) {
                    bitmap?.recycle()
                    return@launch
                }
                if (bitmap != null) {
                    decodedFallbackBitmap = bitmap
                    imageView.setImageBitmap(bitmap)
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    missingLabel.isVisible = false
                } else {
                    imageView.setImageResource(R.drawable.ic_person_placeholder)
                    imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    if (!placeholderHint.isNullOrBlank()) {
                        missingLabel.text = placeholderHint
                        missingLabel.isVisible = true
                    }
                }
                updateStatusLabel()
            }
            return
        }

        startFrameHoldMs = resolveStartFrameHoldMs()
        playbackFrameDelayMs = resolvePlaybackFrameDelayMs()
        imageView.setImageDrawable(null)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP

        val generation = ++loadGeneration
        loadJob = viewScope.launch {
            val decodedFrames = withContext(Dispatchers.IO) {
                framePaths.mapNotNull { path ->
                    decodeReplayFrame(path)
                }
            }

            if (generation != loadGeneration) {
                recycleLoadedFrames(decodedFrames)
                return@launch
            }

            loadedFrames = decodedFrames
            currentFrameIndex = 0

            if (loadedFrames.isEmpty()) {
                Log.w(
                    TAG,
                    "$DEBUG_PREFIX preload_empty clipRep=${replayClip?.repNumber} sourceFrames=${framePaths.size}"
                )
                val bitmap = decodeFallbackBitmapOnIo(fallbackFrame)
                if (generation != loadGeneration) {
                    bitmap?.recycle()
                    return@launch
                }
                if (bitmap != null) {
                    decodedFallbackBitmap = bitmap
                    imageView.setImageBitmap(bitmap)
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    missingLabel.isVisible = false
                } else {
                    showFallbackStill()
                }
                updateStatusLabel()
                return@launch
            }

            Log.d(
                TAG,
                "$DEBUG_PREFIX preload_ready clipRep=${replayClip?.repNumber} loadedFrames=${loadedFrames.size} startHoldMs=$startFrameHoldMs stepDelayMs=$playbackFrameDelayMs"
            )
            renderLoadedFrame(0)
            resumePlaybackOnAttach = loadedFrames.size > 1
            if (loadedFrames.size > 1) {
                if (isAttachedToWindow) {
                    startPlayback()
                } else {
                    updateStatusLabel()
                }
            } else {
                updateStatusLabel()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (loadedFrames.isEmpty()) {
            return
        }

        currentFrameIndex = currentFrameIndex.coerceIn(0, loadedFrames.lastIndex)
        renderLoadedFrame(currentFrameIndex)
        if (resumePlaybackOnAttach && loadedFrames.size > 1) {
            startPlayback()
        } else {
            updateStatusLabel()
        }
    }

    override fun onDetachedFromWindow() {
        stopPlayback(
            showStatus = false,
            resumeOnAttach = isPlaying || (loadedFrames.size > 1 && resumePlaybackOnAttach)
        )
        super.onDetachedFromWindow()
    }

    private fun togglePlayback() {
        if (loadedFrames.size < 2) {
            return
        }

        if (isPlaying) {
            stopPlayback(showStatus = true)
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        if (loadedFrames.size < 2) {
            updateStatusLabel()
            return
        }

        removeCallbacks(playbackRunnable)
        removeCallbacks(restartPlaybackRunnable)
        isPlaying = true
        resumePlaybackOnAttach = true
        updateStatusLabel()

        if (currentFrameIndex !in loadedFrames.indices) {
            currentFrameIndex = 0
        }
        renderLoadedFrame(currentFrameIndex)

        if (currentFrameIndex >= loadedFrames.lastIndex) {
            postDelayed(restartPlaybackRunnable, LOOP_RESTART_DELAY_MS)
        } else {
            val initialDelay = if (currentFrameIndex == 0) {
                startFrameHoldMs
            } else {
                playbackFrameDelayMs
            }
            postDelayed(playbackRunnable, initialDelay)
        }
    }

    private fun stopPlayback(showStatus: Boolean, resumeOnAttach: Boolean = false) {
        removeCallbacks(playbackRunnable)
        removeCallbacks(restartPlaybackRunnable)
        isPlaying = false
        resumePlaybackOnAttach = resumeOnAttach
        if (showStatus) {
            updateStatusLabel()
        }
    }

    private fun renderLoadedFrame(index: Int): Boolean {
        val frame = loadedFrames.getOrNull(index) ?: return false
        if (frame.bitmap.isRecycled) {
            Log.w(TAG, "$DEBUG_PREFIX render_recycled path=${frame.path}")
            return false
        }

        imageView.setImageBitmap(frame.bitmap)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        return true
    }

    private fun bitmapDecodeOptions(highQuality: Boolean): BitmapFactory.Options =
        BitmapFactory.Options().apply {
            inPreferredConfig =
                if (highQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        }

    /** Full-resolution still first, then thumbnail (report cards must not upscale 200px thumbs). */
    private fun fallbackStillPaths(frame: FrameCapture?): List<String> {
        val f = frame ?: return emptyList()
        return listOfNotNull(
            f.frameUri.takeIf { it.isNotEmpty() },
            f.thumbnailUri.takeIf { it.isNotEmpty() }
        )
    }

    private fun renderFrame(path: String, highQuality: Boolean = true): Boolean {
        val file = File(path)
        if (!file.exists()) {
            Log.w(TAG, "$DEBUG_PREFIX render_missing path=${file.absolutePath}")
            return false
        }

        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            bitmapDecodeOptions(highQuality)
        ) ?: run {
            Log.w(TAG, "$DEBUG_PREFIX render_decode_failed path=${file.absolutePath}")
            return false
        }

        imageView.setImageBitmap(bitmap)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        return true
    }

    private fun decodeReplayFrame(path: String): LoadedReplayFrame? {
        val bitmap = BitmapFactory.decodeFile(
            path,
            bitmapDecodeOptions(highQuality = false)
        ) ?: run {
            Log.w(TAG, "$DEBUG_PREFIX preload_decode_failed path=$path")
            return null
        }

        return LoadedReplayFrame(
            bitmap = bitmap,
            path = path
        )
    }

    private fun showFallbackStill() {
        val candidatePaths = fallbackStillPaths(fallbackFrame)

        val renderedFallback = candidatePaths.any { renderFrame(it, highQuality = true) }
        if (!renderedFallback) {
            Log.w(
                TAG,
                "$DEBUG_PREFIX fallback_placeholder candidateCount=${candidatePaths.size}"
            )
            imageView.setImageResource(R.drawable.ic_person_placeholder)
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            if (!placeholderHint.isNullOrBlank()) {
                missingLabel.text = placeholderHint
                missingLabel.isVisible = true
            } else {
                missingLabel.isVisible = false
            }
        } else {
            missingLabel.isVisible = false
            Log.d(
                TAG,
                "$DEBUG_PREFIX fallback_rendered candidateCount=${candidatePaths.size}"
            )
        }
    }

    private suspend fun decodeFallbackBitmapOnIo(frame: FrameCapture?): Bitmap? =
        withContext(Dispatchers.IO) {
            for (path in fallbackStillPaths(frame)) {
                if (!File(path).exists()) continue
                val bitmap = BitmapFactory.decodeFile(
                    path,
                    bitmapDecodeOptions(highQuality = true)
                ) ?: continue
                return@withContext bitmap
            }
            null
        }

    private fun releaseDecodedFallbackBitmap() {
        decodedFallbackBitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                bmp.recycle()
            }
        }
        decodedFallbackBitmap = null
    }

    private fun updateStatusLabel() {
        statusView.text = when {
            loadedFrames.size > 1 && isPlaying ->
                if (isArabic) "إيقاف الحركة" else "Pause replay"
            loadedFrames.size > 1 || playableFrames.size > 1 ->
                if (isArabic) "تشغيل الحركة" else "Play replay"
            loadedFrames.size == 1 || playableFrames.size == 1 ->
                if (isArabic) "لقطة واحدة" else "Single frame"
            else ->
                if (isArabic) "صورة ثابتة" else "Still only"
        }
    }

    private fun resolvePlaybackFrameDelayMs(): Long {
        if (frameOffsetsMs.size < 2) {
            return DEFAULT_PLAYBACK_FRAME_DELAY_MS
        }

        val frameGaps = frameOffsetsMs
            .zipWithNext { currentOffset, nextOffset ->
                (nextOffset - currentOffset).coerceAtLeast(1L)
            }
        if (frameGaps.isEmpty()) {
            return DEFAULT_PLAYBACK_FRAME_DELAY_MS
        }

        val motionGaps = if (frameGaps.size >= 2) {
            val trailingAverage = frameGaps.drop(1).average()
            if (trailingAverage > 0.0 && frameGaps.first() > trailingAverage * 1.8) {
                frameGaps.drop(1)
            } else {
                frameGaps
            }
        } else {
            frameGaps
        }

        val averageDelayMs = motionGaps.average().toLong().coerceAtLeast(1L)
        return (averageDelayMs * 0.92f).toLong()
            .coerceIn(MIN_PLAYBACK_FRAME_DELAY_MS, MAX_PLAYBACK_FRAME_DELAY_MS)
    }

    private fun resolveStartFrameHoldMs(): Long {
        return START_FRAME_HOLD_MS
    }

    private fun releaseLoadedFrames() {
        releaseDecodedFallbackBitmap()
        missingLabel.isVisible = false
        if (loadedFrames.isEmpty()) {
            return
        }
        imageView.setImageDrawable(null)
        recycleLoadedFrames(loadedFrames)
        loadedFrames = emptyList()
    }

    private fun recycleLoadedFrames(frames: List<LoadedReplayFrame>) {
        frames.forEach { frame ->
            if (!frame.bitmap.isRecycled) {
                frame.bitmap.recycle()
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
