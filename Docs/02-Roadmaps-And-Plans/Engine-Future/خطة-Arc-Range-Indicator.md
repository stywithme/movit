# Arc Range Indicator - خطة تنفيذ مؤشر نطاق الحركة الدائري

## 📌 ملخص الميزة

إضافة دائرة ملونة بتدرج (Gradient Arc) حول الزوايا الرئيسية تُظهر نطاق الحركة المسموح والخطأ بألوان واضحة، مما يساعد المتدرب على فهم موقعه الحالي ضمن النطاق المطلوب.

## 🎯 الهدف

```
┌────────────────────────────────────────────────────────────────┐
│  المتدرب يرى قوس دائري ملون حول المفصل (الركبة/الكوع/الورك)    │
│  يُظهر:                                                        │
│  • النطاق الكامل للحركة (0° - 180°)                           │
│  • منطقة UP_ZONE باللون الأخضر                                 │
│  • منطقة DOWN_ZONE باللون الأخضر                               │
│  • منطقة TRANSITION باللون الأزرق                              │
│  • منطقة TOO_HIGH/TOO_LOW باللون الأحمر                        │
│  • مؤشر للموقع الحالي على القوس                               │
└────────────────────────────────────────────────────────────────┘
```

## 🎨 التصميم البصري

### شكل القوس (Arc Visualization)

```
                    180°
                     │
            TOO_HIGH │ (أحمر)
        ┌────────────┼────────────┐
        │   upRange.max           │
        │            │            │
        │   UP_ZONE  │  (أخضر)    │ ← نطاق البداية
        │            │            │
        │   upRange.min           │
        ├────────────┼────────────┤
        │            │            │
        │ TRANSITION │  (أزرق)    │ ← منطقة الحركة
        │            │            │
        ├────────────┼────────────┤
        │  downRange.max          │
        │            │            │
        │  DOWN_ZONE │  (أخضر)    │ ← نطاق الهدف
        │            │            │
        │  downRange.min          │
        └────────────┼────────────┘
            TOO_LOW  │ (أحمر)
                     │
                    0°
```

### ألوان التدرج

| المنطقة | اللون | الكود |
|---------|-------|-------|
| TOO_HIGH | أحمر | `#FF5252` |
| UP_ZONE (center) | أخضر | `#00E676` |
| UP_ZONE (edge) | أصفر → برتقالي | `#FFEB3B` → `#FF9800` |
| TRANSITION | أزرق فاتح | `#81D4FA` |
| DOWN_ZONE (center) | أخضر | `#00E676` |
| DOWN_ZONE (edge) | أصفر → برتقالي | `#FFEB3B` → `#FF9800` |
| TOO_LOW | أحمر | `#FF5252` |

### مؤشر الموقع الحالي

- **نقطة مضيئة** على القوس تُظهر الزاوية الحالية
- **خط** من مركز المفصل إلى القوس
- **تأثير Glow** عند الاقتراب من الحدود

---

## 📁 البنية الملفية

### الملفات الجديدة

```
overlay/
├── SkeletonOverlayView.kt          (موجود - سيتم تعديله)
├── ArcRangeIndicator.kt            (جديد - الرسم الأساسي)
├── ArcRangeData.kt                 (جديد - نماذج البيانات)
└── ArcColorCalculator.kt           (جديد - حساب الألوان)
```

### الملفات المعدلة

| الملف | نوع التعديل |
|-------|-------------|
| `SkeletonOverlayView.kt` | إضافة رسم الـ Arc Indicators |
| `AppSettings.kt` | إضافة `VisualSettings` للإعدادات البصرية |
| `SettingsManager.kt` | إضافة accessors للإعدادات البصرية |
| `app_settings.json` | إضافة قسم `visual` (اختياري) |

**ملاحظة**: `JointArrowInfo` موجود بالفعل ويحتوي على جميع البيانات المطلوبة (upRangeMin/Max, downRangeMin/Max, currentAngle, zone) - لا حاجة لتعديله.

---

## 🏗️ التصميم التقني

### 1. ArcRangeData.kt - نماذج البيانات

```kotlin
/**
 * Data class for Arc Range visualization
 */
data class ArcRangeData(
    val jointCode: String,
    val centerX: Float,
    val centerY: Float,
    val currentAngle: Double,
    val upRangeMin: Double,
    val upRangeMax: Double,
    val downRangeMin: Double,
    val downRangeMax: Double,
    val zone: JointZone,
    val isError: Boolean,
    val isWarning: Boolean
) {
    /**
     * Convert joint angle (0-180°) to canvas arc angle
     * 
     * Canvas arc angles:
     * - 0° = 3 o'clock (East)
     * - 90° = 6 o'clock (South)
     * - 180° = 9 o'clock (West)
     * - 270° = 12 o'clock (North)
     * 
     * We want to map:
     * - Joint 0° → Canvas 90° (bottom of arc)
     * - Joint 180° → Canvas -90° (top of arc)
     * 
     * Formula: canvasAngle = 90° - jointAngle
     */
    fun getCanvasAngle(jointAngle: Double): Float {
        return (90.0 - jointAngle).toFloat()
    }
    
    /**
     * Get arc sweep angle for a range
     */
    fun getSweepAngle(startAngle: Double, endAngle: Double): Float {
        return (endAngle - startAngle).toFloat()
    }
}

/**
 * Configuration for Arc visual appearance
 */
data class ArcConfig(
    val radiusDp: Float = 45f,  // Radius in dp (will be converted to px)
    val strokeWidthDp: Float = 6f,  // Stroke width in dp
    val indicatorRadiusDp: Float = 5f,
    val showCurrentIndicator: Boolean = true,
    val showRangeLabels: Boolean = false,
    val animationEnabled: Boolean = true,
    val showOnlyOnError: Boolean = false,  // Show only when error/warning
    val showOnlyPrimary: Boolean = true  // Show only for primary joints
)
```

### 2. ArcColorCalculator.kt - حساب الألوان

```kotlin
/**
 * Calculates gradient colors for Arc Range Indicator
 */
object ArcColorCalculator {
    
    // Zone colors
    private val COLOR_ERROR = Color.parseColor("#FF5252")
    private val COLOR_OPTIMAL = Color.parseColor("#00E676")
    private val COLOR_NEAR_BOUNDARY = Color.parseColor("#FFEB3B")
    private val COLOR_BOUNDARY = Color.parseColor("#FF9800")
    private val COLOR_TRANSITION = Color.parseColor("#81D4FA")
    
    /**
     * Get color for a specific zone
     */
    fun getZoneColor(zone: JointZone): Int {
        return when (zone) {
            JointZone.TOO_HIGH, JointZone.TOO_LOW -> COLOR_ERROR
            JointZone.UP_ZONE, JointZone.DOWN_ZONE -> COLOR_OPTIMAL
            JointZone.TRANSITION -> COLOR_TRANSITION
        }
    }
    
    /**
     * Get color for zone edge (approaching boundary)
     */
    fun getZoneEdgeColor(): Int = COLOR_BOUNDARY
    
    /**
     * Get color for zone center (optimal position)
     */
    fun getZoneCenterColor(): Int = COLOR_OPTIMAL
    
    /**
     * Get color for current angle position
     */
    fun getColorForAngle(
        angle: Double,
        upRangeMin: Double,
        upRangeMax: Double,
        downRangeMin: Double,
        downRangeMax: Double
    ): Int {
        return when {
            angle > upRangeMax -> COLOR_ERROR
            angle < downRangeMin -> COLOR_ERROR
            angle >= upRangeMin && angle <= upRangeMax -> {
                getZoneColor(angle, upRangeMin, upRangeMax)
            }
            angle >= downRangeMin && angle <= downRangeMax -> {
                getZoneColor(angle, downRangeMin, downRangeMax)
            }
            else -> COLOR_TRANSITION
        }
    }
    
    private fun getZoneColor(angle: Double, min: Double, max: Double): Int {
        val center = (min + max) / 2
        val halfRange = (max - min) / 2
        val distFromCenter = kotlin.math.abs(angle - center)
        val normalizedDist = (distFromCenter / halfRange).coerceIn(0.0, 1.0)
        
        return when {
            normalizedDist < 0.4 -> COLOR_OPTIMAL
            normalizedDist < 0.7 -> interpolateColor(COLOR_OPTIMAL, COLOR_NEAR_BOUNDARY, 
                ((normalizedDist - 0.4) / 0.3).toFloat())
            else -> interpolateColor(COLOR_NEAR_BOUNDARY, COLOR_BOUNDARY,
                ((normalizedDist - 0.7) / 0.3).toFloat())
        }
    }
    
    private fun interpolateColor(colorA: Int, colorB: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        return Color.argb(
            255,
            (Color.red(colorA) + (Color.red(colorB) - Color.red(colorA)) * r).toInt(),
            (Color.green(colorA) + (Color.green(colorB) - Color.green(colorA)) * r).toInt(),
            (Color.blue(colorA) + (Color.blue(colorB) - Color.blue(colorA)) * r).toInt()
        )
    }
}
```

### 3. ArcRangeIndicator.kt - الرسم الأساسي

**استراتيجية الرسم**: استخدام **Segments** بدلاً من SweepGradient المعقد لضمان دقة الألوان وتوافقها مع المنطق.

```kotlin
package com.trainingvalidator.poc.overlay

import android.graphics.*
import android.graphics.Paint.Cap
import com.trainingvalidator.poc.training.engine.JointZone

/**
 * ArcRangeIndicator - Draws segmented arc showing valid angle ranges
 * 
 * Uses segment-based drawing for precise color mapping:
 * - Each zone (TOO_LOW, DOWN_ZONE, TRANSITION, UP_ZONE, TOO_HIGH) drawn separately
 * - Gradient within each zone for smooth transitions
 * - Better performance and accuracy than SweepGradient
 */
class ArcRangeIndicator {
    
    companion object {
        private const val INDICATOR_RADIUS_DP = 5f
        private const val GLOW_RADIUS_DP = 12f
        private const val LINE_WIDTH_DP = 2f
    }
    
    // Paint objects (reused for performance)
    private val segmentPaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Cap.ROUND
    }
    
    private val indicatorPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val glowPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val linePaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    /**
     * Draw arc range indicator for a joint
     * 
     * @param canvas Canvas to draw on
     * @param data Arc range data
     * @param config Configuration
     * @param density Screen density for dp to px conversion
     */
    fun draw(
        canvas: Canvas,
        data: ArcRangeData,
        config: ArcConfig,
        density: Float
    ) {
        // Convert dp to px
        val radius = config.radiusDp * density
        val strokeWidth = config.strokeWidthDp * density
        
        // Calculate bounding rect for arc
        val rectF = RectF(
            data.centerX - radius,
            data.centerY - radius,
            data.centerX + radius,
            data.centerY + radius
        )
        
        // 1. Draw arc segments (zones)
        drawArcSegments(canvas, rectF, data, strokeWidth, radius)
        
        // 2. Draw current position indicator
        if (config.showCurrentIndicator) {
            drawCurrentIndicator(canvas, data, radius, density)
        }
    }
    
    /**
     * Draw arc as segments for each zone
     * More accurate than SweepGradient for half-circle
     */
    private fun drawArcSegments(
        canvas: Canvas,
        rectF: RectF,
        data: ArcRangeData,
        strokeWidth: Float,
        radius: Float
    ) {
        segmentPaint.strokeWidth = strokeWidth
        
        // Convert joint angles to canvas angles
        // Canvas: 0° = East, 90° = South, 180° = West, 270° = North
        // We want: Joint 0° → Canvas 90° (bottom), Joint 180° → Canvas -90° (top)
        fun toCanvasAngle(jointAngle: Double): Float {
            return (90.0 - jointAngle).toFloat()
        }
        
        // Draw segments in order from 0° to 180°
        
        // 1. TOO_LOW zone (0° to downRangeMin)
        if (data.downRangeMin > 0) {
            val startAngle = toCanvasAngle(0.0)
            val sweepAngle = toCanvasAngle(data.downRangeMin) - startAngle
            segmentPaint.color = ArcColorCalculator.getZoneColor(JointZone.TOO_LOW)
            canvas.drawArc(rectF, startAngle, sweepAngle, false, segmentPaint)
        }
        
        // 2. DOWN_ZONE (downRangeMin to downRangeMax) - with gradient
        val downSweep = toCanvasAngle(data.downRangeMax) - toCanvasAngle(data.downRangeMin)
        drawGradientSegment(
            canvas, rectF, data.centerX, data.centerY,
            toCanvasAngle(data.downRangeMin), downSweep,
            data.downRangeMin, data.downRangeMax,
            ArcColorCalculator.getZoneColor(JointZone.DOWN_ZONE),
            strokeWidth
        )
        
        // 3. TRANSITION zone (downRangeMax to upRangeMin)
        if (data.upRangeMin > data.downRangeMax) {
            val transitionStart = toCanvasAngle(data.downRangeMax)
            val transitionSweep = toCanvasAngle(data.upRangeMin) - transitionStart
            segmentPaint.color = ArcColorCalculator.getZoneColor(JointZone.TRANSITION)
            canvas.drawArc(rectF, transitionStart, transitionSweep, false, segmentPaint)
        }
        
        // 4. UP_ZONE (upRangeMin to upRangeMax) - with gradient
        val upSweep = toCanvasAngle(data.upRangeMax) - toCanvasAngle(data.upRangeMin)
        drawGradientSegment(
            canvas, rectF, data.centerX, data.centerY,
            toCanvasAngle(data.upRangeMin), upSweep,
            data.upRangeMin, data.upRangeMax,
            ArcColorCalculator.getZoneColor(JointZone.UP_ZONE),
            strokeWidth
        )
        
        // 5. TOO_HIGH zone (upRangeMax to 180°)
        if (data.upRangeMax < 180.0) {
            val startAngle = toCanvasAngle(data.upRangeMax)
            val sweepAngle = toCanvasAngle(180.0) - startAngle
            segmentPaint.color = ArcColorCalculator.getZoneColor(JointZone.TOO_HIGH)
            canvas.drawArc(rectF, startAngle, sweepAngle, false, segmentPaint)
        }
    }
    
    /**
     * Draw a segment with gradient (center = optimal, edges = boundary)
     */
    private fun drawGradientSegment(
        canvas: Canvas,
        rectF: RectF,
        centerX: Float,
        centerY: Float,
        startAngle: Float,
        sweepAngle: Float,
        rangeMin: Double,
        rangeMax: Double,
        centerColor: Int,
        strokeWidth: Float
    ) {
        val centerAngle = (rangeMin + rangeMax) / 2
        val canvasCenterAngle = (90.0 - centerAngle).toFloat()
        
        // Create gradient: edge → center → edge
        val colors = intArrayOf(
            ArcColorCalculator.getZoneEdgeColor(),  // Edge
            centerColor,                            // Center
            ArcColorCalculator.getZoneEdgeColor()   // Edge
        )
        val positions = floatArrayOf(0f, 0.5f, 1f)
        
        val gradient = SweepGradient(centerX, centerY, colors, positions).apply {
            val matrix = Matrix()
            matrix.postRotate(canvasCenterAngle, centerX, centerY)
            setLocalMatrix(matrix)
        }
        
        segmentPaint.shader = gradient
        segmentPaint.strokeWidth = strokeWidth
        canvas.drawArc(rectF, startAngle, sweepAngle, false, segmentPaint)
        segmentPaint.shader = null
    }
    
    /**
     * Draw indicator for current angle position
     */
    private fun drawCurrentIndicator(
        canvas: Canvas,
        data: ArcRangeData,
        radius: Float,
        density: Float
    ) {
        // Convert joint angle to canvas angle
        val canvasAngle = (90.0 - data.currentAngle).toFloat()
        val angleRad = Math.toRadians(canvasAngle.toDouble())
        
        val indicatorX = data.centerX + (radius * kotlin.math.cos(angleRad)).toFloat()
        val indicatorY = data.centerY + (radius * kotlin.math.sin(angleRad)).toFloat()
        
        // Get color for current position
        val indicatorColor = ArcColorCalculator.getColorForAngle(
            data.currentAngle,
            data.upRangeMin,
            data.upRangeMax,
            data.downRangeMin,
            data.downRangeMax
        )
        
        val indicatorRadius = INDICATOR_RADIUS_DP * density
        val glowRadius = GLOW_RADIUS_DP * density
        
        // Draw glow effect
        glowPaint.color = indicatorColor
        glowPaint.alpha = if (data.isError || data.isWarning) 150 else 100
        glowPaint.maskFilter = BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(indicatorX, indicatorY, indicatorRadius * 2, glowPaint)
        
        // Draw indicator dot
        indicatorPaint.color = indicatorColor
        canvas.drawCircle(indicatorX, indicatorY, indicatorRadius, indicatorPaint)
        
        // Draw line from center to indicator
        linePaint.color = indicatorColor
        linePaint.alpha = 150
        linePaint.strokeWidth = LINE_WIDTH_DP * density
        canvas.drawLine(data.centerX, data.centerY, indicatorX, indicatorY, linePaint)
    }
}
```

### 4. تعديلات على SkeletonOverlayView.kt

```kotlin
// إضافة في أعلى الملف
private val arcRangeIndicator = ArcRangeIndicator()
private var showArcIndicators = true

// إضافة method جديدة
fun setShowArcIndicators(show: Boolean) {
    showArcIndicators = show
    invalidate()
}

// تعديل onDraw - إضافة بعد drawLandmarks
override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val currentLandmarks = landmarks ?: return
    if (currentLandmarks.isEmpty()) return

    // Draw connections first (behind points)
    drawConnections(canvas, currentLandmarks)
    
    // Draw glow on tracked joints when correct (no errors)
    if (isTrainingMode && errorJointCodes.isEmpty()) {
        drawCorrectFormGlow(canvas, currentLandmarks)
    }
    
    // Draw landmark points
    drawLandmarks(canvas, currentLandmarks)
    
    // ★ NEW: Draw Arc Range Indicators for tracked joints
    if (isTrainingMode && showArcIndicators) {
        drawArcRangeIndicators(canvas, currentLandmarks)
    }
    
    // Draw position errors (knee-over-toe, alignment, etc.)
    if (isTrainingMode && positionErrors.isNotEmpty()) {
        drawPositionErrors(canvas, currentLandmarks)
    }
    
    // Draw angles if enabled (only on error when showAnglesOnlyOnError)
    if (showAngles) {
        drawAngles(canvas, currentLandmarks)
    }
}

/**
 * Draw Arc Range Indicators for all tracked joints
 */
private fun drawArcRangeIndicators(
    canvas: Canvas,
    landmarks: List<SmoothedLandmark>
) {
    for ((jointCode, arrowInfo) in jointArrowInfos) {
        val landmarkIndex = jointCodeToLandmarkIndex(jointCode) ?: continue
        if (landmarkIndex >= landmarks.size) continue
        
        val landmark = landmarks[landmarkIndex]
        if (landmark.visibility < VISIBILITY_THRESHOLD) continue
        
        val centerX = landmark.x * imageWidth * scaleFactor
        val centerY = landmark.y * imageHeight * scaleFactor
        
        val arcData = ArcRangeData(
            jointCode = jointCode,
            centerX = centerX,
            centerY = centerY,
            currentAngle = arrowInfo.currentAngle,
            upRangeMin = arrowInfo.upRangeMin,
            upRangeMax = arrowInfo.upRangeMax,
            downRangeMin = arrowInfo.downRangeMin,
            downRangeMax = arrowInfo.downRangeMax,
            zone = arrowInfo.zone,
            isError = arrowInfo.isError,
            isWarning = arrowInfo.isWarning
        )
        
        // Get density from resources
        val density = resources.displayMetrics.density
        
        val config = ArcConfig(
            radiusDp = 45f,
            strokeWidthDp = 6f,
            showCurrentIndicator = true,
            showOnlyOnError = false,  // Can be from settings
            showOnlyPrimary = true    // Show only primary joints by default
        )
        
        // Filter based on config
        if (config.showOnlyOnError && !arrowInfo.isError && !arrowInfo.isWarning) {
            return@forEach
        }
        
        if (config.showOnlyPrimary && !arrowInfo.isPrimary) {
            return@forEach
        }
        
        arcRangeIndicator.draw(canvas, arcData, config, density)
    }
}
```

---

## 📋 خطوات التنفيذ

### المرحلة 1: إنشاء الملفات الأساسية (1-2 ساعات)

| # | المهمة | الملف |
|---|--------|-------|
| 1 | إنشاء `ArcRangeData.kt` | overlay/ |
| 2 | إنشاء `ArcColorCalculator.kt` | overlay/ |
| 3 | إنشاء `ArcRangeIndicator.kt` | overlay/ |
| 4 | تعديل `AppSettings.kt` - إضافة `VisualSettings` | training/config/ |
| 5 | تعديل `SettingsManager.kt` - إضافة accessors | training/config/ |

### المرحلة 2: دمج مع SkeletonOverlayView (1 ساعة)

| # | المهمة |
|---|--------|
| 1 | إضافة `arcRangeIndicator` instance |
| 2 | إضافة `drawArcRangeIndicators()` method |
| 3 | تعديل `onDraw()` لاستدعاء الرسم الجديد |
| 4 | إضافة `setShowArcIndicators()` للتحكم |
| 5 | استخدام `density` من resources بدلاً من scaleFactor |
| 6 | تطبيق filtering (showOnlyOnError, showOnlyPrimary) |

### المرحلة 3: ضبط المظهر البصري (1-2 ساعات)

| # | المهمة |
|---|--------|
| 1 | ضبط حجم القوس وموقعه |
| 2 | ضبط الألوان والتدرج |
| 3 | إضافة تأثيرات Glow و Animation |
| 4 | التأكد من عدم تداخل القوس مع العناصر الأخرى |

### المرحلة 4: الاختبار والتحسين (1 ساعة)

| # | المهمة |
|---|--------|
| 1 | اختبار مع تمارين مختلفة (Squat, Deadlift, Bicep Curl) |
| 2 | التأكد من الأداء (FPS) |
| 3 | اختبار على أحجام شاشات مختلفة |
| 4 | إضافة خيار إخفاء/إظهار من الإعدادات |

---

## 🔧 إعدادات اختيارية

### 1. تعديل AppSettings.kt

```kotlin
data class AppSettings(
    val version: String = "1.0.0",
    val angleDetection: AngleDetectionSettings = AngleDetectionSettings(),
    val movementDetection: MovementDetectionSettings = MovementDetectionSettings(),
    val defaults: DefaultTimingSettings = DefaultTimingSettings(),
    val holdDefaults: HoldDefaults = HoldDefaults(),
    val visual: VisualSettings = VisualSettings()  // ★ NEW
)

/**
 * Visual settings for UI elements
 */
data class VisualSettings(
    val showArcRangeIndicators: Boolean = true,
    val arcIndicatorRadiusDp: Float = 45f,
    val arcIndicatorStrokeWidthDp: Float = 6f,
    val arcShowCurrentIndicator: Boolean = true,
    val arcShowOnlyOnError: Boolean = false,
    val arcShowOnlyPrimary: Boolean = true,
    val arcAnimationEnabled: Boolean = true
)
```

### 2. تعديل SettingsManager.kt

```kotlin
// إضافة accessors جديدة
fun getShowArcIndicators(): Boolean = settings.visual.showArcRangeIndicators
fun getArcIndicatorRadiusDp(): Float = settings.visual.arcIndicatorRadiusDp
fun getArcIndicatorStrokeWidthDp(): Float = settings.visual.arcIndicatorStrokeWidthDp
fun getArcShowOnlyOnError(): Boolean = settings.visual.arcShowOnlyOnError
fun getArcShowOnlyPrimary(): Boolean = settings.visual.arcShowOnlyPrimary
```

### 3. في app_settings.json (اختياري)

```json
{
  "version": "1.0.0",
  "angleDetection": { ... },
  "movementDetection": { ... },
  "defaults": { ... },
  "holdDefaults": { ... },
  "visual": {
    "showArcRangeIndicators": true,
    "arcIndicatorRadiusDp": 45,
    "arcIndicatorStrokeWidthDp": 6,
    "arcShowCurrentIndicator": true,
    "arcShowOnlyOnError": false,
    "arcShowOnlyPrimary": true,
    "arcAnimationEnabled": true
  }
}
```

---

## ⚡ اعتبارات الأداء

### تحسينات مطبقة:

1. **Paint Object Reuse**: إعادة استخدام Paint objects (موجودة في class)
2. **Lazy Drawing**: رسم القوس فقط للمفاصل المرئية (visibility check)
3. **Filtering**: إمكانية إخفاء القوس إلا عند الخطأ أو للمفاصل الأساسية فقط
4. **dp to px Conversion**: استخدام dp بدلاً من scaleFactor المباشر لتوحيد الحجم
5. **Segment-based Drawing**: استخدام Segments بدلاً من SweepGradient المعقد (أسرع وأدق)

### تحسينات مستقبلية (اختيارية):

1. **Gradient Caching**: Cache للـ gradients عند تغير النطاقات (إذا كانت ثابتة)
2. **Hardware Acceleration**: التأكد من تفعيل تسريع الأجهزة في View
3. **Path Caching**: Cache للـ Paths إذا كانت النطاقات ثابتة

---

## 🎬 تأثيرات Animation (اختيارية)

### 1. Pulse على الخطأ
```kotlin
// عند دخول منطقة الخطأ - نبض في القوس
if (isError && !wasError) {
    pulseAnimation.start()
}
```

### 2. Smooth Indicator Movement
```kotlin
// تحريك مؤشر الزاوية بسلاسة
currentIndicatorPosition = lerp(
    currentIndicatorPosition,
    targetIndicatorPosition,
    0.2f
)
```

### 3. Glow Intensity
```kotlin
// تكثيف الـ Glow عند الاقتراب من الحدود
val distanceFromBoundary = getDistanceFromNearestBoundary()
glowIntensity = 1.0f - (distanceFromBoundary / WARNING_THRESHOLD)
```

---

## 📊 مخطط التدفق

```
┌─────────────────┐
│ JointArrowInfo  │
│ (from Engine)   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ ArcRangeData    │
│ (convert data)  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ArcColorCalculator│
│ (get colors)    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ArcRangeIndicator │
│ (draw arc)      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│SkeletonOverlay  │
│ (render)        │
└─────────────────┘
```

---

## ✅ معايير القبول

- [ ] القوس يظهر حول المفاصل المتتبعة فقط
- [ ] الألوان تتطابق مع حالة الزاوية (أخضر/أزرق/أحمر)
- [ ] مؤشر الموقع الحالي واضح ومتحرك بشكل صحيح
- [ ] الزوايا محولة بشكل صحيح (0° في الأسفل، 180° في الأعلى)
- [ ] الأداء لا يتأثر (≥30 FPS)
- [ ] القوس لا يتداخل مع الهيكل العظمي
- [ ] يمكن إخفاء/إظهار القوس من الإعدادات
- [ ] خيارات العرض تعمل (Primary only, Error only)
- [ ] الحجم متسق على جميع الشاشات (استخدام dp)

---

## 📚 المراجع

- `SkeletonOverlayView.kt` - الرسم الحالي
- `JointArrowInfo` - بيانات النطاقات (موجود بالفعل - لا حاجة لتعديل)
- `FormValidator.kt` - منطق التحقق
- `AppSettings.kt` - إعدادات التطبيق
- Android Canvas & Arc drawing documentation

## ⚠️ ملاحظات مهمة

### تم تصحيحها في هذه الخطة:

1. ✅ **Angle Mapping**: تم توضيح كيفية تحويل الزوايا بشكل صحيح (0° → 90°, 180° → -90°)
2. ✅ **Segment-based Drawing**: استخدام Segments بدلاً من SweepGradient المعقد
3. ✅ **dp vs scaleFactor**: استخدام dp بدلاً من scaleFactor المباشر
4. ✅ **Settings Integration**: إضافة VisualSettings بشكل صحيح في AppSettings.kt
5. ✅ **Filtering Options**: إضافة خيارات للعرض (Primary only, Error only)
6. ✅ **JointArrowInfo**: لا حاجة لتعديله - موجود بالفعل

---

## 🚀 الخطوة التالية

بعد الموافقة على هذه الخطة، سيتم البدء بتنفيذ المرحلة 1 (إنشاء الملفات الأساسية).

**الوقت المتوقع للتنفيذ الكامل: 4-6 ساعات**
