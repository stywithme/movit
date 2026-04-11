# Deep Research on Wrist Z Instability in Pose World Landmarks and Elbow Angle Errors

## What your issue is and why it shows up specifically at the wrist

Your description matches a class of problems people run into when they treat **PoseLandmarker "world landmarks"** as camera-accurate depth measurements (especially for joints at the ends of limbs). In PoseLandmarker, results contain both:

- **`pose_landmarks`**: normalized image-space landmarks, where `x` and `y` are normalized to the input image and `z` is a *relative depth-like value* (scaled roughly like `x`) with the hip midpoint as an origin. citeturn18view0turn14search0  
- **`pose_world_landmarks`**: "world coordinates" described as **3D coordinates in meters with origin at the hip midpoint**. citeturn18view0turn14search0turn17search9  

However, multiple technical sources strongly imply that these "world" coordinates are **not a calibrated per-frame reconstruction of camera-space 3D**. In particular, a stereo-fusion evaluation paper explains that (in their terms) *worldmark mode maps image coordinates onto an internal human model to estimate real-world coordinates*, with a hip-centered coordinate system that moves with the subject. citeturn27view4  

That distinction matters: if `pose_world_landmarks` are derived through a learned body-model mapping (rather than true depth), **depth along the camera axis (Z) can become the least reliable component**, and that unreliability is amplified at distal joints like wrists—exactly where your elbow-angle computation depends heavily on the wrist position.

image_group{"layout":"carousel","aspect_ratio":"16:9","query":["MediaPipe pose landmarks 33 points diagram","BlazePose 33 body keypoints diagram","MediaPipe pose world landmarks 3D plot"],"num_per_query":1}

PoseLandmarker's model bundle is explicitly described as a BlazePose-family pipeline that outputs **33 three-dimensional landmarks** and uses **GHUM**, a learned 3D human shape/pose modeling pipeline. citeturn19view3turn23view0turn22view1  
This is a strong clue about the "shape" of the output: it is *model-based 3D*, not sensor-based depth.

## What public issue reports say about depth/Z behaving "wrong" around arms and extremities

Across the MediaPipe GitHub issue tracker, several reports align with your symptoms (arm-related Z behaving in a way that breaks intuitive 3D geometry), even if they're not always phrased as "wrist Z jitter":

- **Elbow/arm depth ordering anomalies**: One report states that elbow depths are "always in the depth foreground" even when other landmarks look reasonable, suggesting systematic depth inconsistencies around the arms. citeturn28view2  
- **Forward-lean / wrong depth in front-facing standing poses**: Another report describes a consistent failure mode where feet appear deeper than expected, the trunk bends forward, and arms appear in front of the body in the depth analysis—worsening in straight-on camera views. citeturn28view3  
- **Z scale inconsistency across components** (Holistic JS): A separate Holistic report says the pose Z scale is about double that of face/hands, producing distorted 3D visualization, and the reporter "fixes" it visually by dividing pose Z by 0.5. citeturn28view1  

Taken together, these reports suggest a pattern: **Z is the most fragile dimension**, and **arms/hands/wrists are frequent stress points**—consistent with your observation that knee segments can look "balanced" while forearms (elbow→wrist) can be dominated by Z.

A key additional clue from your write-up is that **XY looks correct** while **wrist Z is unstable and frame-jittery**. That split is very typical of monocular pose pipelines: 2D localization is usually much stronger than inferred depth, and extremities (wrists/hands) are more occlusion-prone and have fewer strong depth cues.

This broader limitation is explicitly stated in a large benchmark paper on markerless pose estimation: **monocular methods are less accurate in depth and more sensitive to self-occlusions than multi-camera approaches**. citeturn19view0  

## Why wrists tend to be worse than knees in monocular 3D pose

There are three reinforcing reasons (all supported by how the system is documented and evaluated):

First, **the "world" output is not guaranteed to be physically calibrated depth**. A comparative biomechanics paper explicitly notes that MediaPipe's 3D output is based on **uncalibrated relative estimates**, and that MediaPipe "infers" the Z dimension relatively—useful for visualization but limited for exact physical metrics. citeturn31view2turn31view3  
This aligns with the "worldmark mode" description in the stereo-fusion paper (mapping 2D onto an internal model). citeturn27view4  

Second, **camera-angle dependency and occlusion are known failure drivers**. The stereo-fusion paper notes that even when using two cameras, 3D reconstruction accuracy is hindered by problems like **self-occlusion and camera-angle dependency**, and that accuracy typically scales with more camera views. citeturn27view1  
Your observation that elbow angle errors worsen when the forearm points toward/away from the camera is consistent with this: when a limb aligns with the viewing direction, small Z errors create large angle errors.

Third, the model is optimized for on-device real-time use. BlazePose is presented as a lightweight real-time model producing 33 keypoints suitable for applications like fitness tracking. citeturn22view1  
Real-time single-camera systems usually trade absolute 3D accuracy (especially in depth) for robustness and speed.

Putting these together: **knees often have more stable depth cues** (larger limb segments, less fine articulation detail than hands, and often less self-occlusion than wrists/hands near the torso), while **wrists/hands are smaller, move faster, self-occlude more, and have ambiguous depth in single RGB views**—so wrist Z can swing even when XY stays good.

## What "solutions" have been applied publicly, and what the outcome seems to be

Your request asked specifically for "all solutions applied" and "developer comments." Based on the sources I could retrieve, the publicly accessible pages for the relevant GitHub issues show the **report content and metadata**, but they do not expose additional maintainer replies in the captured views. As a result, I cannot cite an authoritative "we fixed X in version Y" statement from those issue threads themselves, and I cannot confirm a specific patch that targets wrist-world-Z instability as a resolved bug.

What we *can* document (with sources) is the set of mitigations and design features that exist in the ecosystem and are commonly used to address instability:

- **Tracking-based smoothing / using previous landmarks (legacy graphs)**: The canonical pose landmark graph configuration includes a mechanism to use landmarks from the previous frame (`USE_PREV_LANDMARKS`) and optional smoothing controls in the pipeline. citeturn17search9turn14search7  
- **Task-level tracking in VIDEO/LIVE_STREAM**: The Pose Landmarker guides state that in video/live-stream modes it uses tracking to avoid running the detector every frame (for latency), which also tends to reduce frame-to-frame discontinuities compared to pure per-frame detection. citeturn18view0  
- **Heuristic Z rescaling in practice**: At least one public report (Holistic JS) claims that pose Z appears scaled differently from other components and becomes visually reasonable when divided by 0.5. citeturn28view1  
  This is not a guaranteed "correct fix," but it shows community members resort to **post-hoc Z normalization** when Z scaling looks inconsistent.

There is also a notable documentation tension across the wider MediaPipe ecosystem: in the Holistic solution docs (legacy), the pose `z` is explicitly said to "be discarded" because the model is not fully trained to predict depth. citeturn14search2turn17search10  
Meanwhile, the Pose Landmarker task documentation presents both landmark Z and world-landmark coordinates as meaningful outputs. citeturn18view0turn19view3  
The practical takeaway is that, even if PoseLandmarker provides 3D outputs, **you should treat Z (especially world Z at extremities) as an estimate with nontrivial uncertainty**, not a measurement.

## Practical mitigation strategies that match your elbow-angle use case

Because your application is elbow flexion angle measurement and you've already found that **wrist Z dominates the forearm vector**, the most effective mitigations are the ones that reduce reliance on raw wrist Z or constrain it.

A strong evidence-backed option is to compute angles primarily in 2D (or in a controlled projection), because multiple biomechanics-oriented works explicitly choose 2D outputs over MediaPipe's 3D when they need more stable measurements with a single camera, citing the "relative uncalibrated" nature of 3D. citeturn31view2turn27view4  

In practice, there are three implementation patterns that cover most fitness/rehab needs:

**Compute elbow angle in the image plane when you have a single camera.**  
Use shoulder–elbow–wrist from `pose_landmarks` (x,y) and compute the 2D angle. This matches your observation that XY is stable, and it aligns with research practice when depth is unreliable from a single view. citeturn18view0turn31view2  

**Use world landmarks, but gate or repair the wrist Z before computing the angle.**  
Since your own "|dz|/len3D imbalance" metric already quantifies when Z dominates, you can turn it into a reliability test:

- Maintain a running estimate of forearm length in world space (median over a short window).
- If in the current frame the elbow→wrist vector's Z fraction spikes far above typical (your 0.62–0.78 examples), treat wrist Z as an outlier.
- Replace wrist Z with a constrained value that preserves (a) the smoothed forearm length and (b) the observed XY direction.

This is conceptually consistent with the fact that the world coordinates are model-estimated and can benefit from kinematic regularization rather than raw use. citeturn27view4  

**Add temporal filtering specifically to wrist world Z.**  
Even if visibility/presence remains high, that score is defined as likelihood of being visible/present, not a guarantee of metric accuracy. citeturn14search0turn18view0  
Filtering Z (e.g., One Euro / low-pass / Kalman) is a common stabilization approach in real-time tracking systems, and the legacy configuration explicitly emphasizes mechanisms to reduce jitter and use temporal continuity. citeturn14search7turn17search9  

If you need accurate 3D flexion angles under camera-axis motion (forearm pointing toward the camera), the literature consistently points to **multi-view or depth-assisted approaches** as the line where depth becomes meaningfully reliable. The stereo-fusion paper directly motivates multi-view due to occlusion and camera-angle dependency, and the LACCEI biomechanics comparison highlights that depth/triangulation-based systems require depth sensors and/or multiple views for precise 3D. citeturn27view1turn31view2  

## Is this a MediaPipe-only problem or a general limitation of monocular 3D pose?

The strongest evidence says it is **largely a general limitation of monocular markerless pose estimation**, not uniquely MediaPipe—while the exact failure modes (like "wrist Z spikes") vary by model and implementation.

A large benchmarking study of multiple open-source monocular pose estimators explicitly reports that monocular approaches are **less accurate in depth** and more sensitive to self-occlusions than multi-camera methods. citeturn19view0  
That same study reports **nontrivial elbow flexion angle errors** even under careful evaluation. citeturn19view0  

Similarly, the stereo-fusion evaluation highlights "camera angle dependency" and occlusion as limiting factors even when reconstructing 3D from 2D estimates, reinforcing that depth/3D angles are the hard part—not x/y localization. citeturn27view1  

What *is* more MediaPipe-specific is how often people interpret `pose_world_landmarks` as camera-true 3D "in meters." The official task docs describe them that way, but other technical descriptions clarify they're produced by an internal mapping/modeling step, and multiple biomechanics users explicitly treat the resulting Z as relative/uncalibrated for single-camera setups. citeturn18view0turn27view4turn31view2  

So the most defensible conclusion is:

- Your wrist Z instability is consistent with **monocular 3D ambiguity + extremity sensitivity**, and similar depth anomalies are reported by others (especially around arms). citeturn28view2turn28view3turn19view0  
- There is no clear, citable public evidence (from the accessible issue views) of a specific upstream "fix" that makes wrist world-Z reliable enough for high-stakes elbow goniometry from a single RGB camera.  
- The "solutions" that are publicly documented and repeatedly used are **tracking/smoothing**, **2D-first measurement**, and **multi-view/depth fusion when true 3D is required**. citeturn18view0turn14search7turn27view1turn31view2

---

## What was actually implemented — the key insight and final solution

*Added March 2026 — documenting the outcome of the investigation.*

After five failed or partially-successful approaches (DepthCorrector, Hybrid 2D/3D blend, Smoothing fixes, Body-Plane Projection, dzImbalance correction), a **confidence-based architecture** was implemented in `ElbowAngleEstimator.kt` (v3).

### The critical insight: dzShare is a confidence metric, not a correction direction

Previous approaches treated `dzImbalance` (the difference in depth-share between upper arm and forearm) as a signal for **which direction** to correct the angle — down if upper arm is deeper, up (toward 3D) if forearm is deeper. This was **fundamentally wrong**:

- **When dzImbalance > 0** (forearm deeper): The code blended toward ang3D. But ang3D was **inflated** by the very same depth noise that created the high dzShare. With real angle ~40°, ang2D = 59°, ang3D = 95°, the blended output was **77°** — catastrophically worse than using ang2D alone.

- **The correct interpretation**: High dzShare in either segment means **low confidence in both 2D and 3D**, regardless of which segment is deeper. It measures HOW MUCH depth is involved in the geometry, not WHERE the truth lies relative to the measurements.

This insight aligns directly with the research findings in this document: Z is unreliable, and its unreliability increases with how much the segment aligns with the camera axis. dzShare literally quantifies that alignment.

### The implemented solution: confidence tiers

The final `ElbowAngleEstimator` uses `maxDzShare` (the higher depth-ratio of the two arm segments) to select a **confidence tier**:

| Strategy | Condition | Action |
|----------|-----------|--------|
| **STRAIGHT** | ang2D > 150° | Boost toward 180° (fixes MediaPipe's chronic underreporting of straight arms) |
| **TRUST_3D** | ang3D ≤ ang2D + 12° | 3D resolved depth correctly — use it directly |
| **TRUST_2D** | maxDz < 0.15 | Negligible depth involvement — 2D is accurate |
| **MILD_DOWN** | 0.15 ≤ maxDz < 0.40 | Moderate depth — mild foreshortening correction downward from ang2D |
| **DEEP_DOWN** | 0.40 ≤ maxDz < 0.60 | Significant depth — stronger correction, gated by sideStrength |
| **HOLD** | maxDz > 0.60 | Both signals unreliable — hold last known stable value |

All downward corrections are modulated by `sideStrength` (derived from `facingRatio`):
- **Frontal view** (facingRatio > 0.85): sideStrength ≈ 0 → no correction (2D is naturally accurate)
- **Side view** (facingRatio < 0.40): sideStrength = 1 → full correction (foreshortening is maximal)

### What this achieves vs. what remains unsolvable

**Achieves**:
- Stops making things worse — previous approaches could turn a 40° real angle into 77° (blending toward inflated 3D) or 26° (overcorrecting 2D)
- Provides honest confidence reporting — when depth is too high to trust, it holds rather than outputting garbage
- Works well for frontal and near-frontal camera views (the most common user setup)

**Does NOT achieve**:
- Camera-independent measurements — the side-view problem (93° measured for 35° real) is a **physics limitation of monocular vision**, not a software bug
- The only path to true camera-independent elbow angles is a **trained 2D→3D lifting model** with multi-hypothesis depth handling (concepts from MDN, ManiPose, BLAPose, HybrIK papers — reviewed in `Docs/Reasearch/`)

### Implementation details

- **File**: `ElbowAngleEstimator.kt` — 294 lines, zero external dependencies, real-time performance
- **Integration**: Called after `AngleCalculator.calculateAllAnglesSmoothed()`, overrides elbow angles only
- **State management**: `reset()` method called alongside `LandmarkSmoother.reset()` on all camera/mode transitions
- **Diagnostics**: `strategy` field in `ElbowDiagnostics` shows exact branch name in Angle Lab
- **Full report**: `Docs/New-Project/Joint-Debug/elbow-angle-problem-report.md`

*Updated: March 2026*
