> **Status:** `ARCHIVED` â€” implemented or superseded; not current product truth.
> **Current SSOT:** `Docs/00-Active-Reference/Product-Master/Post-Training-Report-Review.md`
> **Archived:** 2026-05-29

# 📊 Post-Training Report Enhancement Plan
## State-Based Report with Clear User Feedback

---

## 1. Vision & Goals

### 🎯 Main Objective
Professional user experience that shows:
1. **What was done well** → Motivation and encouragement
2. **What was done wrong** → Clear explanation with solution
3. **Improvement path** → Practical tips for next session

### 🎨 Design Principles
| Principle | Description |
|-----------|-------------|
| **Clarity** | Clear, organized information |
| **Motivation** | Highlight achievements first |
| **Simplicity** | Simple, smooth UI |
| **Visual Errors** | Images are KEY - show problems visually |
| **DANGER Alert** | Strongly emphasize dangerous states |
| **PERFECT Celebration** | Celebrate perfect performance |

---

## 2. State Names Mapping (User-Friendly)

### Internal → Display Names

| Internal State | Color | Arabic Display | English Display | Icon |
|----------------|-------|----------------|-----------------|------|
| `PERFECT` | 🟢 Green | **مثالي!** | **Excellent!** | ⭐ |
| `NORMAL` | 🟡 Yellow | **جيد** | **Good** | ✓ |
| `PAD` | 🟠 Orange | **مقبول** | **Acceptable** | ~ |
| `WARNING` | 🔴 Red | **يحتاج تحسين** | **Needs Work** | ⚠️ |
| `DANGER` | ⚫ Dark Red | **خطر - انتبه!** | **Danger - Caution!** | 🚨 |

### Implementation

```kotlin
// StateDisplayConfig.kt
object StateDisplayConfig {
    
    data class DisplayInfo(
        val nameAr: String,
        val nameEn: String,
        val icon: String,
        val colorRes: Int,
        val priority: DisplayPriority
    )
    
    enum class DisplayPriority {
        CELEBRATE,    // PERFECT - show with celebration
        POSITIVE,     // NORMAL - positive feedback
        NEUTRAL,      // PAD - neutral, can improve
        ATTENTION,    // WARNING - needs attention
        CRITICAL      // DANGER - critical alert
    }
    
    val DISPLAY_MAP = mapOf(
        JointState.PERFECT to DisplayInfo(
            nameAr = "مثالي!",
            nameEn = "Excellent!",
            icon = "⭐",
            colorRes = R.color.state_perfect,
            priority = DisplayPriority.CELEBRATE
        ),
        JointState.NORMAL to DisplayInfo(
            nameAr = "جيد",
            nameEn = "Good",
            icon = "✓",
            colorRes = R.color.state_normal,
            priority = DisplayPriority.POSITIVE
        ),
        JointState.PAD to DisplayInfo(
            nameAr = "مقبول",
            nameEn = "Acceptable",
            icon = "~",
            colorRes = R.color.state_pad,
            priority = DisplayPriority.NEUTRAL
        ),
        JointState.WARNING to DisplayInfo(
            nameAr = "يحتاج تحسين",
            nameEn = "Needs Work",
            icon = "⚠️",
            colorRes = R.color.state_warning,
            priority = DisplayPriority.ATTENTION
        ),
        JointState.DANGER to DisplayInfo(
            nameAr = "خطر - انتبه!",
            nameEn = "Danger - Caution!",
            icon = "🚨",
            colorRes = R.color.state_danger,
            priority = DisplayPriority.CRITICAL
        )
    )
    
    fun getDisplayName(state: JointState, isArabic: Boolean): String {
        val info = DISPLAY_MAP[state] ?: return state.name
        return if (isArabic) info.nameAr else info.nameEn
    }
}
```

---

## 3. Report Structure

### 3.1 Hero Section (Performance Summary)

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│              🏆 ممتاز! أداء رائع!                        │
│                                                         │
│     ┌─────────┬─────────┬─────────┐                    │
│     │   12    │   85%   │  02:34  │                    │
│     │ تكرار   │  نقاط   │  مدة    │                    │
│     └─────────┴─────────┴─────────┘                    │
│                                                         │
│   ┌─────────────────────────────────────────────────┐   │
│   │ ⭐⭐⭐⭐⭐⭐ | ✓✓ | ~ | ⚠ |                         │   │
│   │ مثالي(6)  جيد(2) مقبول(1) يحتاج تحسين(1)         │   │
│   └─────────────────────────────────────────────────┘   │
│                                                         │
│        "استمر بهذا الأداء الرائع! 💪"                   │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

#### Data Model

```kotlin
data class PerformanceSummary(
    val totalReps: Int,
    val countedReps: Int,           // مثالي + جيد + مقبول
    val invalidatedReps: Int,       // خطر
    val averageScore: Float,        // 0-100
    val durationMs: Long,
    val rating: PerformanceRating,
    
    // State breakdown for visual bar
    val stateBreakdown: StateBreakdown,
    
    // Motivational message from exercise JSON
    val motivationalMessage: LocalizedText,
    
    // Celebration mode for excellent performance
    val shouldCelebrate: Boolean
)

data class StateBreakdown(
    val perfectCount: Int,
    val normalCount: Int,
    val padCount: Int,
    val warningCount: Int,
    val dangerCount: Int
) {
    val totalCounted: Int get() = perfectCount + normalCount + padCount
    val perfectRatio: Float get() = perfectCount.toFloat() / (totalCounted.coerceAtLeast(1))
    
    fun shouldCelebrate(): Boolean = perfectRatio >= 0.5f && dangerCount == 0
}
```

---

### 3.2 DANGER Alert Section (If Any)

**Critical**: This section appears ONLY if there were DANGER states. It should be prominent and impossible to miss.

```
┌─────────────────────────────────────────────────────────┐
│  🚨 تنبيه هام!                                          │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                         │
│  تم رصد وضعية خطيرة في التكرار #8                       │
│                                                         │
│  ┌────────────────────────────────────────────────────┐ │
│  │                                                    │ │
│  │              [صورة كبيرة واضحة]                    │ │
│  │                                                    │ │
│  │     زاوية الركبة: 25° (خطر!)                       │ │
│  │     المدى الآمن: 60° - 90°                         │ │
│  │                                                    │ │
│  └────────────────────────────────────────────────────┘ │
│                                                         │
│  "توقف! هذا قد يؤذي ركبتيك"                             │
│                                                         │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│  💡 الحل:                                               │
│  "ادفع من كعبيك للصعود ولا تنزل أكثر من اللازم"         │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

#### Danger Alert Card

```kotlin
data class DangerAlert(
    val repNumber: Int,
    val jointCode: String,
    val jointName: LocalizedText,
    val actualAngle: Double,
    val safeRange: AngleRange,
    
    // Critical: The frame showing the dangerous position
    val dangerFrame: FrameCapture,
    
    // Message from stateMessages.danger in exercise JSON
    val dangerMessage: LocalizedText,
    
    // Tip from feedbackMessages.tips
    val solutionTip: LocalizedText
)
```

---

### 3.3 Perfect Celebration Section (If Any)

**Important**: Celebrate PERFECT reps with enthusiasm. Show best moments.

```
┌─────────────────────────────────────────────────────────┐
│  ⭐ لحظاتك المثالية!                                    │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │  [صورة]  │  │  [صورة]  │  │  [صورة]  │              │
│  │  Rep #1  │  │  Rep #4  │  │  Rep #9  │              │
│  │  ⭐ 100% │  │  ⭐ 100% │  │  ⭐ 100% │              │
│  └──────────┘  └──────────┘  └──────────┘              │
│                                                         │
│  "ممتاز! أداء رائع! 🎉"                                 │
│                                                         │
│  نصيحة للحفاظ على هذا المستوى:                          │
│  "اعصر عضلة البايسبس في أعلى الحركة"                    │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

### 3.4 Errors Analysis Section

**Focus**: Visual comparison between error and correct form.

```
┌─────────────────────────────────────────────────────────┐
│  ⚠️ نقاط تحتاج تحسين                                    │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                         │
│  ┌─────────────────────────────────────────────────────┐│
│  │  الركبة - يحتاج تحسين (3 مرات)                      ││
│  │  ─────────────────────────────────────────────────  ││
│  │                                                     ││
│  │  "جيد، حاول النزول أكثر قليلاً"                      ││
│  │                                                     ││
│  │  ┌─────────────┐    VS    ┌─────────────┐          ││
│  │  │   ❌ خطأ    │          │   ✓ صحيح   │          ││
│  │  │             │          │             │          ││
│  │  │  [صورتك]   │          │ [أفضل أداء] │          ││
│  │  │             │          │             │          ││
│  │  │   زاوية:   │          │   زاوية:   │          ││
│  │  │    105°    │          │    75°     │          ││
│  │  └─────────────┘          └─────────────┘          ││
│  │                                                     ││
│  │  📍 التكرارات: #3, #6, #10                          ││
│  │                                                     ││
│  │  💡 الحل:                                           ││
│  │  "ادفع من كعبيك للصعود"                             ││
│  │                                                     ││
│  └─────────────────────────────────────────────────────┘│
│                                                         │
└─────────────────────────────────────────────────────────┘
```

#### Error Analysis Item (Updated)

```kotlin
data class ErrorAnalysisItem(
    val errorKey: String,
    val jointCode: String,
    val jointName: LocalizedText,           // "الركبة" / "Knee"
    
    // State info
    val state: JointState,                  // WARNING or DANGER
    val stateDisplayInfo: DisplayInfo,      // User-friendly name & icon
    
    val occurrenceCount: Int,
    val affectedReps: List<Int>,
    
    // Messages from exercise JSON
    val stateMessage: LocalizedText,        // From stateMessages
    val solutionTip: LocalizedText,         // From feedbackMessages.tips
    
    // CRITICAL: Visual comparison
    val errorFrame: FrameCapture?,          // User's error moment
    val bestRepFrame: FrameCapture?,        // User's best moment (for comparison)
    
    // Angle info for display
    val averageErrorAngle: Double,
    val expectedRange: AngleRange,
    val bestRepAngle: Double?               // Angle in best rep
)
```

---

### 3.5 Rep Timeline Section

**Visual timeline showing each rep with state and score**

```
┌─────────────────────────────────────────────────────────┐
│  📋 تفاصيل التكرارات                                    │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                         │
│  #1  ⭐ مثالي!     100%   2.1s  ────────────────────   │
│  #2  ⭐ مثالي!     100%   2.3s  ────────────────────   │
│  #3  ✓  جيد        60%   2.0s  "حاول النزول أكثر"     │
│  #4  ⭐ مثالي!     100%   2.2s  ────────────────────   │
│  #5  ~  مقبول      20%   2.5s  "يمكنك التحسن"         │
│  #6  ⚠️ يحتاج      0%    1.8s  "أنت تنزل أكثر"        │
│      تحسين                                              │
│  #7  ⭐ مثالي!     100%   2.1s  ────────────────────   │
│  #8  🚨 خطر!       0%    1.5s  "توقف! خطر!"           │
│      [ملغى]                                             │
│  ...                                                    │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

#### Rep Timeline Entry (Updated)

```kotlin
data class RepTimelineEntry(
    val repNumber: Int,
    val worstState: JointState,
    val stateDisplayInfo: DisplayInfo,      // User-friendly display
    val score: Float,                       // 0-100
    val durationMs: Long,
    
    val isCounted: Boolean,
    val isInvalidated: Boolean,             // DANGER
    val isBestRep: Boolean,
    
    // State message (shown for non-PERFECT reps)
    val stateMessage: LocalizedText?,
    
    val frameCapture: FrameCapture?
)
```

---

### 3.6 Tips Section

**Actionable tips for next session**

```
┌─────────────────────────────────────────────────────────┐
│  💡 نصائح للجلسة القادمة                                │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                         │
│  ┌─────────────────────────────────────────────────────┐│
│  │ 1️⃣ الأهم                                           ││
│  │                                                     ││
│  │ "ادفع من كعبيك للصعود"                              ││
│  │                                                     ││
│  │ هذا سيساعدك على تجنب الأخطاء التي ظهرت في           ││
│  │ التكرارات #3, #6, #10                               ││
│  └─────────────────────────────────────────────────────┘│
│                                                         │
│  ┌─────────────────────────────────────────────────────┐│
│  │ 2️⃣ نصيحة إضافية                                    ││
│  │                                                     ││
│  │ "انظر للأمام وليس للأسفل"                           ││
│  └─────────────────────────────────────────────────────┘│
│                                                         │
│  ┌─────────────────────────────────────────────────────┐│
│  │ 🎯 تركيز الجلسة القادمة                             ││
│  │                                                     ││
│  │ "شد عضلات بطنك طوال الحركة"                         ││
│  └─────────────────────────────────────────────────────┘│
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 4. Frame Capture Strategy

### Critical Frames to Capture

| Frame Type | When to Capture | Priority | Use in Report |
|------------|-----------------|----------|---------------|
| `BEST_REP` | Perfect rep completion | High | Celebration + Comparison |
| `DANGER_FRAME` | DANGER state detected | Critical | DANGER Alert |
| `ERROR_FRAME` | WARNING state detected | High | Error Analysis |
| `PEAK_FRAME` | Each rep's peak position | Medium | Timeline |

### Frame Capture Manager Updates

```kotlin
class FrameCaptureManager {
    
    companion object {
        const val MAX_BEST_REPS = 3
        const val MAX_DANGER_FRAMES = 2       // Capture multiple DANGER moments
        const val MAX_ERROR_FRAMES_PER_TYPE = 1
    }
    
    // Priority: DANGER > BEST > ERROR > PEAK
    fun captureDangerFrame(
        bitmap: Bitmap,
        repNumber: Int,
        phase: Phase,
        jointCode: String,
        actualAngle: Double,
        dangerRange: AngleRange
    ): FrameCapture? {
        // Always capture DANGER - this is critical for user safety awareness
        // Include angle annotation on the frame if possible
    }
    
    fun captureBestRepFrame(
        bitmap: Bitmap,
        repNumber: Int,
        phase: Phase,
        angles: Map<String, Double>
    ): FrameCapture? {
        // Capture PERFECT reps for celebration
    }
}
```

---

## 5. Motivational Messages Strategy

### When to Show Motivational Messages

| Scenario | Message Source | Example |
|----------|----------------|---------|
| 80%+ PERFECT reps | `feedbackMessages.motivational` | "ممتاز! استمر!" |
| No DANGER states | `feedbackMessages.motivational` | "أداء رائع!" |
| First session | Static message | "بداية رائعة!" |
| Improved from last | Computed | "تحسنت عن المرة السابقة!" |

### Message Selection Logic

```kotlin
object MotivationalMessageSelector {
    
    fun selectMessage(
        summary: PerformanceSummary,
        exerciseConfig: ExerciseConfig,
        previousBestScore: Float? = null
    ): LocalizedText {
        
        val motivationalMessages = exerciseConfig.getFeedbackMessages()?.motivational
            ?: getDefaultMotivational()
        
        return when {
            // Celebrate excellent performance
            summary.stateBreakdown.perfectRatio >= 0.8f && 
            summary.stateBreakdown.dangerCount == 0 -> {
                motivationalMessages.randomOrNull() 
                    ?: LocalizedText(ar = "ممتاز! أداء مثالي!", en = "Excellent! Perfect form!")
            }
            
            // Good performance
            summary.rating == PerformanceRating.GOOD -> {
                LocalizedText(ar = "عمل رائع! استمر!", en = "Great job! Keep it up!")
            }
            
            // Improvement detected
            previousBestScore != null && summary.averageScore > previousBestScore -> {
                LocalizedText(
                    ar = "تحسنت عن المرة السابقة! 📈",
                    en = "You improved from last time! 📈"
                )
            }
            
            // Needs work but encouraging
            else -> {
                LocalizedText(
                    ar = "استمر بالتدريب! ستتحسن!",
                    en = "Keep practicing! You'll get better!"
                )
            }
        }
    }
}
```

---

## 6. Tips Selection Strategy

### Priority Order for Tips

1. **DANGER Fix** (if any) - From `stateMessages.danger` tip
2. **Most Common WARNING** - From `stateMessages.warning` tip  
3. **Exercise Tips** - From `feedbackMessages.tips`
4. **Next Focus** - One tip for next session

### Tips Generator

```kotlin
object TipsGenerator {
    
    fun generateTips(
        errorAnalysis: List<ErrorAnalysisItem>,
        exerciseConfig: ExerciseConfig,
        maxTips: Int = 3
    ): List<ImprovementTip> {
        val tips = mutableListOf<ImprovementTip>()
        
        // Priority 1: DANGER fixes (CRITICAL)
        errorAnalysis
            .filter { it.state == JointState.DANGER }
            .maxByOrNull { it.occurrenceCount }
            ?.let { dangerError ->
                tips.add(ImprovementTip(
                    priority = 1,
                    icon = "🚨",
                    titleAr = "الأهم - لسلامتك",
                    titleEn = "Most Important - For Your Safety",
                    description = dangerError.solutionTip,
                    relatedReps = dangerError.affectedReps,
                    severity = TipSeverity.CRITICAL
                ))
            }
        
        // Priority 2: Most common WARNING
        errorAnalysis
            .filter { it.state == JointState.WARNING }
            .maxByOrNull { it.occurrenceCount }
            ?.let { warningError ->
                tips.add(ImprovementTip(
                    priority = 2,
                    icon = "1️⃣",
                    titleAr = "نقطة التحسين الرئيسية",
                    titleEn = "Main Improvement Point",
                    description = warningError.solutionTip,
                    relatedReps = warningError.affectedReps,
                    severity = TipSeverity.IMPORTANT
                ))
            }
        
        // Priority 3: Exercise tips (from JSON)
        exerciseConfig.getFeedbackTips().take(2).forEachIndexed { index, tip ->
            tips.add(ImprovementTip(
                priority = 3 + index,
                icon = if (index == 0) "2️⃣" else "🎯",
                titleAr = if (index == 0) "نصيحة إضافية" else "تركيز الجلسة القادمة",
                titleEn = if (index == 0) "Additional Tip" else "Next Session Focus",
                description = tip,
                relatedReps = emptyList(),
                severity = TipSeverity.HELPFUL,
                isNextFocus = index == 1
            ))
        }
        
        return tips.take(maxTips)
    }
}
```

---

## 7. UI Components

### 7.1 StateDistributionBar

Visual bar showing state distribution with user-friendly labels.

```kotlin
class StateDistributionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    
    private var breakdown: StateBreakdown? = null
    
    fun setBreakdown(breakdown: StateBreakdown) {
        this.breakdown = breakdown
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        breakdown?.let { bd ->
            val total = bd.totalCounted.coerceAtLeast(1)
            var startX = 0f
            
            // Draw segments with icons
            drawSegment(canvas, startX, bd.perfectCount, total, StateDisplayConfig.DISPLAY_MAP[JointState.PERFECT]!!)
            // ... other segments
        }
    }
}
```

### 7.2 DangerAlertCard

Prominent card for DANGER states.

```kotlin
class DangerAlertCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CardView(context, attrs) {
    
    fun bind(dangerAlert: DangerAlert) {
        // Red/dark background
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.danger_background))
        
        // Large warning icon
        ivWarningIcon.setImageResource(R.drawable.ic_danger_large)
        
        // Large, clear image
        loadImage(dangerAlert.dangerFrame.frameUri, ivDangerImage)
        
        // Angle overlay on image
        tvAngleOverlay.text = "${dangerAlert.actualAngle.toInt()}°"
        
        // Message from JSON
        tvDangerMessage.text = dangerAlert.dangerMessage.getLocalized()
        
        // Solution tip
        tvSolutionTip.text = dangerAlert.solutionTip.getLocalized()
    }
}
```

### 7.3 ErrorComparisonCard

Side-by-side comparison of error vs correct form.

```kotlin
class ErrorComparisonCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CardView(context, attrs) {
    
    fun bind(error: ErrorAnalysisItem) {
        // Header with state display name
        tvErrorTitle.text = "${error.jointName.getLocalized()} - ${error.stateDisplayInfo.nameAr}"
        ivStateIcon.text = error.stateDisplayInfo.icon
        
        // State message
        tvStateMessage.text = error.stateMessage.getLocalized()
        
        // Error frame (left)
        error.errorFrame?.let { frame ->
            loadImage(frame.frameUri, ivErrorFrame)
            tvErrorAngle.text = "${error.averageErrorAngle.toInt()}°"
            ivErrorBadge.setImageResource(R.drawable.ic_error_badge)
        }
        
        // Best rep frame (right) for comparison
        error.bestRepFrame?.let { frame ->
            loadImage(frame.frameUri, ivCorrectFrame)
            tvCorrectAngle.text = "${error.bestRepAngle?.toInt() ?: ""}°"
            ivCorrectBadge.setImageResource(R.drawable.ic_correct_badge)
        }
        
        // Affected reps
        tvAffectedReps.text = error.affectedReps.joinToString(", ") { "#$it" }
        
        // Solution tip
        tvSolutionTip.text = "💡 ${error.solutionTip.getLocalized()}"
    }
}
```

---

## 8. Implementation Phases

### Phase 1: Data Models (Foundation)

| Task | File | Priority |
|------|------|----------|
| Create `StateDisplayConfig.kt` | `training/models/` | High |
| Update `PerformanceSummary` | `PostTrainingReport.kt` | High |
| Add `StateBreakdown` | `PostTrainingReport.kt` | High |
| Update `RepTimelineEntry` | `PostTrainingReport.kt` | High |
| Update `ErrorAnalysisItem` | `PostTrainingReport.kt` | High |
| Add `DangerAlert` | `PostTrainingReport.kt` | High |

### Phase 2: Report Generator

| Task | File | Priority |
|------|------|----------|
| Update `generateSummary()` with states | `ReportGenerator.kt` | High |
| Add `generateDangerAlerts()` | `ReportGenerator.kt` | High |
| Update `generateErrorAnalysis()` | `ReportGenerator.kt` | High |
| Add `generatePerfectMoments()` | `ReportGenerator.kt` | High |
| Update `generateTimeline()` | `ReportGenerator.kt` | Medium |
| Update `generateTips()` | `ReportGenerator.kt` | Medium |

### Phase 3: Frame Capture

| Task | File | Priority |
|------|------|----------|
| Add `captureDangerFrame()` | `FrameCaptureManager.kt` | High |
| Update `captureBestRepFrame()` | `FrameCaptureManager.kt` | High |
| Integrate with `TrainingEngine` | `TrainingEngine.kt` | High |

### Phase 4: UI Components

| Task | File | Priority |
|------|------|----------|
| Create `StateDistributionBar` | `ui/report/components/` | High |
| Create `DangerAlertCard` | `ui/report/components/` | High |
| Create `ErrorComparisonCard` | `ui/report/components/` | High |
| Create `PerfectMomentsSection` | `ui/report/components/` | Medium |
| Update layout XMLs | `res/layout/` | Medium |

### Phase 5: ReportActivity

| Task | File | Priority |
|------|------|----------|
| Update Hero Section | `ReportActivity.kt` | High |
| Add DANGER Alert Section | `ReportActivity.kt` | High |
| Update Errors Tab | `ReportActivity.kt` | High |
| Add Perfect Moments | `ReportActivity.kt` | Medium |
| Update Timeline Tab | `ReportActivity.kt` | Medium |
| Update Tips Tab | `ReportActivity.kt` | Medium |

### Phase 6: Polish

| Task | File | Priority |
|------|------|----------|
| Add animations | Various | Low |
| Add share functionality | `ReportActivity.kt` | Low |
| Add localization | `strings.xml` | Medium |
| Performance optimization | Various | Low |

---

## 9. Color Palette

```kotlin
// colors.xml
<color name="state_perfect">#4CAF50</color>      <!-- Green -->
<color name="state_normal">#FFEB3B</color>       <!-- Yellow -->
<color name="state_pad">#FF9800</color>          <!-- Orange -->
<color name="state_warning">#FF5252</color>      <!-- Light Red -->
<color name="state_danger">#B71C1C</color>       <!-- Dark Red -->

<color name="danger_background">#1AB71C1C</color> <!-- Dark Red 10% -->
<color name="perfect_background">#1A4CAF50</color> <!-- Green 10% -->
<color name="warning_background">#1AFF5252</color> <!-- Light Red 10% -->
```

---

## 10. Success Criteria

| Criteria | Description | Status |
|----------|-------------|--------|
| ✅ State-friendly Names | User sees "مثالي", "جيد", not "PERFECT", "NORMAL" | Pending |
| ✅ DANGER Prominent | DANGER states are impossible to miss | Pending |
| ✅ PERFECT Celebrated | PERFECT moments are celebrated with images | Pending |
| ✅ Visual Comparison | Errors show side-by-side comparison | Pending |
| ✅ JSON Messages | Uses `stateMessages` from exercise JSON | Pending |
| ✅ Exercise Tips | Uses `feedbackMessages.tips` from JSON | Pending |
| ✅ Motivational | Uses `feedbackMessages.motivational` | Pending |
| ✅ State Distribution | Visual bar shows state breakdown | Pending |
| ✅ Clear Timeline | Each rep shows state, score, and message | Pending |
| ✅ Bilingual | All text in Arabic and English | Pending |

---

## 11. Example Flow

### User completes Squat with 12 reps

**Session Data:**
- 6 PERFECT, 2 NORMAL, 2 PAD, 1 WARNING, 1 DANGER
- Average Score: 72%
- DANGER on rep #8 (knee at 25°)

**Report Shows:**

1. **Hero**: "جيد! 72% نقاط" with state distribution bar
2. **DANGER Alert**: Large image of rep #8 with "توقف! هذا قد يؤذي ركبتيك"
3. **Perfect Moments**: Images from reps #1, #2, #4 with "ممتاز! أداء رائع!"
4. **Errors**: Comparison of WARNING rep #6 vs PERFECT rep #1
5. **Timeline**: All 12 reps with icons and messages
6. **Tips**: 
   - 🚨 "ادفع من كعبيك للصعود" (for DANGER)
   - 1️⃣ "انظر للأمام وليس للأسفل" (from tips)
   - 🎯 "شد عضلات بطنك" (next focus)

---

*Last Updated: 2026-01-15*
*Status: PLANNED - Ready for Implementation*
