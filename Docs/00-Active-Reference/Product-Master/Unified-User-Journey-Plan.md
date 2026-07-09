# Unified User Journey Plan — The Gold Standard

| | |
|---|---|
| **Status** | `ROADMAP` |
| **SSOT for** | Target product journey (master blueprint) |
| **As-built** | [Journey-Index.md](Journey-Index.md) |
| **Verified** | 2026-05-29 |

> **Goal:** Transform Movit from a collection of powerful but disconnected systems  
> into a unified, intelligent coaching platform with a clear, trustworthy user journey.

---

## Table of Contents

1. [Philosophy & Vision](#1-philosophy--vision)
2. [The Two Modes: Train & Explore](#2-the-two-modes-train--explore)
3. [User Journey — First Time (Onboarding)](#3-user-journey--first-time-onboarding)
4. [User Journey — Train Mode (The AI Coach)](#4-user-journey--train-mode-the-ai-coach)
5. [User Journey — Explore Mode (The Free Gym)](#5-user-journey--explore-mode-the-free-gym)
6. [Home Screen — The Command Center](#6-home-screen--the-command-center)
7. [The AI Coaching Loop — System Architecture](#7-the-ai-coaching-loop--system-architecture)
8. [Existing Systems Map & Integration Points](#8-existing-systems-map--integration-points)
9. [Backend Changes Required](#9-backend-changes-required)
10. [Android Changes Required](#10-android-changes-required)
11. [Implementation Phases](#11-implementation-phases)
12. [Success Metrics](#12-success-metrics)

---

## 1. Philosophy & Vision

### The Core Problem

The project has strong individual systems:
- Pose Estimation Engine (Android)
- Assessment / Body Scan
- Level System (1-5)
- Programs (Weeks → Days → Planned Workouts → Exercises)
- Workouts (Templates)
- Progression Engine (Rules-based)
- Prescription Engine (Program recommendation)
- Comprehensive Metrics (Form, Safety, Control)
- Detailed Reports (7-page reports)

**But** these systems are not connected into a coherent user journey. The user opens the app and faces choices without guidance, context, or a clear path forward.

### The Vision

Movit should operate as an **Elite Virtual Coach** — a system that:

1. **Understands** your body (Assessment)
2. **Plans** your training (Prescription + Active Plan)
3. **Guides** every rep (AI Training Engine)
4. **Adapts** based on your performance (Progression)
5. **Lets you explore** freely when you want (Explore Mode)

### The Gold Standard Principles

| # | Principle | Meaning |
|---|-----------|---------|
| 1 | **Trust Before Training** | Prove you understand the user's body before prescribing anything |
| 2 | **One Clear Action** | At any moment, the user knows exactly what to do next |
| 3 | **Progress is Visible** | The user always sees where they are and where they're going |
| 4 | **Freedom Without Chaos** | Explore mode gives freedom but still tracks and learns |
| 5 | **Every Rep Counts** | Data from both modes feeds back into the coaching loop |
| 6 | **Safety First** | The system protects the user, never pushes into danger |

---

## 2. The Two Modes: Train & Explore

The app operates in two distinct modes, each serving a different user intent:

### Mode 1: Train (The AI Coach)

```
"I want to be guided. Tell me what to do today."
```

| Aspect | Description |
|--------|-------------|
| **Entry** | Body Scan → Level Classification → Active Plan |
| **Experience** | Open app → See "Today's Mission" → Press Start → AI guides every rep |
| **Content** | Programs prescribed by the system based on assessment |
| **Progression** | Automatic — system adjusts weights, reps, difficulty |
| **User Control** | Minimal — trust the system |
| **Data Flow** | Workout → Report → Progression Engine → Updated Plan |

### Mode 2: Explore (The Free Gym)

```
"I want to train freely. Let me choose what to do."
```

| Aspect | Description |
|--------|-------------|
| **Entry** | Browse → Pick exercises/workouts → Customize → Train |
| **Experience** | Browse library → Select exercises → Adjust order/rest/sets → Start |
| **Content** | Full exercise library + workout templates |
| **Customization** | Full control — reorder exercises, change rest periods, add/remove |
| **Progression** | Manual + Insights — system tracks but doesn't auto-adjust |
| **Data Flow** | Workout → Report → History → Insights (no auto-progression) |

### How Both Modes Coexist

```
┌─────────────────────────────────────────────────────────────┐
│                        HOME SCREEN                          │
│                                                             │
│  ┌─────────────────────┐   ┌─────────────────────────────┐  │
│  │    TRAIN MODE        │   │      EXPLORE MODE            │  │
│  │                     │   │                             │  │
│  │  "Today's Mission"  │   │  Browse Exercises           │  │
│  │  Week 2, Day 3      │   │  Browse Workouts            │  │
│  │  [START SESSION]     │   │  Quick Start (pick & go)    │  │
│  │                     │   │  [EXPLORE]                  │  │
│  │  Progress: ████░ 60% │   │                             │  │
│  └─────────────────────┘   └─────────────────────────────┘  │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  YOUR LEVEL: 2 (Building)  │  Body Score: 65/100     │   │
│  │  ████████████░░░░ → Level 3                          │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Critical Rule: Every Rep Counts

Both modes feed data back to the system:

```
Train Mode Workout ──┐
                     ├──→ WorkoutExecution + Metrics ──→ User History
Explore Mode Workout ┘
                     
Train Mode ONLY ──→ Progression Engine (auto-adjust)
                 ──→ Program Progress (week/day tracking)
```

Explore mode planned workouts are saved with full metrics but do NOT trigger auto-progression or affect program progress. They DO contribute to:
- Exercise history and personal records
- Overall training volume stats
- Insights and trends in Reports
- Reassessment timing (total training frequency)

---

## 3. User Journey — First Time (Onboarding)

### Flow

```
Install → Splash → Onboarding (3 screens) → Sign Up
    → Body Scan Gate → PAR-Q → Assessment (3 min)
    → Results (Level + Body Score + Limiting Factors)
    → Plan Generation (Prescription Engine)
    → Home (Train Mode active, first mission ready)
```

### Step-by-Step

#### Step 1: Onboarding Screens (3 swipeable)
- Screen 1: "AI-powered training that sees every rep"
- Screen 2: "We assess your body first, then build your plan"
- Screen 3: "Track your progress, level up, transform"

#### Step 2: Sign Up / Sign In
- Email or Google OAuth
- Name, preferred language

#### Step 3: Body Scan Gate (Mandatory for Train Mode)
> This is non-negotiable. No program enrollment without assessment.

- Brief explanation: "In 3 minutes, we'll understand your body"
- PAR-Q+ screening (7 questions) via `PreScreeningActivity`
- Launch `AssessmentSessionActivity` → runs 3-5 exercises via `TrainingActivity` (assessment mode)

#### Step 4: Assessment Results
- `AssessmentResultActivity` shows:
  - **Body Score** (0-100) — the headline number
  - **Level** (1-5) with name and icon
  - **Domain breakdown** (Mobility, Control, Symmetry, Safety)
  - **Limiting factors** ("Your left shoulder mobility is holding you back")
  - **Body Map** visualization

#### Step 5: Plan Generation (Automatic)
- Backend `prescription.recommend(userId)` runs automatically after assessment upload
- System creates `UserProgram` + `ActivePlan` + `ActivePlanProgram`
- User sees: "We've built your 4-week plan based on your assessment"
- CTA: "Start Your Journey" → navigates to Home

#### Step 6: Home Screen — First Mission Ready
- Train Mode section shows Week 1, Day 1, Workout 1
- Big button: "Start Today's Workout"

### Skip Body Scan (Explore Only)

Users CAN skip the Body Scan, but:
- Train Mode is **locked** — shows "Complete Body Scan to unlock your personalized plan"
- Explore Mode is **fully available** — browse, pick, train freely
- A persistent banner reminds them: "Complete your Body Scan for a personalized experience"

### Existing Systems Used

| System | Role in Onboarding |
|--------|-------------------|
| `PreScreeningActivity` | PAR-Q+ screening |
| `AssessmentSessionActivity` | Runs assessment exercises |
| `AssessmentEngine` | Processes results → `BodyScanResult` |
| `AssessmentUploadService` | Uploads to backend |
| `assessment.service.ts` | Saves `BodyScanResult` |
| `level-profile.service.ts` | Creates `UserLevelProfile` |
| `prescription.service.ts` | Recommends program |
| `active-plan.service.ts` | Creates `ActivePlan` |

---

## 4. User Journey — Train Mode (The AI Coach)

### Daily Flow

```
Open App → Home Screen → "Today's Mission" card
    → Tap "Start Workout"
    → ProgramWorkoutActivity (see exercises, sets, weights)
    → Train each exercise via TrainingActivity
    → PlannedWorkoutReportActivity (workout summary)
    → Home (progress updated, next mission shown)
```

### Detailed Steps

#### 4.1 Home → Today's Mission

The Home Screen shows the current position in the Active Plan:

```
┌─────────────────────────────────────────┐
│  PROGRAM: Foundation Builder             │
│  Week 2 of 4  │  Day 3 of 5             │
│                                         │
│  Today's Workout: Lower Body Focus       │
│  4 exercises  │  ~25 min                 │
│                                         │
│  ┌─────────────────────────────────────┐ │
│  │        START SESSION →              │ │
│  └─────────────────────────────────────┘ │
│                                         │
│  Progress this week: ███░░ 3/5 days     │
└─────────────────────────────────────────┘
```

**If today is a rest day:**
```
┌─────────────────────────────────────────┐
│  REST DAY — Recovery                     │
│  Your body grows during rest.            │
│  Next planned workout: Tomorrow                  │
│                                         │
│  Tip: Stay hydrated and stretch          │
│  [VIEW WEEKLY SUMMARY]                  │
└─────────────────────────────────────────┘
```

**If the program is completed:**
```
┌─────────────────────────────────────────┐
│  🎉 PROGRAM COMPLETE!                    │
│  You've finished "Foundation Builder"    │
│                                         │
│  Time to level up! Take the assessment   │
│  to see your progress.                   │
│                                         │
│  [START REASSESSMENT]                   │
└─────────────────────────────────────────┘
```

#### 4.2 Program Workout Screen

`ProgramWorkoutActivity` shows the planned workout structure:

```
┌─────────────────────────────────────────┐
│  Lower Body Focus                        │
│  Week 2, Day 3                          │
│                                         │
│  1. Squat          3×12  │ 60s rest     │
│  2. Lunge (L)      3×10  │ 45s rest     │
│  3. Lunge (R)      3×10  │ 45s rest     │
│  4. Glute Bridge   3×15  │ 30s rest     │
│                                         │
│  Estimated: 25 min                       │
│                                         │
│  [START TRAINING →]                     │
└─────────────────────────────────────────┘
```

Each exercise launches `TrainingActivity` in planned workout mode:
- AI tracks reps, form, ROM
- Voice feedback guides corrections
- Workout run flow managed by `WorkoutTrainingEngine`

#### 4.3 Post-Workout Report

`PlannedWorkoutReportActivity` shows:
- Workout score (avg form score)
- Per-exercise breakdown
- Key insights ("Your squat ROM improved by 5° since last week")
- Progression notifications ("Weight increased to 12.5kg for next planned workout")

#### 4.4 Progression (Automatic, Behind the Scenes)

After `completePlannedWorkoutReport()`:
1. `progression.evaluateAfterPlannedWorkout()` checks rules
2. If conditions met → auto-adjust `PlannedWorkoutItem` for future planned workouts
3. Changes logged in `ProgressionHistory`
4. User sees notification: "Based on your performance, we've adjusted your next planned workout"

#### 4.5 Week Completion

When all days in a week are done:
- Weekly summary available (aggregated metrics, trends)
- Week-level progression rules evaluated
- Next week unlocked

#### 4.6 Program Completion → Reassessment

When all weeks done:
1. `active-plan.completeActiveProgram()` marks program complete
2. `ReassessmentSchedule` created with reason `program_complete`
3. Home screen shows "Level Up Challenge" CTA
4. User takes Body Scan again
5. New assessment compared to previous (`previousId` link)
6. If improved → level up, new program prescribed
7. If plateaued → adjusted program, focus on limiting factors
8. New `ActivePlan` generated automatically

### State Machine (Train Mode)

```
NO_ASSESSMENT ──[Body Scan]──→ ASSESSED
ASSESSED ──[Prescription]──→ PLAN_READY
PLAN_READY ──[Start Day 1]──→ IN_PROGRESS
IN_PROGRESS ──[Complete Planned Workouts]──→ IN_PROGRESS
IN_PROGRESS ──[Complete Program]──→ PROGRAM_COMPLETE
PROGRAM_COMPLETE ──[Reassessment]──→ ASSESSED (loop)
```

---

## 5. User Journey — Explore Mode (The Free Gym)

### Philosophy

Explore mode is the user's freedom zone. They can:
- Browse the full exercise library
- Browse workout templates
- Build custom planned workouts on-the-fly
- Train with full AI tracking
- Get full reports

But the system is still watching, learning, and providing insights.

### Entry Points

```
Bottom Nav: Explore tab
    ├── Exercises (full library with search/filter)
    ├── Workouts (curated templates)
    └── Quick Start (pick exercises → customize → train)
```

### 5.1 Browse Exercises

```
ExploreFragment → Exercises section
    → ExercisesFragment (search, filter by muscle/equipment/category)
    → ExerciseDetailActivity (description, video, history)
    → PreWorkoutActivity (Start Camera / Analyze Video)
    → TrainingActivity (free mode, single exercise)
    → ReportPagerActivity (full 7-page report)
```

This flow **already works**. The report is saved to `WorkoutExecution` and synced.

### 5.2 Browse Workouts

```
ExploreFragment → Workouts section
    → WorkoutListActivity (curated workout templates)
    → WorkoutDetailActivity (exercise list, total time, difficulty)
    → [CUSTOMIZE] → WorkoutCustomizeScreen (NEW)
    → TrainingActivity (workout mode, multiple exercises)
    → WorkoutReportActivity (workout summary)
```

#### Workout Customization (NEW Feature)

Before starting a workout, the user can:

```
┌─────────────────────────────────────────┐
│  Upper Body Blast                        │
│  Customize Your Workout                  │
│                                         │
│  ☰ 1. Push-up        3×12  [60s rest]  │ ← drag to reorder
│  ☰ 2. Shoulder Press  3×10  [45s rest]  │ ← tap to edit
│  ☰ 3. Tricep Dip      3×15  [30s rest]  │ ← swipe to remove
│                                         │
│  [+ Add Exercise]                       │
│                                         │
│  Total: 3 exercises │ ~20 min            │
│                                         │
│  [START WORKOUT →]                      │
└─────────────────────────────────────────┘
```

Customization options per exercise:
- Change number of sets
- Change target reps
- Change rest duration between sets
- Remove from workout
- Reorder (drag & drop)

This uses `DayCustomizationStore` (already exists in Android) or creates a local workout variant.

### 5.3 Quick Start (Pick & Go)

A streamlined flow for users who know what they want:

```
ExploreFragment → Quick Start
    → Exercise Picker (multi-select from library)
    → Customize order, sets, rest
    → Start Training
    → Workout report
```

This is essentially building a temporary workout on-the-fly without saving it as a template.

### 5.4 Data Tracking in Explore Mode

Every planned workout in Explore mode:

| Data | Tracked? | Used For |
|------|----------|----------|
| `WorkoutExecution` | Yes | Exercise history |
| `WorkoutExecutionMetrics` | Yes | Personal records, trends |
| `RepMetrics` | Yes | Form analysis, insights |
| Program Progress | **No** | Does not affect Active Plan |
| Progression Rules | **No** | No auto-adjustment |
| Reassessment Trigger | Indirect | Contributes to training frequency |

### 5.5 Explore Mode Reports

Single exercise → `ReportPagerActivity` (full 7-page report, already works)

Multi-exercise workout → `WorkoutReportActivity` (NEW, similar to `PlannedWorkoutReportActivity`):
- Workout summary (total time, exercises completed, avg form)
- Per-exercise card with key metrics
- Personal records highlighted
- Quick insights

---

## 6. Home Screen — The Command Center

### Layout Structure

The Home Screen is the single source of truth for the user's state.

```
┌─────────────────────────────────────────────────────────────┐
│  HEADER                                                     │
│  ┌───────────────────────────────────────────────────────┐   │
│  │  Level 2 (Building)  │  Body Score: 65              │   │
│  │  ████████████░░░░░░░  → Level 3 (need 70)            │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                             │
│  TRAIN MODE CARD ───────────────────────────────────────     │
│  ┌───────────────────────────────────────────────────────┐   │
│  │  Foundation Builder — Week 2, Day 3                  │   │
│  │  Today: Lower Body Focus  │  4 exercises  │  ~25 min │   │
│  │                                                     │   │
│  │  ┌─────────────────────────────────────────────────┐ │   │
│  │  │           START TODAY'S SESSION →                │ │   │
│  │  └─────────────────────────────────────────────────┘ │   │
│  │                                                     │   │
│  │  This week: ███░░ 3/5                               │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                             │
│  QUICK STATS ─────────────────────────────────────────────   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │ Planned Workouts │ │ Avg Form │ │  Streak  │ │ This Week    │   │
│  │    47    │ │   82%    │ │  5 days  │ │  3 planned workouts  │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘   │
│                                                             │
│  EXPLORE SHORTCUT ────────────────────────────────────────   │
│  ┌───────────────────────────────────────────────────────┐   │
│  │  Want to train freely?                               │   │
│  │  [Browse Exercises]  [Browse Workouts]  [Quick Start] │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                             │
│  RECENT ACTIVITY ─────────────────────────────────────────   │
│  ┌───────────────────────────────────────────────────────┐   │
│  │  Yesterday: Squat — 82% form, 12 reps ✓             │   │
│  │  2 days ago: Push-up — 90% form, 15 reps ✓          │   │
│  └───────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Home Screen States

| State | What User Sees |
|-------|---------------|
| **New User (no assessment)** | "Welcome! Start your Body Scan" CTA + Explore shortcuts |
| **Assessed, no plan** | Level card + "Generating your plan..." or "View Recommended Plan" |
| **Active Plan, training day** | Today's Mission card with START button |
| **Active Plan, rest day** | "Rest Day" card with recovery tips |
| **Program Complete** | "Level Up Challenge" reassessment CTA |
| **Reassessment Due** | Prominent "Time for Reassessment" banner |

### Bottom Navigation (4 Tabs)

| Tab | Screen | Purpose |
|-----|--------|---------|
| **Home** | HomeFragment | Command center, today's action |
| **Train** | TrainFragment | Active program view, week calendar, planned workouts |
| **Explore** | ExploreFragment | Browse exercises, workouts, quick start |
| **Reports** | HistoryFragment | Reports hub (overview, exercises, trends, records) |

### Train Tab vs Explore Tab

**Train Tab** is the dedicated view for the Active Plan:
- Week calendar (which days are done, today highlighted)
- Planned workouts for today
- Program progress overview
- Past planned workout reports

**Explore Tab** is the discovery and free training zone:
- Hero cards (featured workouts, challenges)
- Exercise library (search, filter)
- Workout templates (curated collections)
- Quick Start builder
- Body Scan shortcut (for reassessment)

---

## 7. The AI Coaching Loop — System Architecture

### The Closed Loop

```
┌──────────────┐
│   ASSESS     │ Body Scan → BodyScanResult → UserLevelProfile
│  (Diagnose)  │ Measures: Mobility, Control, Symmetry, Safety
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  PRESCRIBE   │ Prescription Engine → ActivePlan
│   (Plan)     │ Selects programs based on level, limiting factors, safety
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   EXECUTE    │ Android Training Engine → Real-time AI feedback
│   (Train)    │ Tracks every rep: ROM, form, tempo, alignment
└──────┬───────┘
       │
       ▼
┌──────────────┐
│    ADAPT     │ Progression Engine → Auto-adjust next planned workouts
│  (Evolve)    │ Micro (workout-run-level) + Macro (program-level)
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  REASSESS    │ Triggered by: program complete / periodic / progression
│ (Level Up)   │ New Body Scan → Compare with previous → Update level
└──────┬───────┘
       │
       └──────→ Back to PRESCRIBE (new plan based on new level)
```

### Data Flow Diagram

```
[Android TrainingActivity]
        │
        │ PostTrainingReport (per exercise)
        ▼
[WorkoutSyncService] ──→ [POST /api/mobile/workout-executions]
        │                         │
        │                         ▼
        │                 [workout-executions.service]
        │                    │              │
        │                    │     Saves: WorkoutExecution
        │                    │             WorkoutExecutionMetrics
        │                    │             RepMetrics
        │                    │
        │              (if planned workout)
        │                    │
        │                    ▼
        │          [completePlannedWorkoutReport]
        │                    │
        │                    ├──→ Update UserProgramProgress
        │                    ├──→ Update PlannedWorkoutReport
        │                    └──→ [progression.evaluateAfterPlannedWorkout]
        │                              │
        │                              ▼
        │                    Check ProgressionRules
        │                    Apply actions (adjust weight/reps/sets)
        │                    Log to ProgressionHistory
        │                              │
        │                    (if program complete)
        │                              │
        │                              ▼
        │                    [active-plan.completeActiveProgram]
        │                    Create ReassessmentSchedule
        │
        ▼
[ReportPagerActivity / WorkoutReportActivity]
        │
        ▼
     [Home Screen Updated]
```

---

## 8. Existing Systems Map & Integration Points

### What's Built and Working

| System | Status | Location |
|--------|--------|----------|
| Pose Estimation Engine | Working | Android `TrainingEngine` |
| Workout run (multi-exercise) | Working | Android `WorkoutTrainingEngine` |
| Assessment Engine | Working | Android `AssessmentEngine` |
| Report Generator | Working | Android `ReportGenerator` |
| 7-Page Report | Working | Android `ReportPagerActivity` |
| Exercise Library | Working | Backend + Android |
| Program CRUD | Working | Backend + Admin Dashboard |
| Program Workout Training | Working | Android `ProgramWorkoutActivity` |
| Planned Workout Reports | Working | Backend + Android |
| Workout execution sync | Working | Android `WorkoutSyncService` |
| Level System | Working | Backend `level-profile.service` |
| Active Plan | Working | Backend `active-plan.service` |
| Prescription Engine | Working | Backend `prescription.service` |
| Progression Engine | Working | Backend `progression.service` |
| Reassessment Scheduling | Working | Backend `reassessment.service` |
| Metrics System | Working | Backend `WorkoutExecutionMetrics` + `RepMetrics` |
| Reports Hub | Working | Android Reports tabs |
| Home Screen | Partial | Android `HomeFragment` (needs refinement) |
| Workout Templates | Partial | Backend + Android (browse only, no training start) |

### What Needs Connection/Enhancement

| Gap | Current State | Target State |
|-----|--------------|--------------|
| **Onboarding → Body Scan** | Optional, disconnected | Mandatory gate for Train Mode |
| **Assessment → Auto Plan** | Manual enrollment | Auto-prescription after Body Scan |
| **Home → Today's Mission** | Generic, shows mock/default | Dynamic from `getTodayPlan()` |
| **Workout Training** | Template view only | Full training flow with customization |
| **Quick Start** | Not implemented | Exercise picker → customize → train |
| **Progression Notifications** | Backend only | Show in app (post-workout + home) |
| **Program Complete → Reassessment** | Not triggered in UI | Auto-prompt with "Level Up Challenge" |
| **Explore Reports** | Single exercise only | Multi-exercise planned workout report |
| **Train vs Explore separation** | Mixed/unclear | Clear mode distinction |
| **Rest Day handling** | Not shown | Clear rest day card on Home |

---

## 9. Backend Changes Required

### 9.1 Auto-Prescription After Assessment

**File:** `assessment.service.ts`

After saving `BodyScanResult` and creating `UserLevelProfile`:
```
→ Call prescription.recommend(userId)
→ If recommendation exists and user has no active plan:
    → Auto-create UserProgram + ActivePlan
    → Return planId in response
```

**New field in assessment response:**
```typescript
{
  assessment: BodyScanResult,
  levelProfile: UserLevelProfile,
  recommendedPlan?: {
    programId: string,
    programName: Json,
    enrolled: boolean  // auto-enrolled if no active plan
  }
}
```

### 9.2 Enhanced Home API

**File:** `mobile-home.controller.ts`

Current `GET /api/mobile/home` returns basic stats.  
Enhanced response:

```typescript
{
  user: { name, level, bodyScore, levelProgress },
  trainMode: {
    status: 'no_assessment' | 'no_plan' | 'active' | 'rest_day' | 'program_complete' | 'reassessment_due',
    activeProgram?: { name, weekNumber, totalWeeks },
    todayWorkout?: { name, exerciseCount, estimatedMinutes, plannedWorkoutId },
    weekProgress: { completed: number, total: number },
    nextPlannedWorkout?: { dayName, dayNumber }
  },
  stats: {
    totalWorkoutExecutions: number,
    avgFormScore: number,
    streak: number,
    thisWeekExecutions: number
  },
  recentWorkoutExecutions: Array<{ exerciseName, formScore, reps, date }>,
  alerts: Array<{
    type: 'reassessment_due' | 'progression_applied' | 'level_up' | 'streak_risk',
    message: string,
    actionUrl?: string
  }>
}
```

### 9.3 Explore Workout Support

**New endpoint:** `POST /api/mobile/planned-workouts/explore`

For multi-exercise planned workouts done in Explore mode:
```typescript
{
  executions: Array<{
    exerciseId: string,
    // ... same as regular planned workout fields
  }>,
  workoutId?: string,        // if started from a workout template
  isCustomized: boolean,     // if user modified the workout
  customizations?: Json      // what they changed
}
```

Saves multiple `WorkoutExecution` records linked by a shared `groupId` (new field).

### 9.4 WorkoutExecution Enhancement

**Schema change — add grouping for multi-exercise free workouts:**

```prisma
model WorkoutExecution {
  // ... existing fields ...
  
  groupId    String?   // Links planned workouts done together (explore mode)
  context    String    @default("free") // free | program | assessment | explore_workout
  workoutId  String?   // If done from a workout template
}
```

### 9.5 Progression Notification API

**New endpoint:** `GET /api/mobile/progression/recent`

Returns recent progression changes the user hasn't seen:

```typescript
{
  changes: Array<{
    id: string,
    exerciseName: string,
    field: string,        // "weightKg" | "targetReps" | "sets"
    previousValue: number,
    newValue: number,
    reason: string,
    appliedAt: DateTime,
    seen: boolean
  }>
}
```

**New endpoint:** `POST /api/mobile/progression/mark-seen`

### 9.6 Workout Training Support

**File:** `mobile-workouts.controller.ts`

**New endpoint:** `GET /api/mobile/workout-templates/:id/training-config`

Returns workout with full exercise configs for training:
```typescript
{
  workout: {
    id, name, difficulty,
    exercises: Array<{
      exerciseId: string,
      exerciseName: Json,
      variantIndex: number,
      difficulty: string,
      targetReps: number,
      sets: number,
      restBetweenSetsMs: number,
      restAfterExerciseMs: number,
      weightKg?: number,
      // Full exercise data for training
      exercise: {
        slug, countingMethod, repCountingConfig,
        poseVariants: [...],  // with tracked joints, position checks, etc.
      }
    }>
  }
}
```

---

## 10. Android Changes Required

### 10.1 Onboarding Flow Enhancement

**Files affected:**
- `SplashActivity` — check if assessment exists
- `OnboardingActivity` — add Body Scan explanation screen
- `HomeFragment` — handle `no_assessment` state

**Logic:**
```
if (user.hasCompletedAssessment) → MainContainer (normal)
else → Show "Complete Body Scan" gate on Home + unlock Explore
```

### 10.2 Home Screen Redesign

**File:** `HomeFragment`

Replace current layout with:

1. **Level Header** — from `GET /api/mobile/level-profile`
2. **Train Mode Card** — from `GET /api/mobile/home` → `trainMode`
   - Dynamic states: no_assessment, no_plan, active, rest_day, program_complete, reassessment_due
3. **Quick Stats Row** — from home API `stats`
4. **Explore Shortcuts** — static buttons to Explore tab sections
5. **Recent Activity** — from home API `recentWorkoutExecutions`
6. **Alerts Banner** — from home API `alerts` (progression changes, reassessment due)

### 10.3 Workout Training Flow (NEW)

**New/Modified Activities:**
- `WorkoutDetailActivity` — add "Start Workout" and "Customize" buttons
- `WorkoutCustomizeActivity` (NEW) — reorder, edit sets/reps/rest, add/remove exercises
- Reuse `WorkoutTrainingEngine` for multi-exercise workout execution
- `WorkoutReportActivity` (NEW or reuse `PlannedWorkoutReportActivity`)

**Flow:**
```
WorkoutDetailActivity
    → [Customize] → WorkoutCustomizeActivity → modified exercise list
    → [Start] → TrainingActivity (workout run mode with workout exercises)
    → WorkoutReportActivity (summary)
```

### 10.4 Quick Start Flow (NEW)

**New Activity:** `QuickStartActivity`

```
ExploreFragment → Quick Start button
    → QuickStartActivity (exercise multi-picker + customize)
    → TrainingActivity (workout run mode)
    → Workout report
```

Reuses:
- `ExercisesFragment` component for exercise selection
- `WorkoutTrainingEngine` for multi-exercise execution
- `WorkoutReportActivity` for summary

### 10.5 Explore Tab Enhancement

**File:** `ExploreFragment`

Restructure into clear sections:

```
┌─────────────────────────────────┐
│  EXPLORE                         │
│                                 │
│  [Search exercises...]           │
│                                 │
│  ── Quick Actions ──            │
│  [Quick Start]  [Body Scan]      │
│                                 │
│  ── Exercises ──                │
│  Horizontal cards by category    │
│  [See All →]                    │
│                                 │
│  ── Workouts ──                 │
│  Vertical list with filters      │
│  [See All →]                    │
│                                 │
│  ── Your Favorites ──           │
│  Recent/bookmarked exercises     │
└─────────────────────────────────┘
```

### 10.6 Train Tab Clarification

**File:** `TrainFragment`

This tab is exclusively for the Active Plan:

```
┌─────────────────────────────────┐
│  TRAIN                           │
│                                 │
│  (if no plan)                    │
│  "Complete Body Scan to get      │
│   your personalized plan"        │
│  [Start Body Scan]               │
│                                 │
│  (if active plan)                │
│  Program: Foundation Builder     │
│  Week 2 of 4                    │
│                                 │
│  [Mon] [Tue] [Wed✓] [Thu•] [Fri] │
│                                 │
│  Today's Planned Workouts:               │
│  ┌─────────────────────────────┐ │
│  │ Lower Body Focus            │ │
│  │ 4 exercises │ ~25 min       │ │
│  │ [START →]                   │ │
│  └─────────────────────────────┘ │
│                                 │
│  [VIEW PROGRAM OVERVIEW]         │
└─────────────────────────────────┘
```

### 10.7 Post-Workout Progression Display

After `PlannedWorkoutReportActivity`:
- Check `GET /api/mobile/progression/recent`
- If changes exist, show a bottom sheet:

```
┌─────────────────────────────────┐
│  Training Adjusted               │
│                                 │
│  Based on your performance:      │
│                                 │
│  Squat: Weight 10kg → 12.5kg ↑  │
│  Push-up: Reps 12 → 14 ↑       │
│                                 │
│  [GOT IT]                       │
└─────────────────────────────────┘
```

---

## 11. Implementation Phases

### Phase 0: Foundation Fixes (1 week)

> Connect what's already built but broken/disconnected.

| Task | Priority | Files |
|------|----------|-------|
| Fix `HomeFragment` to use real `getTodayPlan()` data | Critical | `HomeFragment`, `mobile-home.controller` |
| Fix `btnContinue` in Home to go to actual today's planned workout | Critical | `HomeFragment` |
| Fix `completePlannedWorkoutReport` to pass `exerciseId` to progression | High | `workout-executions.service.ts` |
| Remove mock data from Home screen | High | `HomeFragment` |
| Fix `active-plan.getTodayPlan()` to handle all states | High | `active-plan.service.ts` |

### Phase 1: Train Mode — Clear Path (2 weeks)

> Make Train Mode a smooth, guided experience.

| Task | Priority |
|------|----------|
| Enhanced Home API with `trainMode` states | Critical |
| Home Screen redesign (Level header, Today's Mission card, states) | Critical |
| Body Scan gate for Train Mode (UI lock if no assessment) | Critical |
| Auto-prescription after assessment (backend) | Critical |
| Rest day display on Home | High |
| Program complete → Reassessment CTA | High |
| Train tab showing Active Plan with week calendar | High |
| Post-workout progression notification display | Medium |

### Phase 2: Explore Mode — Free Training (2 weeks)

> Enable full Explore mode with workout training and customization.

| Task | Priority |
|------|----------|
| Workout training flow (start workout → train → report) | Critical |
| Workout customization screen (reorder, edit, remove) | Critical |
| Quick Start flow (pick exercises → customize → train) | High |
| Explore tab redesign (sections, search, categories) | High |
| Multi-exercise planned workout report (WorkoutReportActivity) | High |
| `WorkoutExecution.context` field for tracking planned workout source | Medium |
| `WorkoutExecution.groupId` for linking explore planned workouts | Medium |
| Explore planned workout API (`POST /api/mobile/planned-workouts/explore`) | Medium |

### Phase 3: Progression & Intelligence (1-2 weeks)

> Make the system intelligent and adaptive.

| Task | Priority |
|------|----------|
| Progression notifications API and UI | High |
| Seed meaningful progression rules | High |
| Weekly progression evaluation (week_completed trigger) | High |
| Reassessment auto-scheduling (after program complete) | High |
| Reassessment flow in Android (prompt → Body Scan → results → new plan) | High |
| Level up celebration screen | Medium |
| Progression history view in Reports | Medium |

### Phase 4: Polish & Trust (1-2 weeks)

> Build trust through transparency and polish.

| Task | Priority |
|------|----------|
| "Your Path" timeline view (past → current → future) | High |
| Insights engine — meaningful tips based on trends | High |
| Safety gates enforcement in training | High |
| Onboarding screens (3 intro screens) | Medium |
| Favorites/bookmarks for exercises | Medium |
| Share/export reports | Medium |
| Streak tracking and display | Medium |

---

## 12. Success Metrics

### User Engagement

| Metric | Target |
|--------|--------|
| Body Scan completion rate (new users) | > 80% |
| Day 1 → Day 2 retention | > 60% |
| Week 1 completion (all planned workouts done) | > 50% |
| Program completion rate | > 40% |
| Reassessment completion (after program) | > 60% |

### System Quality

| Metric | Target |
|--------|--------|
| Train Mode: time from open to training start | < 30 seconds |
| Explore Mode: time from browse to training start | < 60 seconds |
| Planned Workout sync success rate | > 95% |
| Progression rule execution rate | 100% of eligible planned workouts |
| Home screen load time | < 2 seconds |

### Training Quality

| Metric | Target |
|--------|--------|
| Average form score improvement over 4 weeks | > 10% |
| Danger rep rate decrease over 4 weeks | > 30% decrease |
| ROM improvement (assessed exercises) | > 5° average |
| User level progression (assessment to reassessment) | > 0.5 level |

---

## Appendix A: Screen Map

```
Splash
  ├── Onboarding (3 screens) → SignUp/SignIn
  └── MainContainer
        ├── Home Tab (HomeFragment)
        │     ├── [Start Workout] → ProgramWorkoutActivity → TrainingActivity → PlannedWorkoutReport
        │     ├── [Body Scan] → PreScreening → AssessmentSession → AssessmentResult
        │     ├── [Reassessment] → PreScreening → AssessmentSession → AssessmentResult → New Plan
        │     └── [Explore shortcuts] → Explore Tab
        │
        ├── Train Tab (TrainFragment)
        │     ├── No plan → [Start Body Scan]
        │     ├── Active plan → Week calendar → Day planned workouts
        │     │     └── [Start] → ProgramWorkoutActivity → TrainingActivity → Report
        │     └── [Program Overview] → PlanOverviewActivity
        │
        ├── Explore Tab (ExploreFragment)
        │     ├── [Quick Start] → QuickStartActivity → TrainingActivity → Report
        │     ├── Exercises → ExercisesFragment → ExerciseDetail → PreWorkout → Training → Report
        │     ├── Workouts → WorkoutList → WorkoutDetail → [Customize] → Training → Report
        │     └── [Body Scan] → Assessment flow
        │
        └── Reports Tab (HistoryFragment)
              ├── Overview (stats, trends)
              ├── Exercises (per-exercise history)
              ├── Trends (charts, progress)
              └── Records (personal bests)
```

## Appendix B: API Endpoints Summary

### New Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/mobile/home` | Enhanced home data with trainMode states |
| POST | `/api/mobile/planned-workouts/explore` | Save grouped explore planned workouts |
| GET | `/api/mobile/workout-templates/:id/training-config` | Full workout data for training |
| GET | `/api/mobile/progression/recent` | Unseen progression changes |
| POST | `/api/mobile/progression/mark-seen` | Mark progression changes as seen |

### Modified Endpoints

| Method | Path | Change |
|--------|------|--------|
| POST | `/api/assessment` | Auto-trigger prescription after save |
| GET | `/api/mobile/home` | Add trainMode, alerts, recentWorkoutExecutions |
| GET | `/api/mobile/plan/today` | Handle all states (rest, complete, reassessment) |

## Appendix C: Database Changes Summary

```prisma
// WorkoutExecution — add context and grouping
model WorkoutExecution {
  // ... existing fields ...
  groupId    String?   // Links explore planned workouts done together
  context    String    @default("free") // free | program | assessment | explore_workout
  workoutId  String?   // If done from a workout template
}

// ProgressionHistory — add seen tracking
model ProgressionHistory {
  // ... existing fields ...
  seen      Boolean   @default(false) // For mobile notifications
}
```

---

> **This plan is the blueprint for transforming Movit from a collection of powerful systems  
> into the Gold Standard of AI-powered training platforms.**
>
> Every system we've built has a clear role.  
> Every user action has a clear path.  
> Every rep feeds the coaching loop.
