# Training Platform — System Architecture Plan

> A structured training system that provides real, measurable progress for trainees
> through clear paths, data-driven progression, and intelligent program prescription.

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Entity Hierarchy](#2-entity-hierarchy)
3. [Entity Definitions](#3-entity-definitions)
4. [Level System](#4-level-system)
5. [Metrics Contract](#5-metrics-contract)
6. [Prescription Engine](#6-prescription-engine)
7. [Progression Engine](#7-progression-engine)
8. [Reassessment System](#8-reassessment-system)
9. [User Journey](#9-user-journey)
10. [What Exists vs What's New](#10-what-exists-vs-whats-new)
11. [Product Metrics](#11-product-metrics)
12. [Implementation Phases](#12-implementation-phases)

---

## 1. System Overview

### Core Philosophy

The system operates as a closed feedback loop:

```
Assess → Classify → Prescribe → Train → Measure → Progress → Reassess
```

Every decision in this loop is driven by **real performance data** collected through pose estimation (ROM, form score, symmetry, stability, tempo, velocity). No guesswork.

### Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│                    USER INTERFACE                        │
│          Android App  ·  Admin Dashboard                │
├─────────────────────────────────────────────────────────┤
│                   APPLICATION LAYER                      │
│  Prescription Engine · Progression Engine · Reports     │
├─────────────────────────────────────────────────────────┤
│                     DATA LAYER                          │
│  Level Profile · Programs · Sessions · Assessments      │
├─────────────────────────────────────────────────────────┤
│                   MEASUREMENT LAYER                      │
│  Pose Estimation · Angle Calculation · Form Validation  │
│  Rep Counting · Metrics Collection · Report Generation  │
└─────────────────────────────────────────────────────────┘
```

---

## 2. Entity Hierarchy

### Complete Structure

```
User
 └── UserLevelProfile ─────────────────── Multi-dimensional level snapshot
       ├── overallLevel                    Simple number (1-5) shown to user
       ├── domainLevels[]                  Per-domain breakdown
       └── regionLevels[]                  Per-body-region breakdown

 └── ActivePlan ───────────────────────── User's current training schedule
       └── ActivePlanProgram[] ────────── Ordered list of enrolled programs
             └── Program
                   └── ProgramWeek[]
                         └── ProgramDay[]
                               ├── ProgramSession[]
                               │     └── ProgramSessionItem[]
                               │           ├── Exercise (sets, reps, weight, targets)
                               │           └── TimedRest
                               └── RestDay

 └── BodyScanResult[] ─────────────────── Assessment history
 └── TrainingSession[] ────────────────── Exercise-level session history
       ├── SessionMetrics                  Aggregated session metrics
       └── RepMetrics[]                    Per-rep detailed metrics
```

### Relationship Map

```
Assessment ──produces──▸ BodyScanResult ──feeds──▸ UserLevelProfile
                                                         │
UserLevelProfile ──feeds──▸ PrescriptionEngine           │
                                  │                      │
                           selects & configures          │
                                  │                      │
                                  ▼                      │
                           ActivePlan (Programs)         │
                                  │                      │
                           user trains                   │
                                  │                      │
                                  ▼                      │
                           TrainingSession + Metrics     │
                                  │                      │
                           ProgressionEngine evaluates   │
                                  │                      │
                            ┌─────┴──────┐               │
                            ▼            ▼               │
                     adjust next    trigger              │
                      session     reassessment ──────────┘
```

---

## 3. Entity Definitions

### 3.1 Level

A **Level** is a named stage in the user's training journey. It defines the difficulty tier and default training parameters. Levels are system-wide (not per-program).

| Field | Type | Description |
|-------|------|-------------|
| id | String | Unique identifier |
| number | Int | Level number (1-5) |
| name | LocalizedText | Display name (e.g., "Foundation", "Building", "Intermediate", "Advanced", "Elite") |
| description | LocalizedText | What this level means |
| icon | String? | Level badge/icon |
| color | String? | Theme color for UI |
| defaults | LevelDefaults | Default training parameters (see below) |
| entryThreshold | Float | Minimum bodyScore to enter this level |

**LevelDefaults** — Default training parameters when a program doesn't specify its own:

| Field | Type | Description |
|-------|------|-------------|
| setsRange | {min, max} | Default sets range (e.g., {2,3} for Level 1) |
| repsRange | {min, max} | Default reps range (e.g., {8,10} for Level 1) |
| intensityGuide | String | "bodyweight_only" / "light" / "moderate" / "heavy" / "max_effort" |
| restBetweenSetsMs | Int | Default rest between sets |
| sessionDurationRange | {min, max} | Session duration in minutes |
| weeklyFrequencyRange | {min, max} | Recommended training days per week |

**Level Thresholds (single source of truth — aligned with existing FitnessLevel):**

| Level | Number | BodyScore Range | FitnessLevel Equivalent | Description |
|-------|--------|-----------------|-------------------------|-------------|
| Foundation | 1 | 0–24 | needs_rehab | Safety first. Corrective focus. Bodyweight only. |
| Building | 2 | 25–44 | limited | Basic movement patterns. Light resistance. |
| Intermediate | 3 | 45–64 | average | Progressive overload. Moderate intensity. |
| Advanced | 4 | 65–84 | good | Complex movements. Heavy loads. |
| Elite | 5 | 85–100 | excellent | Performance optimization. Peak training. |

> These ranges are clean boundaries with no overlaps. The same `scoreToLevel()` function is used everywhere — backend, Android, and admin dashboard. This aligns with the existing `FitnessLevel.fromBodyScore()` in the Android codebase.

---

### 3.2 UserLevelProfile

A snapshot of the user's current level across all dimensions. Updated after each assessment.

| Field | Type | Description |
|-------|------|-------------|
| id | String | Unique identifier |
| userId | String | FK → User |
| overallLevel | Int | The simple level number shown to user (1-5) |
| bodyScore | Float | Latest assessment bodyScore (0-100) |
| domainLevels | DomainLevel[] | Per-domain level breakdown |
| regionLevels | RegionLevel[] | Per-body-region level breakdown |
| limitingFactors | LimitingFactor[] | Domains/regions holding the user back |
| assessmentId | String | FK → BodyScanResult that produced this profile |
| classifiedAt | DateTime | When this profile was calculated |

**DomainLevel:**

| Field | Type | Description |
|-------|------|-------------|
| domain | String | "mobility" / "control" / "symmetry" / "safety" |
| level | Int | Level number for this domain (1-5) |
| score | Float | Raw score (0-100) |

**RegionLevel:**

| Field | Type | Description |
|-------|------|-------------|
| region | String | "shoulder" / "hip" / "knee" / "ankle" / "spine" / "core" / "balance" |
| level | Int | Level number for this region (1-5) |
| score | Float | Raw score (0-100) |
| isLimiting | Boolean | Whether this region is a limiting factor |

**LimitingFactor:**

| Field | Type | Description |
|-------|------|-------------|
| type | String | "domain" or "region" |
| code | String | Domain or region code |
| currentLevel | Int | Current level |
| targetLevel | Int | Level it should be at (based on overallLevel) |
| gap | Int | How far behind (targetLevel - currentLevel) |

**How overallLevel is calculated:**

```
overallLevel = scoreToLevel(bodyScore)

Where scoreToLevel maps (single function, used everywhere):
  0-24   → Level 1 (Foundation)
  25-44  → Level 2 (Building)
  45-64  → Level 3 (Intermediate)
  65-84  → Level 4 (Advanced)
  85-100 → Level 5 (Elite)
```

The same `scoreToLevel()` function is applied to domain scores and region scores to compute their individual levels.

---

### 3.3 Program

A structured training plan spanning multiple weeks. Enhanced from existing Program entity.

**Existing fields** (already in schema — no changes):
- id, name, description, slug, coverImageUrl, durationWeeks, difficulty, isDefault, isPublished, tags
- Relationships: weeks[], userPrograms[], programSessionReports[]

**New fields to add:**

| Field | Type | Description |
|-------|------|-------------|
| type | String | Program type (see Program Types below) |
| targetDomain | String? | Primary domain this program targets (mobility/strength/control/symmetry) |
| targetRegions | String[]? | Body regions this program targets (shoulder/hip/knee/...) |
| levelRange | {min, max} | Which levels this program is suitable for |
| entryCriteria | Json? | Conditions to start (e.g., { "mobilityScore": { "max": 50 } }) |
| exitCriteria | Json? | Conditions to complete/graduate (e.g., { "mobilityScore": { "min": 70 } }) |
| contraindications | String[]? | Safety gate codes that block this program |
| prescriptionPriority | Int | Priority when multiple programs match (lower = higher priority) |
| prerequisiteProgramId | String? | FK → Program that must be completed first |
| nextProgramId | String? | FK → Suggested next program after completion |

**Program Types:**

| Type | Code | Description | Example |
|------|------|-------------|---------|
| Training | `training` | Standard strength/fitness training | "4-Week Full Body Training" |
| Mobility | `mobility` | Flexibility and range of motion improvement | "Shoulder Mobility Program" |
| Therapeutic | `therapeutic` | Targeted fix for a specific problem | "Lower Back Pain Relief" |

> Assessment remains a standalone process (not a program type) — it uses its own flow (PAR-Q+ screening → assessment exercises → BodyScanResult).

> Warm-up / Cool-down are handled as Workouts (exercise groups) that can be included in any Session.

> Recovery / Deload are built into programs as specific weeks with reduced volume.

---

### 3.4 ProgramWeek

A week within a program. Contains 7 days (some may be rest days).

**Existing fields** (no changes needed):
- id, programId, weekNumber, name, description, sortOrder

**New fields to add:**

| Field | Type | Description |
|-------|------|-------------|
| weekType | String? | "normal" / "deload" / "testing" — defaults to "normal" |

---

### 3.5 ProgramDay

A single day within a week.

**Existing fields** (no changes needed):
- id, weekId, dayNumber, isRestDay, name

**New fields to add:**

| Field | Type | Description |
|-------|------|-------------|
| dayType | String? | "training" / "rest" / "active_recovery" / "assessment" |

> The week always has 7 days structurally. Training days are flexible (1 to 7). Rest days are explicitly marked. This preserves the calendar-like view while allowing any training frequency.

---

### 3.6 ProgramSession

A training session within a day. A day can have multiple sessions (e.g., morning mobility + evening training).

**Existing fields** (no changes needed):
- id, dayId, name, sortOrder
- Relationships: items[], reports[]

**New fields to add:**

| Field | Type | Description |
|-------|------|-------------|
| estimatedDurationMin | Int? | Expected session duration |
| sessionCategory | String? | "main" / "warmup" / "cooldown" / "supplementary" |
| sourceWorkoutId | String? | If this session was generated from a Workout template |

---

### 3.7 ProgramSessionItem

An individual item within a session (exercise or rest).

**Existing fields** (no changes needed):
- id, sessionId, sortOrder, type (exercise/rest)
- Exercise fields: exerciseId, sets, targetReps, targetDuration, restBetweenSetsMs, weightKg, weightPerSet, notes
- Rest fields: restDurationMs
- Workout tracking: sourceWorkoutId, isModified

**New fields to add:**

| Field | Type | Description |
|-------|------|-------------|
| levelOverride | Int? | If set, use this level's defaults instead of user's level |
| targetROM | Float? | Target ROM for this exercise (from assessment data) |
| targetFormScore | Float? | Minimum acceptable form score |
| difficultyCode | String? | Which difficulty level to use (beginner/normal/advanced) |
| alternatives | String[]? | Exercise slugs that can substitute this exercise |
| progressionRuleId | String? | FK → ProgressionRule to apply |
| isPersonalized | Boolean | True if parameters were adjusted by Prescription/Progression Engine |

---

### 3.8 Exercise

**No structural changes needed.** The existing Exercise entity is already comprehensive:
- Pose variants with camera positions
- State-based form validation (PERFECT/NORMAL/PAD/WARNING/DANGER)
- Position checks (knee-over-toe, alignment, etc.)
- Feedback messages (joint states + position errors)
- Rep counting config (reps or duration)
- Weight config (min/max/default, supportsWeight)
- Bilateral config (switchEvery, startSide)
- Report metrics config (primary/optional/excluded)
- Difficulty levels per pose variant

---

### 3.9 Workout

**No structural changes needed.** Workouts serve as reusable exercise group templates (super sets, circuits, warm-up routines, cool-down routines). They can be imported into any ProgramSession.

---

### 3.10 ActivePlan

The user's current training schedule — which programs are active and in what order.

| Field | Type | Description |
|-------|------|-------------|
| id | String | Unique identifier |
| userId | String | FK → User (unique — one active plan per user) |
| status | String | "active" / "paused" / "completed" |
| createdAt | DateTime | When the plan was created |
| updatedAt | DateTime | Last update |

**ActivePlanProgram** — A program slot within the active plan:

| Field | Type | Description |
|-------|------|-------------|
| id | String | Unique identifier |
| activePlanId | String | FK → ActivePlan |
| userProgramId | String | FK → UserProgram (the enrollment) |
| sortOrder | Int | Order in the plan |
| status | String | "upcoming" / "active" / "completed" / "skipped" |
| scheduledStartDate | DateTime? | When this program should start |
| actualStartDate | DateTime? | When the user actually started |
| completedAt | DateTime? | When completed |

**How scheduling works:**

Programs within an ActivePlan are ordered sequentially. When the active program completes (or its exitCriteria are met), the next program becomes active. The system can also schedule a reassessment between programs.

> This replaces the current behavior where UserProgram.isActive is just a boolean. The ActivePlan adds ordering, scheduling, and automatic transitions.

> **Important migration note:** The current reports module (`/mobile/reports/metrics`) requires an active enrollment to query program reports. This must be relaxed before ActivePlan is introduced — users need to see historical reports from completed programs. Phase 0 addresses this by removing the active-program lock from reports queries.

---

### 3.11 ProgressionRule

Defines how training parameters change based on performance data.

| Field | Type | Description |
|-------|------|-------------|
| id | String | Unique identifier |
| name | String | Human-readable name |
| scope | String | "global" / "program" / "exercise" |
| programId | String? | If scope is "program", which program |
| exerciseSlug | String? | If scope is "exercise", which exercise |
| trigger | String | "session_completed" / "week_completed" / "set_completed" |
| conditions | ProgressionCondition[] | All must be true to fire |
| action | ProgressionAction | What to change |
| priority | Int | Higher priority rules evaluated first |
| isActive | Boolean | Can be toggled on/off |

**ProgressionCondition:**

| Field | Type | Description |
|-------|------|-------------|
| metric | String | "avgFormScore" / "completionRate" / "avgROM" / "totalVolume" / "symmetryScore" |
| operator | String | ">=" / "<=" / ">" / "<" / "==" |
| value | Float | Threshold value |
| window | String | "last_session" / "last_2_sessions" / "last_week" / "all_program" |

**ProgressionAction:**

| Field | Type | Description |
|-------|------|-------------|
| type | String | "increase_weight" / "decrease_weight" / "increase_reps" / "decrease_reps" / "increase_sets" / "change_difficulty" / "suggest_reassessment" |
| amount | Float? | How much to change (e.g., 2.5 for kg, 2 for reps) |
| notification | LocalizedText? | Message to show the user |

**Example Rules:**

1. **Weight Progression (positive):**
   - Trigger: session_completed
   - Conditions: avgFormScore >= 75 for last_2_sessions AND completionRate >= 90%
   - Action: increase_weight by 2.5 kg

2. **Weight Regression (safety):**
   - Trigger: session_completed
   - Conditions: avgFormScore < 60 for last_2_sessions
   - Action: decrease_weight by 2.5 kg + notification

3. **Rep Progression:**
   - Trigger: week_completed
   - Conditions: avgFormScore >= 80 for last_week AND avgROM >= 95% of target
   - Action: increase_reps by 2

4. **Reassessment Trigger:**
   - Trigger: week_completed
   - Conditions: week_number == program.durationWeeks (program complete)
   - Action: suggest_reassessment

---

### 3.12 ReassessmentSchedule

Tracks when users should be reassessed.

| Field | Type | Description |
|-------|------|-------------|
| id | String | Unique identifier |
| userId | String | FK → User |
| reason | String | "program_complete" / "periodic" / "progression_trigger" / "manual" |
| scheduledDate | DateTime | When reassessment should happen |
| status | String | "pending" / "completed" / "skipped" / "overdue" |
| assessmentId | String? | FK → BodyScanResult (filled after completion) |
| notes | String? | Context for why this reassessment was triggered |

---

## 4. Level System

### 4.1 Level Calculation Flow

```
BodyScanResult (from Assessment)
  │
  ├── bodyScore (0-100)
  │     └── maps to ──▸ overallLevel (1-5)
  │
  ├── domainScores
  │     ├── mobility (0-100) ──▸ mobilityLevel (1-5)
  │     ├── control (0-100)  ──▸ controlLevel (1-5)
  │     ├── symmetry (0-100) ──▸ symmetryLevel (1-5)
  │     └── safety (0-100)   ──▸ safetyLevel (1-5)
  │
  └── regions[]
        ├── shoulder ──▸ shoulderLevel (1-5)
        ├── hip      ──▸ hipLevel (1-5)
        ├── knee     ──▸ kneeLevel (1-5)
        └── ...
```

**`scoreToLevel()` — Single function used everywhere:**

```
score 0-24   → Level 1 (Foundation)
score 25-44  → Level 2 (Building)
score 45-64  → Level 3 (Intermediate)
score 65-84  → Level 4 (Advanced)
score 85-100 → Level 5 (Elite)
```

> This aligns with Android's `FitnessLevel.fromBodyScore()` (needs_rehab < 25, limited < 45, average < 65, good < 85, excellent >= 85).

### 4.2 Limiting Factor Detection

After computing all levels, the system identifies limiting factors:

```
For each domain:
  if domainLevel < overallLevel - 1:
    mark as limiting factor with gap = overallLevel - domainLevel

For each region:
  if regionLevel < overallLevel - 1:
    mark as limiting factor with gap = overallLevel - regionLevel
```

Limiting factors drive the Prescription Engine — they determine which programs to recommend.

### 4.3 Level Influence on Training

When a ProgramSessionItem doesn't define explicit reps/sets/weight, the system fills them from the user's level:

```
Resolve training parameters:
  1. ProgramSessionItem has explicit value? → Use it
  2. ProgramSessionItem has levelOverride? → Use that level's defaults
  3. User's domain level for this exercise's target domain → Use its defaults
  4. User's overallLevel → Use its defaults (final fallback)
```

This means the same program template can serve users at different levels — the Prescription Engine personalizes the parameters.

---

## 5. Metrics Contract

### 5.1 The Problem

The current system has an inconsistency in how metrics are stored:

| Location | Field | Type | Scale | Example |
|----------|-------|------|-------|---------|
| `SessionMetrics` (DB) | avgFormScore | Int | x10 (0-1000) | 850 = 85.0% |
| `SessionMetrics` (DB) | avgRom | Int | x10 (0-1800) | 900 = 90.0° |
| `RepMetrics` (DB) | formScore | Int | x10 | 750 = 75.0% |
| `ProgramSessionReport` (DB) | avgFormScore | Float | 0-100 | 85.0 |
| `ProgramSessionReport` (DB) | avgAccuracy | Float | 0-100 | 92.5 |
| Android `RepMetrics` | formScore | Int | x10 | 850 |
| Reports API response | averageFormScore | Float | 0-100 | 85.0 |

The same metric name (`avgFormScore`) means different things depending on where you read it. This is a direct risk to the Progression Engine — a rule checking `avgFormScore >= 75` would behave differently depending on which table it reads from.

### 5.2 The Contract

**Storage convention (DB):** All kinematic metrics are stored as **Int x10** for precision without floating point issues. This is already the convention in `SessionMetrics` and `RepMetrics`.

**API convention (responses):** All metrics are returned as **Float 0-100** (percentages) or natural units (degrees, ms, kg). The backend converts x10 → Float at the API boundary.

**ProgramSessionReport alignment:** `avgFormScore` and `avgAccuracy` in `ProgramSessionReport` should follow the same convention as `SessionMetrics` (Int x10). This requires a migration.

### 5.3 Conversion Rules

```
DB (Int x10) → API (Float): value / 10.0
API (Float)  → DB (Int x10): round(value * 10)

Examples:
  DB avgFormScore = 850 → API avgFormScore = 85.0
  DB avgRom = 1200       → API avgRom = 120.0 (degrees)
  DB stability = 750     → API stability = 75.0
```

### 5.4 Progression Engine Contract

The Progression Engine always works with **Float 0-100** values. It reads from the API-normalized layer, never directly from raw DB integers. This means:

- Rule condition `avgFormScore >= 75` always means "75% form quality"
- No confusion between x10 and 0-100 scales
- One conversion point, not scattered across the codebase

> This unification must happen before the Progression Engine is built (Phase 0).

---

## 6. Prescription Engine

### 5.1 Overview

The Prescription Engine is a **rule-based system** that translates assessment results into a personalized training plan. No AI/ML — deterministic, debuggable, and expert-configurable.

### 5.2 Classification Step

After each assessment, the user is classified into one of these priority categories:

| Priority | Category | Condition | Action |
|----------|----------|-----------|--------|
| 1 | SAFETY_BLOCK | safetyGates[] is not empty OR painFlags[] is not empty OR parqPassed == false | Therapeutic programs for affected regions. Block contraindicated exercises. |
| 2 | CORRECTION_NEED | Any region score < 25 (WEAK status) | Corrective/therapeutic program for that region + modified training for rest. |
| 3 | IMBALANCE | Any symmetry data < 60% OR symmetryLevel < overallLevel - 1 | Training with extra unilateral work for the weak side. |
| 4 | WEAKNESS | Any domain level < overallLevel - 1 (gap >= 2) | Targeted program for the weak domain + maintenance for others. |
| 5 | NORMAL | All domains balanced within 1 level | Standard training program matching overallLevel. |

> The highest-priority matching category wins. A user in SAFETY_BLOCK will never get a standard training program.

### 5.3 Program Selection Step

Given the classification, filter the program catalog:

```
matchingPrograms = allPublishedPrograms.filter(program =>
  program.type matches classification.requiredType
  AND program.levelRange includes user's relevant level
  AND program.targetDomain matches classification.targetDomain (if applicable)
  AND program.targetRegions overlaps classification.targetRegions (if applicable)
  AND program.contraindications does NOT overlap user's safetyGates
  AND program.prerequisiteProgramId is null OR user has completed it
)
.sortBy(program.prescriptionPriority)
```

### 5.4 Parameter Personalization Step

For each selected program, personalize the training parameters:

```
For each ProgramSessionItem in the program:
  1. Start with the item's defined values (admin-set)
  2. For any undefined value, apply LevelDefaults from user's level:
     - sets → LevelDefaults.setsRange.min
     - targetReps → LevelDefaults.repsRange.min
     - weightKg → exercise.defaultWeight (adjusted by level intensity guide)
     - restBetweenSetsMs → LevelDefaults.restBetweenSetsMs
  3. Apply safety adjustments:
     - If exercise region has safety gate → skip exercise, use alternative
     - If exercise has contraindication → reduce intensity or substitute
  4. Mark isPersonalized = true
```

### 5.5 Plan Assembly Step

Build the user's ActivePlan from selected programs:

```
ActivePlan:
  Program 1: [Therapeutic - Shoulder Mobility] (if SAFETY_BLOCK/CORRECTION)
  Program 2: [Training - Level 2 Full Body]    (main training)
  Program 3: [Reassessment checkpoint]          (auto-scheduled)
  Program 4: [Next program based on reassessment results]
```

---

## 7. Progression Engine

### 6.1 Overview

The Progression Engine evaluates performance data after each training session and adjusts future sessions. It uses the **rich per-rep metrics** collected by the pose estimation system.

### 6.2 Available Metrics for Rules

From the existing system (already collected per rep and per session):

| Metric | Source | Description |
|--------|--------|-------------|
| avgFormScore | SessionMetrics | Average form quality (0-100) |
| avgROM | SessionMetrics | Average range of motion |
| avgSymmetry | SessionMetrics | Bilateral symmetry |
| avgStability | SessionMetrics | Trunk/core stability |
| avgVelocity | SessionMetrics | Movement velocity |
| formConsistency | SessionMetrics | DTW-based consistency |
| fatigueIndex | SessionMetrics | When fatigue started |
| totalVolume | SessionMetrics | Weight x reps |
| est1RM | SessionMetrics | Estimated 1 rep max |
| completionRate | ProgramSessionReport | Reps completed vs planned |
| totalTUT | SessionMetrics | Time under tension |

### 6.3 Evaluation Flow

```
Session Completed
  │
  ▼
Load applicable ProgressionRules (by scope: global → program → exercise)
  │
  ▼
For each rule (by priority):
  │
  ├── Evaluate conditions against historical metrics
  │     └── Query: "last N sessions for this exercise/program"
  │
  ├── All conditions met?
  │     ├── YES → Execute action
  │     │     ├── Update next session's parameters
  │     │     ├── Log the change (for audit trail)
  │     │     └── Generate notification for user
  │     └── NO → Skip rule
  │
  ▼
Check reassessment triggers
  │
  ├── Program complete? → Schedule reassessment
  ├── N weeks since last assessment? → Schedule reassessment
  └── Performance plateau detected? → Suggest reassessment
```

### 6.4 Progression History

Every change made by the Progression Engine is logged:

| Field | Type | Description |
|-------|------|-------------|
| id | String | Unique identifier |
| userId | String | FK → User |
| ruleId | String | FK → ProgressionRule that fired |
| sessionId | String | FK → TrainingSession that triggered it |
| field | String | What changed ("weightKg" / "targetReps" / "sets") |
| previousValue | Float | Before |
| newValue | Float | After |
| reason | String | Human-readable explanation |
| appliedAt | DateTime | When the change was applied |

---

## 8. Reassessment System

### 7.1 Triggers

| Trigger | Condition | Priority |
|---------|-----------|----------|
| Program Complete | User finishes their active training program | High |
| Periodic Timer | N weeks since last assessment (configurable, default: 6 weeks) | Medium |
| Progression Rule | A progression rule fires "suggest_reassessment" action | Medium |
| Manual Request | User requests reassessment from the app | Low |

### 7.2 Reassessment Flow

```
Trigger fires
  │
  ▼
Create ReassessmentSchedule (status: pending)
  │
  ▼
Notify user: "It's time for a reassessment"
  │
  ▼
User completes Assessment (existing flow: PAR-Q+ → exercises → BodyScanResult)
  │
  ▼
AssessmentEngine.process() → new BodyScanResult
  │
  ▼
Calculate new UserLevelProfile from BodyScanResult
  │
  ├── Compare with previous profile
  │     ├── Level UP? → Celebration + unlock new programs
  │     ├── Same level? → Adjust focus areas based on new limiting factors
  │     └── Level DOWN? → Safety concern, shift to corrective programs
  │
  ▼
Run Prescription Engine with new profile → Update ActivePlan
  │
  ▼
Mark ReassessmentSchedule as completed
```

---

## 9. User Journey

### 8.1 New User (First Time)

```
Step 1: Sign up / Login
Step 2: PAR-Q+ Pre-screening
          └── Safety questions → pain flags, medical conditions
Step 3: Initial Assessment (BodyScan)
          └── 3-5 assessment movements (overhead squat, lunge, shoulder mobility, forward fold, single leg balance)
          └── AssessmentEngine processes results → BodyScanResult
Step 4: Level Profile Created
          └── User sees their overall level (e.g., "Level 2 - Building")
          └── Domain scores shown (mobility, control, symmetry, safety)
          └── Limiting factors highlighted (e.g., "Shoulder mobility needs work")
Step 5: Prescription Engine Runs
          └── Classification: e.g., WEAKNESS (shoulder mobility at Level 1, rest at Level 3)
          └── Selected programs: Shoulder Mobility Program + Modified Full Body Training
Step 6: ActivePlan Created
          └── Program 1: Shoulder Mobility (4 weeks)
          └── Program 2: Full Body Training Level 2 (4 weeks)
          └── Checkpoint: Reassessment scheduled
Step 7: User starts training
```

### 8.2 Daily Training

```
Step 1: User opens app → sees today's plan
          └── "Week 2, Day 3 — Upper Body Session"
          └── 5 exercises with sets/reps/weight (personalized by level)
Step 2: User starts session
          └── SessionTrainingEngine orchestrates exercises
          └── Real-time pose feedback (state-based form validation)
          └── Per-rep metrics collected (ROM, form, symmetry, stability, tempo)
Step 3: Session complete → Report generated
          └── Performance summary, danger alerts, improvement tips
Step 4: Data uploaded to backend
          └── TrainingSession + SessionMetrics + RepMetrics saved
          └── ProgramSessionReport updated
Step 5: Progression Engine evaluates
          └── "Form score 82% for last 2 sessions, completion 95% → increase weight 2.5kg next session"
          └── User notified: "Great progress! We've increased your weight for next time."
```

### 8.3 Program Completion & Reassessment

```
Step 1: User completes final session of program
Step 2: Program completion report shown (grade, progress charts, insights)
Step 3: Reassessment triggered
          └── "You've completed 4 weeks of training. Let's check your progress!"
Step 4: User completes reassessment
Step 5: New BodyScanResult compared with previous
          └── "Shoulder mobility improved 23%! Overall score up 12 points."
Step 6: New UserLevelProfile calculated
          └── Level UP: "Congratulations! You've reached Level 3"
          └── Or: Same level but new limiting factors identified
Step 7: Prescription Engine runs with new data
Step 8: New ActivePlan with next programs
```

---

## 10. What Exists vs What's New

### Already Built (Current System)

| Component | Status | Location |
|-----------|--------|----------|
| Exercise entity (full configuration) | Complete | Backend: schema + exercises module |
| Pose variants + camera positions | Complete | Backend: schema + exercises module |
| State-based form validation | Complete | Android: FormValidator, JointState |
| Position checks (knee-over-toe, etc.) | Complete | Android: PositionValidator |
| Feedback messages (joint states + errors) | Complete | Backend: FeedbackMessageTemplate/Assignment |
| Rep counting (reps + hold exercises) | Complete | Android: RepCounter, HoldTimer |
| Bilateral exercise support | Complete | Android: TrainingEngine |
| Per-rep metrics (ROM, form, symmetry, stability, tempo, velocity) | Complete | Android: MetricsCalculator → RepMetrics |
| Session metrics (aggregated) | Complete | Android: SessionMetrics |
| Workout entity (super sets/circuits) | Complete | Backend: schema + workouts module |
| Program → Week → Day → Session → Item hierarchy | Complete | Backend: schema + programs module |
| UserProgram enrollment + progress tracking | Complete | Backend: schema + programs module |
| ProgramSessionReport (per-session reports) | Complete | Backend: schema + training-sessions module |
| Unified reports system (Rep→Set→Exercise→Session→Day→Week→Program) | Complete | Backend: reports module |
| Assessment engine (3-layer: Measure→Interpret→Prescribe) | Complete | Android: assessment/engine/ |
| BodyScanResult (domains, regions, hypotheses, safety gates) | Complete | Backend: schema + assessment module |
| Assessment progress comparison | Complete | Backend: assessment module |
| Domain scores (mobility, control, symmetry, safety) | Complete | Android: DomainScoreCalculator |
| Regional assessment with reference norms | Complete | Android: AssessmentEngine + ReferenceNormsProvider |
| Hypothesis generation | Complete | Android: HypothesisGenerator |
| Safety gate evaluation | Complete | Android: SafetyGateEngine |
| Recommendation generation | Complete | Android: RecommendationGenerator |
| PAR-Q+ pre-screening | Complete | Android: PreScreeningActivity |
| Mobile sync (exercises, workouts, programs) | Complete | Backend: mobile-sync module |
| Admin Dashboard CRUD (exercises, workouts, programs) | Complete | Admin-Dashboard |
| Training flow (single exercise + multi-exercise sessions) | Complete | Android: TrainingActivity, SessionTrainingEngine |
| Report UI (performance cards, danger alerts, form analysis) | Complete | Android: report fragments |

### Needs To Be Built (New System)

**Phase 0 — Critical fixes to existing system:**

| Component | Priority | Complexity | Description |
|-----------|----------|------------|-------------|
| **Wire BodyScanResult upload** | Critical | Low | Android calls POST `/assessment` after assessment (endpoint exists, just not wired) |
| **Unify metrics scales** | Critical | Medium | Migrate ProgramSessionReport to Int x10 convention. Single conversion at API boundary. |
| **Remove report active-program lock** | Critical | Low | Reports module allows querying historical programs, not just active enrollment. |
| **Replace Home mock data** | Critical | Low | Wire Home stats to real data from existing APIs. |

**Phase 1-5 — New system components:**

| Component | Phase | Complexity | Description |
|-----------|-------|------------|-------------|
| **Level entity + LevelDefaults** | 1 | Low | 5 level definitions with default training parameters. Seed data. |
| **UserLevelProfile** | 1 | Medium | New DB table + calculation logic from BodyScanResult. |
| **Level Profile API + Mobile UI** | 1 | Medium | Endpoint + screen showing level, domains, limiting factors. |
| **Program prescription metadata** | 2 | Low | Add type, targetDomain, levelRange, contraindications to Program table. |
| **Prescription Engine V1** | 2 | Medium | Classify user → filter programs → return one recommended program. |
| **ProgramWeek.weekType, ProgramDay.dayType** | 2 | Trivial | Add fields to existing tables. |
| **Admin: Program prescription fields** | 2 | Medium | Dashboard UI for editing program type, targetDomain, levelRange. |
| **ActivePlan + ActivePlanProgram** | 3 | Medium | New DB tables + API endpoints for plan management. |
| **ReassessmentSchedule** | 3 | Low | New DB table + trigger logic. |
| **Plan Overview UI** | 3 | Medium | Screen showing active plan with program timeline. |
| **ProgressionRule + ProgressionHistory** | 4 | Medium | New DB tables. Start with 3 rules only. |
| **Progression Engine** | 4 | High | Evaluate rules after each session → adjust parameters → log changes. |
| **ProgramSessionItem — new fields** | 4 | Low | Add targetROM, targetFormScore, progressionRuleId, isPersonalized. |
| **Level-up celebration** | 5 | Low | Animation/modal when user advances a level. |
| **Admin: ProgressionRule management** | 5 | Medium | Dashboard UI for creating/editing progression rules. |

---

## 11. Product Metrics

### North Star Metric

**Percentage of users who achieve measurable improvement within 28 days** (e.g., +5 points bodyScore with statistical confidence).

This directly measures the product's core promise: "real, measurable progress."

### Leading Indicators

| Metric | What It Measures | Target |
|--------|-----------------|--------|
| Weekly session completion rate | Are users following the plan? | > 70% |
| D7 retention | Do users come back after first week? | > 50% |
| D30 retention | Do users stick for a full program cycle? | > 30% |
| Reassessment completion rate | Do users close the feedback loop? | > 60% |
| Average sessions per week | Training frequency | >= 3 |
| Time-to-first-session | How fast from signup to first workout? | < 24 hours |

### Safety Guardrails

| Metric | What It Catches | Alert Threshold |
|--------|----------------|-----------------|
| Pain flag frequency | Users reporting pain during training | > 2 flags in 7 days |
| Form score decline with weight increase | Progression too aggressive | FormScore drops > 15% after weight increase |
| Danger rep percentage | Unsafe movement patterns | > 20% DANGER reps in a session |
| Safety gate override attempts | Users ignoring restrictions | Any attempt to bypass blocked exercises |

> These metrics should be tracked from Phase 0 onwards. They validate whether the system is actually helping users, not just technically working.

---

## 12. Implementation Phases

### Phase 0: Close the Loop (Critical Foundation)

**Goal:** Fix the broken assessment → program lifecycle. Without this, nothing else works.

**Tasks:**
1. **Wire BodyScanResult upload:** Android `AssessmentSessionActivity` → POST `/assessment` (endpoint already exists, just not called)
2. **Fetch previous assessment:** Use GET `/assessment/latest` for comparison in `AssessmentResultActivity`
3. **Unify metrics scales:** Migrate `ProgramSessionReport.avgFormScore` and `avgAccuracy` to Int x10 convention (matching `SessionMetrics`). Update reports API to normalize consistently.
4. **Remove report active-program lock:** Update reports module to allow querying historical programs (not just `isActive: true`). Add `programId` filter without requiring active enrollment.
5. **Connect assessment result to CTA:** After assessment, show "Your recommended program" with a direct enrollment button (even if manually curated for now)
6. **Replace mock data:** Wire Home screen stats to real user data from existing APIs (`/mobile/sessions`, `/mobile/user-programs`)

**Depends on:** Nothing — uses existing endpoints and infrastructure.

**Why this is Phase 0:** The critic correctly identified that the core lifecycle (Assess → Prescribe → Train → Reassess) is broken at the most basic level. Building Level System or Prescription Engine on top of a broken loop is building on sand.

### Phase 1: Level System (Vertical Slice)

**Goal:** Introduce user levels — simple, visible, motivating. Not the full ActivePlan yet.

**Tasks:**
1. Create Level seed data (5 levels with LevelDefaults)
2. Add `UserLevelProfile` table to schema (store as JSON fields for domainLevels, regionLevels, limitingFactors)
3. Build `scoreToLevel()` as a shared utility (backend service, used everywhere)
4. Build level calculation service: `BodyScanResult` → `UserLevelProfile`
5. Auto-calculate level profile after assessment upload (hook into POST `/assessment`)
6. Build Level Profile API endpoint: GET `/mobile/level-profile`
7. Build mobile screen: "Your Level" — overall level + domain radar + limiting factor highlight + "What's next" CTA
8. Update mobile sync to include level data

**Depends on:** Phase 0 (assessment upload must work)

### Phase 2: Program Enhancement + Prescription V1

**Goal:** Programs become prescribable. Start simple — one main program recommendation.

**Tasks:**
1. Add prescription metadata to Program table: `type`, `targetDomain`, `targetRegions`, `levelRange`, `contraindications`
2. Add `weekType` to ProgramWeek, `dayType` to ProgramDay (trivial schema additions)
3. Build Prescription Engine V1 — simplified:
   - Classify user (the 5 priority categories)
   - Filter matching programs from catalog
   - Return **one** recommended program (highest priority match)
   - If no match → fallback to default program for user's level
4. Build prescription API: POST `/mobile/prescription/recommend` → returns recommended program
5. Wire into post-assessment flow: Assessment → Level Profile → "Here's your program" → Enroll
6. Update admin dashboard: add program type, targetDomain, levelRange fields to program editor

**Depends on:** Phase 1 (needs UserLevelProfile for classification)

> V1 deliberately does NOT include ActivePlan, multi-program scheduling, or automatic transitions. The user gets one program at a time, recommended intelligently. This proves the concept before adding complexity.

### Phase 3: ActivePlan + Lifecycle

**Goal:** Support program sequences and automatic transitions.

**Tasks:**
1. Create `ActivePlan` + `ActivePlanProgram` tables
2. Build ActivePlan API: GET plan, transition between programs, enhanced today's plan
3. Update enrollment to create ActivePlan (migrate from `UserProgram.isActive` boolean)
4. Support program chaining: when program completes → suggest next (using `nextProgramId` or re-run prescription)
5. Create `ReassessmentSchedule` table
6. Build reassessment triggers: program completion, periodic timer (configurable)
7. Wire reassessment into ActivePlan transitions
8. Build plan overview UI (program timeline showing completed → active → upcoming)

**Depends on:** Phase 2 (needs prescription to populate plans)

### Phase 4: Progression Engine

**Goal:** Automatically adjust training parameters based on real performance data.

**Tasks:**
1. Create `ProgressionRule` + `ProgressionHistory` tables
2. Start with **3 rules only** (proven, simple, high-impact):
   - **Weight increase:** avgFormScore >= 75 for last 2 sessions AND completionRate >= 90% → +2.5kg
   - **Rep increase:** avgFormScore >= 80 for full week AND avgROM >= 95% of target → +2 reps
   - **Deload safety:** avgFormScore < 60 for last 2 sessions → -2.5kg + notification
3. Add `targetROM`, `targetFormScore`, `progressionRuleId`, `isPersonalized` to ProgramSessionItem
4. Build Progression Engine service (evaluate → act → log)
5. Wire into session completion flow (after `ProgramSessionReport` is saved)
6. Add user notifications for progression changes
7. Build admin UI for rule management (later — start with seed data)

**Depends on:** Phase 1 (LevelDefaults), Phase 3 (ActivePlan context for which program to adjust)

> Starting with exactly 3 rules keeps the system predictable. Add more rules only after validating these work with real users.

### Phase 5: Polish + Intelligence

**Goal:** Refine based on real usage data. Close remaining gaps.

**Tasks:**
1. Build level comparison UI (before/after reassessment with animated transitions)
2. Build level-up celebration flow
3. Analyze progression rule effectiveness from ProgressionHistory data
4. Tune level thresholds based on actual user distribution
5. Add more progression patterns if data supports it (undulating periodization, auto-regulation)
6. Build admin analytics: program effectiveness, user progression trends, rule hit rates
7. Seed comprehensive program library with full prescription metadata
8. Evaluate whether AI/ML layer would improve program selection (only after sufficient data — thousands of completed program cycles)

**Depends on:** Phase 4 + real user data

---

## Appendix: Key Design Decisions

### Why Rule-Based, Not AI?

1. **Fitness programming is well-studied** — established protocols exist for every scenario
2. **Safety decisions must be predictable** — a therapist can audit and approve the rules
3. **No training data yet** — AI needs thousands of user journeys to learn from
4. **Debuggable** — when something goes wrong, you can trace exactly which rule fired and why
5. **AI can be added later** — as an optimization layer on top of solid rules, not as the foundation

### Why Levels are Multi-Dimensional but Show One Number?

- **User sees:** "Level 3" — simple, motivating, gamified
- **System uses:** mobilityLevel=2, strengthLevel=4, shoulderLevel=2 — for precise prescription
- **This avoids:** overwhelming the user while still making smart decisions

### Why Assessment Stays Standalone (Not a Program Type)?

1. Assessment has its own unique flow (PAR-Q+ → specific test movements → BodyScanResult)
2. Assessment movements are evaluated differently (measurement, not training)
3. Mixing assessment into the program hierarchy would complicate the data model
4. Assessment results feed INTO the program system — they're an input, not a program type

### Why ActivePlan Instead of Just UserProgram?

1. UserProgram is a 1:1 enrollment — user in one program
2. ActivePlan is a schedule of ordered programs with transitions
3. A user might need: Therapeutic → Training → Reassessment → Next Training
4. ActivePlan manages this sequence; UserProgram tracks progress within each program

### Why Phase 0 Before Everything?

The assessment system computes BodyScanResult on Android but doesn't upload it to the backend. The reports module only queries active enrollments. Metrics scales are inconsistent between tables. Building Levels, Prescription, or Progression on top of these gaps would create cascading problems. Phase 0 fixes the foundation so everything built on top is reliable.

### Why Start Progression Engine with Only 3 Rules?

1. Fewer rules = easier to validate correctness with real users
2. The 3 rules (weight up, rep up, deload) cover 90% of progression scenarios
3. Complex rules (undulating periodization, auto-regulation) need usage data to tune properly
4. Adding rules is trivial once the engine is built — removing bad rules after users relied on them is not
