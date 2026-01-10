package com.trainingvalidator.poc.training.report

import android.graphics.Bitmap
import android.util.Log
import com.trainingvalidator.poc.training.engine.Phase
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * FrameCaptureManager - Captures and manages frames during training
 * 
 * Captures frames at key moments:
 * - Peak of each rep (BOTTOM/EXTENDED phase)
 * - When errors are detected
 * - Best reps (marked after completion)
 * - Hold exercise samples
 * 
 * Includes limits to prevent storage bloat:
 * - Max 3 best reps
 * - One error frame per error type
 * - One peak frame per rep
 * - Max 3 hold samples
 */
class FrameCaptureManager(
    private val storageDir: File,
    private val sessionId: String
) {
    companion object {
        private const val TAG = "FrameCaptureManager"
        private const val THUMBNAIL_SIZE = 200  // px
        private const val FULL_SIZE = 720       // px
        private const val JPEG_QUALITY = 85     // %
        private const val MAX_BEST_REPS = 3
        private const val MAX_HOLD_SAMPLES = 3
        private const val ERROR_CAPTURE_COOLDOWN_MS = 2000L
    }
    
    private val capturedFrames = mutableListOf<FrameCapture>()
    private val sessionDir: File = File(storageDir, "frame_captures/$sessionId")
    
    // Track captured error types to avoid duplicates
    private val capturedErrorTypes = mutableSetOf<String>()
    private val lastErrorCaptureTimes = mutableMapOf<String, Long>()
    
    // Track best reps count
    private var bestRepCount = 0
    
    // Track hold samples
    private var holdSampleCount = 0
    
    // Track peak frames per rep (to later mark as best)
    private val peakFramesByRep = mutableMapOf<Int, FrameCapture>()
    
    init {
        sessionDir.mkdirs()
        Log.d(TAG, "FrameCaptureManager initialized: ${sessionDir.absolutePath}")
    }
    
    /**
     * Create instance with context
     */
    constructor(context: android.content.Context, sessionId: String) : this(
        context.filesDir,
        sessionId
    )
    
    // ==================== Capture Methods ====================
    
    /**
     * Capture peak frame (when entering BOTTOM/EXTENDED phase)
     * 
     * @param bitmap The bitmap to capture (will be copied)
     * @param repNumber Current rep in progress (engine.getCurrentRep() + 1)
     * @param phase Current phase
     * @param angles Current joint angles
     */
    fun capturePeakFrame(
        bitmap: Bitmap,
        repNumber: Int,
        phase: Phase,
        angles: Map<String, Double>? = null
    ): FrameCapture? {
        // Only one peak frame per rep
        if (peakFramesByRep.containsKey(repNumber)) {
            return null
        }
        
        val capture = captureInternal(
            bitmap = bitmap,
            repNumber = repNumber,
            phase = phase,
            captureType = CaptureType.PEAK_FRAME,
            errorType = null,
            angles = angles
        ) ?: return null
        
        peakFramesByRep[repNumber] = capture
        return capture
    }
    
    /**
     * Capture error frame (when error first detected)
     * 
     * @param bitmap The bitmap to capture
     * @param repNumber Current rep in progress
     * @param phase Current phase
     * @param errorKey Error type key (e.g., "left_knee:TOO_HIGH")
     * @param angles Current joint angles
     */
    fun captureErrorFrame(
        bitmap: Bitmap,
        repNumber: Int,
        phase: Phase,
        errorKey: String,
        angles: Map<String, Double>? = null
    ): FrameCapture? {
        // Only one capture per error type per session
        if (capturedErrorTypes.contains(errorKey)) {
            return null
        }
        
        // Cooldown check
        val lastTime = lastErrorCaptureTimes[errorKey] ?: 0L
        if (System.currentTimeMillis() - lastTime < ERROR_CAPTURE_COOLDOWN_MS) {
            return null
        }
        
        val capture = captureInternal(
            bitmap = bitmap,
            repNumber = repNumber,
            phase = phase,
            captureType = CaptureType.ERROR_FRAME,
            errorType = errorKey,
            angles = angles
        ) ?: return null
        
        capturedErrorTypes.add(errorKey)
        lastErrorCaptureTimes[errorKey] = System.currentTimeMillis()
        
        return capture
    }
    
    /**
     * Capture hold sample (every N seconds during hold)
     * 
     * @param bitmap The bitmap to capture
     * @param elapsedMs Time elapsed in hold
     * @param phase Current phase
     * @param angles Current joint angles
     */
    fun captureHoldSample(
        bitmap: Bitmap,
        elapsedMs: Long,
        phase: Phase,
        angles: Map<String, Double>? = null
    ): FrameCapture? {
        if (holdSampleCount >= MAX_HOLD_SAMPLES) {
            return null
        }
        
        val capture = captureInternal(
            bitmap = bitmap,
            repNumber = 0, // Not applicable for hold
            phase = phase,
            captureType = CaptureType.HOLD_SAMPLE,
            errorType = "hold_${elapsedMs}ms",
            angles = angles
        ) ?: return null
        
        holdSampleCount++
        return capture
    }
    
    /**
     * Mark a peak frame as best rep (called when rep completes with no errors)
     * 
     * @param repNumber The rep number that just completed correctly
     * @return true if marked successfully
     */
    fun markAsBestRep(repNumber: Int): Boolean {
        if (bestRepCount >= MAX_BEST_REPS) {
            Log.d(TAG, "Max best reps ($MAX_BEST_REPS) reached, skipping")
            return false
        }
        
        val peakFrame = peakFramesByRep[repNumber]
        if (peakFrame == null) {
            Log.d(TAG, "No peak frame found for rep $repNumber")
            return false
        }
        
        // Update the capture type in our list
        val index = capturedFrames.indexOf(peakFrame)
        if (index >= 0) {
            val updatedFrame = peakFrame.copy(captureType = CaptureType.BEST_REP)
            capturedFrames[index] = updatedFrame
            peakFramesByRep[repNumber] = updatedFrame
            bestRepCount++
            
            Log.d(TAG, "Marked rep $repNumber as BEST_REP (total: $bestRepCount)")
            return true
        }
        
        return false
    }
    
    // ==================== Internal Capture ====================
    
    private fun captureInternal(
        bitmap: Bitmap,
        repNumber: Int,
        phase: Phase,
        captureType: CaptureType,
        errorType: String?,
        angles: Map<String, Double>?
    ): FrameCapture? {
        return try {
            // Copy bitmap (important for video mode where original is recycled)
            val bitmapCopy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                ?: return null
            
            // Generate unique ID
            val id = UUID.randomUUID().toString().take(8)
            
            // Save full resolution
            val fullPath = saveFrame(bitmapCopy, id, FULL_SIZE)
            
            // Save thumbnail
            val thumbPath = saveFrame(bitmapCopy, "${id}_thumb", THUMBNAIL_SIZE)
            
            // Recycle our copy
            bitmapCopy.recycle()
            
            if (fullPath == null || thumbPath == null) {
                Log.e(TAG, "Failed to save frame files")
                return null
            }
            
            val capture = FrameCapture(
                id = id,
                repNumber = repNumber,
                phase = phase,
                timestamp = System.currentTimeMillis(),
                captureType = captureType,
                errorType = errorType,
                frameUri = fullPath,
                thumbnailUri = thumbPath,
                metadata = FrameMetadata(
                    angles = angles ?: emptyMap(),
                    hasError = captureType == CaptureType.ERROR_FRAME,
                    errorDetails = errorType
                )
            )
            
            capturedFrames.add(capture)
            Log.d(TAG, "Captured ${captureType.name} for rep $repNumber: $id")
            
            capture
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing frame: ${e.message}")
            null
        }
    }
    
    /**
     * Save bitmap to file with scaling
     */
    private fun saveFrame(bitmap: Bitmap, name: String, maxSize: Int): String? {
        return try {
            // Scale if needed
            val scaled = if (bitmap.width > maxSize || bitmap.height > maxSize) {
                val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else {
                bitmap
            }
            
            val file = File(sessionDir, "$name.jpg")
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            
            // Recycle scaled bitmap if we created it
            if (scaled !== bitmap) {
                scaled.recycle()
            }
            
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save frame $name: ${e.message}")
            null
        }
    }
    
    // ==================== Getters ====================
    
    /**
     * Get all captured frames
     */
    fun getAllCaptures(): List<FrameCapture> = capturedFrames.toList()
    
    /**
     * Get best rep frames
     */
    fun getBestRepFrames(): List<FrameCapture> = 
        capturedFrames.filter { it.captureType == CaptureType.BEST_REP }
    
    /**
     * Get error frames
     */
    fun getErrorFrames(): List<FrameCapture> =
        capturedFrames.filter { it.captureType == CaptureType.ERROR_FRAME }
    
    /**
     * Get error frame for specific error type
     */
    fun getErrorFrame(errorKey: String): FrameCapture? =
        capturedFrames.find { 
            it.captureType == CaptureType.ERROR_FRAME && it.errorType == errorKey
        }
    
    /**
     * Get peak/best frame for a specific rep
     */
    fun getFrameForRep(repNumber: Int): FrameCapture? = peakFramesByRep[repNumber]
    
    /**
     * Get hold sample frames
     */
    fun getHoldSamples(): List<FrameCapture> =
        capturedFrames.filter { it.captureType == CaptureType.HOLD_SAMPLE }
    
    /**
     * Get total capture count
     */
    fun getCaptureCount(): Int = capturedFrames.size
    
    /**
     * Get session ID
     */
    fun getSessionId(): String = sessionId
    
    // ==================== Cleanup ====================
    
    /**
     * Cleanup all captures for this session
     */
    fun cleanup() {
        try {
            sessionDir.deleteRecursively()
            capturedFrames.clear()
            capturedErrorTypes.clear()
            lastErrorCaptureTimes.clear()
            peakFramesByRep.clear()
            bestRepCount = 0
            holdSampleCount = 0
            Log.d(TAG, "Cleaned up session: $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }
    
    /**
     * Cleanup old sessions (keep only last N)
     */
    fun cleanupOldSessions(keepCount: Int = 5) {
        try {
            val parentDir = sessionDir.parentFile ?: return
            val sessions = parentDir.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            
            sessions.drop(keepCount).forEach { dir ->
                dir.deleteRecursively()
                Log.d(TAG, "Deleted old session: ${dir.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old sessions: ${e.message}")
        }
    }
    
    /**
     * Get storage size for this session (bytes)
     */
    fun getStorageSize(): Long {
        return try {
            sessionDir.walkTopDown()
                .filter { it.isFile }
                .map { it.length() }
                .sum()
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Get formatted storage size
     */
    fun getFormattedStorageSize(): String {
        val bytes = getStorageSize()
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
