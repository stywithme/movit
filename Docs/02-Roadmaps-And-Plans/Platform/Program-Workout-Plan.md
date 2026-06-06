# Comprehensive Plan: Programs - Workouts - Exercises Architecture

> **Naming (2026-06):** Training-domain `Session` was removed. Use **PlannedWorkout** (program block), **WorkoutTemplate** (catalog), **WorkoutExecution** (per-exercise run). See [`Workout-Domain-Naming.md`](../../00-Active-Reference/Contracts/Workout-Domain-Naming.md).

> **Vision**: Build an AI-powered fitness platform that guides beginners through safe, correct exercise execution within a structured, long-term training program. Simplicity, clarity, and safety are the core principles.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Data Model — Backend Schema](#2-data-model--backend-schema)
3. [Alternating in Exercise (New Concept)](#3-alternating-in-exercise-new-concept)
4. [Program Structure — Weeks, Days, Planned Workouts](#4-program-structure--weeks-days-planned-workouts)
5. [Simplified Workout (Template)](#5-simplified-workout-template)
6. [Exercise Enhancement — Sets, Weight, Alternating](#6-exercise-enhancement--sets-weight-alternating)
7. [Mobile App Flow — Complete User Experience](#7-mobile-app-flow--complete-user-experience)
8. [Training Engine Redesign](#8-training-engine-redesign)
9. [Rest & Transition Screens](#9-rest--transition-screens)
10. [Reporting Architecture](#10-reporting-architecture)
11. [Admin Panel Changes](#11-admin-panel-changes)
12. [API & Sync Changes](#12-api--sync-changes)
13. [Migration Strategy](#13-migration-strategy)
14. [Implementation Phases](#14-implementation-phases)

---

## 1. Architecture Overview

### Current Architecture (Problems)
```
Workout (complex: type + executionMode + rounds)
  └── WorkoutExercise (flat, no sets)
        └── Exercise
```
- Workout has many types (CIRCUIT, SUPER_SET, AMRAP, EMOM) + execution modes (SEQUENTIAL, ALTERNATING)
- No "Program" concept — no long-term planning
- Alternating is at Workout level — limiting and complex
- No Sets per exercise — only round-level repetition
- No weight tracking
- Training breaks per exercise (sequential mode opens new Activity each time)

### New Architecture (Target)
```
Program
  └── Week
        └── Day
              └── WorkoutExecution ("Morning", "Evening", etc.)
                    └── PlannedWorkoutItem (Exercise + Sets + Rest)

Workout (Template — reusable preset, can be imported into any planned workout)
  └── WorkoutExercise (with Sets config)

Exercise (enhanced — with Alternating Variants)
  └── PoseVariant (camera angle)
  └── AlternatingGroup (NEW — multiple joint configs for alternating)
```

### Core Principles
1. **Program** = The "big picture" — a complete training plan for weeks/months
2. **Week** = Milestone unit — for tracking progress, copying plans, generating weekly reports
3. **Day** = Container for Planned Workouts
4. **WorkoutExecution** = The actual training unit — a named period ("Morning", "Pre-sleep") containing exercises in order
5. **Workout** = A reusable TEMPLATE (preset) — importable into any Planned Workout
6. **Exercise** = The atomic unit of training — enhanced with Sets and Alternating support

---

## 2. Data Model — Backend Schema

### New Models

```prisma
// ═══════════════════════════════════════════
//  PROGRAM LAYER
// ═══════════════════════════════════════════

model Program {
  id              String    @id @default(uuid())
  name            Json      // { en: "Full Body 4-Week", ar: "..." }
  description     Json?
  slug            String    @unique
  coverImageUrl   String?
  durationWeeks   Int       // e.g., 4
  difficulty      String    @default("beginner") // beginner | intermediate | advanced
  isDefault       Boolean   @default(false) // system-provided program
  isPublished     Boolean   @default(false)
  tags            Json?     // ["weight-loss", "muscle-building", ...]
  createdBy       String?   // admin who created it
  createdAt       DateTime  @default(now())
  updatedAt       DateTime  @updatedAt

  weeks           ProgramWeek[]
  userPrograms    UserProgram[]
}

model ProgramWeek {
  id          String   @id @default(uuid())
  programId   String
  weekNumber  Int      // 1, 2, 3, 4
  name        Json?    // optional: { en: "Foundation Week" }
  description Json?    // optional motivational message
  sortOrder   Int      @default(0)

  program     Program  @relation(fields: [programId], references: [id], onDelete: Cascade)
  days        ProgramDay[]

  @@unique([programId, weekNumber])
}

model ProgramDay {
  id          String   @id @default(uuid())
  weekId      String
  dayNumber   Int      // 1-7
  isRestDay   Boolean  @default(false)
  name        Json?    // optional: { en: "Chest & Back Day" }

  week        ProgramWeek @relation(fields: [weekId], references: [id], onDelete: Cascade)
  plannedWorkouts    WorkoutExecution[]

  @@unique([weekId, dayNumber])
}

model WorkoutExecution {
  id          String   @id @default(uuid())
  dayId       String
  name        Json     // { en: "Morning", ar: "صباحا" } or { en: "Before Bed" }
  sortOrder   Int      @default(0)

  day         ProgramDay @relation(fields: [dayId], references: [id], onDelete: Cascade)
  items       PlannedWorkoutItem[]
}

model PlannedWorkoutItem {
  id                  String   @id @default(uuid())
  plannedWorkoutId           String
  sortOrder           Int      @default(0)

  // Either exercise or rest (mutually exclusive)
  type                String   // "exercise" | "rest"

  // ── Exercise fields (when type = "exercise") ──
  exerciseId          String?
  sets                Int?     @default(1)
  targetReps          Int?     // per set
  targetDuration      Int?     // per set (seconds) — for hold exercises
  restBetweenSetsMs   Int?     @default(30000) // rest between sets of same exercise
  weightKg            Float?   // default weight (can be overridden per set)
  weightPerSet        Json?    // optional: [12.5, 15, 17.5] for progressive sets
  notes               Json?    // { en: "Keep back straight" }

  // ── Source tracking ──
  sourceWorkoutId     String?  // if imported from a Workout template
  isModified          Boolean  @default(false) // if user modified after import

  // ── Rest fields (when type = "rest") ──
  restDurationMs      Int?     // rest duration between exercises

  planned workout             WorkoutExecution @relation(fields: [plannedWorkoutId], references: [id], onDelete: Cascade)
  exercise            Exercise? @relation(fields: [exerciseId], references: [id])
}


// ═══════════════════════════════════════════
//  USER PROGRAM (Enrollment & Customization)
// ═══════════════════════════════════════════

model UserProgram {
  id          String    @id @default(uuid())
  userId      String
  programId   String?   // null if fully custom
  name        Json?     // custom name override
  startDate   DateTime  @default(now())
  isActive    Boolean   @default(true)

  user        User      @relation(fields: [userId], references: [id], onDelete: Cascade)
  program     Program?  @relation(fields: [programId], references: [id])

  // User-level overrides stored as JSON patches
  customizations Json?  // overrides for specific days/planned-workouts/items

  progress    UserProgramProgress[]
}

model UserProgramProgress {
  id              String    @id @default(uuid())
  userProgramId   String
  weekNumber      Int
  dayNumber       Int
  plannedWorkoutId       String?
  completedAt     DateTime?
  status          String    @default("pending") // pending | in_progress | completed | skipped

  userProgram     UserProgram @relation(fields: [userProgramId], references: [id], onDelete: Cascade)

  @@unique([userProgramId, weekNumber, dayNumber])
}
```

### Simplified Workout Model (Template)

```prisma
// ═══════════════════════════════════════════
//  WORKOUT (Reusable Template)
// ═══════════════════════════════════════════

model Workout {
  id                     String    @id @default(uuid())
  name                   Json
  description            Json?
  slug                   String    @unique
  coverImageUrl          String?
  difficulty             String    @default("beginner")
  isPublished            Boolean   @default(false)
  estimatedDurationMin   Int?      // calculated from exercises
  tags                   Json?     // ["upper-body", "no-equipment", ...]
  createdBy              String?
  createdAt              DateTime  @default(now())
  updatedAt              DateTime  @updatedAt

  // ── REMOVED: type, executionMode, rounds, repsPerSwitch,
  //    restBetweenSwitchMs, restBetweenExercisesMs, restBetweenRoundsMs ──

  exercises              WorkoutExercise[]
}

model WorkoutExercise {
  id                  String   @id @default(uuid())
  workoutId           String
  exerciseId          String
  sortOrder           Int      @default(0)

  // ── NEW: Sets config ──
  sets                Int      @default(1)
  targetReps          Int?     // per set
  targetDuration      Int?     // per set (seconds)
  restBetweenSetsMs   Int      @default(30000)
  restAfterExerciseMs Int      @default(60000) // rest before next exercise
  weightKg            Float?   // default weight per set
  weightPerSet        Json?    // [10, 12.5, 15] for progressive overload

  // ── Enhanced ──
  variantIndex        Int      @default(0)
  difficulty          String   @default("beginner")
  notes               Json?

  workout             Workout  @relation(fields: [workoutId], references: [id], onDelete: Cascade)
  exercise            Exercise @relation(fields: [exerciseId], references: [id])
}
```

### Enhanced Exercise Model

```prisma
model Exercise {
  // ... existing fields remain ...

  // ── NEW: Alternating configuration ──
  isAlternating         Boolean  @default(false)
  alternatingConfig     Json?
  // Structure:
  // {
  //   "label": { "en": "Squat + Push-up Combo", "ar": "..." },
  //   "switchEvery": 1,  // switch variant every N reps
  //   "variants": [
  //     {
  //       "label": { "en": "Squat Phase", "ar": "..." },
  //       "variantIndex": 0  // maps to PoseVariant
  //     },
  //     {
  //       "label": { "en": "Push-up Phase", "ar": "..." },
  //       "variantIndex": 1
  //     }
  //   ]
  // }

  // ... existing relations ...
  plannedWorkoutItems          PlannedWorkoutItem[]
}
```

---

## 3. Alternating in Exercise (New Concept)

### What Changed
**Before**: Alternating was at Workout level — the workout alternated between DIFFERENT exercises.
**After**: Alternating is at Exercise level — a single exercise can have multiple movement phases with different joint configurations.

### How It Works

#### Admin: Exercise Creation (Joints Step)

**Current Flow**:
1. Select Camera Positions → creates PoseVariants
2. Configure Joints → same joints applied to all PoseVariants

**New Flow**:
1. Select Camera Positions → creates PoseVariants
2. Configure Primary Joints (Mode A)
3. **[NEW]** "Add Alternating Mode" button
4. Configure Alternating Joints (Mode B, C, ...)
5. Each mode gets its own:
   - Tracked joints with ranges
   - Camera position (can be different)
   - Position checks
   - Reference image

#### Data Structure

```typescript
// Exercise with alternating
{
  name: { en: "Squat to Push-up Combo" },
  isAlternating: true,
  alternatingConfig: {
    switchEvery: 1,  // alternate every rep
    variants: [
      {
        label: { en: "Squat" },
        variantIndex: 0,    // PoseVariant[0] — side camera, knee/hip joints
      },
      {
        label: { en: "Push-up" },
        variantIndex: 1,    // PoseVariant[1] — side camera, elbow/shoulder joints
      }
    ]
  },
  poseVariants: [
    {
      // Variant 0: Squat
      cameraPosition: "side",
      trackedJointsConfig: [
        { joint: "left_knee", role: "primary", upRange: {...}, downRange: {...} },
        { joint: "right_knee", role: "primary", ... }
      ]
    },
    {
      // Variant 1: Push-up
      cameraPosition: "side",
      trackedJointsConfig: [
        { joint: "left_elbow", role: "primary", upRange: {...}, downRange: {...} },
        { joint: "right_elbow", role: "primary", ... }
      ]
    }
  ]
}
```

#### Training Behavior (Mobile)

```
Alternating Exercise (switchEvery: 1):
  Rep 1 → Mode A (Squat joints active)
  Rep 2 → Mode B (Push-up joints active)
  Rep 3 → Mode A
  Rep 4 → Mode B
  ...until target reps reached

Alternating Exercise (switchEvery: 3):
  Reps 1-3 → Mode A
  Reps 4-6 → Mode B
  Reps 7-9 → Mode A
  ...
```

#### Camera Handling
- If both variants use the same camera position → seamless hot-swap (just swap tracked joints)
- If variants need different camera positions → show brief transition screen: "Turn to face [direction]"
- The existing `WorkoutTrainingEngine` hot-swap mechanism is perfectly suited for this

#### Key Advantage
This replaces the old Workout-level alternating with a much more flexible Exercise-level concept:
- Can alternate between ANY joint configurations
- Can have 2+ alternating modes (not just 2)
- Exercise is self-contained — no dependency on workout structure
- Same exercise can be used in any workout/program without special handling

---

## 4. Program Structure — Weeks, Days, Planned Workouts

### Hierarchy

```
Program: "Full Body 4-Week Challenge"
│
├── Week 1: "Foundation Week"
│   ├── Day 1: "Upper Body Focus"
│   │   ├── Planned Workout: "Morning Workout"
│   │   │   ├── Exercise: Push-ups (3 sets × 10 reps, rest 60s)
│   │   │   ├── [Rest: 90s]
│   │   │   ├── Exercise: Squats (3 sets × 12 reps, rest 45s)
│   │   │   ├── [Rest: 60s]
│   │   │   └── Exercise: Plank (2 sets × 30s hold, rest 30s)
│   │   │
│   │   └── Planned Workout: "Evening Stretch"
│   │       ├── Exercise: Cobra Stretch (1 set × 20s hold)
│   │       └── Exercise: Chest Stretch (1 set × 20s hold)
│   │
│   ├── Day 2: "Rest Day" (isRestDay: true)
│   ├── Day 3: "Lower Body Focus"
│   │   └── Planned Workout: "Default"
│   │       └── [Imported from Workout Template: "Leg Day Basic"]
│   │           ├── Lunges (3 × 10, rest 45s) — modified: 2 × 8
│   │           ├── [Rest: 60s]
│   │           └── Calf Raises (3 × 15, rest 30s)
│   │
│   ├── Day 4: "Rest Day"
│   ├── Day 5: "Full Body"
│   │   └── Planned Workout: "Morning"
│   │       └── ...exercises...
│   ├── Day 6: "Active Recovery"
│   └── Day 7: "Rest Day"
│
├── Week 2: (copy of Week 1 with increased intensity)
│   └── ... (same structure, higher reps/sets)
│
├── Week 3: (different focus)
│   └── ...
│
└── Week 4: "Peak Week"
    └── ...
```

### Week as Milestone
- **Reports**: Weekly summary report aggregating all planned workouts
- **Copy**: "Copy Week 1 to Week 3" — duplicate entire week structure
- **Progress**: Weekly completion percentage displayed in calendar view
- **Adaptation**: User can modify exercises/sets for specific weeks without affecting others

### Day Structure
- A day can have **multiple Planned Workouts** (morning, evening, etc.)
- Each planned workout is a **standalone training unit** — user can complete planned workouts independently
- Rest days are clearly marked — no exercises required
- User can **name planned workouts freely**: "Morning", "Before Bed", "Lunch Break", "During Work"

### Importing Workouts into planned workouts
When user adds a Workout (template) to a Planned Workout:
1. All exercises from the Workout are expanded into the planned workout
2. Each exercise retains its sets, reps, rest, weight config
3. User can modify any exercise LOCALLY (only in this planned workout)
4. Original Workout template is NOT affected
5. `sourceWorkoutId` tracks where the exercises came from
6. `isModified` flag marks if user changed anything

---

## 5. Simplified Workout (Template)

### What Changed
**Before**: Workout was complex with `type` (CIRCUIT, SUPER_SET, AMRAP, EMOM), `executionMode` (SEQUENTIAL, ALTERNATING), and `rounds`.

**After**: Workout is a **simple, reusable template** — just an ordered list of exercises with their Sets and Rest configurations.

### Why This Works
The old complex types are now achievable through simple arrangement:

| Old Type | New Equivalent |
|----------|---------------|
| CIRCUIT (3 rounds) | Add exercises to planned workout, repeat the whole set 3 times using Sets per exercise |
| SUPER_SET | Place 2 exercises back-to-back with 0s rest between them |
| ALTERNATING | Use Exercise-level alternating (Section 3) |
| AMRAP | Set workout run time limit, no fixed rep targets |
| EMOM | Set exercises with 1-minute intervals (future enhancement) |

### Workout Template Structure

```
Workout Template: "Quick Upper Body"
├── Exercise: Push-ups
│   ├── Sets: 3
│   ├── Target: 12 reps/set
│   ├── Rest between sets: 30s
│   ├── Weight: none
│   └── Rest after: 60s
│
├── Exercise: Shoulder Press
│   ├── Sets: 3
│   ├── Target: 10 reps/set
│   ├── Rest between sets: 45s
│   ├── Weight: 5kg (default)
│   ├── Weight per set: [5, 7.5, 10] (progressive)
│   └── Rest after: 60s
│
└── Exercise: Plank Hold
    ├── Sets: 2
    ├── Target: 30s hold/set
    ├── Rest between sets: 20s
    └── Rest after: 0s (last exercise)
```

---

## 6. Exercise Enhancement — Sets, Weight, Alternating

### Sets per Exercise
Each exercise in a planned workout/workout has:
- **sets**: Number of sets (e.g., 3)
- **targetReps**: Reps per set (for UP_DOWN exercises)
- **targetDuration**: Duration per set in seconds (for HOLD exercises)
- **restBetweenSetsMs**: Rest time between sets of the same exercise

### Training Flow for Sets

```
Exercise: Push-ups (3 sets × 10 reps)
│
├── Set 1 of 3
│   ├── [Weight Dialog if applicable]
│   ├── Camera → AI counts 10 reps
│   ├── Set Complete!
│   └── [Rest 30s — countdown screen]
│
├── Set 2 of 3
│   ├── Camera → AI counts 10 reps
│   ├── Set Complete!
│   └── [Rest 30s — countdown screen]
│
├── Set 3 of 3 (Final Set)
│   ├── Camera → AI counts 10 reps
│   └── Exercise Complete!
│
└── [Rest 60s before next exercise]
```

### Weight Management

#### In Program/Workout Configuration (Admin/User)
```
Exercise: Shoulder Press
├── Default Weight: 10kg
├── Per-Set Weight: [10, 12.5, 15] (optional progressive overload)
└── Weight Type: kg | lb (user preference)
```

#### During Live Training (Mobile)
Before each exercise starts:
1. If exercise has weight → show **Weight Confirmation Dialog**:
   ```
   ┌─────────────────────────────────┐
   │        Shoulder Press           │
   │                                 │
   │   Recommended Weight: 10 kg    │
   │                                 │
   │   ┌─────────────────────┐      │
   │   │      10 kg          │      │  ← editable
   │   └─────────────────────┘      │
   │                                 │
   │   [ Use Recommended ]          │
   │   [     Start Set     ]        │
   │                                 │
   └─────────────────────────────────┘
   ```
2. User can adjust weight for THIS planned workout only
3. Actual weight used is recorded in the planned workout report
4. If next set has different planned weight → show dialog again with new recommendation

### Alternating at Exercise Level
(Detailed in Section 3)
- Exercise defines its own alternating variants
- Each variant has its own joints, camera, position checks
- Training engine handles mode switching automatically
- User sees: "Squat Phase" → "Push-up Phase" → "Squat Phase" → ...

---

## 7. Mobile App Flow — Complete User Experience

### 7.1 Navigation Architecture

```
Bottom Navigation
├── Home (Weekly overview + Today's plan)
├── Programs (Browse & manage programs)
├── Exercises (Browse individual exercises)
├── Reports (Training history & analytics)
└── Profile (Settings)
```

### 7.2 Home Screen (Enhanced)

```
┌─────────────────────────────────┐
│  Weekly Goal           2/4  ✏️  │
│  [1] [2] [3] [4] [5] [6] [7]  │
│       ●   ●                     │ ← completed days highlighted
├─────────────────────────────────┤
│  👋 Good morning!              │
│  Your workout is ready         │
│                                 │
│  ┌─────────────────────────┐   │
│  │  TODAY — Day 3           │   │
│  │  "Lower Body Focus"     │   │
│  │                          │   │
│  │  Morning Workout         │   │
│  │  5 exercises · 25 min    │   │
│  │                          │   │
│  │  [    Start Training   ] │   │
│  └─────────────────────────┘   │
│                                 │
│  Evening Stretch               │
│  2 exercises · 8 min           │
│  [Start]                       │
│                                 │
├─────────────────────────────────┤
│  Active Program                 │
│  "Full Body 4-Week Challenge"  │
│  Week 2 · Day 3 · 35% done    │
│  ████████░░░░░░░░░░░░░░        │
│  [View Program →]              │
└─────────────────────────────────┘
```

### 7.3 Program Calendar Screen

Inspired by example app — shows weeks with day progression:

```
┌─────────────────────────────────┐
│ ← Full Body 4-Week Challenge   │
│                                 │
│  ┌───────────────────────┐     │
│  │ [Hero Image]          │     │
│  │ 28 Days Left     12%  │     │
│  │ ████░░░░░░░░░░░░░░░░  │     │
│  └───────────────────────┘     │
│                                 │
│  💪 Kick off your fitness      │
│  journey with energy!          │
│                                 │
│  ── Week 1 ──────── 3/7 ──    │
│  ✅ ✅ ✅ ④ ⑤ ⑥ ⑦ 🏆       │
│                                 │
│  ── Week 2 ──────── 0/7 ──    │
│  ① ② ③ ④ ⑤ ⑥ ⑦              │
│                                 │
│  ── Week 3 ──────── 0/7 ──    │
│  ① ② ③ ④ ⑤ ⑥ ⑦              │
│                                 │
│  ── Week 4 ──────── 0/7 ──    │
│  ① ② ③ ④ ⑤ ⑥ ⑦              │
│                                 │
│  [        GO          ]        │
└─────────────────────────────────┘
```

- Each day circle: tap to see that day's exercises
- Completed days: ✅ with checkmark
- Current day: highlighted/pulsing
- Rest days: grayed out with "R" or rest icon
- Week milestone: trophy icon at end of each week

### 7.4 Day Detail Screen

Shows planned workouts for a specific day:

```
┌─────────────────────────────────┐
│ ← Day 3 — Lower Body      ⋮   │
│                                 │
│  ── Morning Workout ──────     │
│                                 │
│  ☰  [🏃] Lunges           ⇄   │
│        3 sets × 10 reps        │
│        Weight: 5kg              │
│                                 │
│  ☰  [🏋] Squats           ⇄   │
│        3 sets × 12 reps        │
│                                 │
│  ☰  [🦵] Calf Raises      ⇄   │
│        3 sets × 15 reps        │
│                                 │
│  ☰  [🧘] Cobra Stretch    ⇄   │
│        1 set × 00:20           │
│                                 │
│  ☰  [🧘] Chest Stretch    ⇄   │
│        1 set × 00:20           │
│                                 │
│              [📋 Adjust]        │
│                                 │
│  ┌───────────┐ ┌────────────┐  │
│  │  Restart   │ │ Continue   │  │
│  │            │ │ 18% done   │  │
│  └───────────┘ └────────────┘  │
│                                 │
│  ── Evening Stretch ───────    │
│  (separate planned workout card)       │
└─────────────────────────────────┘
```

**Features**:
- ☰ Drag handle: reorder exercises
- ⇄ Replace button: swap with similar exercise (shows bottom sheet with alternatives filtered by same muscle group, tagged as Similar/Easier/Harder)
- [Adjust] button: enters edit mode (modify reps/sets per exercise using +/- controls)
- Continue: resumes from last completed exercise
- Restart: starts from beginning
- Multiple planned workouts shown as separate sections

### 7.5 Adjust/Edit Mode

```
┌─────────────────────────────────┐
│ ← Edit plan                ⋮   │
│                                 │
│  ☰  [🏃] Lunges           ⇄   │
│        — [ 10 ] +   reps       │
│        — [  3 ] +   sets       │
│                                 │
│  ☰  [🏋] Squats           ⇄   │
│        — [ 12 ] +   reps       │
│        — [  3 ] +   sets       │
│                                 │
│  ☰  [🧘] Cobra Stretch    ⇄   │
│        — [00:20] +             │
│        — [  1  ] +   sets      │
│                                 │
│  [+ Add Exercise]              │
│  [+ Add Rest Period]           │
│                                 │
│  [     Save Changes     ]      │
└─────────────────────────────────┘
```

### 7.6 Replace Exercise Bottom Sheet

```
┌─────────────────────────────────┐
│  Current: Incline Push-ups     │
│  Replace it with...        ✕   │
│                                 │
│  🔽 Filtered (28)     Clear    │
│  [Chest ✕]                      │
│                                 │
│  🔍 Search exercises           │
│                                 │
│  ── Recommended ──             │
│  [img] Push-ups        Similar │
│  [img] Burpees         Similar │
│  [img] Punches         Easier  │
│  [img] Wall Push-ups   Easier  │
│  [img] Decline Push-up Harder  │
└─────────────────────────────────┘
```

### 7.7 Pre-Exercise Screens

#### Weight Confirmation (if exercise uses weight)

```
┌─────────────────────────────────┐
│                                 │
│        [Exercise Image]        │
│                                 │
│        Shoulder Press          │
│        Set 1 of 3              │
│                                 │
│  Recommended:  10 kg           │
│                                 │
│       ┌──── ⊖  10  ⊕ ────┐    │
│       │       kg           │    │
│       └────────────────────┘    │
│                                 │
│  [   Use Recommended   ]      │
│  [      Start Set      ]      │
│                                 │
└─────────────────────────────────┘
```

#### Ready Screen (no weight)

```
┌─────────────────────────────────┐
│                                 │
│        [Exercise GIF/Image]    │
│                                 │
│        Push-ups                │
│        Set 2 of 3              │
│        Target: 10 reps         │
│                                 │
│  Position yourself in front    │
│  of the camera                 │
│                                 │
│  [      Start Set      ]      │
│                                 │
└─────────────────────────────────┘
```

### 7.8 Training Screen (During Exercise)

Same as current TrainingActivity — camera feed with:
- Skeleton overlay
- Rep counter
- Set indicator (Set 2/3)
- Form feedback (joint state colors + messages)
- Timer (for hold exercises)

**NEW indicators**:
- Set progress: "Set 2 of 3"
- Weight indicator (if applicable): "10 kg"
- Alternating mode label: "Squat Phase" / "Push-up Phase"

### 7.9 Rest Screen (Between Sets / Between Exercises)

```
┌─────────────────────────────────┐
│                                 │
│        [Next Exercise Image]   │
│        (or same exercise for   │
│         between-set rest)      │
│                                 │
│  NEXT 2/5                      │
│  INCLINE PUSH-UPS  ℹ    × 6   │
│                                 │
│          REST                  │
│                                 │
│         00:12                  │
│                                 │
│    [ Edit Rest Time ]          │
│                                 │
│  ┌──────────┐ ┌──────────────┐ │
│  │   +20s   │ │    Skip      │ │
│  └──────────┘ └──────────────┘ │
│                                 │
└─────────────────────────────────┘
```

**Features**:
- Shows preview of NEXT exercise (image + name + target)
- Large countdown timer
- [Edit Rest Time]: change rest duration for this instance
- [+20s]: extend rest
- [Skip]: skip rest and start immediately
- Auto-start when countdown reaches 0
- Between-set rest: shows "Set 2 of 3 coming up"
- Between-exercise rest: shows next exercise info

### 7.10 Exit Before Finish Dialog

```
┌─────────────────────────────────┐
│                                 │
│        [Exercise Image]        │
│                                 │
│         💪                     │
│  2 exercises completed.        │
│                                 │
│  Every rep brings you          │
│  closer to the goal!           │
│                                 │
│                                 │
│  [   Keep exercising    ]      │ ← primary (highlighted)
│                                 │
│  [ Restart this exercise ]     │
│                                 │
│       Do it later              │ ← text link
│                                 │
└─────────────────────────────────┘
```

### 7.11 Workout Complete → Report

```
┌─────────────────────────────────┐
│                                 │
│       🎉 Workout Complete!     │
│                                 │
│  Morning Workout               │
│  ─────────────────────         │
│  Total Time:    18:34          │
│  Exercises:     5/5            │
│  Total Sets:    14             │
│  Total Reps:    156            │
│  Avg Accuracy:  87%            │
│  Calories:      ~145 kcal      │
│                                 │
│  ── Exercise Breakdown ──      │
│                                 │
│  Push-ups          ⭐ 92%      │
│  3/3 sets · 30 reps            │
│  [View Details →]              │
│                                 │
│  Squats            ⭐ 85%      │
│  3/3 sets · 36 reps            │
│  [View Details →]              │
│                                 │
│  ...                           │
│                                 │
│  [    View Full Report    ]    │
│  [    Back to Program     ]    │
└─────────────────────────────────┘
```

---

## 8. Training Engine Redesign

### Current Problems
1. Sequential mode launches NEW `TrainingActivity` for each exercise → breaks flow
2. `WorkoutRunner` manages exercise progression OUTSIDE the training screen
3. No set concept — only rounds at workout level
4. Alternating is at workout level, not exercise level

### New Architecture

```
TrainingActivity (Single instance — never recreated during workout run)
│
├── WorkoutTrainingEngine (NEW — replaces WorkoutRunner + WorkoutTrainingEngine)
│   ├── Manages the ENTIRE planned workout
│   ├── Handles: exercise sequence → sets → rest → next exercise
│   ├── Maintains single camera stream throughout
│   └── State Machine:
│
│       IDLE → PRE_EXERCISE → TRAINING → SET_REST → 
│       EXERCISE_REST → PRE_EXERCISE → TRAINING → ...
│       → SESSION_COMPLETE
│
├── TrainingEngine (per set — recycled, not recreated)
│   ├── Handles one set of one exercise
│   ├── Counts reps / tracks duration
│   ├── Monitors form with AI
│   └── Reports set metrics on completion
│
└── Camera Pipeline (always running)
    ├── CameraManager: initialized once
    ├── PoseDetector: always detecting
    └── Frame routing: frames → current TrainingEngine
```

### State Machine Flow

```
WorkoutTrainingEngine State Machine:

┌──────────┐
│   IDLE   │ ← Planned Workout loaded, not started
└────┬─────┘
     │ user taps "Start"
     ▼
┌──────────────┐
│ PRE_EXERCISE │ ← Show exercise info, weight dialog if needed
└──────┬───────┘
       │ user taps "Start Set"
       ▼
┌──────────┐
│ TRAINING │ ← Camera active, AI counting reps
└────┬─────┘
     │ set target reached (reps or duration)
     ▼
┌──────────────────┐     ┌─────────────────┐
│ Has more sets?   │─YES→│    SET_REST     │ ← countdown between sets
└────────┬─────────┘     └────────┬────────┘
         │ NO                     │ countdown done / skip
         ▼                        ▼
┌──────────────────┐     ┌──────────────┐
│ Has more         │     │ PRE_EXERCISE │ ← same exercise, next set
│ exercises?       │     │ (next set)   │
└────────┬─────────┘     └──────────────┘
    YES  │
         ▼
┌──────────────────┐
│ EXERCISE_REST    │ ← countdown between exercises + next exercise preview
└────────┬─────────┘
         │
         ▼
┌──────────────┐
│ PRE_EXERCISE │ ← new exercise info
│ (next ex.)   │
└──────────────┘

         │ NO more exercises
         ▼
┌──────────────────┐
│ SESSION_COMPLETE │ ← show report
└──────────────────┘
```

### Alternating Exercise Handling

When `WorkoutTrainingEngine` encounters an alternating exercise:

```
Exercise: "Squat + Push-up Combo" (3 sets × 6 reps, switchEvery: 1)

Set 1:
  Rep 1 → Load Squat joints → AI tracks squat
  Rep 2 → Hot-swap to Push-up joints → AI tracks push-up
  Rep 3 → Hot-swap to Squat joints
  Rep 4 → Hot-swap to Push-up joints
  Rep 5 → Squat
  Rep 6 → Push-up
  → Set 1 complete!
  → [Rest 30s]

Set 2:
  ...same pattern...
  → Set 2 complete!
  → [Rest 30s]

Set 3:
  ...same pattern...
  → Exercise complete!
  → [Rest 60s before next exercise]
```

**Camera handling**: The existing hot-swap mechanism in `WorkoutTrainingEngine` is reused inside `TrainingEngine` itself. When an alternating exercise switches variant:
1. Current tracked joint indices are swapped
2. New skeleton overlay is applied
3. Camera planned workout continues uninterrupted
4. If camera position changes needed → brief transition overlay

### Key Engine Components

```kotlin
// NEW: WorkoutTrainingEngine
class WorkoutTrainingEngine(
    private val plannedWorkoutItems: List<PlannedWorkoutExerciseItem>,
    private val context: Context
) {
    // State
    sealed class State {
        object Idle : State()
        data class PreExercise(
            val exerciseIndex: Int,
            val setNumber: Int,
            val exerciseConfig: ExerciseConfig,
            val weight: Float?
        ) : State()
        data class Training(
            val exerciseIndex: Int,
            val setNumber: Int,
            val totalSets: Int
        ) : State()
        data class SetRest(
            val exerciseIndex: Int,
            val nextSetNumber: Int,
            val durationMs: Long
        ) : State()
        data class ExerciseRest(
            val nextExerciseIndex: Int,
            val durationMs: Long,
            val nextExerciseName: String,
            val nextExerciseImage: String?
        ) : State()
        data class WorkoutComplete(
            val report: WorkoutReport
        ) : State()
    }

    // Metrics collection per set
    data class SetMetrics(
        val exerciseSlug: String,
        val setNumber: Int,
        val actualReps: Int,
        val targetReps: Int?,
        val actualDurationMs: Long,
        val averageAccuracy: Float,
        val errors: List<FormError>,
        val weightKg: Float?,
        val repDetails: List<RepMetrics>
    )
}
```

---

## 9. Rest & Transition Screens

### Types of Rest

| Rest Type | When | Default | Customizable |
|-----------|------|---------|-------------|
| Between Sets | After each set (except last) of same exercise | 30s | Yes (in plan & live) |
| Between Exercises | After last set of exercise, before next exercise | 60s | Yes (in plan & live) |

### Rest Screen UI
(Shown as overlay within TrainingActivity — no Activity transition)

- **Between Sets**:
  - Shows: "Rest before Set 3 of 3"
  - Countdown timer
  - Same exercise image/animation
  - [+20s] [Skip] buttons
  - Auto-continues when timer reaches 0

- **Between Exercises**:
  - Shows: "NEXT 3/5 — Squats × 12"
  - Next exercise preview image
  - Countdown timer
  - [+20s] [Skip] buttons
  - [Edit Rest Time] button
  - Auto-continues when timer reaches 0 (starts with PRE_EXERCISE screen for next exercise)

### Key UX Decisions
- Rest screen is NOT a separate Activity — it's a panel/overlay within TrainingActivity
- Camera can be paused during rest (save battery)
- Rest countdown is clearly visible with large numbers
- Sound/vibration alert when rest is about to end (3-2-1 countdown beeps)
- User can ALWAYS skip rest
- User can extend rest (+20s increments)

---

## 10. Reporting Architecture

### Report Hierarchy

```
Workout report (Planned Workout level)
├── Total time, total reps, avg accuracy, exercises completed
│
├── Exercise Report 1: Push-ups
│   ├── Overall: 3/3 sets, 30 reps total, 92% accuracy
│   ├── Set 1: 10 reps, 94% accuracy, weight: 0kg
│   │   └── Rep details: [rep1: 95%, rep2: 88%, ...]
│   ├── Set 2: 10 reps, 91% accuracy
│   └── Set 3: 10 reps, 90% accuracy
│
├── Exercise Report 2: Squats
│   ├── Overall: 3/3 sets, 36 reps total, 85% accuracy
│   └── ...sets and reps...
│
└── Exercise Report 3: Plank
    ├── Overall: 2/2 sets, 60s total, 88% accuracy
    └── ...

Weekly Report (aggregated from all planned workouts in the week)
├── Days completed: 5/7
├── Total training time: 2h 45m
├── Total planned workouts: 8
├── Exercises performed: 35
├── Avg accuracy trend: 82% → 87% (improving!)
└── Muscle group distribution chart
```

### Data Collection Points

```
Per Rep:
  - repNumber, accuracy (%), duration (ms)
  - joint angles at key points
  - form errors detected
  - phase timings (up/down/hold)

Per Set:
  - setNumber, totalReps, targetReps
  - averageAccuracy, bestRep, worstRep
  - actualWeight (user-confirmed)
  - totalDuration
  - form error frequency

Per Exercise:
  - exerciseName, setsCompleted, totalSets
  - totalReps across all sets
  - overallAccuracy
  - weight progression across sets
  - common form errors (aggregated)
  - improvement vs last time

Per Planned Workout:
  - plannedWorkoutName, totalDuration
  - exercisesCompleted / exercisesTotal
  - totalSets, totalReps
  - avgAccuracy, calorieEstimate
  - completion status (full / partial)

Per Week:
  - daysCompleted / daysTotal
  - plannedWorkoutsCompleted
  - totalTrainingTime
  - accuracyTrend
  - muscleGroupDistribution
  - comparison with previous week
```

### Report Storage

```
Local (Mobile):
  - Per-planned-workout reports stored as JSON in app storage
  - Cached for offline viewing
  - Synced to server when online

Server:
  - WorkoutExecution table stores workout-run-level metrics
  - WorkoutExecutionMetrics table stores per-exercise/set/rep details
  - WeeklyReport generated on-demand from planned workout data
```

---

## 11. Admin Panel Changes

### New Modules

#### 1. Program Management
- **Program List**: Browse all programs with filters (difficulty, duration, tags)
- **Program Builder** (Wizard):
  1. Basic Info (name, description, duration, difficulty, cover image)
  2. Week Builder:
     - Add weeks (1, 2, 3, 4...)
     - Copy week from another week
     - For each week → Day Builder
  3. Day Builder:
     - 7 days per week
     - Mark rest days
     - Add Planned Workouts per day
  4. Planned Workout Builder:
     - Name the planned workout
     - Add exercises manually (search & add)
     - Import from Workout template
     - Set per-exercise: sets, reps/duration, rest, weight
     - Add rest periods between exercises
     - Reorder with drag & drop
  5. Review & Publish

#### 2. Exercise Creation — Alternating Enhancement
**Current Joints Step** (Step 3):
- Configure primary + secondary joints
- Applied to all pose variants

**Enhanced Joints Step**:
- Configure primary + secondary joints (Mode A — default)
- **[+ Add Alternating Mode]** button at bottom
- Clicking opens new joints configuration panel (Mode B)
- Each mode gets:
  - Its own camera position selection
  - Its own joints configuration
  - Its own position checks
  - A label (e.g., "Squat Phase", "Push-up Phase")
- Configure `switchEvery` (how many reps before switching)
- Can add Mode C, Mode D, etc.
- Preview: animated diagram showing the alternating flow

#### 3. Simplified Workout Creation
- Remove: type selector, executionMode, rounds, repsPerSwitch
- Keep: ordered list of exercises
- Add: per-exercise Sets, Rest, Weight configuration
- Add: Rest between exercises
- Preview: estimated total duration calculator

### Modified Modules
- **Exercise Form**: Add alternating toggle + multi-variant joints config
- **Workout Form**: Simplify (remove complex types), add sets/weight
- **Sync Service**: Include program data in mobile sync

---

## 12. API & Sync Changes

### New Endpoints

```
// Program CRUD (Admin)
POST   /api/programs
GET    /api/programs
GET    /api/programs/:id
PUT    /api/programs/:id
DELETE /api/programs/:id
POST   /api/programs/:id/publish
POST   /api/programs/:id/duplicate

// Program Weeks/Days/Planned Workouts (Admin)
POST   /api/programs/:id/weeks
PUT    /api/programs/:id/weeks/:weekId
DELETE /api/programs/:id/weeks/:weekId
POST   /api/programs/:id/weeks/:weekId/copy-to/:targetWeek

POST   /api/programs/:programId/weeks/:weekId/days/:dayId/planned-workouts
PUT    /api/programs/.../planned-workouts/:plannedWorkoutId
DELETE /api/programs/.../planned-workouts/:plannedWorkoutId

POST   /api/programs/.../planned-workouts/:plannedWorkoutId/items
PUT    /api/programs/.../planned-workouts/:plannedWorkoutId/items/:itemId
DELETE /api/programs/.../planned-workouts/:plannedWorkoutId/items/:itemId
POST   /api/programs/.../planned-workouts/:plannedWorkoutId/import-workout-template/:workoutTemplateId

// Mobile — Program
GET    /api/mobile/programs              // list available programs
GET    /api/mobile/programs/:id          // full program with all weeks/days/planned-workouts
POST   /api/mobile/programs/:id/enroll   // user enrolls in program
PUT    /api/mobile/user-programs/:id     // update customizations
GET    /api/mobile/user-programs/:id/today  // get today's plan

// Mobile — Planned Workout Training
POST   /api/mobile/planned-workouts/:plannedWorkoutId/start
POST   /api/mobile/planned-workouts/:plannedWorkoutId/complete
POST   /api/mobile/planned-workouts/:plannedWorkoutId/report  // submit planned workout report

// Mobile — Sync (Enhanced)
GET    /api/mobile/sync   // now includes programs + simplified workouts
```

### Sync Payload Changes

```typescript
// Enhanced sync response
interface SyncResponse {
  exercises: ExerciseExport[];      // existing + alternatingConfig
  workouts: WorkoutExport[];        // simplified (no types/modes/rounds)
  programs: ProgramExport[];        // NEW
  userPrograms: UserProgramExport[]; // NEW — user's enrolled programs
  deletedIds: string[];
  syncToken: string;
}

// New Exercise export includes alternating
interface ExerciseExport {
  // ...existing fields...
  isAlternating: boolean;
  alternatingConfig?: {
    switchEvery: number;
    variants: {
      label: LocalizedText;
      variantIndex: number;
    }[];
  };
}

// Simplified Workout export
interface WorkoutExport {
  id: string;
  slug: string;
  name: LocalizedText;
  description?: LocalizedText;
  exercises: WorkoutExerciseExport[];
  // REMOVED: type, executionMode, rounds, repsPerSwitch, restBetweenSwitchMs
}

interface WorkoutExerciseExport {
  exercise: string;  // slug
  variantIndex: number;
  difficulty: string;
  sets: number;
  targetReps?: number;
  targetDuration?: number;
  restBetweenSetsMs: number;
  restAfterExerciseMs: number;
  weightKg?: number;
  weightPerSet?: number[];
  notes?: LocalizedText;
}

// Program export
interface ProgramExport {
  id: string;
  slug: string;
  name: LocalizedText;
  description?: LocalizedText;
  coverImageUrl?: string;
  durationWeeks: number;
  difficulty: string;
  weeks: ProgramWeekExport[];
}

interface ProgramWeekExport {
  weekNumber: number;
  name?: LocalizedText;
  days: ProgramDayExport[];
}

interface ProgramDayExport {
  dayNumber: number;
  isRestDay: boolean;
  name?: LocalizedText;
  executions: WorkoutExecutionExport[];
}

interface WorkoutExecutionExport {
  id: string;
  name: LocalizedText;
  sortOrder: number;
  items: PlannedWorkoutItemExport[];
}

interface PlannedWorkoutItemExport {
  type: "exercise" | "rest";
  // exercise fields
  exerciseSlug?: string;
  sets?: number;
  targetReps?: number;
  targetDuration?: number;
  restBetweenSetsMs?: number;
  weightKg?: number;
  weightPerSet?: number[];
  notes?: LocalizedText;
  // rest fields
  restDurationMs?: number;
  sortOrder: number;
}
```

---

## 13. Migration Strategy

### Phase 1: Backend Schema Migration

1. **Add new tables**: Program, ProgramWeek, ProgramDay, WorkoutExecution, PlannedWorkoutItem, UserProgram, UserProgramProgress
2. **Modify Workout table**: 
   - Add: estimatedDurationMin, coverImageUrl, tags, difficulty
   - Mark deprecated (keep for backward compatibility): type, executionMode, rounds, repsPerSwitch, restBetweenSwitchMs
   - Don't DELETE old columns yet
3. **Modify WorkoutExercise table**:
   - Add: sets, restBetweenSetsMs, restAfterExerciseMs, weightKg, weightPerSet
   - Keep existing fields
4. **Modify Exercise table**:
   - Add: isAlternating, alternatingConfig
5. **Add relation**: PlannedWorkoutItem → Exercise

### Phase 2: Backend Logic Migration

1. Build Program CRUD module (controller, service, types)
2. Update Workout CRUD to handle simplified model
3. Update sync service to include programs
4. Build "import template to planned workout" logic
5. Update exercise creation to support alternating config

### Phase 3: Admin Panel Migration

1. Build Program management pages (list, create/edit wizard)
2. Update Exercise wizard — add Alternating step
3. Simplify Workout wizard — remove types/modes, add sets/weight
4. Update dashboard to show programs

### Phase 4: Mobile Migration

1. Update data models (add Program, PlannedWorkout, Sets)
2. Update sync to fetch programs
3. Build new `WorkoutTrainingEngine`
4. Update `TrainingActivity` for set-based flow
5. Build rest screens (between sets, between exercises)
6. Build weight confirmation dialog
7. Build new reporting (per set, per exercise, per planned workout)
8. Build Home screen with today's plan
9. Build Program calendar screen
10. Build Day detail screen
11. Build Workout complete screen

### Phase 5: Cleanup

1. Remove deprecated Workout fields (type, executionMode, rounds, etc.)
2. Remove `WorkoutRunner` (replaced by `WorkoutTrainingEngine`)
3. Remove `WorkoutTrainingEngine` (absorbed into `WorkoutTrainingEngine` + `TrainingEngine`)
4. Remove `WorkoutActivity` (no longer needed — everything in `TrainingActivity`)
5. Convert existing workouts to new format

---

## 14. Implementation Phases

### Phase A: Foundation (Backend + Data) — ~2 weeks
1. Database schema migration
2. Program CRUD APIs
3. Enhanced Workout APIs (simplified)
4. Enhanced Exercise APIs (alternating)
5. Updated Sync API

### Phase B: Admin Panel — ~2 weeks
1. Program builder wizard
2. Exercise alternating step
3. Simplified workout builder
4. Program-Workout import flow

### Phase C: Mobile Core Engine — ~2 weeks
1. New data models + sync
2. `WorkoutTrainingEngine` (state machine)
3. Set-based training flow
4. Alternating exercise handling in engine
5. Rest countdown overlay

### Phase D: Mobile UI — ~2 weeks
1. Home screen redesign (today's plan)
2. Program calendar screen
3. Day detail screen
4. Weight confirmation dialog
5. Rest screen
6. Replace exercise bottom sheet

### Phase E: Reporting — ~1 week
1. Per-set metrics collection
2. Per-exercise aggregation
3. Planned Workout report screen
4. Weekly report (basic)

### Phase F: Polish & Migration — ~1 week
1. Convert existing workouts to new format
2. Edge cases & error handling
3. Offline support
4. Performance optimization
5. Cleanup deprecated code

---

## Summary

| Component | Current | New |
|-----------|---------|-----|
| **Top-level structure** | None | Program → Weeks → Days → Planned Workouts |
| **Workout complexity** | 4 types + 2 modes + rounds | Simple template (exercise list with sets/rest) |
| **Alternating** | Workout level (between exercises) | Exercise level (between joint configs) |
| **Repetition** | Rounds (whole workout) | Sets (per exercise) |
| **Weight** | Not tracked | Per set, with live confirmation |
| **Training flow** | Breaks between exercises | Continuous planned workout (single Activity) |
| **Rest** | No visual rest screen | Countdown + Skip + Extend |
| **Reports** | Per exercise only | Per Set → Per Exercise → Per Planned Workout → Per Week |
| **Customization** | None | Copy program, modify exercises, replace alternatives |

> **Goal**: A beginner opens the app, sees their plan for today, taps "Start Training", and the app guides them through every set, every rep, every rest — with AI-powered form correction — all within a single, seamless planned workout. No confusion, no complexity, just safe, effective exercise.

