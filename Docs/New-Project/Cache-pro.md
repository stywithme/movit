# Performance Optimization Plan
## Android Training Validator - Comprehensive Caching & Optimization Strategy

---

## Executive Summary

This document outlines a comprehensive performance optimization plan based on deep code analysis. The optimizations are categorized by impact level and organized to avoid redundant work. Each fix addresses a specific bottleneck in the 30+ FPS real-time pose detection pipeline.

**Key Metrics:**
- Current: ~120 bitmap allocations/sec, 50+ color lookups/frame, 33 landmark smoothings/frame
- Target: Reduce allocations by 80%, eliminate redundant lookups, achieve consistent 30 FPS

---

## Phase 1: Memory Allocation Reduction (Critical)

### 1.1 Bitmap Pool Implementation

| Component | File | Issue |
|-----------|------|-------|
| Frame Extraction | `VideoManager.kt` | 3-4 new bitmaps per frame |
| Frame Capture | `VideoModeController.kt` | Bitmap copy on every frame |
| Rotation | `PoseLandmarkerHelper.kt` | New bitmap for each rotation |

**Solution Architecture:**
- Implement `BitmapPool` singleton with LRU cache
- Pool size: 4-6 bitmaps based on resolution
- Use `Bitmap.reconfigure()` for dimension changes
- Implement `acquire()` / `release()` pattern

**Affected Files:**
- `VideoManager.kt` - Frame extraction
- `VideoModeController.kt` - Frame processing
- `PoseLandmarkerHelper.kt` - Image rotation

---

### 1.2 Matrix Reuse

| Component | File | Issue |
|-----------|------|-------|
| Image Rotation | `PoseLandmarkerHelper.kt` | New Matrix() every frame |

**Solution Architecture:**
- Declare Matrix as class-level field
- Call `matrix.reset()` before each use
- Eliminates 30+ allocations/second

---

### 1.3 Collection Pre-allocation

| Component | File | Issue |
|-----------|------|-------|
| Landmark Smoothing | `LandmarkSmoother.kt` | 33 SmoothedLandmark objects/frame |
| Form Validation | `FormValidator.kt` | New Map/List every frame |
| Gradient Building | `AngleColorResolver.kt` | New MutableList for colors |

**Solution Architecture:**
- Implement object pooling for `SmoothedLandmark`
- Pre-allocate result collections at class level
- Clear and reuse instead of recreate

---

## Phase 2: Lookup Optimization (High Priority)

### 2.1 State Colors Caching

| Component | File | Issue |
|-----------|------|-------|
| Skeleton Rendering | `SkeletonOverlayView.kt` | 6 color lookups per joint per frame |
| Line Indicator | `LineRangeIndicator.kt` | Repeated StateConfig.getColor() |
| Arc Indicator | `ArcRangeIndicator.kt` | Same color lookup issue |

**Solution Architecture:**
- Cache all 6 state colors as class-level constants
- Initialize once in companion object or lazy initialization
- Replace `StateConfig.getColor(state)` with direct field access

**Implementation Principle:**
- Single source of truth for colors in `StateConfig`
- Expose as `@JvmField` for direct access without getter overhead

---

### 2.2 StateRanges Lazy Properties

| Component | File | Issue |
|-----------|------|-------|
| Range Calculation | `JointState.kt` | `listOfNotNull()` allocation every call |
| Zone Determination | `TrackedJoint` | Repeated min/max calculations |

**Solution Architecture:**
- Convert `getEffectiveMin()` / `getEffectiveMax()` to lazy properties
- Calculate once on first access, cache forever
- Use direct comparison instead of list creation

**Affected Properties:**
- `effectiveMin` - Cache as lazy val
- `effectiveMax` - Cache as lazy val
- `transitionZone` - Pre-calculate boundaries

---

### 2.3 Settings Manager Caching

| Component | File | Issue |
|-----------|------|-------|
| Line Indicator | `LineRangeIndicator.kt` | 6+ settings reads per frame |
| Skeleton Overlay | `SkeletonOverlayView.kt` | Repeated threshold lookups |
| Arc Indicator | `ArcRangeIndicator.kt` | Repeated configuration reads |

**Solution Architecture:**
- Cache all frequently-accessed settings at initialization
- Implement `refreshSettings()` for dynamic updates
- Call refresh only on settings change, not per frame

---

### 2.4 Data Structure Optimization

| Component | File | Issue |
|-----------|------|-------|
| Filter Lookup | `LandmarkSmoother.kt` | Map lookup for 33 landmarks |
| Mirroring | `BodyLandmarks.kt` | Map lookup for mirror indices |
| Joint Mapping | `JointLandmarkMapping.kt` | `lowercase()` on every lookup |

**Solution Architecture:**
- Replace `Map<Int, Filter>` with `Array<Filter?>` - O(1) direct index access
- Replace mirror `Map<Int, Int>` with `IntArray` - faster and no boxing
- Normalize strings once at initialization, not per lookup

---

## Phase 3: Thread & Concurrency Optimization (High Priority)

### 3.1 Synchronized Block Minimization

| Component | File | Issue |
|-----------|------|-------|
| Frame Processing | `TrainingEngine.kt` | Entire processFrame() in synchronized block |

**Solution Architecture:**
- Identify minimal critical section (only shared state mutation)
- Move StateFlow emissions outside synchronized block
- Consider `ReentrantLock` with `tryLock()` for non-blocking behavior
- Extract read-only operations outside the lock

**Principle:** Lock should protect state mutation only, not computation.

---

### 3.2 Coroutine Management

| Component | File | Issue |
|-----------|------|-------|
| Frame Processing | `VideoModeController.kt` | Unbounded coroutine launches |
| State Observation | `TrainingViewModel.kt` | Multiple concurrent collectors |

**Solution Architecture:**
- Use `Dispatchers.Default.limitedParallelism(2)` for frame processing
- Implement processing flag to drop frames during busy periods
- Combine multiple StateFlow observations using `combine()` operator
- Use `Channel` with capacity 1 for frame queue (drop oldest)

---

### 3.3 Main Thread Protection

| Component | File | Issue |
|-----------|------|-------|
| Frame Extraction | `VideoManager.kt` | Loop on Dispatchers.Main |
| Video Rotation | `VideoManager.kt` | Blocking I/O on main thread |
| Bitmap Operations | `VideoModeController.kt` | getBitmap() on main thread |

**Solution Architecture:**
- Move extraction loop to `Dispatchers.Default`
- Use `withContext(Dispatchers.Main)` only for TextureView access
- Move `getVideoRotation()` to `Dispatchers.IO`
- Cache rotation value after first read

---

## Phase 4: Computation Optimization (Medium Priority)

### 4.1 Repeated Calculations

| Component | File | Issue |
|-----------|------|-------|
| Scale Factor | `SkeletonOverlayView.kt` | Calculated every frame |
| Angle Average | `PhaseStateMachine.kt` | `values.average()` every frame |
| Zone Type | `FormValidator.kt` | `determineZoneType()` per joint per frame |

**Solution Architecture:**
- Cache scale factor, recalculate only on dimension change
- Cache angle average if values unchanged
- Cache zone type per joint, invalidate on angle change

---

### 4.2 Mathematical Optimizations

| Component | File | Issue |
|-----------|------|-------|
| Distance Calc | `VelocityFilter.kt` | `sqrt()` for distance comparison |
| PI Constant | `OneEuroFilter.kt` | `Math.PI.toFloat()` every call |

**Solution Architecture:**
- Compare squared distances (avoid sqrt)
- Extract PI as compile-time constant
- Use `@JvmField` for constant access without getter

---

### 4.3 Algorithm Efficiency

| Component | File | Issue |
|-----------|------|-------|
| Joint Filtering | `TrainingEngine.kt` | `filterKeys()` O(n*m) per frame |
| Landmark Indices | `PositionValidator.kt` | `jointToLandmark()` lookup per frame |

**Solution Architecture:**
- Pre-compute primary joint Set at initialization
- Use `Set.contains()` for O(1) lookup
- Pre-compute landmark indices mapping once

---

## Phase 5: UI Rendering Optimization (Medium Priority)

### 5.1 Paint Object Caching

| Component | File | Issue |
|-----------|------|-------|
| Glow Effects | `SkeletonOverlayView.kt` | BlurMaskFilter per frame |
| Line Rendering | `LineRangeIndicator.kt` | BlurMaskFilter per frame |

**Solution:** Already implemented lazy BlurMaskFilter caching.

---

### 5.2 Gradient Elimination

| Component | File | Issue |
|-----------|------|-------|
| Track Drawing | `LineRangeIndicator.kt` | LinearGradient per frame |

**Solution:** Already implemented discrete segment drawing.

---

### 5.3 String Formatting

| Component | File | Issue |
|-----------|------|-------|
| Time Display | `TrainingActivity.kt` | String.format() per second |
| FPS Display | `TrainingActivity.kt` | String concatenation per frame |
| Phase Names | `TrainingActivity.kt` | String lookup per phase change |

**Solution Architecture:**
- Use StringBuilder with clear/reuse pattern
- Cache formatted strings when values unchanged
- Pre-compute phase name map at compile time

---

## Implementation Order

### Sprint 1: Quick Wins (Immediate Impact)
1. StateRanges lazy properties
2. State colors caching
3. Settings manager caching
4. PI constant extraction

### Sprint 2: Memory Management
5. Matrix reuse in PoseLandmarkerHelper
6. Bitmap pool implementation
7. Collection pre-allocation in FormValidator
8. Array replacement in LandmarkSmoother

### Sprint 3: Concurrency
9. Synchronized block minimization
10. Coroutine limiting in VideoModeController
11. StateFlow combination in TrainingViewModel
12. Main thread protection in VideoManager

### Sprint 4: Computation
13. Scale factor caching
14. Zone type caching
15. Primary joint Set pre-computation
16. Squared distance comparison

---

## Validation Criteria

### Performance Metrics
- Frame rate: Consistent 30 FPS (no drops below 25)
- Allocation rate: < 50 objects/frame (vs current ~100+)
- GC pause: < 5ms average
- Main thread blocking: < 16ms per frame

### Testing Protocol
1. Run Android Profiler during 2-minute session
2. Monitor allocation timeline
3. Check for GC pauses > 10ms
4. Validate UI responsiveness (touch latency)

---

## Risk Assessment

| Change | Risk Level | Mitigation |
|--------|------------|------------|
| Bitmap Pool | Medium | Thorough null-safety, proper recycling |
| Synchronized reduction | High | Extensive testing, gradual changes |
| Array replacement | Low | Index bounds checking |
| Lazy properties | Low | Thread-safe lazy initialization |
| Coroutine limiting | Medium | Frame drop monitoring |

---

## Dependencies

- No external library additions required
- All optimizations use standard Kotlin/Android APIs
- Backward compatible with current API surface

---

## Success Metrics

| Metric | Current | Target |
|--------|---------|--------|
| Bitmap allocs/sec | ~120 | < 10 |
| Color lookups/frame | ~50 | 0 |
| StateRanges allocs/frame | ~20 | 0 |
| Coroutines/frame | 1 | 0 (reused) |
| Synchronized block time | ~15ms | < 5ms |

---

*Document Version: 1.0*
*Last Updated: February 4, 2026*
