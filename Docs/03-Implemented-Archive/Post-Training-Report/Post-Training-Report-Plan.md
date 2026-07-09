> **Status:** `ARCHIVED` — not current product truth.
> **Current SSOT:** [Post-Training-Report-Review.md](../../00-Active-Reference/Product-Master/Post-Training-Report-Review.md)
> **Archived:** 2026-05-29

# 📊 Post-Training Report Implementation Plan

> **Version:** 1.1.0  
> **Created:** January 2026  
> **Updated:** January 2026  
> **Status:** Implementation Phase

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Goals & Objectives](#goals--objectives)
3. [Available Data Analysis](#available-data-analysis)
4. [Report Structure](#report-structure)
5. [Frame Capture System](#frame-capture-system)
6. [Data Models](#data-models)
7. [UI Design](#ui-design)
8. [Implementation Phases](#implementation-phases)
9. [File Structure](#file-structure)
10. [Future Enhancements](#future-enhancements)

---

## Overview

### What is the Post-Training Report?

A comprehensive feedback report shown to users after completing an workout run. The report provides:

- **Performance evaluation** with accuracy metrics
- **Visual feedback** with captured frames from camera/video
- **Error analysis** showing what went wrong and how to fix it
- **Best moments** highlighting perfect reps for motivation
- **Improvement tips** personalized based on detected errors

### Scope (Current Phase)

| Feature | Status |
|---------|--------|
| Performance Summary | ✅ In Scope |
| Best Reps Highlights | ✅ In Scope |
| Error Analysis with Frames | ✅ In Scope |
| Rep Timeline | ✅ In Scope |
| Improvement Tips | ✅ In Scope |
| Frame Capture (Camera/Video) | ✅ In Scope |
| Planned Workout Quality Indicators | ✅ In Scope (NEW) |
| Consistency Metrics | ✅ In Scope (NEW) |
| Share Report | 🔜 Future Phase |
| Sync to Admin Dashboard | 🔜 Future Phase |
| Progress Tracking & Comparison | 🔜 Future Phase |

---

## Goals & Objectives

### Primary Goals

| Goal | Description | Priority |
|------|-------------|----------|
| **Evaluation** | Comprehensive performance assessment with accuracy percentage | 🔴 High |
| **Education** | Show all errors during exercise with visual examples | 🔴 High |
| **Motivation** | Highlight best reps to encourage users | 🔴 High |
| **Improvement** | Teach users how to improve based on their specific errors | 🔴 High |

### User Experience Goals

1. **Immediate Feedback** - Report generates instantly after training
2. **Visual Learning** - Captured frames make errors easy to understand
3. **Actionable Insights** - Every error comes with a fix suggestion
4. **Positive Reinforcement** - Celebrate successes before showing errors

---

## Available Data Analysis

### Currently Available in Codebase

#### From `WorkoutRunSummary`
```kotlin
data class WorkoutRunSummary(
    val exerciseName: String,
    val difficulty: DifficultyType,
    val totalReps: Int,
    val correctReps: Int,
    val incorrectReps: Int,
    val accuracy: Float,              // 0-100%
    val durationMs: Long,             // ⚠️ Currently returns 0 - use ViewModel.getPlanned WorkoutDurationMs()
    val commonErrors: Map<String, Int>,
    val repDetails: List<RepResult>
)
```

> ⚠️ **Important**: `durationMs` in `WorkoutRunSummary` currently returns 0 (TODO in code).
> Use `TrainingViewModel.getPlanned WorkoutDurationMs()` which calculates from `workoutStartTime`.

#### From `RepResult`
```kotlin
data class RepResult(
    val repNumber: Int,
    val isCorrect: Boolean,
    val errors: List<JointError>,           // Angle-based errors
    val positionErrors: List<PositionError>, // Position-based errors
    val phaseTimings: Map<String, Long>,    // Time in each phase
    val timestamp: Long
)
```

#### From `JointError`
```kotlin
data class JointError(
    val jointCode: String,      // e.g., "left_knee"
    val errorType: ErrorType,   // TOO_HIGH or TOO_LOW
    val actualAngle: Double,
    val expectedMin: Double,
    val expectedMax: Double,
    val message: LocalizedText  // Arabic + English
)
```

#### From `PositionError`
```kotlin
data class PositionError(
    val checkId: String,        // e.g., "knee_over_toe"
    val type: PositionCheckType,
    val severity: CheckSeverity, // ERROR, WARNING, TIP
    val message: LocalizedText,
    val actualValue: Double,
    val threshold: Double,
    val landmark1: String,
    val landmark2: String
)
```

#### For HOLD Exercises
```kotlin
// Available from HoldTimer/TrainingEngine
val holdDurationMs: Long        // Achieved duration
val holdTargetMs: Long          // Target duration
val gracePeriodsUsed: Int       // Grace periods used
val holdFormQuality: Float      // 0.0 - 1.0
val holdJointErrorMap: Map<String, Int>  // Errors per joint
```

#### Planned Workout Quality Data (NEW)
```kotlin
// Available from VisibilityMonitor via TrainingEngine
val visibilityStats: VisibilityStats  // Pause count, total invisible time
val cameraWarnings: Int               // Camera position warnings count
```

### Data to Add (Frame Capture)

```kotlin
data class FrameCapture(
    val id: String,
    val repNumber: Int,
    val phase: Phase,
    val timestamp: Long,
    val captureType: CaptureType,
    val errorType: String?,
    val frameUri: String,          // Full resolution path
    val thumbnailUri: String,      // Compressed thumbnail
    val metadata: FrameMetadata
)

enum class CaptureType {
    BEST_REP,      // Perfect rep with no errors
    ERROR_FRAME,   // Frame when error detected
    PEAK_FRAME,    // Peak of each rep (BOTTOM phase)
    HOLD_SAMPLE    // Sample during hold exercise
}

data class FrameMetadata(
    val angles: Map<String, Double>,
    val hasError: Boolean,
    val errorDetails: String?
)
```

---

## Report Structure

### Section 1: Performance Summary

```
┌─────────────────────────────────────────────────────────┐
│  🏋️ [Exercise Name] - [Difficulty Level]                │
│                                                          │
│  ⏱️ Duration: MM:SS                                      │
│  ✅ X/Y Reps Correct (XX%)                               │
│                                                          │
│  [████████████░░░░] XX% Accuracy                        │
│                                                          │
│  🏅 [Motivational Message Based on Performance]          │
└─────────────────────────────────────────────────────────┘
```

**Data Required:**
- `exerciseName`, `difficulty`
- `totalReps`, `correctReps`, `accuracy`
- `TrainingViewModel.getPlanned WorkoutDurationMs()` (formatted as MM:SS)

**Rating Logic:**
| Accuracy | Rating | Message (EN) | Message (AR) |
|----------|--------|--------------|--------------|
| 90%+ | EXCELLENT | "Outstanding! Nearly perfect form!" | "ممتاز! أداء شبه مثالي!" |
| 75-89% | GOOD | "Great job! Keep it up!" | "عمل رائع! استمر!" |
| 60-74% | FAIR | "Good effort! Room to improve." | "مجهود جيد! يمكنك التحسن." |
| <60% | NEEDS_WORK | "Keep practicing! You'll get better!" | "استمر بالتدريب! ستتحسن!" |

---

### Section 2: Best Reps Highlights ⭐

**Purpose:** Positive reinforcement - show users what they did RIGHT

```
┌─────────────────────────────────────────────────────────┐
│  ⭐ YOUR BEST REPS                                       │
│                                                          │
│  ┌──────────────────┐  ┌──────────────────┐             │
│  │  [📸 Frame]       │  │  [📸 Frame]       │             │
│  │  Rep #3          │  │  Rep #7          │             │
│  │  ✓ Perfect Form  │  │  ✓ Perfect Form  │             │
│  │  2.1s duration   │  │  1.9s duration   │             │
│  └──────────────────┘  └──────────────────┘             │
│                                                          │
│  💪 You nailed X perfect reps!                          │
└─────────────────────────────────────────────────────────┘
```

**Selection Criteria for "Best Rep":**
1. `isCorrect == true` (no errors)
2. Optimal timing (within expected range)
3. If multiple perfect reps, show up to 3

**Data Required:**
- Filter `repDetails.filter { it.isCorrect }`
- Associated `FrameCapture` with `captureType == BEST_REP`

---

### Section 3: Error Analysis ⚠️

**Purpose:** Education - show what went wrong and how to fix

```
┌─────────────────────────────────────────────────────────┐
│  ⚠️ AREAS TO IMPROVE                                     │
│                                                          │
│  ┌───────────────────────────────────────────────────┐  │
│  │  1. Left Knee - Not bending enough (4 times)      │  │
│  │                                                    │  │
│  │  [📸 Your Form]     →     [📸 Your Best Rep]       │  │
│  │  Angle: 95°               Target: 80-90°          │  │
│  │                                                    │  │
│  │  💡 Tip: Go lower until thighs are parallel       │  │
│  │          to the ground.                           │  │
│  │                                                    │  │
│  │  Affected Reps: #2, #4, #5, #8                    │  │
│  └───────────────────────────────────────────────────┘  │
│                                                          │
│  ┌───────────────────────────────────────────────────┐  │
│  │  2. Knee Over Toe (2 times)                       │  │
│  │  ...                                              │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

> **Note:** Instead of separate "Correct Form Reference" images, we use the user's **Best Rep** 
> as the reference. This is simpler (no external assets) and more personalized.

**Error Grouping Logic:**
```kotlin
// Group errors by type
val groupedErrors = repDetails
    .flatMap { rep -> 
        rep.errors.map { error -> Pair(rep.repNumber, error) } 
    }
    .groupBy { "${it.second.jointCode}:${it.second.errorType}" }
    .map { (key, occurrences) ->
        ErrorAnalysisItem(
            errorKey = key,
            count = occurrences.size,
            affectedReps = occurrences.map { it.first },
            sample = occurrences.first().second,
            frames = getFramesForError(key)
        )
    }
    .sortedByDescending { it.count }  // Most common first
```

**For Each Error Type:**
- Error name and joint (localized)
- Occurrence count
- Side-by-side comparison: Error frame vs Best Rep frame (user's own)
- Actual angle vs Expected range
- Improvement tip (from exercise JSON `feedbackMessages.tip`)
- List of affected rep numbers

---

### Section 4: Rep Timeline 📋

**Purpose:** Visual overview of entire workout run

```
┌─────────────────────────────────────────────────────────┐
│  📋 REP TIMELINE                                         │
│                                                          │
│  Rep 1  ✅  ●━━━━━━━━━━━━━━━━━━━━━━●  2.3s              │
│  Rep 2  ⚠️  ●━━━[knee]━━━━━━━━━━━━●  2.8s              │
│  Rep 3  ⭐  ●━━━━━━━━━━━━━━━━━━━━━━●  2.0s  ★ Best     │
│  Rep 4  ⚠️  ●━━━━━━━━[position]━━━●  2.5s              │
│  Rep 5  ✅  ●━━━━━━━━━━━━━━━━━━━━━━●  2.1s              │
│  Rep 6  ✅  ●━━━━━━━━━━━━━━━━━━━━━━●  2.2s              │
│  ...                                                     │
│                                                          │
│  [Tap any rep to see details]                           │
└─────────────────────────────────────────────────────────┘
```

**NEW: Consistency Metrics**
```
┌─────────────────────────────────────────────────────────┐
│  📊 CONSISTENCY                                          │
│                                                          │
│  Average Rep Duration: 2.3s                              │
│  Fastest: 1.9s (Rep #7)  |  Slowest: 2.8s (Rep #2)      │
│  Variation: ±0.3s                                        │
└─────────────────────────────────────────────────────────┘
```

**Visual Indicators:**
| Icon | Meaning |
|------|---------|
| ✅ | Correct rep |
| ⚠️ | Rep with errors |
| ⭐ | Best rep (perfect + optimal timing) |
| 🔴 | Failed rep (for HOLD exercises) |

**On Tap:** Show rep detail with:
- Captured frame
- All errors for that rep
- Phase timing breakdown

---

### Section 5: Improvement Tips 📚

**Purpose:** Actionable guidance for next planned workout

```
┌─────────────────────────────────────────────────────────┐
│  📚 HOW TO IMPROVE                                       │
│                                                          │
│  Based on your workout, focus on:                       │
│                                                          │
│  ┌─ TOP PRIORITY ──────────────────────────────────┐    │
│  │  🎯 DEPTH                                        │    │
│  │                                                  │    │
│  │  Your knee angle averaged 95°, target is 90°.   │    │
│  │                                                  │    │
│  │  How to fix:                                    │    │
│  │  • Practice box squats to learn proper depth    │    │
│  │  • Focus on sitting back, not just down         │    │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  🔮 NEXT FOCUS: Knee Tracking                           │
└─────────────────────────────────────────────────────────┘
```

> **Simplified**: Show only **Top 2 errors** + **1 "Next Focus"** for next planned workout.
> Too many tips overwhelm the user.

**Tip Generation Logic:**
1. Get top 2 most common errors
2. Load tips from exercise JSON `feedbackMessages.tip` and `common_mistake`
3. Add "Next Focus" as the 3rd most common error for next planned workout

---

### Section 6: Planned Workout Quality (NEW) 📋

**Purpose:** Explain factors that may have affected accuracy

```
┌─────────────────────────────────────────────────────────┐
│  📋 SESSION QUALITY                                      │
│                                                          │
│  ✅ Visibility: Good (no pauses)                         │
│  ⚠️ Camera Position: 2 warnings (side view recommended) │
│                                                          │
│  💡 For better tracking, try filming from the side.     │
└─────────────────────────────────────────────────────────┘
```

**Data Source:**
- `VisibilityMonitor.getStats()` → pause count, total invisible time
- `TrainingEngine.cameraWarning` → camera position issues

---

### Section 7: HOLD Exercise Specific

For exercises with `countingMethod: "hold"`:

```
┌─────────────────────────────────────────────────────────┐
│  ⏱️ HOLD PERFORMANCE                                     │
│                                                          │
│  Target: 30 seconds                                      │
│  Achieved: 28 seconds (93%)                              │
│                                                          │
│  [████████████████░░] 28s / 30s                         │
│                                                          │
│  Form Quality: 85%                                       │
│  Grace Periods Used: 1                                   │
│                                                          │
│  Form Breakdown:                                         │
│  • Spine angle: Stable ✅                               │
│  • Hip position: Minor drift (2 corrections)            │
│                                                          │
│  [📸 Sample Frames from Hold]                           │
│  [Frame 5s] [Frame 15s] [Frame 25s]                     │
└─────────────────────────────────────────────────────────┘
```

---

## Frame Capture System

### Overview

The frame capture system captures actual camera/video frames at key moments during training for inclusion in the post-training report.

### Capture Sources

| Mode | Source | Notes |
|------|--------|-------|
| **Camera Mode** | `PreviewView.bitmap` or convert `ImageProxy` to Bitmap | PreviewView.bitmap is simpler |
| **Video Mode** | `Bitmap` from `VideoManager.onFrameAvailable` | Already available, copy before recycle |

> ⚠️ **Important**: In Video Mode, `VideoModeController` recycles the bitmap after processing.
> Must copy the bitmap before it's recycled if we want to save it.

### Capture Triggers

| Trigger | When | Purpose | Limit |
|---------|------|---------|-------|
| **Peak Frame** | When entering BOTTOM/EXTENDED phase | Record form at target position | 1 per rep |
| **Error Frame** | First error detected per error type | Show what went wrong | 1 per errorKey per planned workout |
| **Best Rep Frame** | On rep completion if no errors | Highlight good form | Up to 3 best |
| **Hold Sample** | At 5s, 15s, 25s during hold | Show form throughout hold | 3 max |

### Implementation

```kotlin
/**
 * FrameCaptureManager - Captures frames during training
 * 
 * Integrated with TrainingActivity to capture at key moments.
 * Saves frames to internal storage with compression.
 */
class FrameCaptureManager(
    private val context: Context,
    private val plannedWorkoutId: String
) {
    companion object {
        private const val TAG = "FrameCaptureManager"
        private const val STORAGE_DIR = "frame_captures"
        private const val THUMBNAIL_SIZE = 200  // px
        private const val FULL_SIZE = 720       // px
        private const val JPEG_QUALITY = 85     // %
        private const val MAX_BEST_REPS = 3
        private const val ERROR_CAPTURE_COOLDOWN_MS = 2000L
    }
    
    private val capturedFrames = mutableListOf<FrameCapture>()
    private val storageDir: File = File(context.filesDir, "$STORAGE_DIR/$plannedWorkoutId")
    
    // Track captured error types to avoid duplicates
    private val capturedErrorTypes = mutableSetOf<String>()
    private val lastErrorCaptureTimes = mutableMapOf<String, Long>()
    
    // Track best reps count
    private var bestRepCount = 0
    
    init {
        storageDir.mkdirs()
    }
    
    /**
     * Capture frame for a specific purpose
     * 
     * @param bitmap The bitmap to capture (will be copied, not recycled)
     * @param repNumber Current rep number (use engine.getCurrentRep() + 1 for in-progress)
     * @param phase Current phase
     * @param captureType Type of capture
     * @param errorType Error type key (for ERROR_FRAME only)
     * @param angles Current joint angles
     */
    fun capture(
        bitmap: Bitmap,
        repNumber: Int,
        phase: Phase,
        captureType: CaptureType,
        errorType: String? = null,
        angles: Map<String, Double>? = null
    ): FrameCapture? {
        // Apply limits
        when (captureType) {
            CaptureType.BEST_REP -> {
                if (bestRepCount >= MAX_BEST_REPS) return null
            }
            CaptureType.ERROR_FRAME -> {
                if (errorType == null) return null
                // Only one capture per error type per planned workout
                if (capturedErrorTypes.contains(errorType)) return null
                // Cooldown check
                val lastTime = lastErrorCaptureTimes[errorType] ?: 0L
                if (System.currentTimeMillis() - lastTime < ERROR_CAPTURE_COOLDOWN_MS) return null
            }
            else -> {}
        }
        
        // Copy bitmap (important for video mode where original is recycled)
        val bitmapCopy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        
        // Save frames
        val id = UUID.randomUUID().toString()
        val fullPath = saveFrame(bitmapCopy, id, FULL_SIZE)
        val thumbPath = saveFrame(bitmapCopy, "${id}_thumb", THUMBNAIL_SIZE)
        
        bitmapCopy.recycle()
        
        if (fullPath == null || thumbPath == null) return null
        
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
        
        // Update tracking
        when (captureType) {
            CaptureType.BEST_REP -> bestRepCount++
            CaptureType.ERROR_FRAME -> {
                errorType?.let { 
                    capturedErrorTypes.add(it)
                    lastErrorCaptureTimes[it] = System.currentTimeMillis()
                }
            }
            else -> {}
        }
        
        Log.d(TAG, "Captured ${captureType.name} for rep $repNumber")
        return capture
    }
    
    /**
     * Mark a peak frame as best rep (called when rep completes with no errors)
     */
    fun markPeakAsBestRep(repNumber: Int): Boolean {
        if (bestRepCount >= MAX_BEST_REPS) return false
        
        val peakFrame = capturedFrames.find { 
            it.repNumber == repNumber && it.captureType == CaptureType.PEAK_FRAME 
        } ?: return false
        
        // Update the capture type
        val index = capturedFrames.indexOf(peakFrame)
        capturedFrames[index] = peakFrame.copy(captureType = CaptureType.BEST_REP)
        bestRepCount++
        
        Log.d(TAG, "Marked rep $repNumber peak as BEST_REP")
        return true
    }
    
    /**
     * Save bitmap to file
     */
    private fun saveFrame(bitmap: Bitmap, name: String, maxSize: Int): String? {
        return try {
            val scaled = if (bitmap.width > maxSize || bitmap.height > maxSize) {
                val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else bitmap
            
            val file = File(storageDir, "$name.jpg")
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            
            if (scaled !== bitmap) scaled.recycle()
            
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save frame: ${e.message}")
            null
        }
    }
    
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
     * Get error frames grouped by error type
     */
    fun getErrorFramesByType(): Map<String, FrameCapture> =
        capturedFrames
            .filter { it.captureType == CaptureType.ERROR_FRAME && it.errorType != null }
            .associateBy { it.errorType!! }
    
    /**
     * Get peak frame for a specific rep
     */
    fun getPeakFrame(repNumber: Int): FrameCapture? =
        capturedFrames.find { 
            it.repNumber == repNumber && 
            (it.captureType == CaptureType.PEAK_FRAME || it.captureType == CaptureType.BEST_REP)
        }
    
    /**
     * Cleanup all captures for this planned workout
     */
    fun cleanup() {
        storageDir.deleteRecursively()
        capturedFrames.clear()
        capturedErrorTypes.clear()
        lastErrorCaptureTimes.clear()
        bestRepCount = 0
    }
    
    /**
     * Cleanup old planned workouts (keep only last N)
     */
    fun cleanupOldPlanned Workouts(keepCount: Int = 5) {
        val parentDir = storageDir.parentFile ?: return
        val planned workouts = parentDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        
        planned workouts.drop(keepCount).forEach { it.deleteRecursively() }
    }
}
```

### Integration Points

#### In TrainingActivity (Camera Mode)

```kotlin
// Initialize capture manager
private var frameCaptureManager: FrameCaptureManager? = null

private fun initializeFrameCapture() {
    val plannedWorkoutId = UUID.randomUUID().toString()
    frameCaptureManager = FrameCaptureManager(this, plannedWorkoutId)
}

// Capture from PreviewView
private fun captureCurrentFrame(
    captureType: CaptureType,
    repNumber: Int,
    errorType: String? = null
) {
    val bitmap = binding.previewView.bitmap ?: return
    val phase = viewModel.currentPhase.value
    val angles = viewModel.trainingEngine?.currentAngles?.value
    
    frameCaptureManager?.capture(
        bitmap = bitmap,
        repNumber = repNumber,
        phase = phase,
        captureType = captureType,
        errorType = errorType,
        angles = angles
    )
}

// Observe events for capture triggers
private fun observeCaptureEvents() {
    lifecycleScope.launch {
        viewModel.currentPhase.collect { phase ->
            // Capture peak frame when entering BOTTOM/EXTENDED
            if (phase == Phase.BOTTOM || phase == Phase.EXTENDED) {
                val currentRep = (viewModel.trainingEngine?.getCurrentRep() ?: 0) + 1
                captureCurrentFrame(CaptureType.PEAK_FRAME, currentRep)
            }
        }
    }
    
    lifecycleScope.launch {
        viewModel.feedbackEvents.collect { event ->
            when (event) {
                is FeedbackEvent.JointErrorDetected -> {
                    val errorKey = "${event.error.jointCode}:${event.error.errorType}"
                    val currentRep = (viewModel.trainingEngine?.getCurrentRep() ?: 0) + 1
                    captureCurrentFrame(CaptureType.ERROR_FRAME, currentRep, errorKey)
                }
                is FeedbackEvent.RepCompleted -> {
                    if (event.isCorrect) {
                        frameCaptureManager?.markPeakAsBestRep(event.repNumber)
                    }
                }
                else -> {}
            }
        }
    }
}
```

#### In VideoModeController (Video Mode)

```kotlin
// Modify processFrame to support capture
private fun processFrame(bitmap: Bitmap, timestampMs: Long) {
    // IMPORTANT: Copy bitmap BEFORE any processing if we might capture it
    // The original will be recycled after this method
    
    val bitmapForCapture = if (shouldCapture()) {
        bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
    } else null
    
    // ... existing processing code ...
    
    // Capture if needed (on main thread)
    bitmapForCapture?.let { capturedBitmap ->
        scope.launch(Dispatchers.Main) {
            frameCaptureManager?.capture(
                bitmap = capturedBitmap,
                repNumber = currentRep,
                phase = currentPhase,
                captureType = captureType,
                errorType = errorType,
                angles = angles
            )
            capturedBitmap.recycle()
        }
    }
}
```

### Storage Structure

```
/data/data/com.trainingvalidator.poc/files/
└── frame_captures/
    └── [workout_execution_id]/
        ├── abc123.jpg              (full 720px)
        ├── abc123_thumb.jpg        (thumbnail 200px)
        ├── def456.jpg
        ├── def456_thumb.jpg
        └── ...
```

---

## Data Models

### Core Report Model

```kotlin
/**
 * PostTrainingReport - Complete report for a planned workout
 */
data class PostTrainingReport(
    val id: String = UUID.randomUUID().toString(),
    val plannedWorkoutId: String,
    val exerciseId: String,
    val exerciseName: LocalizedText,
    val difficulty: DifficultyType,
    val timestamp: Long = System.currentTimeMillis(),
    
    // Summary section
    val summary: PerformanceSummary,
    
    // Best moments (for motivation)
    val bestReps: List<BestRepHighlight>,
    
    // Worst rep (for comparison with best)
    val worstRep: WorstRepHighlight?,
    
    // Error analysis
    val errorAnalysis: List<ErrorAnalysisItem>,
    
    // Rep-by-rep timeline
    val repTimeline: List<RepTimelineEntry>,
    
    // Consistency metrics
    val consistency: ConsistencyMetrics,
    
    // Planned Workout quality
    val workoutQuality: Planned WorkoutQuality,
    
    // Top improvement tips (max 2 + 1 next focus)
    val improvementTips: List<ImprovementTip>,
    
    // Captured frames
    val frameCaptures: List<FrameCapture>,
    
    // Hold-specific (null for rep-based)
    val holdSummary: HoldSummary? = null
)
```

### Summary Model

```kotlin
data class PerformanceSummary(
    val totalReps: Int,
    val correctReps: Int,
    val incorrectReps: Int,
    val accuracy: Float,            // 0-100
    val durationMs: Long,
    val rating: PerformanceRating,
    val motivationalMessage: LocalizedText
) {
    fun getFormattedDuration(): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / 1000) / 60
        return "%02d:%02d".format(minutes, seconds)
    }
    
    fun getFormattedAccuracy(): String = "%.0f%%".format(accuracy)
}

enum class PerformanceRating {
    EXCELLENT,   // 90%+
    GOOD,        // 75-89%
    FAIR,        // 60-74%
    NEEDS_WORK   // <60%
}
```

### Best/Worst Rep Models

```kotlin
data class BestRepHighlight(
    val repNumber: Int,
    val durationMs: Long,
    val reasons: List<LocalizedText>,
    val frameCapture: FrameCapture?
)

data class WorstRepHighlight(
    val repNumber: Int,
    val durationMs: Long,
    val errorCount: Int,
    val primaryError: String,
    val frameCapture: FrameCapture?
)
```

### Error Analysis Model

```kotlin
data class ErrorAnalysisItem(
    val errorKey: String,           // "left_knee:TOO_HIGH"
    val jointCode: String,
    val errorType: ErrorType,
    val count: Int,
    val affectedReps: List<Int>,
    val message: LocalizedText,     // Error description
    val tip: LocalizedText,         // How to fix
    val averageActualAngle: Double,
    val expectedRange: AngleRange,
    val errorFrame: FrameCapture?,  // User's error frame
    val bestRepFrame: FrameCapture? // User's best rep for comparison
)
```

### Timeline Model

```kotlin
data class RepTimelineEntry(
    val repNumber: Int,
    val status: RepStatus,
    val durationMs: Long,
    val errors: List<String>,       // Short error labels
    val isBestRep: Boolean,
    val isWorstRep: Boolean,
    val frameCapture: FrameCapture?
)

enum class RepStatus {
    CORRECT,
    HAS_ERRORS,
    BEST_REP,
    WORST_REP,
    FAILED  // For hold exercises
}
```

### Consistency Metrics (NEW)

```kotlin
data class ConsistencyMetrics(
    val averageDurationMs: Long,
    val minDurationMs: Long,
    val maxDurationMs: Long,
    val fastestRep: Int,
    val slowestRep: Int,
    val variationMs: Long           // max - min
) {
    fun getFormattedAverage(): String = formatMs(averageDurationMs)
    fun getFormattedMin(): String = formatMs(minDurationMs)
    fun getFormattedMax(): String = formatMs(maxDurationMs)
    fun getFormattedVariation(): String = "±${formatMs(variationMs / 2)}"
    
    private fun formatMs(ms: Long): String {
        val seconds = ms / 1000.0
        return "%.1fs".format(seconds)
    }
}
```

### Planned Workout Quality (NEW)

```kotlin
data class Planned WorkoutQuality(
    val visibilityPauseCount: Int,
    val totalInvisibleMs: Long,
    val cameraWarningCount: Int,
    val overallQuality: QualityLevel,
    val suggestions: List<LocalizedText>
)

enum class QualityLevel {
    EXCELLENT,  // No issues
    GOOD,       // Minor issues
    FAIR,       // Some issues
    POOR        // Many issues
}
```

### Improvement Tips Model

```kotlin
data class ImprovementTip(
    val id: String,
    val category: TipCategory,
    val title: LocalizedText,
    val description: LocalizedText,
    val priority: Int,              // 1 = highest priority
    val isNextFocus: Boolean = false // For "Next planned workout focus"
)

enum class TipCategory {
    DEPTH,          // Not going low enough
    ALIGNMENT,      // Body alignment issues
    TIMING,         // Too fast/slow
    POSITION,       // Position-based errors
    STABILITY       // Form breaking during movement
}
```

### Hold Summary Model

```kotlin
data class HoldSummary(
    val targetMs: Long,
    val achievedMs: Long,
    val percentage: Float,          // achievedMs / targetMs * 100
    val formQuality: Float,         // 0-100
    val gracePeriodsUsed: Int,
    val jointBreakdown: List<JointHoldQuality>,
    val sampleFrames: List<FrameCapture>
)

data class JointHoldQuality(
    val jointCode: String,
    val jointName: LocalizedText,
    val quality: HoldQuality,
    val errorCount: Int
)

enum class HoldQuality {
    STABLE,         // No or minimal errors
    MINOR_DRIFT,    // 1-3 corrections needed
    UNSTABLE        // Frequent corrections
}
```

### Frame Capture Model

```kotlin
data class FrameCapture(
    val id: String,
    val repNumber: Int,
    val phase: Phase,
    val timestamp: Long,
    val captureType: CaptureType,
    val errorType: String?,
    val frameUri: String,
    val thumbnailUri: String,
    val metadata: FrameMetadata
)

enum class CaptureType {
    BEST_REP,
    ERROR_FRAME,
    PEAK_FRAME,
    HOLD_SAMPLE
}

data class FrameMetadata(
    val angles: Map<String, Double>,
    val hasError: Boolean,
    val errorDetails: String?
)
```

---

## UI Design

### Report Activity Layout

```xml
<!-- activity_report.xml -->
<CoordinatorLayout>
    
    <!-- Collapsing Toolbar with Hero Section -->
    <AppBarLayout>
        <CollapsingToolbarLayout>
            <!-- Hero with animation for good performance -->
            <include layout="@layout/report_hero_section"/>
            <Toolbar/>
        </CollapsingToolbarLayout>
    </AppBarLayout>
    
    <NestedScrollView>
        <LinearLayout orientation="vertical">
            
            <!-- Summary Card -->
            <include layout="@layout/report_summary_card"/>
            
            <!-- Tab Layout for Sections -->
            <TabLayout>
                <TabItem text="Best"/>
                <TabItem text="Errors"/>
                <TabItem text="Timeline"/>
                <TabItem text="Tips"/>
            </TabLayout>
            
            <ViewPager2/>
            
            <!-- Bottom Actions -->
            <LinearLayout>
                <Button text="Train Again"/>
                <Button text="View Captures"/>
            </LinearLayout>
            
        </LinearLayout>
    </NestedScrollView>
    
</CoordinatorLayout>
```

### Key Components

#### Summary Card
```
┌────────────────────────────────────┐
│  🏋️ Squat                          │
│  Normal Level                       │
│                                     │
│  ┌──────┐  ┌──────┐  ┌──────┐     │
│  │  10  │  │  83% │  │ 3:45 │     │
│  │ Reps │  │ Acc  │  │ Time │     │
│  └──────┘  └──────┘  └──────┘     │
│                                     │
│  [████████████░░░] 83%             │
│                                     │
│  🏅 Great Job! Keep it up!          │
└────────────────────────────────────┘
```

#### Best Reps Section
```
┌────────────────────────────────────┐
│  ⭐ YOUR BEST MOMENTS               │
│                                     │
│  ┌─────────┐ ┌─────────┐           │
│  │ [Frame] │ │ [Frame] │           │
│  │  Rep 3  │ │  Rep 7  │           │
│  │ ✓ 2.1s  │ │ ✓ 1.9s  │           │
│  └─────────┘ └─────────┘           │
│                                     │
│  💪 3 perfect reps!                 │
└────────────────────────────────────┘
```

#### Error Analysis Section
```
┌────────────────────────────────────┐
│  ⚠️ AREAS TO IMPROVE                │
│                                     │
│  ┌──────────────────────────────┐  │
│  │ Left Knee - Too High (4x)    │  │
│  │                              │  │
│  │ [Error Frame]→[Best Rep]     │  │
│  │                              │  │
│  │ 💡 Go lower until thighs     │  │
│  │    are parallel.             │  │
│  │                              │  │
│  │ Reps: #2, #4, #5, #8         │  │
│  └──────────────────────────────┘  │
└────────────────────────────────────┘
```

#### Timeline Section
```
┌────────────────────────────────────┐
│  📋 REP TIMELINE                    │
│                                     │
│  1 ✅ ━━━━━━━━━━━━━━━━━━━ 2.3s     │
│  2 ⚠️ ━━━[knee]━━━━━━━━━ 2.8s     │
│  3 ⭐ ━━━━━━━━━━━━━━━━━━━ 2.0s ★   │
│  4 ⚠️ ━━━━━━━━[pos]━━━━━ 2.5s     │
│  5 ✅ ━━━━━━━━━━━━━━━━━━━ 2.1s     │
│  ...                                │
│                                     │
│  📊 Avg: 2.3s | ±0.3s variation    │
│                                     │
│  [Tap rep for details]              │
└────────────────────────────────────┘
```

### Color Scheme

| Element | Color | Hex |
|---------|-------|-----|
| Correct/Success | Green | #4CAF50 |
| Error | Red | #F44336 |
| Warning | Amber | #FFC107 |
| Best/Star | Gold | #FFD700 |
| Primary | Blue | #2196F3 |
| Background | Dark Gray | #1E1E1E |
| Card Background | Lighter Gray | #2D2D2D |
| Text Primary | White | #FFFFFF |
| Text Secondary | Gray | #B0B0B0 |

---

## Implementation Phases

### Phase 1: Data Foundation (Week 1) ✅ IN PROGRESS

**Goals:**
- Create all data models
- Implement ReportGenerator
- Basic report storage

**Tasks:**

| Task | File | Status |
|------|------|--------|
| Create `PostTrainingReport.kt` | `training/report/` | 🔄 In Progress |
| Create `FrameCapture.kt` | `training/report/` | 🔄 In Progress |
| Create `ConsistencyMetrics.kt` | `training/report/` | 🔄 In Progress |
| Create `Planned WorkoutQuality.kt` | `training/report/` | 🔄 In Progress |
| Create `ReportGenerator.kt` | `training/report/` | ⬜ Pending |
| Create `ReportStorage.kt` | `storage/` | ⬜ Pending |

**Deliverable:** Report can be generated from WorkoutRunSummary (without frames)

---

### Phase 2: Frame Capture System (Week 2)

**Goals:**
- Implement frame capture manager
- Integrate with TrainingActivity
- Handle camera and video modes

**Tasks:**

| Task | File | Status |
|------|------|--------|
| Create `FrameCaptureManager.kt` | `training/report/` | ⬜ |
| Integrate in TrainingActivity (Camera) | `ui/` | ⬜ |
| Integrate in VideoModeController | `ui/training/` | ⬜ |
| Add bitmap capture from PreviewView | `ui/` | ⬜ |
| Storage cleanup logic | `training/report/` | ⬜ |

**Deliverable:** Frames captured during training and associated with report

---

### Phase 3: Report UI - Core (Week 3)

**Goals:**
- Implement ReportActivity
- Summary and Best Reps sections
- Navigation from training completion

**Tasks:**

| Task | File | Status |
|------|------|--------|
| Create `ReportActivity.kt` | `ui/report/` | ⬜ |
| Create `ReportViewModel.kt` | `ui/report/` | ⬜ |
| Design `activity_report.xml` layout | `res/layout/` | ⬜ |
| Create `SummarySection.kt` component | `ui/report/components/` | ⬜ |
| Create `BestRepsSection.kt` component | `ui/report/components/` | ⬜ |
| Navigate to report on training complete | `ui/TrainingActivity.kt` | ⬜ |
| Rating calculation logic | `training/report/` | ⬜ |

**Deliverable:** Basic report screen with summary and best reps

---

### Phase 4: Report UI - Error Analysis (Week 4)

**Goals:**
- Error analysis section with frames
- Side-by-side comparison view (Error vs Best Rep)
- Tips display

**Tasks:**

| Task | File | Status |
|------|------|--------|
| Create `ErrorAnalysisSection.kt` | `ui/report/components/` | ⬜ |
| Create `ErrorCard.kt` component | `ui/report/components/` | ⬜ |
| Frame comparison view (Error vs Best) | `ui/report/components/` | ⬜ |
| Error grouping logic | `training/report/` | ⬜ |
| Tip generation from exercise JSON | `training/report/` | ⬜ |

**Deliverable:** Full error analysis with captured frames and tips

---

### Phase 5: Report UI - Timeline & Polish (Week 5)

**Goals:**
- Rep timeline section with consistency metrics
- Hold exercise support
- Planned Workout quality display
- UI polish and animations

**Tasks:**

| Task | File | Status |
|------|------|--------|
| Create `RepTimeline.kt` component | `ui/report/components/` | ⬜ |
| Rep detail dialog | `ui/report/` | ⬜ |
| Create `HoldSummarySection.kt` | `ui/report/components/` | ⬜ |
| Create `TipsSection.kt` | `ui/report/components/` | ⬜ |
| Create `Planned WorkoutQualitySection.kt` | `ui/report/components/` | ⬜ |
| Tab navigation (ViewPager2) | `ui/report/` | ⬜ |
| Animations and transitions | Various | ⬜ |
| Dark mode support | `res/values/` | ⬜ |
| Arabic RTL layout | Various | ⬜ |

**Deliverable:** Complete report UI with all sections

---

### Phase 6: Testing & Refinement (Week 6)

**Goals:**
- End-to-end testing
- Performance optimization
- Bug fixes

**Tasks:**

| Task | Status |
|------|--------|
| Test with all exercise types | ⬜ |
| Test with HOLD exercises | ⬜ |
| Test video mode captures | ⬜ |
| Memory optimization (large images) | ⬜ |
| Crash/error handling | ⬜ |
| Arabic translation review | ⬜ |
| User testing feedback | ⬜ |

**Deliverable:** Production-ready post-training report

---

## File Structure

### New Files to Create

```
app/src/main/java/com/trainingvalidator/poc/
├── training/
│   └── report/
│       ├── PostTrainingReport.kt       // Main report model + all sub-models
│       ├── FrameCapture.kt             // Frame capture model
│       ├── FrameCaptureManager.kt      // Capture logic
│       ├── ReportGenerator.kt          // Generate report from workout run
│       └── TipGenerator.kt             // Generate tips from errors
│
├── storage/
│   └── ReportStorage.kt                // Save/load reports
│
└── ui/
    └── report/
        ├── ReportActivity.kt           // Main report screen
        ├── ReportViewModel.kt          // ViewModel
        └── components/
            ├── SummaryCard.kt          // Summary section
            ├── BestRepsSection.kt      // Best reps display
            ├── ErrorAnalysisSection.kt // Error breakdown
            ├── ErrorCard.kt            // Individual error card
            ├── FrameComparisonView.kt  // Side-by-side frames
            ├── RepTimeline.kt          // Rep timeline
            ├── RepDetailDialog.kt      // Rep detail popup
            ├── TipsSection.kt          // Improvement tips
            ├── Planned WorkoutQualityCard.kt   // Planned Workout quality
            └── HoldSummarySection.kt   // Hold-specific section

app/src/main/res/
├── layout/
│   ├── activity_report.xml
│   ├── report_summary_card.xml
│   ├── report_hero_section.xml
│   ├── item_best_rep.xml
│   ├── item_error_card.xml
│   ├── item_timeline_rep.xml
│   ├── item_improvement_tip.xml
│   ├── dialog_rep_detail.xml
│   └── view_frame_comparison.xml
│
├── anim/
│   └── slide_in_bottom.xml
│
├── drawable/
│   └── ic_report_*.xml                 // Report icons
│
└── values/
    ├── strings_report.xml              // Report strings (EN)
    └── strings_report_ar.xml           // Report strings (AR)
```

### Files to Modify

| File | Changes |
|------|---------|
| `TrainingActivity.kt` | Add FrameCaptureManager, navigate to report |
| `VideoModeController.kt` | Support frame capture (copy bitmap before recycle) |
| `TrainingViewModel.kt` | Expose workout duration properly |

---

## Future Enhancements

### 🔜 Phase 2: Share & Export

| Feature | Description |
|---------|-------------|
| Share as Image | Generate shareable image summary |
| Export PDF | Full detailed PDF report |
| Social Media | Direct share to Instagram/WhatsApp |

### 🔜 Phase 3: Admin Sync

| Feature | Description |
|---------|-------------|
| Upload Report | Sync report to admin dashboard |
| Cloud Storage | Store frames in cloud |
| User History | View all reports in admin |

### 🔜 Phase 4: Progress Tracking

| Feature | Description |
|---------|-------------|
| Historical Comparison | Compare with last N planned workouts |
| Progress Charts | Line/bar charts over time |
| Streaks | Track consecutive workout days |
| Achievements | Badges and milestones |
| Personal Records | Track PRs for each exercise |

### 🔜 Phase 5: Advanced Analytics

| Feature | Description |
|---------|-------------|
| AI Insights | Machine learning-based form analysis |
| Video Playback | Watch captured moments as video |
| Coach Mode | Share with trainer for feedback |
| Workout Plans | Recommendations based on history |

---

## Appendix

### A. Localization Strings

```xml
<!-- strings_report.xml (English) -->
<resources>
    <string name="report_title">Training Report</string>
    <string name="report_summary_title">Performance Summary</string>
    <string name="report_best_reps_title">Your Best Moments</string>
    <string name="report_errors_title">Areas to Improve</string>
    <string name="report_timeline_title">Rep Timeline</string>
    <string name="report_tips_title">How to Improve</string>
    <string name="report_quality_title">Planned Workout Quality</string>
    <string name="report_consistency_title">Consistency</string>
    
    <string name="report_rating_excellent">Outstanding! Nearly perfect form!</string>
    <string name="report_rating_good">Great job! Keep it up!</string>
    <string name="report_rating_fair">Good effort! Room to improve.</string>
    <string name="report_rating_needs_work">Keep practicing! You\'ll get better!</string>
    
    <string name="report_best_count">You nailed %d perfect reps!</string>
    <string name="report_error_count">%s (%d times)</string>
    <string name="report_affected_reps">Affected Reps: %s</string>
    <string name="report_tip_label">💡 Tip:</string>
    <string name="report_next_focus">🔮 Next Focus:</string>
    
    <string name="report_avg_duration">Average: %s</string>
    <string name="report_variation">Variation: %s</string>
    
    <string name="report_train_again">Train Again</string>
    <string name="report_view_captures">View Captures</string>
</resources>

<!-- strings_report_ar.xml (Arabic) -->
<resources>
    <string name="report_title">تقرير التدريب</string>
    <string name="report_summary_title">ملخص الأداء</string>
    <string name="report_best_reps_title">أفضل لحظاتك</string>
    <string name="report_errors_title">نقاط للتحسين</string>
    <string name="report_timeline_title">الجدول الزمني</string>
    <string name="report_tips_title">كيف تتحسن</string>
    <string name="report_quality_title">جودة الجلسة</string>
    <string name="report_consistency_title">الاتساق</string>
    
    <string name="report_rating_excellent">ممتاز! أداء شبه مثالي!</string>
    <string name="report_rating_good">عمل رائع! استمر!</string>
    <string name="report_rating_fair">مجهود جيد! يمكنك التحسن.</string>
    <string name="report_rating_needs_work">استمر بالتدريب! ستتحسن!</string>
    
    <string name="report_best_count">أحرزت %d عدات مثالية!</string>
    <string name="report_error_count">%s (%d مرات)</string>
    <string name="report_affected_reps">العدات المتأثرة: %s</string>
    <string name="report_tip_label">💡 نصيحة:</string>
    <string name="report_next_focus">🔮 التركيز القادم:</string>
    
    <string name="report_avg_duration">المتوسط: %s</string>
    <string name="report_variation">التفاوت: %s</string>
    
    <string name="report_train_again">تدريب مرة أخرى</string>
    <string name="report_view_captures">عرض الصور</string>
</resources>
```

### B. No Additional Dependencies Required

The implementation uses only existing dependencies:
- `Bitmap.compress()` for image saving (Android SDK)
- Existing `AnimationUtils` for animations
- Standard Android UI components

---

## Summary

This plan provides a comprehensive implementation roadmap for the Post-Training Report feature:

- **6 weeks** of development across 6 phases
- **Core features**: Performance summary, best reps, error analysis, timeline, tips
- **NEW features**: Consistency metrics, planned workout quality, worst rep comparison
- **Frame capture**: Real camera/video frames at key moments
- **Simplified approach**: Use user's best rep as reference (no external assets)
- **Localization**: Full Arabic and English support
- **Future-ready**: Architecture supports upcoming share/sync/progress features

**Key Improvements in v1.1:**
1. Fixed duration tracking (use ViewModel instead of WorkoutRunSummary)
2. Correct frame capture source (PreviewView for camera, copied bitmap for video)
3. Best Rep as reference instead of external "correct form" images
4. Added consistency metrics and planned workout quality
5. Simplified tips (Top 2 + Next Focus)
6. Added worst rep for comparison
7. Frame capture limits to prevent storage bloat
8. No additional dependencies required

Ready to begin implementation. ✅
