# Training Validator - Improvements Roadmap

## Document Info
- **Created**: December 2024
- **Purpose**: Technical roadmap for improving pose estimation accuracy and performance
- **Status**: Active Development

---

## 📊 Current Implementation Status

### ✅ Completed Features

| Feature | Implementation | File |
|---------|---------------|------|
| EMA Landmark Smoothing | `smoothingFactor = 0.5f` | `LandmarkSmoother.kt` |
| Visibility Filtering | `threshold = 0.5f` | `SkeletonOverlayView.kt` |
| Presence vs Visibility | Separate fields in `SmoothedLandmark` | `LandmarkSmoother.kt` |
| Fast Bitmap Conversion | `copyPixelsFromBuffer()` | `PoseLandmarkerHelper.kt` |
| Accurate Timestamps | `SystemClock.uptimeMillis()` | `PoseLandmarkerHelper.kt` |
| Model Switching | Full ↔ Heavy runtime switch | `MainActivity.kt` |
| Scale Factor Calculation | FILL_START mode support | `SkeletonOverlayView.kt` |
| GPU Acceleration | With CPU fallback | `PoseLandmarkerHelper.kt` |

### ❌ Known Issues

1. **Jitter with Lag**: EMA smoothing causes noticeable lag during fast movements
2. **Memory Pressure**: New Bitmap created every frame → GC stutters
3. **Perspective Distortion**: Using NormalizedLandmarks for angle calculation
4. **No Frame Validation**: No check if body is fully visible in camera
5. **Invisible Landmarks Drawn**: Sometimes landmarks appear where body parts are hidden

---

## 🎯 Improvement Pipeline

### Critical Principle: Filter Ordering

```
⚠️ IMPORTANT: Do not stack multiple filters on the same signal!

Pipeline Design:
┌─────────────────────────────────────────────────────────────────┐
│ RAW LANDMARKS                                                    │
│      │                                                           │
│      ▼                                                           │
│ ┌──────────────────┐                                            │
│ │ One Euro Filter  │ ← For drawing (reduces jitter, minimal lag)│
│ └────────┬─────────┘                                            │
│          │                                                       │
│          ▼                                                       │
│   SMOOTHED LANDMARKS → SkeletonOverlayView                       │
│                                                                  │
├─────────────────────────────────────────────────────────────────┤
│ WORLD LANDMARKS                                                  │
│      │                                                           │
│      ▼                                                           │
│ ┌──────────────────┐                                            │
│ │ Angle Calculation │ ← 3D space (no perspective distortion)    │
│ └────────┬─────────┘                                            │
│          │                                                       │
│          ▼                                                       │
│ ┌──────────────────┐                                            │
│ │ Savitzky-Golay   │ ← For rep counting (smooth curves)         │
│ └────────┬─────────┘                                            │
│          │                                                       │
│          ▼                                                       │
│   SMOOTHED ANGLES → Rep Detection & Validation                   │
│                                                                  │
├─────────────────────────────────────────────────────────────────┤
│ OUTLIER REJECTION (Applied at raw stage)                        │
│   • Velocity Check → Reject sudden teleportation                │
│   • Bone Length Gates → Reject physically impossible poses      │
└─────────────────────────────────────────────────────────────────┘
```

### Key Rules

1. **Drawing** → Use `NormalizedLandmarks` (screen-space coordinates)
2. **Angle Calculation** → Use `WorldLandmarks` (3D physical space)
3. **One Filter Per Signal** → Don't combine EMA + One Euro + Savitzky
4. **Outlier Rejection First** → Before any smoothing
5. **Joint Limits for Validation Only** → Don't auto-correct values

---

## 📋 Prioritized Improvements

### Phase 1: Critical Fixes 🔴 (Immediate)

#### 1.1 Bitmap Reuse
**Problem**: Creating new `Bitmap.createBitmap()` every frame causes GC pressure.

**Solution**:
```kotlin
// In PoseLandmarkerHelper
private var bitmapBuffer: Bitmap? = null

fun detectPose(imageProxy: ImageProxy, isFrontCamera: Boolean) {
    // Reuse bitmap if dimensions match
    val buffer = bitmapBuffer?.takeIf { 
        it.width == imageProxy.width && it.height == imageProxy.height 
    } ?: Bitmap.createBitmap(
        imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
    ).also { bitmapBuffer = it }
    
    buffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
    // ... rest of processing
}
```

**Impact**: Reduces GC pauses, smoother FPS

---

#### 1.2 One Euro Filter (Replace EMA)
**Problem**: EMA with fixed factor causes either jitter (low factor) or lag (high factor).

**Solution**: One Euro Filter adapts based on movement speed.

```kotlin
class OneEuroFilter(
    private val minCutoff: Float = 1.0f,      // Minimum cutoff frequency
    private val beta: Float = 0.007f,          // Speed coefficient
    private val dCutoff: Float = 1.0f          // Derivative cutoff
) {
    private var xPrev: Float? = null
    private var dxPrev: Float = 0f
    private var tPrev: Long = 0L

    fun filter(x: Float, timestamp: Long): Float {
        val tE = if (tPrev == 0L) 1f / 30f else (timestamp - tPrev) / 1000f
        tPrev = timestamp

        // Derivative estimation
        val dx = if (xPrev == null) 0f else (x - xPrev!!) / tE
        val edx = exponentialSmoothing(dx, dxPrev, alpha(tE, dCutoff))
        dxPrev = edx

        // Adaptive cutoff based on speed
        val cutoff = minCutoff + beta * abs(edx)
        
        // Filter the signal
        val result = if (xPrev == null) x 
                     else exponentialSmoothing(x, xPrev!!, alpha(tE, cutoff))
        xPrev = result
        return result
    }

    private fun alpha(tE: Float, cutoff: Float): Float {
        val tau = 1f / (2 * PI.toFloat() * cutoff)
        return 1f / (1f + tau / tE)
    }

    private fun exponentialSmoothing(x: Float, xPrev: Float, alpha: Float) = 
        alpha * x + (1 - alpha) * xPrev
}
```

**Usage**:
```kotlin
class LandmarkSmoother {
    private val filters = Array(33) { 
        Triple(OneEuroFilter(), OneEuroFilter(), OneEuroFilter()) // x, y, z
    }
    
    fun smooth(index: Int, landmark: NormalizedLandmark, timestamp: Long): SmoothedLandmark {
        val (fx, fy, fz) = filters[index]
        return SmoothedLandmark(
            x = fx.filter(landmark.x(), timestamp),
            y = fy.filter(landmark.y(), timestamp),
            z = fz.filter(landmark.z(), timestamp),
            visibility = landmark.visibility().orElse(0f),
            presence = landmark.presence().orElse(0f)
        )
    }
}
```

**Impact**: Fast movements = less smoothing, slow movements = more smoothing

---

#### 1.3 Camera Framing Validation
**Problem**: No feedback when body is cut off or too close/far from camera.

**Solution**: Check landmark positions relative to frame edges.

```kotlin
object FramingValidator {
    private const val EDGE_MARGIN = 0.05f  // 5% from edges
    private const val MIN_BODY_SIZE = 0.3f // Body should be at least 30% of frame
    private const val MAX_BODY_SIZE = 0.95f // Body shouldn't exceed 95%

    data class FramingResult(
        val isValid: Boolean,
        val issues: List<FramingIssue>
    )

    enum class FramingIssue {
        HEAD_CUT_OFF,
        FEET_CUT_OFF,
        LEFT_ARM_CUT_OFF,
        RIGHT_ARM_CUT_OFF,
        TOO_CLOSE,
        TOO_FAR,
        MOVE_LEFT,
        MOVE_RIGHT
    }

    fun validate(landmarks: List<SmoothedLandmark>): FramingResult {
        val issues = mutableListOf<FramingIssue>()
        
        // Check head visibility (nose, eyes)
        val nose = landmarks[BodyLandmarks.NOSE]
        if (nose.y < EDGE_MARGIN) issues.add(FramingIssue.HEAD_CUT_OFF)
        
        // Check feet
        val leftAnkle = landmarks[BodyLandmarks.LEFT_ANKLE]
        val rightAnkle = landmarks[BodyLandmarks.RIGHT_ANKLE]
        if (leftAnkle.y > 1 - EDGE_MARGIN || rightAnkle.y > 1 - EDGE_MARGIN) {
            issues.add(FramingIssue.FEET_CUT_OFF)
        }
        
        // Check body size (distance from head to feet)
        val bodyHeight = maxOf(leftAnkle.y, rightAnkle.y) - nose.y
        if (bodyHeight < MIN_BODY_SIZE) issues.add(FramingIssue.TOO_FAR)
        if (bodyHeight > MAX_BODY_SIZE) issues.add(FramingIssue.TOO_CLOSE)
        
        // Check horizontal centering
        val centerX = (landmarks[BodyLandmarks.LEFT_HIP].x + 
                       landmarks[BodyLandmarks.RIGHT_HIP].x) / 2
        if (centerX < 0.3f) issues.add(FramingIssue.MOVE_RIGHT)
        if (centerX > 0.7f) issues.add(FramingIssue.MOVE_LEFT)
        
        return FramingResult(
            isValid = issues.isEmpty(),
            issues = issues
        )
    }
}
```

**Impact**: Better user guidance, more reliable detection

---

### Phase 2: Accuracy Improvements 🟡 (Next Sprint)

#### 2.1 WorldLandmarks for Angle Calculation
**Problem**: NormalizedLandmarks have perspective distortion (foreshortening).

**Current** (Incorrect for tilted poses):
```kotlin
val angle = calculateAngle(
    landmarks[SHOULDER],  // 2D normalized
    landmarks[ELBOW],
    landmarks[WRIST]
)
```

**Solution**:
```kotlin
object AngleCalculator {
    // Use WorldLandmarks for accurate 3D angle calculation
    fun calculateAngle3D(
        a: Landmark,  // WorldLandmark (3D)
        b: Landmark,
        c: Landmark
    ): Double {
        val ba = floatArrayOf(a.x() - b.x(), a.y() - b.y(), a.z() - b.z())
        val bc = floatArrayOf(c.x() - b.x(), c.y() - b.y(), c.z() - b.z())
        
        val dot = ba[0]*bc[0] + ba[1]*bc[1] + ba[2]*bc[2]
        val magBA = sqrt(ba[0]*ba[0] + ba[1]*ba[1] + ba[2]*ba[2])
        val magBC = sqrt(bc[0]*bc[0] + bc[1]*bc[1] + bc[2]*bc[2])
        
        val cosAngle = dot / (magBA * magBC)
        return Math.toDegrees(acos(cosAngle.coerceIn(-1f, 1f).toDouble()))
    }
}
```

**⚠️ Note**: WorldLandmarks Z-axis can be noisy. Apply smoothing before use.

**Impact**: Accurate angles regardless of camera perspective

---

#### 2.2 Velocity Check (Teleportation Prevention)
**Problem**: Occasional frame-to-frame jumps when detection temporarily fails.

**Solution**:
```kotlin
class VelocityFilter(
    private val maxVelocity: Float = 0.5f // Max 50% of screen per frame
) {
    private var previousLandmarks: List<SmoothedLandmark>? = null
    private var previousTimestamp: Long = 0L

    fun filter(
        landmarks: List<SmoothedLandmark>,
        timestamp: Long
    ): List<SmoothedLandmark> {
        val prev = previousLandmarks
        val dt = if (previousTimestamp == 0L) 33f else (timestamp - previousTimestamp).toFloat()
        
        val filtered = if (prev == null) {
            landmarks
        } else {
            landmarks.mapIndexed { i, current ->
                val previous = prev[i]
                val dx = current.x - previous.x
                val dy = current.y - previous.y
                val velocity = sqrt(dx * dx + dy * dy) / (dt / 1000f)
                
                if (velocity > maxVelocity) {
                    // Teleportation detected - use previous position
                    previous.copy(
                        visibility = current.visibility * 0.5f // Mark as suspicious
                    )
                } else {
                    current
                }
            }
        }
        
        previousLandmarks = filtered
        previousTimestamp = timestamp
        return filtered
    }
}
```

**Impact**: No sudden jumps in skeleton

---

#### 2.3 Occlusion Visualization
**Problem**: Semi-visible landmarks drawn with same style as visible ones.

**Solution**:
```kotlin
// In SkeletonOverlayView
private val dashedLinePaint = Paint().apply {
    color = Color.argb(150, 0, 255, 255)
    strokeWidth = LINE_WIDTH
    style = Paint.Style.STROKE
    pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
}

private fun drawConnections(canvas: Canvas, landmarks: List<SmoothedLandmark>) {
    POSE_LANDMARKS.forEach { connection ->
        val start = landmarks[connection.start()]
        val end = landmarks[connection.end()]
        
        val paint = when {
            start.isVisible() && end.isVisible() -> linePaint // Solid
            start.isPresent() && end.isPresent() -> dashedLinePaint // Dashed (estimated)
            else -> return@forEach // Don't draw
        }
        
        canvas.drawLine(
            getScreenX(start.x), getScreenY(start.y),
            getScreenX(end.x), getScreenY(end.y),
            paint
        )
    }
}
```

**UX Label**: Show "Estimated" indicator when using dashed lines.

**Impact**: Clear visual feedback about detection confidence

---

### Phase 3: Advanced Features 🟢 (Future)

#### 3.1 Savitzky-Golay Filter for Rep Counting
**When**: During rep detection implementation.

```kotlin
class SavitzkyGolayFilter(
    private val windowSize: Int = 5,
    private val polynomialOrder: Int = 2
) {
    private val buffer = ArrayDeque<Double>(windowSize)
    
    fun filter(value: Double): Double {
        buffer.addLast(value)
        if (buffer.size > windowSize) buffer.removeFirst()
        if (buffer.size < windowSize) return value
        
        // Apply Savitzky-Golay coefficients
        return applySGCoefficients(buffer.toList())
    }
}
```

**Use For**: Angle signals before peak detection (reps).

---

#### 3.2 Bone Length Consistency
**When**: After calibration system is in place.

```kotlin
object BoneLengthValidator {
    // Use WorldLandmarks to avoid perspective issues
    fun validateBoneLengths(
        worldLandmarks: List<Landmark>,
        calibratedLengths: Map<String, Float>
    ): Map<String, Float> {
        val confidenceScores = mutableMapOf<String, Float>()
        
        BONE_CONNECTIONS.forEach { (name, startIdx, endIdx) ->
            val start = worldLandmarks[startIdx]
            val end = worldLandmarks[endIdx]
            
            val currentLength = sqrt(
                (end.x() - start.x()).pow(2) +
                (end.y() - start.y()).pow(2) +
                (end.z() - start.z()).pow(2)
            )
            
            val expected = calibratedLengths[name] ?: return@forEach
            val deviation = abs(currentLength - expected) / expected
            
            // Allow 15% deviation (accounts for measurement noise)
            confidenceScores[name] = if (deviation < 0.15f) 1f else (1f - deviation).coerceAtLeast(0f)
        }
        
        return confidenceScores
    }
}
```

**⚠️ Important**: Apply on WorldLandmarks ONLY. NormalizedLandmarks will give false rejections due to foreshortening.

---

#### 3.3 Joint Angle Limits (Validation Only)
**Purpose**: Detect physically impossible poses.

```kotlin
object JointLimits {
    // Human joint ROM (Range of Motion)
    val LIMITS = mapOf(
        "elbow" to 0.0..150.0,      // Can't hyperextend
        "knee" to 0.0..160.0,        // Slight hyperextension possible
        "shoulder" to 0.0..180.0,
        "hip" to 0.0..120.0
    )
    
    fun validate(angles: JointAngles): Map<String, ValidationResult> {
        return angles.toMap().mapValues { (joint, angle) ->
            val limit = LIMITS[joint.substringAfter("_")] ?: return@mapValues ValidationResult.UNKNOWN
            when {
                angle in limit -> ValidationResult.VALID
                else -> ValidationResult.SUSPICIOUS // Don't auto-correct!
            }
        }
    }
    
    enum class ValidationResult {
        VALID,
        SUSPICIOUS,  // Flag for review, don't override
        UNKNOWN
    }
}
```

**⚠️ Rule**: Mark as suspicious, NEVER auto-correct the landmark position. Auto-correction hides real detection problems.

---

#### 3.4 Adaptive Resolution (Performance Optimization)
**When**: Supporting low-end devices.

```kotlin
object AdaptiveResolution {
    fun getOptimalResolution(
        deviceTier: DeviceTier,
        currentFps: Int,
        targetFps: Int = 25
    ): Resolution {
        return when {
            currentFps >= targetFps -> Resolution.HIGH // 720p
            deviceTier == DeviceTier.LOW -> Resolution.LOW // 480p
            else -> Resolution.MEDIUM // 540p
        }
    }
}
```

**⚠️ Avoid**: Downscaling below 480p for full-body poses (extremities get lost).

---

## 📌 Implementation Notes

### Distance Estimation
**Don't use**: WorldLandmarks for absolute distance (unreliable across devices/lenses).

**Do use**: Combination of:
- Body bounding box size relative to frame
- Presence scores of extremities (head, hands, feet)
- Landmark proximity to frame edges

```kotlin
fun estimateDistance(landmarks: List<SmoothedLandmark>): DistanceEstimate {
    val bbox = calculateBoundingBox(landmarks)
    val bodyRatio = bbox.height / 1.0f // Relative to frame height
    
    return when {
        bodyRatio > 0.9f -> DistanceEstimate.TOO_CLOSE
        bodyRatio < 0.4f -> DistanceEstimate.TOO_FAR
        else -> DistanceEstimate.OPTIMAL
    }
}
```

---

### Presence vs Visibility

| Property | Meaning | Use Case |
|----------|---------|----------|
| **Visibility** | Landmark is visible in frame | Drawing decisions |
| **Presence** | Landmark exists (even if occluded) | Pose completeness check |

```kotlin
// Example: Leg behind desk
leftKnee.visibility = 0.2f  // Not visible (hidden by desk)
leftKnee.presence = 0.9f    // But we know it's there

// UI Decision:
if (isPresent && !isVisible) {
    drawDashedLine() // Estimated position
    showLabel("Estimated")
}
```

---

## 📅 Implementation Timeline

```
Week 1-2: Phase 1 (Critical Fixes)
├── Bitmap Reuse
├── One Euro Filter
└── Camera Framing Validation

Week 3-4: Phase 2 (Accuracy)
├── WorldLandmarks for Angles
├── Velocity Check
└── Occlusion Visualization

Week 5+: Phase 3 (As Needed)
├── Savitzky-Golay (with rep counting)
├── Bone Length (with calibration)
└── Adaptive Resolution (for device support)
```

---

## 📚 References

- [One Euro Filter Paper](http://cristal.univ-lille.fr/~casiez/1euro/)
- [MediaPipe Pose Landmarker](https://developers.google.com/mediapipe/solutions/vision/pose_landmarker)
- [Savitzky-Golay Filter](https://docs.scipy.org/doc/scipy/reference/generated/scipy.signal.savgol_filter.html)

---

## ✏️ Revision History

| Date | Changes |
|------|---------|
| Dec 2024 | Initial roadmap based on code review and community feedback |
