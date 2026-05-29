> **Status:** `ARCHIVED` â€” superseded, cancelled, or historical review only.
> **Current SSOT:** `Docs/00-Active-Reference/README.md`
> **Archived:** 2026-05-29

# Elbow Angle / Depth Ambiguity — Knowledge Base (Facts & Alternatives Only)

This document aggregates information, alternatives, and research references from the POSE-2 repository and related exports. **No recommendations or opinions** — only what appears in project docs, research summaries, and standard practice options.

**Primary internal sources:**
- `Docs/New-Project/Joint-Debug/Solutions.md`
- `Docs/New-Project/Joint-Debug/elbow-angle-problem-report.md`
- `Docs/New-Project/Joint-Debug/ml-engineer-brief.md`
- `Docs/New-Project/Joint-Debug/lifting-model-solution-plan.md`
- `Docs/New-Project/Joint-Debug/deep-research-report (3).md`
- `Docs/New-Project/Joint-Debug/elbow-correction-mlp-plan.md`
- `Docs/Reasearch/cursor_*.md` (PDF summaries)
- `tools/posture-mlp/TRAINING_GUIDE.md`, `README.md`

**External reference cited in repo:** [MediaPipe Issue #3555](https://github.com/google-ai-edge/mediapipe/issues/3555)

---

## 1. Problem — Facts Documented in Repo

### 1.1 Landmark types (from investigation report)

| Output | Coordinates | Notes in docs |
|--------|---------------|----------------|
| `pose_landmarks` (Normalized) | x,y in [0,1]; z relative to hip | x,y described as reliable for detection |
| `pose_world_landmarks` (World) | x,y,z in meters; hip midpoint origin | Z described as unreliable for extremities; world landmarks described as model-estimated (GHUM), not true camera-space reconstruction |

### 1.2 Angle definitions in app (from report / code context)

- **2D angle:** vectors in normalized image space (x,y)
- **3D angle:** dot product using world x,y,z
- **Joint chain:** Shoulder → Elbow → Wrist (BlazePose indices 11/12 → 13/14 → 15/16)

### 1.3 Metrics documented

- **`dzShare`** = `|dz| / segment_length_3D` (depth share of segment)
- **`facingRatio`** (in `ElbowAngleEstimator.kt`): `‖shoulders‖_2D / ‖shoulders‖_3D` (used as frontal vs side indicator)

### 1.4 Quantitative examples in repo (same pose, camera varies)

From `elbow-angle-problem-report.md` (controlled test ~35° real):

| Camera | 2D | 3D |
|--------|-----|-----|
| Front-side | 45.2° | 73.4° |
| Frontal | 18.6° | 65.0° |
| Front-behind | 26.0° | 64.0° |
| Side | 93.4° | 92.6° |

From `Solutions.md` (YZ projection stability table): YZ angles vs ~35° real across views (values listed there).

### 1.5 Statement on monocular limits (multiple docs)

- Single RGB loses depth; many 3D poses can match one 2D projection (ill-posed inverse problem).
- Literature cited in `deep-research-report (3).md`: monocular methods often weaker in depth than multi-camera; multi-view / depth sensors cited where “true 3D” is required.

---

## 2. Proposed Heuristic / Geometric Solutions (Not All Implemented)

From `Solutions.md` — **six numbered ideas** with pros/cons as written there:

| # | Name | Core idea (summary) |
|---|------|----------------------|
| 1 | Body-Plane Projection | Build body axes from shoulders+hips; compute angle in body/sagittal frame |
| 2 | Segment-Length-Constrained Z Reconstruction | Calibrate bone lengths; infer depth from 2D lengths + constraints; sign ambiguity handling options (MediaPipe Z sign only; anatomical priors) |
| 3 | Adaptive Z-Weight Attenuation | Scale world Z by reliability (dzShare thresholds); blend toward 2D when Z unreliable |
| 4 | Camera-Angle Detection + View-Specific Correction | facing_ratio bands; different method per view; empirical correction curves |
| 5 | YZ-Projection + Adaptive Scaling | Use YZ projection + dynamic correction (data showed smaller spread than pure 2D/3D in their table) |
| 6 | ML-Based 3D Lifting | MediaPipe 2D only + separate 2D→3D model; named options in file: MotionBERT, VideoPose3D, MoveNet + depth estimator |

---

## 3. Solutions Actually Attempted (Project History)

From `elbow-angle-problem-report.md` and `ml-engineer-brief.md`:

| # | Name | Files / API | Outcome stated |
|---|------|-------------|----------------|
| 1 | DepthCorrector (Z cap, imbalance, bone median) | `DepthCorrector.kt` (removed) | Failed — cannot separate real depth from bad Z; calibration contamination |
| 2 | Hybrid 2D/3D blend | `AngleCalculator.calculateAngleHybrid()` (removed) | Failed — 2D not camera-independent; side view both wrong |
| 3 | Smoothing / OneEuro / frame fixes | `OneEuroFilter.kt`, `LandmarkSmoother.kt`, activities | Partial — stability only, not accuracy |
| 4 | Body-plane + segment constraints (v1) | `ElbowAngleEstimator.kt` v1 | Failed — state drift, artifacts |
| 5 | dzImbalance direction correction (v2) | `ElbowAngleEstimator.kt` v2 | Partial — mid-range wrong; blend toward 3D harmful when 3D inflated |
| 6 | Confidence-based tiers (v3) | `ElbowAngleEstimator.kt` (current) | Current — avoids worst errors; hold when low confidence; does not claim camera-independent side view |

**Insight documented:** `dzShare` treated as **confidence**, not reliable **direction** of error.

---

## 4. ML / Model Paths (From `ml-engineer-brief.md` + `lifting-model-solution-plan.md`)

### 4.1 Option A — Lightweight 2D→3D lifting (full skeleton)

- **Input shapes mentioned:** 17 joints × 2 = 34 (H36M-style); output 17×3 = 51.
- **Architecture example:** Martinez-style MLP + residual blocks; hidden 512 or 256 cited.
- **Loss components listed in lifting plan:** MPJPE (L2), bone length loss, elbow angle loss (weighted).
- **Training hyperparameters example in lifting plan:** batch 1024, lr 1e-3, AdamW, cosine schedule, augmentations (flip, joint noise, scale).
- **Success metrics mentioned:** MPJPE &lt; 50–60 mm; elbow joint error targets in doc.
- **Export paths mentioned:** PyTorch → ONNX → TensorFlow → TFLite; or `ai_edge_torch` / LiteRT mention.
- **Domain-gap mitigations listed:** noise augmentation; run MediaPipe on dataset frames.

### 4.2 Option B — Modify Z at source

- Fine-tune BlazePose / train Z correction head on 33 landmarks.
- **Cons noted in brief:** proprietary pipeline, infrastructure, risk to other landmarks.

### 4.3 Option C — Multi-hypothesis

- **MDN:** mixture of Gaussians over 3D pose.
- **D3DP:** diffusion + multi-hypothesis; JPMA per-joint aggregation vs 2D reprojection.
- **ManiPose:** manifold + multiple rotation hypotheses + FK; multiple-choice learning.
- **ProHMR (flows):** probabilistic SMPL; image-based; combining with 2D priors mentioned.
- **FMPose:** flow matching; heatmap-conditioned; ODE steps.

### 4.4 Elbow Correction MLP (dedicated plan)

From `elbow-correction-mlp-plan.md`:

- **Task options discussed:** direct angle regression vs corrected 3D vs delta correction (table in plan).
- **Feature count in plan:** 26 engineered features (groups A–F listed in that file).
- **Architecture example in plan:** 26 → 128 → 64 → 32 → 1 with sigmoid×180 (example only).
- **Loss example in plan:** MAE + penalty on large errors.
- **Data pipeline:** Human3.6M + MediaPipe on frames; subject split; sampling rate options; supplementary datasets named.

---

## 5. Research Paper Themes (From `Docs/Reasearch/cursor_*.md` Exports)

### 5.1 Multi-hypothesis / probabilistic 3D (PDF set 1)

| Paper | Technique (as summarized) | Post on MediaPipe? | Mobile note in export |
|-------|----------------------------|--------------------|------------------------|
| Li & Lee MDN | 2D→3D MDN, multiple Gaussians | Possible if joints normalized to training convention | Lighter among options |
| D3DP | Diffusion, JPMA per joint | Theoretical; heavy | Hard real-time without simplification |
| ProHMR | Normalizing flows, SMPL | Image + optimization with priors; not landmarks-only | Heavy |
| FMPose | Flow matching, heatmaps+GCN | Needs heatmaps or input change | Medium–heavy |
| ManiPose | Shared bone lengths, K rotations, FK, manifold | Suited to 2D sequences | Depends on T and architecture |

### 5.2 AugLift / PoseMoE / JAR (PDF set 2)

| Paper | Technique (as summarized) | Post on MediaPipe? | Note |
|-------|----------------------------|--------------------|------|
| AugLift | Monocular depth map → UADD per joint + lifting | Partial — depth pipeline + trained lifting | Extra forward pass |
| PoseMoE | MoE separating XY vs depth features | Full replacement-style network, not thin post-process | Heavier |
| JAR | 2D angles → Fourier smoothing → BiGRU-Attention → rebuild with limb lengths | Retrain/adapt; video | Improves 2D angle stability; depth ambiguity limits noted |

### 5.3 BioPose / BLAPose / HSMR-SKEL (PDF set 3)

| Paper | Technique (as summarized) | Post on MediaPipe? | Mobile note |
|-------|----------------------------|--------------------|-------------|
| BioPose | MQ-HMR → NeurIK, BSK, FK, temporal transformer | Not light post on landmarks only | Heavy stack |
| BLAPose | GRU predicts bone lengths; adjust lifted 3D directions + lengths | Concept applies after any lifter | Bi-GRU offline vs unidirectional online |
| HSMR/SKEL | SKEL params, joint limits, SKELify optimization | Theoretical fit+optimize on 2D; slow baseline cited | Not mobile as full pipeline |

### 5.4 Perspective / camera geometry (PDF set 4)

| Paper | Technique (as summarized) | Relation to landmarks-only post |
|-------|----------------------------|--------------------------------|
| Beyond Weak Perspective | Full pinhole, f≈√(W²+H²), principal point, SMPLify-style | Needs SMPL/SMPLify + iterative fit |
| KPE | θx, θy from intrinsics; dense/sparse KPE | Small MLP possible with 2D + intrinsics + crop |
| PersPose | Kcrop, perspective encoding, perspective rotation | Full CNN training; ideas usable in custom lifter |
| EPOCH | RegNet + LiftNet loop; flows; limb constraints; approximate K from bbox | LiftNet+K in principle; RegNet heavy |

---

## 6. Datasets Named Across Repo

| Dataset | Mentions | Typical use described |
|---------|----------|------------------------|
| Human3.6M | Many | MoCap 3D; multi-camera; standard subject splits [1,5,6,7,8] train / [9,11] test in docs |
| MPI-INF-3DHP | Several | Smaller, more environment diversity |
| 3DPW | Several | Outdoor, SMPL GT |
| Custom / in-app | ml-brief, plans | Protractor, multi-angle capture, Angle Lab export |

---

## 7. Posture MLP — Technical Constraints (Reusable Pattern)

From `tools/posture-mlp/TRAINING_GUIDE.md`:

- Pipeline: **Image → MediaPipe → engineered features → Keras MLP → TFLite + `*_norm.json`**
- **Z-score** normalization: mean/std from training set only; JSON at inference.
- **TFLite:** float32 export **without** `converter.optimizations` dynamic quantization — reason given: `FULLY_CONNECTED v12` ops need TFLite **≥ 2.17**; Android dep **2.16.1**.
- Feature parity: Python `feature_engineering.py` ↔ Kotlin `PostureMlpFeatureExtractor.kt`.

---

## 8. Public / Ecosystem Mitigations (From `deep-research-report (3).md`)

- Legacy: **USE_PREV_LANDMARKS**, smoothing in graph.
- **VIDEO / LIVE_STREAM** tracking reduces discontinuities vs pure per-frame.
- Community **heuristic Z rescaling** (example: divide pose Z by 0.5 in one Holistic report) — explicitly **not** guaranteed correct.
- Holistic legacy docs: pose **z** sometimes said to be discarded; Pose Landmarker docs present z/world as usable — **documentation tension** noted.
- Mitigation patterns listed: **2D-first** angle; **gate/repair wrist Z** with kinematic constraints; **filter wrist Z** (One Euro, low-pass, Kalman); **multi-view or depth** for stronger 3D.

---

## 9. Future Directions Table (`elbow-angle-problem-report.md` §8)

| Approach | Feasibility stated | Impact stated |
|----------|-------------------|---------------|
| Lightweight lifting 2D→3D | High effort 4–8 weeks, needs data | Addresses depth ambiguity |
| Simplified HybrIK (analytical IK) | Medium effort | Constrains impossible poses |
| Perspective correction (intrinsics) | Low effort | Incremental; edges more than center |
| Temporal smoothing + bone constraints | Low effort | Smoothness not guaranteed accuracy |

---

## 10. Open Questions (Q1–Q24) — All Documented Alternatives as Answers

*Questions originate from `elbow-correction-mlp-plan.md` §11. Below: every alternative or method named in repo research for each theme — **no selection**.*

### Q1 — Elbow angle diversity in Human3.6M for “exercise-like” ranges

**Facts in repo:** H3.6M described as everyday activities; skew toward relaxed arm; possible lack of deep flexion.

**Alternatives mentioned for distribution:**
- Analyze histogram after extraction.
- Weighted sampling / stratified batches by angle bin.
- Supplement **MPI-INF-3DHP**, **3DPW**, custom exercise captures.
- Synthetic augmentation (noise, scale) — lifting plan + elbow plan.
- Synthetic rendering from SMPL/MoCap — general literature path (not detailed in repo).

### Q2 — MediaPipe detection rate on H3.6M frames

**Alternatives:**
- Pilot on N sample frames; count `pose_landmarks` non-empty.
- Adjust `min_pose_*_confidence` thresholds (training scripts use values in `train_posture_mlp.py`: 0.3).
- Skip frames with failed detection (pattern in posture collector).

### Q3 — H3.6M ↔ MediaPipe topology / indices

**Documented mapping** (`ml-engineer-brief.md`, `lifting-model-solution-plan.md`):

- H36M 17 joints ↔ MediaPipe 33 points table (hip mid, spine midpoints, etc.).
- **Alternatives for ground truth angle:**
- Angle from MoCap 3D joints in **world** coordinates.
- Angle from MoCap after transform to **camera** coordinates (Q4–Q5).
- Verify with visual overlay (mentioned as validation step).

### Q4 — Camera parameters for ground truth

**Alternatives:**
- Use H3.6M released **camera intrinsics + extrinsics** per view (standard in dataset pipelines; implementation detail not in repo).
- Ignore extrinsics; compute angle in MoCap space only — **changes meaning** vs image (geometric distinction).
- Approximate K from image W,H, bbox (EPOCH Ψ idea in research export).

### Q5 — Ground truth: camera-relative vs world-relative angle

**Alternatives:**
- **Camera-relative 3D angle** after transforming joints to camera frame (matches “what camera sees” in 3D).
- **World/body-relative 3D angle** invariant to camera if joints in consistent frame.
- **2D image-plane angle** as label — matches projected appearance, not anatomical 3D flexion (JAR-style).
- **Elbow plan text** states one rationale for camera-relative + MediaPipe features; other formulations remain geometric options.

### Q6 — Optimal feature count (26 vs other)

**Alternatives:**
- SHAP / permutation importance (named in plan).
- Ablation: remove groups (depth, context, visibility).
- Auto-ML / feature selection algorithms — not in repo but standard option.
- Raw full 33×(x,y,z,vis) as huge vector — possible but not specified in plan.

### Q7 — Raw normalized x,y vs derived-only

**Alternatives:**
- Include raw shoulder/elbow/wrist (x,y) normalized.
- Include only derived (angles, ratios) — current plan leans derived + partial world dirs.
- Include heatmaps — FMPose path; MediaPipe does not output heatmaps natively.

### Q8 — Lower-body features

**Alternatives:**
- Hip/knee angles, pelvis orientation as extra channels.
- Exclude — elbow plan omitted large lower-body set intentionally.
- Torso-only context (already in posture features: spine, shoulder width, etc.).

### Q9 — Normalized vs world for 2D-like inputs

**Alternatives:**
- **Normalized** [0,1] image — used in posture pipeline.
- **World x,y** in meters — different scale; could be normalized separately.
- Hybrid: both (redundancy).

### Q10 — Regression vs classification

**Alternatives:**
- **Continuous regression** (MAE, MSE, Huber).
- **Binned classification** (e.g. 36 bins × 5°) + softmax.
- **Ordinal regression** — not mentioned in repo but exists in literature.
- **Quantile regression** for uncertainty — not in repo.

### Q11 — Confidence / uncertainty output

**Alternatives:**
- Second output head (sigmoid or scalar).
- **MC Dropout** at inference (named in brief).
- Ensemble of models.
- MDN / probabilistic outputs (research exports).
- Use heuristic `maxDz` / visibility as external gate without learning confidence.

### Q12 — Residual connections in MLP

**Alternatives:**
- Plain stack (Posture MLP style).
- Residual blocks (lifting plan PyTorch example).
- LayerNorm / BatchNorm — lifting uses BatchNorm in example; posture uses Dropout+L2.

### Q13 — TensorFlow vs PyTorch

**Alternatives:**
- **TensorFlow/Keras** — posture-mlp path, direct TFLite.
- **PyTorch** — lifting plan examples, then ONNX→TF or other converters.
- **JAX** — not in repo.

### Q14 — Angle distribution imbalance

**Alternatives:**
- Weighted sampling by inverse frequency.
- Balanced batches by angle bin.
- **Oversampling** rare bins.
- **Synthetic** frames (rendering, interpolation).
- **Loss reweighting** per bin.

### Q15 — Validation split strategy

**Alternatives:**
- **Subject-disjoint** (H3.6M standard in docs).
- Random frame split — risks subject leakage.
- **Action-disjoint** — possible.
- **Camera-disjoint** — possible.
- **Time-disjoint** within video — for temporal models.

### Q16 — Epochs / early stopping

**Alternatives in repo:**
- Posture: up to 200 epochs, patience 20 on `val_loss`.
- Lifting plan: 80 epochs cosine.
- Elbow plan example: 100 epochs, patience 15 on val MAE.
- Any manual schedule (step decay, warmup) — not fixed in repo.

### Q17 — TFLite float32 vs quantized

**Alternatives:**
- Float32 — posture-mlp requirement for op compatibility.
- Dynamic range quantization — **blocked** by current note (v12 ops).
- Full integer quantization with representative dataset — possible if runtime upgraded.
- **GPU delegate / NNAPI** — mentioned in lifting plan as optional for interpreter.

### Q18 — When to disable ML and fallback

**Conditions named across docs:**
- Model failed to load (assets missing).
- **Visibility** below threshold (0.5 used in `ElbowAngleEstimator` for keypoints).
- **maxDz** high — heuristic uses HOLD.
- **Implausible jump** (example threshold 20° in elbow plan question).
- **Timeout** on hold — heuristic uses 500 ms window.

### Q19 — Temporal smoothing of ML output

**Alternatives:**
- **One Euro Filter** — already in project.
- **EMA** — used in elbow estimator output smoothing.
- **Kalman** — cited in deep-research.
- **Savitzky-Golay / Fourier** — JAR paper summary.
- **BiGRU** — JAR; needs sequence, bidirectional vs causal.

### Q20 — Debug UI

**Alternatives:**
- Angle Lab: show MLP angle, heuristic, raw 2D/3D, diff (elbow plan).
- Export clipboard — already in ml-brief for Angle Lab.

### Q21 — Extending to other joints

**Alternatives:**
- Same feature pipeline per joint (shoulder, wrist, knee…).
- Single multi-output model.
- Full skeleton lifter (Option A) then derive all angles.

### Q22 — Temporal features in model input

**Alternatives:**
- Single-frame MLP (elbow plan phase 1).
- **GRU/TCN/Transformer** over past K frames — VideoPose3D, BLAPose, JAR, BioPose references.
- **Optical flow** — not in repo.
- **Previous angle as input feature** — simple autoregressive feature.

### Q23 — Camera intrinsics (e.g. Camera2)

**Alternatives:**
- **fx, fy, cx, cy** as extra inputs to model.
- **KPE** angular coordinates (research export).
- **f ≈ √(W²+H²)** heuristic (Beyond Weak Perspective summary).
- **No intrinsics** — current MediaPipe-only path.

### Q24 — In-app data collection for training

**Alternatives:**
- User-recorded video + labeled folder (posture-mlp style).
- Guided calibration poses with known angles.
- Crowdsourced labeling — not discussed.
- Server-side storage — architecture-dependent.

---

## 11. Hardware / Sensing Paths (Mentioned Across Docs)

- Single RGB (current).
- **Stereo / multi-view** — literature + deep-research.
- **Depth sensor / LiDAR** — mentioned as line where depth becomes more reliable.
- **Two phone cameras** (front+rear) — only as conceptual multi-view in some discussions (not implemented in snippets read).

---

## 12. Loss Functions Named (Repository)

| Loss | Where |
|------|--------|
| Sparse categorical cross-entropy | Posture MLP |
| MPJPE (L2 on joints) | Lifting plan |
| Bone length L1 | Lifting plan |
| Elbow angle loss (weighted) | Lifting plan |
| MAE / hybrid with squared penalty on large errors | Elbow correction plan example |
| MSE | Generic option in Q10 |

---

## 13. Metrics Named (Repository)

| Metric | Where |
|--------|--------|
| Accuracy / val_accuracy | Posture MLP |
| MPJPE (mm) | Lifting plan |
| Mean angular error (elbow, degrees) | Lifting plan, elbow plan |
| MAAE | Elbow plan targets section |
| P95 / P99 error | Elbow plan evaluation |

---

## 14. Code / Pipeline Touchpoints (From Report)

- **Angle pipeline:** `LandmarkSmoother` → `AngleCalculator.calculateAllAnglesSmoothed(use3D=true)` → `ElbowAngleEstimator.correct()` → `JointAngles.mirrored()` (front cam).
- **Call sites:** `MainActivity.kt`, `TrainingActivity.kt`, `DebugActivity.kt`, `VideoModeController.kt`.
- **Reset:** `ElbowAngleEstimator.reset()` with `LandmarkSmoother.reset()` on camera/mode/video changes.

---

## 15. Named External Models / Frameworks (Strings in Repo)

- **GHUM** — body model linked to BlazePose in research text.
- **MotionBERT**, **VideoPose3D**, **MoveNet** — Solutions.md + lifting plan.
- **SMPL**, **SMPLify**, **SKEL**, **BSK**, **OpenSim** — biomechanics papers summaries.
- **HRNet**, **CPN** — classic 2D detectors paired with lifting in literature notes.
- **Martinez et al. 2017** — baseline MLP lifting reference.

---

## 16. Web / Literature — 2025–2026 (Supplement, March 2026)

The following items were gathered via web search **outside** the git repo. They are **not** vetted for correctness beyond what public pages/preprints state. Treat arXiv versions as preprints unless peer-reviewed.

### 16.1 Monocular 3D pose / lifting (recent lines of work)

| Name | Identifier / link | Stated idea (short) |
|------|-------------------|----------------------|
| PoseMamba | [arXiv:2408.03540](https://arxiv.org/abs/2408.03540) | Spatio-temporal state-space model (SSM) for monocular 3D HPE; bidirectional global–local modeling; reported SOTA-class results on Human3.6M / MPI-INF-3DHP in preprint materials. |
| PoseMoE | [arXiv:2512.16494](https://arxiv.org/abs/2512.16494) | Mixture-of-Experts: separate pathways for 2D pose vs depth-related features; cross-expert aggregation; lifting-based 3D HPE. |
| AugLift (2025 HTML) | [arXiv HTML 2508.07112](https://arxiv.org/html/2508.07112v1) | Enrich 2D keypoints with confidence + depth cues from pretrained depth models to improve lifting generalization across datasets. |
| PersPose | [arXiv:2508.17239](https://arxiv.org/abs/2508.17239) | Perspective encoding + perspective rotation; Kcrop-related encoding; full-perspective 3D HPE thread. |
| UniK3D | [arXiv:2503.16591](https://arxiv.org/abs/2503.16591) | “Universal camera” monocular 3D estimation (title-level; see paper for scope). |
| MotionAGFormer / GaLFormer / MixSTE | Cited in 2025 benchmark tables (e.g. [Nature Scientific Reports tables](https://www.nature.com/articles/s41598-025-91426-w/tables/1)) | Temporal transformer family; MPJPE numbers appear in comparative tables (inputs GT 2D vs detected 2D differ). |

### 16.2 View-invariant / canonical kinematics (relevant to “same angle across cameras”)

| Name | Identifier / link | Stated idea (short) |
|------|-------------------|----------------------|
| 3DPCNet | [arXiv:2509.23455](https://arxiv.org/abs/2509.23455) | “Pose canonicalization”: module on 3D joint coordinates to map camera-centered estimates to a body-centered frame; hybrid GCN+Transformer; reports large reduction in mean rotation error and MPJPE vs baseline in preprint; trained with self-supervised rotated poses (MM-Fi); evaluated also on TotalCapture (IMU correlation mentioned). |

### 16.3 Multi-view / calibration-free metric capture (not single-phone, but related ecosystem)

| Name | Identifier / link | Stated idea (short) |
|------|-------------------|----------------------|
| Kineo | [arXiv:2510.24464](https://arxiv.org/abs/2510.24464) | Calibration-free metric mocap from **sparse** consumer RGB cameras (multi-view, unsynchronized); joint camera calibration + 3D keypoints + dense points; audio sync; graph optimization. |

### 16.4 Articulated 3D reconstruction (general robotics — not human pose app)

| Name | Identifier / link | Note |
|------|-------------------|------|
| MonoArt | [arXiv:2603.19231](https://arxiv.org/abs/2603.19231) | Monocular **articulated object** reconstruction (PartNet-Mobility class of problems); TRELLIS generator, part reasoning, kinematic estimator — **different task** from BlazePose elbow angles, listed as “nearby CV research” only. |

### 16.5 Empirical / validation literature (cameras vs biomechanics)

| Topic | Link | Stated takeaway (high level) |
|-------|------|------------------------------|
| BlazePose / depth in scientific study | [Nature Scientific Reports article](https://www.nature.com/articles/s41598-025-22626-7) (2025) | Comparative work noting depth errors larger than in-plane errors for 3D pose estimators; BlazePose metrics discussion in context of depth (see paper body). |
| RGB vs RGB-D validation (older) | [PMC9824231](https://pmc.ncbi.nlm.nih.gov/articles/PMC9824231/) | Validation of angle estimation from body tracking — useful as methodology reference, not necessarily latest models. |
| IEEE — view-independent knee/elbow angles | [IEEE Xplore 9871106](https://ieeexplore.ieee.org/document/9871106) | Deep-learning view-independent knee/elbow angle estimation (title-level; access may need subscription). |

### 16.6 Google MediaPipe / BlazePose ecosystem (release activity)

| Source | Information |
|--------|-------------|
| [Pose Landmarker guide (Google AI Edge)](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker) | Official task API: 33 landmarks, modes IMAGE/VIDEO/LIVE_STREAM, segmentation optional, confidence thresholds. |
| [MediaPipe GitHub releases](https://github.com/google-ai-edge/mediapipe/releases) | Release tags continue through 2025–2026 (e.g. v0.10.32 January 2026, v0.10.33 March 2026 mentioned in search snippets — verify on releases page before relying on version numbers). |
| PRs (examples) | [Landmarker detection confidence #5941](https://github.com/google-ai-edge/mediapipe/pull/5941) — ecosystem moves toward richer confidence/detection metadata (check merge status on GitHub). |

### 16.7 Depth + BlazePose (hardware fusion examples)

| Example | Link |
|---------|------|
| DepthAI + BlazePose integration (community project) | [github.com/geaxgx/depthai_blazepose](https://github.com/geaxgx/depthai_blazepose) |

### 16.8 Relation to POSE-2 knowledge base

- Items in §16.1–16.4 **overlap thematically** with repo exports (PoseMoE, AugLift, PersPose already summarized in `Docs/Reasearch/cursor_*.md`).
- **Web-only additions** worth tracking separately: **3DPCNet** (canonicalization), **Kineo** (multi-RGB metric), **PoseMamba** (SSM temporal), **2025–2026 arXiv versions** of known methods.
- **None of the above** replace the need to verify: task match (human elbow vs object articulation), input type (2D vs 3D estimator output), mobile latency, and license for production.

---

*Compiled as a neutral inventory. Update this file when new experiments or papers are added to `Docs/` or `tools/`.*

*Last consolidated: March 2026 — §16 added after web search supplement.*
