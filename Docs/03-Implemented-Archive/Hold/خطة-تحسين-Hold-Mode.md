> **Status:** `ARCHIVED` â€” implemented or superseded; not current product truth.
> **Current SSOT:** `Docs/00-Active-Reference/Engine/training-engine.md, Positions-Check-Concept.md`
> **Archived:** 2026-05-29

# 📋 خطة تحسين وضع Hold Mode

## 📊 الوضع الحالي

### ✅ ما هو مُنفَّذ ويعمل:
| المكوّن | الحالة | ملاحظات |
|---------|--------|---------|
| `HoldTimer.kt` | ✅ مكتمل | إدارة حالات Hold، Grace Period، Callbacks |
| `PhaseStateMachine.kt` | ✅ يدعم Hold | `updateHold()` تستخدم `downRange` كـ Hold Zone |
| `FormValidator.kt` | ✅ يدعم Phase.COUNT | التحقق من الأخطاء يعمل خلال Hold |
| `FeedbackEvent.kt` | ✅ مكتمل | جميع أحداث Hold موجودة |
| `FeedbackManager.kt` | ✅ مكتمل | TTS & Haptic feedback لـ Hold |
| `TrainingActivity.kt` | ✅ مكتمل | UI يعرض الوقت والتقدم |
| `VideoAnalysisResult.kt` | ✅ مكتمل | تخزين نتائج Hold |

### ⚠️ تمارين Hold الموجودة:
| التمرين | Position Checks | الحالة |
|---------|-----------------|--------|
| `plank.json` | ✅ موجودة (3) | **تم التحديث ✓** |
| `wall_sit.json` | ✅ موجودة (2) | جاهز |
| `side_plank.json` | ✅ موجودة (1) | جاهز |
| `arm_hold.json` | ❌ غير موجودة | تمرين اختباري بسيط |

---

## 🚨 المشاكل المكتشفة

### 1. 🔴 **أخطاء Form لا تُتعقب أثناء Hold (Critical)**

**الموقع:** `TrainingEngine.kt` (lines 454-466)

```kotlin
// الكود الحالي يتجاهل الأخطاء في Hold!
for (error in validation.errors) {
    if (!isHoldExercise) {  // ⚠️ يتجاهل Hold
        repCounter.addError(error)
    }
    emitEvent(FeedbackEvent.JointErrorDetected(error))
}
```

**المشكلة:** 
- الأخطاء تُرسل كـ events للـ UI (✓)
- لكن لا تُسجل لحساب `formQuality` (✗)
- `calculateFormQuality()` يرجع دائماً `1.0f`

**التأثير:** المستخدم لا يحصل على تقييم دقيق لأدائه في تمارين Hold

---

### 2. 🔴 **`calculateFormQuality()` غير منفذة (Critical)**

**الموقع:** `TrainingEngine.kt` (lines 603-611)

```kotlin
private fun calculateFormQuality(): Float {
    // TODO: Track form errors during hold and calculate quality
    return 1.0f  // ⚠️ دائماً perfect!
}
```

---

### 3. 🟡 **لا يوجد تتبع زمني للأخطاء (Medium)**

يجب تتبع:
- `totalErrorTimeMs`: إجمالي الوقت في وضع خاطئ
- `formQuality = 1 - (errorTime / totalHoldTime)`
- أي Joints كانت الأكثر خطأً

---

### 4. 🟡 **Plank.json ينقصه Position Checks (Medium)**

تمرين `plank.json` ينقصه فحوصات مهمة:
- استقامة الظهر (Shoulder-Hip-Ankle alignment)
- عدم رفع أو خفض الحوض

---

### 5. 🟢 **لا يوجد مؤشرات بصرية خاصة بـ Hold في Overlay (Low)**

مقترحات:
- شريط تقدم دائري
- تأثير نبض أثناء Grace Period
- لون خاص لحالة HOLDING

---

## 📋 خطة التنفيذ

### المرحلة 1: تتبع أخطاء Form أثناء Hold 🔴 Critical
**الملفات:** `TrainingEngine.kt`, `HoldTimer.kt`

#### 1.1 إضافة متغيرات تتبع الأخطاء

```kotlin
// في TrainingEngine.kt - إضافة بعد holdTimer
private var holdErrorFrameCount: Int = 0
private var holdTotalFrameCount: Int = 0
private val holdJointErrors = mutableMapOf<String, Int>() // joint -> error count
```

#### 1.2 تعديل processFrame لتسجيل الأخطاء

```kotlin
// تعديل الجزء الخاص بـ Hold في processFrame
if (isHoldExercise && _holdState.value == HoldState.HOLDING) {
    holdTotalFrameCount++
    
    if (validation.errors.isNotEmpty()) {
        holdErrorFrameCount++
        validation.errors.forEach { error ->
            holdJointErrors[error.jointCode] = 
                (holdJointErrors[error.jointCode] ?: 0) + 1
        }
    }
}
```

#### 1.3 تنفيذ calculateFormQuality

```kotlin
private fun calculateFormQuality(): Float {
    if (holdTotalFrameCount == 0) return 1.0f
    val correctFrames = holdTotalFrameCount - holdErrorFrameCount
    return (correctFrames.toFloat() / holdTotalFrameCount).coerceIn(0f, 1f)
}
```

#### 1.4 إضافة Reset للمتغيرات

```kotlin
// في start() و عند Hold reset
private fun resetHoldTracking() {
    holdErrorFrameCount = 0
    holdTotalFrameCount = 0
    holdJointErrors.clear()
}
```

---

### المرحلة 2: إضافة State Flows لـ Form Quality 🟡 Medium
**الملفات:** `TrainingEngine.kt`

```kotlin
// إضافة State Flows جديدة
private val _holdFormQuality = MutableStateFlow(1.0f)
val holdFormQuality: StateFlow<Float> = _holdFormQuality

private val _holdErrorCount = MutableStateFlow(0)
val holdErrorCount: StateFlow<Int> = _holdErrorCount

// تحديث في processFrame
_holdFormQuality.value = calculateFormQuality()
_holdErrorCount.value = holdErrorFrameCount
```

---

### المرحلة 3: تحسين plank.json 🟡 Medium ✅ **تم التنفيذ**
**الملفات:** `exercises/plank.json`

تم إضافة Position Checks التالية:

| Check ID | النوع | الوصف |
|----------|-------|-------|
| `body_alignment` | `vertical_alignment` | محاذاة الكتف مع الورك |
| `hip_sag_check` | `vertical_comparison` | منع انحناء الوسط للأسفل |
| `hip_pike_check` | `vertical_comparison` | منع رفع المؤخرة للأعلى |

```json
// تم إضافة 3 Position Checks:
// 1. body_alignment - فحص استقامة الجسم
// 2. hip_sag_check - فحص انحناء الوسط (severity: error)
// 3. hip_pike_check - فحص رفع المؤخرة (severity: warning)
```

---

### المرحلة 4: تحسين UI لعرض Form Quality 🟢 Low
**الملفات:** `TrainingActivity.kt`

#### 4.1 مراقبة Form Quality

```kotlin
// في observeHoldState
lifecycleScope.launch {
    engine.holdFormQuality.collectLatest { quality ->
        updateFormQualityIndicator(quality)
    }
}

private fun updateFormQualityIndicator(quality: Float) {
    val color = when {
        quality >= 0.9f -> COLOR_CORRECT
        quality >= 0.7f -> COLOR_WARNING
        else -> COLOR_ERROR
    }
    binding.tvFormQuality?.text = "${(quality * 100).toInt()}%"
    binding.tvFormQuality?.setTextColor(color)
}
```

---

### المرحلة 5: تحسينات مستقبلية 🔮 Future

#### 5.1 دعم Multiple Sets
```kotlin
data class HoldSetResult(
    val setNumber: Int,
    val durationMs: Long,
    val formQuality: Float,
    val gracePeriodsUsed: Int
)

// في RepCountingConfig
val sets: Int? = null  // For hold exercises
val restBetweenSetsMs: Long? = null
```

#### 5.2 مؤشرات بصرية في SkeletonOverlay
- شريط تقدم دائري حول المستخدم
- Pulse effect أثناء Grace Period
- تغيير لون الـ Skeleton بناءً على Form Quality

---

## ✅ قائمة المهام

### 🔴 أولوية عالية (يجب تنفيذها)
- [x] 1.1 إضافة متغيرات تتبع الأخطاء في `TrainingEngine.kt` ✅ **تم**
- [x] 1.2 تعديل `processFrame` لتسجيل الأخطاء أثناء Hold ✅ **تم**
- [x] 1.3 تنفيذ `calculateFormQuality()` بشكل صحيح ✅ **تم**
- [x] 1.4 إضافة reset للمتغيرات عند بدء/إعادة Hold ✅ **تم**

### 🟡 أولوية متوسطة
- [x] 2.1 إضافة State Flows لـ Form Quality و Error Count ✅ **تم**
- [x] 3.1 تحديث `plank.json` بإضافة Position Checks ✅ **تم**
- [x] 4.1 عرض Form Quality في UI ✅ **تم**

### 🟢 أولوية منخفضة (مستقبلية)
- [ ] 5.1 دعم Multiple Sets
- [ ] 5.2 مؤشرات بصرية في SkeletonOverlay

---

## 📁 الملفات المتأثرة

| الملف | التعديل |
|-------|---------|
| `TrainingEngine.kt` | إضافة تتبع الأخطاء، تنفيذ `calculateFormQuality()` |
| `plank.json` | إضافة Position Checks |
| `TrainingActivity.kt` | عرض Form Quality (اختياري) |

---

## ⏱️ الوقت المقدر

| المرحلة | الوقت | الحالة |
|---------|-------|--------|
| المرحلة 1 (Critical) | ~30 دقيقة | ✅ **تم** |
| المرحلة 2 (State Flows) | ~15 دقيقة | ✅ **تم** |
| المرحلة 3 (plank.json) | ~10 دقائق | ✅ **تم** |
| المرحلة 4 (UI) | ~20 دقيقة | ✅ **تم** |
| **الإجمالي** | **~1 ساعة 15 دقيقة** | ✅ **مكتمل** |

---

## 📝 ملاحظات

1. **التوافق الخلفي:** جميع التغييرات متوافقة مع الكود الحالي ولن تؤثر على تمارين UP_DOWN/PUSH_PULL

2. **الاختبار:** يُنصح باختبار التغييرات على:
   - `plank.json` (بعد إضافة Position Checks)
   - `wall_sit.json` (لديه Position Checks بالفعل)
   - `side_plank.json`

3. **arm_hold.json:** يحتاج فحص لتحديد إذا كان يحتاج Position Checks

---

**تاريخ الإنشاء:** January 6, 2026
**تاريخ الإكمال:** January 6, 2026
**الحالة:** ✅ **تم التنفيذ بالكامل**

---

## 📝 ملخص التغييرات المنفذة

### ✅ المرحلة 1: تتبع أخطاء Form أثناء Hold
- ✅ إضافة متغيرات `holdErrorFrameCount`, `holdTotalFrameCount`, `holdJointErrors`
- ✅ تعديل `processFrame` لتسجيل الأخطاء أثناء `HoldState.HOLDING`
- ✅ تنفيذ `calculateFormQuality()` بحساب: `(totalFrames - errorFrames) / totalFrames`
- ✅ إضافة `resetHoldTracking()` واستدعاؤها عند بدء/إعادة Hold

### ✅ المرحلة 2: State Flows لـ Form Quality
- ✅ إضافة `holdFormQuality: StateFlow<Float?>`
- ✅ إضافة `holdErrorCount: StateFlow<Int?>`
- ✅ إضافة `holdJointErrorMap: StateFlow<Map<String, Int>?>`
- ✅ تحديث State Flows في `processFrame` أثناء Hold

### ✅ المرحلة 3: تحسين plank.json
- ✅ إضافة 3 Position Checks:
  - `body_alignment` (vertical_alignment, warning)
  - `hip_sag_check` (vertical_comparison, error)
  - `hip_pike_check` (vertical_comparison, warning)

### ✅ المرحلة 4: تحسين UI
- ✅ إضافة `observeHoldState` لمراقبة `holdFormQuality`
- ✅ إضافة `updateFormQualityIndicator()` مع تلوين:
  - 🟢 أخضر: ≥90% (Excellent)
  - 🟡 برتقالي: 70-89% (Good)
  - 🔴 أحمر: <70% (Needs Improvement)

---

## 🎯 النتيجة النهائية

الآن تمارين Hold تدعم:
- ✅ تتبع دقيق لأخطاء Form أثناء Hold
- ✅ حساب Form Quality بناءً على نسبة الأخطاء
- ✅ State Flows للـ UI لمراقبة الجودة
- ✅ Position Checks محسّنة في `plank.json`
- ✅ UI جاهز لعرض Form Quality (يمكن إضافة TextView في Layout لاحقاً)
