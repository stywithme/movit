> **Status:** `ARCHIVED` — implemented or superseded; not current product truth.
> **Current SSOT:** `Docs/00-Active-Reference/Contracts/Exercise-JSON-Schema.md`
> **Archived:** 2026-05-29

# 📋 Exercise Creation System - Development Plan

> **Project:** Movit Back-Admin  
> **Version:** 1.0  
> **Date:** January 2026  
> **Status:** Planning Phase

---

## 📊 Current State Analysis

### ✅ What Already Exists (Good Foundation)

| Component | Status | Details |
|-----------|--------|---------|
| **Database Schema** | ✅ Partial | `Exercise` → `PoseVariant` → `DifficultyLevel` → `Phase` → `AngleRule` |
| **Attributes System** | ✅ Complete | Flexible system for categories, muscles, equipment, tags |
| **Wizard UI** | ⚠️ Basic | 6 steps: Basic Info → Attributes → Pose Variants → Movement Config → Messages → Review |
| **API Endpoints** | ✅ Functional | CRUD operations for exercises |

### ❌ Gaps to Address (Android JSON Schema Compatibility)

| Feature | Required by Android | Currently Exists? | Priority |
|---------|---------------------|-------------------|----------|
| `upRange` / `downRange` per difficulty | ✅ Yes | ❌ No (uses `targetAngle + tolerance`) | 🔴 High |
| `Range` for Secondary joints (capital R) | ✅ Yes | ❌ No | 🔴 High |
| `positionChecks` (7 types) | ✅ Yes | ❌ No (missing in DB & UI) | 🔴 High |
| `expectedFacingDirection` | ✅ Yes | ❌ No | 🟡 Medium |
| `minRepIntervalMs` / `maxRepIntervalMs` | ✅ Yes | ❌ No | 🟡 Medium |
| `gracePeriodMs` (for HOLD) | ✅ Yes | ❌ No | 🟡 Medium |
| `pairedWith` for joints | ✅ Yes | ⚠️ UI only (not saved in DB) | 🟢 Low |
| Auto-save as Draft | ❌ N/A | ❌ No | 🟡 Medium |

---

## 🛠️ Technology Stack

### Current Stack
```
React 19.0.0
Next.js 15.5.9
Prisma 7.2.0
TypeScript 5.7.0
TailwindCSS 4.0.0-alpha
```

### Additional Libraries to Install
```bash
# Form Management & Validation
npm install react-hook-form@^7.70.0 zod@^4.3.5 @hookform/resolvers@^5.2.2

# State Management (for wizard state persistence + auto-save)
npm install zustand@^5.0.9
```

| Library | Version | Purpose |
|---------|---------|---------|
| `react-hook-form` | ^7.70.0 | Form state management with excellent performance |
| `zod` | ^4.3.5 | TypeScript-first schema validation |
| `@hookform/resolvers` | ^5.2.2 | Zod integration for react-hook-form |
| `zustand` | ^5.0.9 | Lightweight state management for wizard persistence |

> Versions verified via `npm view` (Jan 2026).

---

## 📐 Architecture Decisions

### 1. Counting Method - Separate Step
The Counting Method selection will be a **separate step** from Basic Info because:
- It significantly changes the subsequent wizard flow
- Different counting methods require different joint configurations
- Clearer UX for understanding exercise types

### 2. Position Checks - Separate Step
Position Checks will be a **dedicated step** because:
- Complex configuration with multiple check types
- Optional but powerful feature
- Needs templates and advanced UI

### 3. Auto-Save as Draft
The wizard will **automatically save as Draft** when:
- Navigating between steps (for new exercises)
- User hasn't completed all required fields
- Exercise is already in Draft status

---

## 🔎 Contract Alignment (Must-Fix Before Implementation)

The Android contract in `Docs/Exercise-JSON-Schema.md` is the source of truth.  
This repo currently contains a few **internal codes/structures** that do not match that contract.  
We must normalize them early to avoid hidden mapping bugs.

### A) Counting method: `counter` vs `hold`

- **Android contract**: `up_down | push_pull | hold`
- **Current Back-Admin**: `up_down | push_pull | counter`
- **Observed in repo**:
  - `CountingMethodCode` is `counter | up_down | push_pull`
  - DB seed creates counting methods: `counter`, `up_down`, `push_pull`
  - Wizard UI treats `counter` as a **hold-duration** method (functionally = `hold`)
- **Decision (cleanest)**: rename canonical code `counter` → `hold` across **DB + code**, so exported JSON needs **no mapping layer**.

### B) Camera position codes: `side_left/side_right/front/back` vs `side_view/front_view/back_view`

- **Android contract** expects: `side_view | front_view | back_view`
- **Current DB** uses richer internal codes:
  - `side_left`, `side_right` (useful because required joints differ)
  - `front`, `back`
- **Decision**: keep internal codes, but add `CameraPosition.schemaCode` for export:
  - `side_left` → `side_view`
  - `side_right` → `side_view`
  - `front` → `front_view`
  - `back` → `back_view`
- API export uses `schemaCode`; Admin UI continues to display the internal camera positions normally.

### C) Feedback messages location (Variant-level vs Difficulty-level)

- **Android contract**: `feedbackMessages` live under `PoseVariant`
- **Current DB**: `FeedbackMessage` belongs to `DifficultyLevel` (duplicates the same messages 3 times)
- **Decision (clean code + correct contract)**:
  - move feedback messages to **PoseVariant-level** (single source of truth)
  - generate the Android `feedbackMessages` object directly from pose-variant messages

### D) Tracked joints list vs landmarks list (critical validation)

- **Angle-based `trackedJoints.joint`** supports a limited set (per schema doc):
  - `left_elbow`, `right_elbow`, `left_shoulder`, `right_shoulder`
  - `left_hip`, `right_hip`, `spine`
  - `left_knee`, `right_knee`, `left_ankle`, `right_ankle`
- **Landmarks in `positionChecks.landmarks.*`** support the full set (0..32) including hands/feet.
- **Decision**:
  - UI filters tracked joints to the supported **angle-joints list**
  - UI offers the full **landmarks list** for position checks
  - Validation prevents invalid configs (e.g., `spine` cannot be `primary` per schema guidance)

### E) RepCountingConfig is per difficulty (not global)

- Schema: each `difficultyLevel` has its own `repCountingConfig` including timing fields.
- **Decision**: Step 6 UI collects timing per difficulty level (and supports HOLD-specific fields like `gracePeriodMs`).
  - **HOLD note (from Android engine behavior)**: `downRange` is treated as the hold zone (`Phase.COUNT`).  
    For HOLD exercises, configure the primary joint so `upRange` and `downRange` are identical or very close (as in `plank.json`).

### F) Existing code issue to fix during implementation

- `GET /api/exercises/:id/config` currently reads `dl.startPoseAngles`, but start pose angles are stored on `PoseVariant.startPoseAngles`.  
  This must be corrected to avoid runtime/compile issues and to match the contract.

### G) API contract mismatch (current `/config` response ≠ Android schema)

- **Android contract** expects (per pose variant):
  - `trackedJoints[]` with `upRange/downRange/Range`
  - `positionChecks[]`
  - `feedbackMessages{ motivational/common_mistake/tip }`
  - `difficultyLevels[]` with `repCountingConfig` and `phases`
- **Current `/api/exercises/:id/config`** is built from `DifficultyLevel.phases` + `AngleRule` tables and does **not** include
  contract `trackedJoints`/`positionChecks` at all.
- **Decision**:
  - Introduce a dedicated mapper `modules/exercises/json-builder.ts` (DB → contract)
  - Update `GET /api/exercises/:id/config` to return **exactly** the contract output from the builder
  - Update `GET /api/exercises/published` to return contract-aligned summary fields (at least `countingMethod` & `cameraPosition` codes must be schema codes)

---

## ✅ Schema Coverage Checklist (Nothing Missing)

This is a **field-by-field** mapping from `Docs/Exercise-JSON-Schema.md` to **DB + Wizard steps + API export**.

### 1) Root Object: `ExerciseConfig`

- **`name`**: `Exercise.name` (Json) — Wizard Step 1
- **`description`**: `Exercise.description` (Json?) — Wizard Step 1
- **`instructions`**: `Exercise.instructions` (Json?) — Wizard Step 1
- **`category`**:
  - stored as FK `Exercise.categoryId` → `AttributeValue(code,name)` where attribute=`category`
  - exported as `{ code, name }`
- **`countingMethod`**:
  - stored as FK `Exercise.countingMethodId` → `AttributeValue(code)` where attribute=`counting_method`
  - exported as the **contract code**: `up_down | push_pull | hold`
- **`muscles`**: via `ExerciseAttribute` where attribute=`muscle` → export array of `code`
- **`equipment`**: via `ExerciseAttribute` where attribute=`equipment` → export array of `code`
- **`tags`**: via `ExerciseAttribute` where attribute=`tag` → export array of `code`
- **`poseVariants`**:
  - stored in `PoseVariant[]` (must be at least 1 for publishing)
  - exported as contract pose variants (see below)

### 2) `PoseVariant`

- **`name`**: `PoseVariant.name` (Json) — Wizard Step 3/4
- **`cameraPosition`**:
  - stored as FK `PoseVariant.cameraPositionId` → `CameraPosition`
  - exported from `CameraPosition.schemaCode` (**not** internal `code`)
- **`expectedFacingDirection`**:
  - stored on `PoseVariant.expectedFacingDirection` (string)
  - exported as one of: `facing_right | facing_left | facing_camera | facing_away | auto_detect`
  - default: `auto_detect` when not specified (avoid nulls in the JSON output)
- **`trackedJoints`**:
  - stored in `PoseVariant.trackedJointsConfig` (Json array) with exact contract fields:
    - primary: must have `upRange` + `downRange`
    - secondary: must have `"Range"` (capital R)
  - **validation**: restrict to supported angle joints list; prevent `spine` as primary
- **`positionChecks`**:
  - stored in `PositionCheck[]` per poseVariant
  - exported with the same shape as schema (id/type/landmarks/condition/activePhases/errorMessage/severity/cooldownMs/minErrorFrames)
  - `activePhases` must match Android phase names exactly (including `count` for HOLD)
- **`feedbackMessages`**:
  - stored at pose-variant level (after schema update) and exported as:
    - `motivational: LocalizedText[]`
    - `common_mistake: LocalizedText[]`
    - `tip: LocalizedText[]`
- **`difficultyLevels`**:
  - must exist for all 3: beginner/normal/advanced
  - exported using `DifficultyLevel` rows (see below)

### 3) `DifficultyLevel`

- **`level`**:
  - stored as FK `DifficultyLevel.difficultyTypeId` → `AttributeValue(code)` where attribute=`difficulty_type`
  - exported as `beginner | normal | advanced`
- **`repCountingConfig`**:
  - stored on `DifficultyLevel.repCountingConfig` (Json) and exported **as-is**
  - required fields by counting method:
    - `up_down` / `push_pull`: `reps` (required), `minRepIntervalMs` (optional), `maxRepIntervalMs` (optional)
    - `hold`: `duration` (required, seconds), `gracePeriodMs` (optional)
  - **important**: do not output `null` values; omit keys instead
- **`phases`**:
  - contract is `string[]`
  - exported from phase templates based on counting method:
    - `up_down`: `["start","down","bottom","up"]`
    - `push_pull`: `["start","push","extended","pull"]`
    - `hold`: `["hold"]` (matches existing Android assets; Android engine doesn't use the names, only the array size)
  - **important**: for HOLD exercises, **position checks** must use `activePhases: ["count", ...]` (optionally include `"hold"` as an alias, but never rely on it alone)

### 4) Validation Rules (prevent Android runtime crashes)

- Never send `null` for enums/arrays/json objects in exported contract.
- `poseVariants` must have at least one entry.
- `difficultyLevels` must include all three difficulties (beginner/normal/advanced).
- Primary joints must have `upRange` and `downRange` per difficulty.
- Secondary joints must have `"Range"` (capital R) per difficulty.
- `activePhases` values must match Android phases (`idle,start,down,bottom,up,push,extended,pull,count`).
- If multiple **primary** joints are used, keep their ranges very close:
  - Android uses thresholds from the **first** primary joint, but computes an **average angle** across primary joints.
  - Mismatched ranges between primary joints can cause unstable counting/validation.

---

## 🧼 Clean Code Guardrails (Implementation Rules)

- **Single source of truth**:
  - Contract fields live in contract-shaped storage (`trackedJointsConfig`, `positionChecks`, pose-variant feedback messages, difficulty repCountingConfig).
  - Derived/legacy representations (like `startPoseAngles`, `primaryJoint`, or phase/rule tables) must be **generated**, not edited separately.
- **Clear layer boundaries**:
  - `API routes` validate input/output (Zod) and call services.
  - `services` handle persistence (Prisma) and do not depend on React/UI.
  - `json-builder.ts` is a pure mapper: DB → Android contract JSON.
- **No silent mapping**:
  - Normalize codes (`hold`, `*_view`) at the DB model level (`schemaCode`, counting method rename) to avoid fragile conversion logic scattered across code.
- **Avoid duplication**:
  - Do not store the same data in 2 places unless one is explicitly derived and regenerated on write.
- **Strict typing**:
  - Create `android-schema.ts` types that match the contract exactly (including `"Range"` casing) and use them in the exporter.

## 📁 File Structure (New/Modified)

```
Back-Admin/
├── prisma/
│   └── schema.prisma                    # ⚡ UPDATE - Add PositionCheck, update fields
│
├── src/
│   ├── app/
│   │   └── admin/
│   │       └── exercises/
│   │           ├── new/
│   │           │   └── page.tsx         # ⚡ REWRITE - New wizard implementation
│   │           └── [id]/
│   │               └── edit/
│   │                   └── page.tsx     # ⚡ UPDATE - Support new structure
│   │
│   ├── components/
│   │   └── wizard/
│   │       ├── WizardContext.tsx        # 🆕 NEW - Zustand store for wizard state
│   │       ├── WizardStepper.tsx        # ⚡ UPDATE - New step indicators
│   │       ├── AutoSaveIndicator.tsx    # 🆕 NEW - Shows save status
│   │       └── steps/
│   │           ├── index.ts
│   │           ├── BasicInfoStep.tsx    # ⚡ UPDATE - Simplified
│   │           ├── CountingMethodStep.tsx       # 🆕 NEW
│   │           ├── CameraPositionStep.tsx       # ⚡ RENAME from PoseVariantsStep
│   │           ├── JointConfigStep/             # 🆕 NEW - Directory
│   │           │   ├── index.tsx
│   │           │   ├── PrimaryJointCard.tsx
│   │           │   ├── SecondaryJointCard.tsx
│   │           │   ├── RangeInputsGrid.tsx      # upRange/downRange/Range inputs
│   │           │   └── JointPairSelector.tsx
│   │           ├── PositionChecksStep/          # 🆕 NEW - Directory
│   │           │   ├── index.tsx
│   │           │   ├── PositionCheckCard.tsx
│   │           │   ├── PositionCheckTemplates.tsx
│   │           │   ├── LandmarkSelector.tsx
│   │           │   └── types.ts
│   │           ├── RepConfigStep.tsx            # ⚡ UPDATE from MovementConfigStep
│   │           ├── ExtrasStep.tsx               # 🆕 NEW - Merged Attributes + Messages
│   │           └── ReviewStep.tsx               # ⚡ UPDATE - Full JSON preview
│   │
│   ├── modules/
│   │   ├── exercises/
│   │   │   ├── exercises.types.ts       # ⚡ UPDATE - Full Android schema types
│   │   │   ├── exercises.service.ts     # ⚡ UPDATE - Handle new structure
│   │   │   ├── exercises.validation.ts  # 🆕 NEW - Zod schemas
│   │   │   └── json-builder.ts          # 🆕 NEW - Build Android-compatible JSON
│   │   │
│   │   └── position-checks/             # 🆕 NEW - Directory
│   │       ├── position-checks.types.ts
│   │       ├── position-checks.service.ts
│   │       └── templates.ts             # Pre-built templates
│   │
│   └── lib/
│       └── types/
│           └── android-schema.ts        # 🆕 NEW - Exact TypeScript types for Android JSON
```

---

## 🗄️ Phase 1: Database Schema Updates

### 1.0 Normalize Contract Codes (DB + Code)

- **Counting Method**: rename `counter` → `hold`
  - update the `AttributeValue.code` for the `counting_method` attribute (keep the same `id` to preserve FK references)
  - update the seed and TS types (`CountingMethodCode`)
- **CameraPosition export code**: add `schemaCode` column (see 1.1)

### 1.1 Update `CameraPosition` Model (Export Code)

```prisma
model CameraPosition {
  id          String   @id @default(uuid())
  code        String   @unique           // internal: side_left, side_right, front, back
  schemaCode  String                    // exported/mobile: side_view, front_view, back_view
  name        Json     // { "ar": "...", "en": "..." }
  description Json?    // { "ar": "...", "en": "..." }
  imageUrl    String?
  isActive    Boolean  @default(true)
  sortOrder   Int      @default(0)
  createdAt   DateTime @default(now())
  updatedAt   DateTime @updatedAt

  joints       CameraPositionJoint[]
  poseVariants PoseVariant[]

  @@map("camera_positions")
}
```

> This keeps the internal camera catalog intact while guaranteeing the Android contract output.

### 1.2 Update `PoseVariant` Model

```prisma
model PoseVariant {
  id                      String   @id @default(uuid())
  exerciseId              String
  cameraPositionId        String
  name                    Json     // { "ar": "...", "en": "..." }
  description             Json?
  referenceImageUrl       String?
  
  // 🆕 NEW FIELDS
  expectedFacingDirection String?  // 'facing_right' | 'facing_left' | 'facing_camera' | 'facing_away' | 'auto_detect'
  
  // 📝 UPDATED - Full tracked joints with upRange/downRange/Range structure
  trackedJointsConfig     Json?    // Array of TrackedJoint objects (see below)
  
  sortOrder               Int      @default(0)
  createdAt               DateTime @default(now())
  updatedAt               DateTime @updatedAt

  exercise         Exercise          @relation(fields: [exerciseId], references: [id], onDelete: Cascade)
  cameraPosition   CameraPosition    @relation(fields: [cameraPositionId], references: [id])
  difficultyLevels DifficultyLevel[]
  positionChecks   PositionCheck[]   // 🆕 NEW RELATION

  @@index([exerciseId])
  @@index([cameraPositionId])
  @@map("pose_variants")
}
```

### 1.3 New `PositionCheck` Model

```prisma
/// Position-based validation checks (knee over toe, hip alignment, etc.)
model PositionCheck {
  id              String   @id @default(uuid())
  poseVariantId   String
  checkId         String   // Unique within variant (e.g., "knee_over_toe")
  
  type            String   // PositionCheckType enum as string
  landmarks       Json     // { "primary": "left_knee", "secondary": "left_foot_index", "tertiary"?: "...", "quaternary"?: "..." }
  condition       Json     // { "operator": "should_not_exceed", "thresholds": { "beginner": 0.08, "normal": 0.05, "advanced": 0.03 } }
  activePhases    Json     // ["down", "bottom"] - Array of phase names
  errorMessage    Json     // { "ar": "...", "en": "..." }
  
  severity        String   @default("warning") // 'error' | 'warning' | 'tip'
  cooldownMs      Int      @default(2000)
  minErrorFrames  Int      @default(3)
  sortOrder       Int      @default(0)
  
  createdAt       DateTime @default(now())
  updatedAt       DateTime @updatedAt

  poseVariant PoseVariant @relation(fields: [poseVariantId], references: [id], onDelete: Cascade)

  @@unique([poseVariantId, checkId])
  @@index([poseVariantId])
  @@map("position_checks")
}
```

### 1.4 Update `DifficultyLevel` Model

```prisma
model DifficultyLevel {
  id               String   @id @default(uuid())
  poseVariantId    String
  difficultyTypeId String
  name             Json
  description      Json?
  
  // 📝 KEEP - ROM config for general reference
  romConfig        Json?    // { "targetAngle": 90, "tolerance": 10 }
  
  // 📝 UPDATED - Full rep counting config matching Android schema
  repCountingConfig Json?   // {
                            //   "reps": 12,
                            //   "duration": 30,           // For HOLD only
                            //   "minRepIntervalMs": 1500,
                            //   "maxRepIntervalMs": 5000,
                            //   "gracePeriodMs": 2500     // For HOLD only
                            // }
  
  sortOrder        Int      @default(0)
  createdAt        DateTime @default(now())
  updatedAt        DateTime @updatedAt

  poseVariant      PoseVariant       @relation(fields: [poseVariantId], references: [id], onDelete: Cascade)
  difficultyType   AttributeValue    @relation("DifficultyLevelType", fields: [difficultyTypeId], references: [id])
  phases           Phase[]
  feedbackMessages FeedbackMessage[]

  @@index([poseVariantId])
  @@index([difficultyTypeId])
  @@map("difficulty_levels")
}
```

### 1.5 Update Feedback Messages Storage (PoseVariant-level)

Android expects variant-level messages. The current DB stores messages at difficulty-level.  
To match the contract and avoid duplication:

- Move `FeedbackMessage` to belong to `PoseVariant` (recommended), **or**
- Create a new `PoseVariantFeedbackMessage` model and deprecate the old one.

> Implementation should pick **one** of the above (no dual-parent model) to keep the schema clean.

### 1.6 TrackedJoint JSON Structure (stored in `trackedJointsConfig`)

```typescript
interface TrackedJoint {
  joint: string;                    // "left_knee", "right_knee", etc.
  role: "primary" | "secondary";
  startPose: { min: number; max: number };
  pairedWith?: string;              // "right_knee" for left_knee
  
  // For PRIMARY joints only (required)
  upRange?: {
    beginner: { min: number; max: number };
    normal: { min: number; max: number };
    advanced: { min: number; max: number };
  };
  downRange?: {
    beginner: { min: number; max: number };
    normal: { min: number; max: number };
    advanced: { min: number; max: number };
  };
  
  // For SECONDARY joints only (required). Note: capital R in JSON!
  Range?: {
    beginner: { min: number; max: number };
    normal: { min: number; max: number };
    advanced: { min: number; max: number };
  };
  
  errorMessages: {
    tooLow: { ar: string; en: string };
    tooHigh: { ar: string; en: string };
  };
}
```

> Validation rule: primary joints must have `upRange` + `downRange`; secondary joints must have `Range`.

---

## 🖥️ Phase 2: Wizard UI Redesign

### 2.1 New Wizard Steps (8 Steps Total)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Exercise Creation Wizard                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  [1]──[2]──[3]──[4]──[5]──[6]──[7]──[8]                                 │
│   │    │    │    │    │    │    │    │                                  │
│   ▼    ▼    ▼    ▼    ▼    ▼    ▼    ▼                                  │
│ Basic Type Camera Joints Checks Reps Extras Review                      │
│ Info       Position      Position    Config                             │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ Auto-save: Draft saved 2 seconds ago                        ⟳   │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Step Details

#### Step 1: Basic Info
```
┌─────────────────────────────────────────────────────────────────┐
│ Step 1: Basic Information                                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Exercise Name *                                                 │
│  ┌─────────────────────────┐ ┌─────────────────────────┐        │
│  │ EN: Squat               │ │ AR: القرفصاء            │        │
│  └─────────────────────────┘ └─────────────────────────┘        │
│                                                                  │
│  Description                                                     │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ EN: Exercise to strengthen legs and glutes              │    │
│  └─────────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ AR: تمرين لتقوية عضلات الأرجل والمؤخرة                  │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  Instructions                                                    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ EN: Stand straight, then slowly lower...                │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  Category *                                                      │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ ▼ Legs                                                  │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│                                        [Previous] [Next →]       │
└─────────────────────────────────────────────────────────────────┘
```

#### Step 2: Counting Method (Exercise Type)
```
┌─────────────────────────────────────────────────────────────────┐
│ Step 2: Exercise Type                                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  How is this exercise counted? *                                 │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ 🔄 UP_DOWN                                        ○ Select  ││
│  │━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━││
│  │ Flow: START → DOWN → BOTTOM → UP → START                    ││
│  │ Rep counted when: returning to START position               ││
│  │                                                             ││
│  │ Examples: Squat, Lunge, Bicep Curl, Deadlift               ││
│  │                                                             ││
│  │ You'll configure:                                           ││
│  │ • upRange (standing position angles)                        ││
│  │ • downRange (bent/lowered position angles)                  ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ 🔃 PUSH_PULL                                      ○ Select  ││
│  │━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━││
│  │ Flow: START → PUSH → EXTENDED → PULL → START                ││
│  │ Rep counted when: completing PULL back to START             ││
│  │                                                             ││
│  │ Examples: Push-up, Pull-up, Bench Press, Shoulder Press     ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ ⏱️ HOLD                                           ○ Select  ││
│  │━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━││
│  │ Flow: IDLE ↔ COUNT (timer-based)                            ││
│  │ Success when: holding position for target duration          ││
│  │                                                             ││
│  │ Examples: Plank, Wall Sit, Dead Hang, L-Sit                 ││
│  │                                                             ││
│  │ You'll configure:                                           ││
│  │ • Hold duration per difficulty level                        ││
│  │ • gracePeriodMs (allowed break time)                        ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│                                        [← Previous] [Next →]     │
└─────────────────────────────────────────────────────────────────┘
```

#### Step 3: Camera Position
```
┌─────────────────────────────────────────────────────────────────┐
│ Step 3: Camera Position                                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Select Camera Position(s) *                                     │
│  (You can select multiple for different pose variants)          │
│                                                                  │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐          │
│  │   [Image]     │ │   [Image]     │ │   [Image]     │          │
│  │               │ │               │ │               │          │
│  │  Side View    │ │  Front View   │ │  Back View    │          │
│  │  ☑ Selected   │ │  ☐ Select     │ │  ☐ Select     │          │
│  └───────────────┘ └───────────────┘ └───────────────┘          │
│                                                                  │
│  ─────────────────────────────────────────────────────────────   │
│                                                                  │
│  Expected Facing Direction                                       │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ ◉ Auto Detect                                           │    │
│  │ ○ Facing Right                                          │    │
│  │ ○ Facing Left                                           │    │
│  │ ○ Facing Camera                                         │    │
│  │ ○ Facing Away                                           │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ⓘ Auto Detect works for most exercises. Specify direction     │
│    only for exercises requiring specific orientation.           │
│                                                                  │
│                                        [← Previous] [Next →]     │
└─────────────────────────────────────────────────────────────────┘
```

#### Step 4: Joint Configuration (Most Complex)
```
┌─────────────────────────────────────────────────────────────────┐
│ Step 4: Joint Configuration                                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─ Pose Variant: Side View ─────────────────────────────────┐  │
│  │                                                           │  │
│  │ 🎯 PRIMARY JOINTS (Required for rep counting)             │  │
│  │ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │  │
│  │                                                           │  │
│  │ ┌──────────────────────────────────────────────────────┐  │  │
│  │ │ Left Knee + Right Knee (Paired)              [×]     │  │  │
│  │ │──────────────────────────────────────────────────────│  │  │
│  │ │                                                      │  │  │
│  │ │ Start Pose: 120° - 180°                              │  │  │
│  │ │                                                      │  │  │
│  │ │ UP Range (Standing):                                 │  │  │
│  │ │ ┌────────────┬────────────┬────────────┐             │  │  │
│  │ │ │ Beginner   │ Normal     │ Advanced   │             │  │  │
│  │ │ │ 135° - 180°│ 140° - 180°│ 145° - 180°│             │  │  │
│  │ │ └────────────┴────────────┴────────────┘             │  │  │
│  │ │                                                      │  │  │
│  │ │ DOWN Range (Squat position):                         │  │  │
│  │ │ ┌────────────┬────────────┬────────────┐             │  │  │
│  │ │ │ Beginner   │ Normal     │ Advanced   │             │  │  │
│  │ │ │ 50° - 110° │ 40° - 100° │ 35° - 95°  │             │  │  │
│  │ │ └────────────┴────────────┴────────────┘             │  │  │
│  │ │                                                      │  │  │
│  │ │ Error Messages:                                      │  │  │
│  │ │ Too High: "Go lower" / "انزل أكثر"                   │  │  │
│  │ │ Too Low: "Don't go too low" / "لا تنزل كثيراً"       │  │  │
│  │ └──────────────────────────────────────────────────────┘  │  │
│  │                                                           │  │
│  │ [+ Add Primary Joint] [+ Add Paired Joints (L+R)]         │  │
│  │                                                           │  │
│  │ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │  │
│  │                                                           │  │
│  │ 📌 SECONDARY JOINTS (Posture feedback only)               │  │
│  │                                                           │  │
│  │ ┌──────────────────────────────────────────────────────┐  │  │
│  │ │ Spine (Back alignment)                        [×]    │  │  │
│  │ │──────────────────────────────────────────────────────│  │  │
│  │ │ Start Pose: 0° - 15°                                 │  │  │
│  │ │                                                      │  │  │
│  │ │ Range (all phases):                                  │  │  │
│  │ │ ┌────────────┬────────────┬────────────┐             │  │  │
│  │ │ │ Beginner   │ Normal     │ Advanced   │             │  │  │
│  │ │ │ 0° - 20°   │ 0° - 15°   │ 0° - 10°   │             │  │  │
│  │ │ └────────────┴────────────┴────────────┘             │  │  │
│  │ │                                                      │  │  │
│  │ │ Error: "Keep your back straight"                     │  │  │
│  │ └──────────────────────────────────────────────────────┘  │  │
│  │                                                           │  │
│  │ [+ Add Secondary Joint]                                   │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│                                        [← Previous] [Next →]     │
└─────────────────────────────────────────────────────────────────┘
```

#### Step 5: Position Checks
```
┌─────────────────────────────────────────────────────────────────┐
│ Step 5: Position Checks (Optional)                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ⓘ Position checks validate body positioning beyond angles.    │
│    They provide real-time form feedback during exercise.        │
│                                                                  │
│  ┌─ Quick Templates ──────────────────────────────────────────┐ │
│  │ [Knee Over Toe] [Hip Behind Knee] [Shoulder Alignment]     │ │
│  │ [Back Straightness] [Arm Extension] [Depth Check]          │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 🎯 Knee Over Toe Check                            [×]    │   │
│  │────────────────────────────────────────────────────────── │   │
│  │                                                          │   │
│  │ Type: [Forward Comparison        ▼]                      │   │
│  │                                                          │   │
│  │ Landmarks:                                               │   │
│  │ Primary: [left_knee        ▼]                            │   │
│  │ Secondary: [left_foot_index ▼]                           │   │
│  │                                                          │   │
│  │ Condition:                                               │   │
│  │ Operator: [Should NOT Exceed ▼]                          │   │
│  │                                                          │   │
│  │ Thresholds:                                              │   │
│  │ ┌────────────┬────────────┬────────────┐                 │   │
│  │ │ Beginner   │ Normal     │ Advanced   │                 │   │
│  │ │ 0.08       │ 0.05       │ 0.03       │                 │   │
│  │ └────────────┴────────────┴────────────┘                 │   │
│  │                                                          │   │
│  │ Active Phases: [☑ down] [☑ bottom] [☐ up] [☐ start]     │   │
│  │                                                          │   │
│  │ Severity: [◉ Warning] [○ Error] [○ Tip]                 │   │
│  │                                                          │   │
│  │ Error Message:                                           │   │
│  │ EN: "Don't let your knee go past your toes"              │   │
│  │ AR: "لا تدع ركبتك تتجاوز أصابع قدميك"                    │   │
│  │                                                          │   │
│  │ Advanced: cooldownMs [2000] minErrorFrames [5]           │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  [+ Add Position Check]                                          │
│                                                                  │
│                                        [← Previous] [Next →]     │
└─────────────────────────────────────────────────────────────────┘
```

#### Step 6: Rep/Duration Configuration
```
┌─────────────────────────────────────────────────────────────────┐
│ Step 6: Rep & Timing Configuration                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    Target Reps per Difficulty                ││
│  │─────────────────────────────────────────────────────────────││
│  │                                                             ││
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐        ││
│  │  │  🟢 Beginner │ │  🔵 Normal   │ │  🔴 Advanced │        ││
│  │  │              │ │              │ │              │        ││
│  │  │  [10] reps   │ │  [12] reps   │ │  [15] reps   │        ││
│  │  │              │ │              │ │              │        ││
│  │  └──────────────┘ └──────────────┘ └──────────────┘        ││
│  │                                                             ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              Timing Settings (Per Difficulty)                 ││
│  │─────────────────────────────────────────────────────────────││
│  │                                                             ││
│  │  Beginner: minRepIntervalMs [2000]  maxRepIntervalMs [6000] ││
│  │  Normal:   minRepIntervalMs [1600]  maxRepIntervalMs [5000] ││
│  │  Advanced: minRepIntervalMs [1200]  maxRepIntervalMs [4000] ││
│  │                                                             ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ⓘ These values are stored inside each difficulty level's       │
│    `repCountingConfig` (matches Android schema).                │
│                                                                  │
│                                        [← Previous] [Next →]     │
└─────────────────────────────────────────────────────────────────┘
```

#### Step 7: Extras (Attributes + Messages)
```
┌─────────────────────────────────────────────────────────────────┐
│ Step 7: Attributes & Feedback                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─ Target Muscles ─────────────────────────────────────────┐   │
│  │ [☑ Quadriceps] [☑ Glutes] [☑ Hamstrings] [☐ Calves]     │   │
│  │ [☐ Core] [☐ Lower Back]                                  │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─ Equipment ──────────────────────────────────────────────┐   │
│  │ [☑ Bodyweight] [☐ Barbell] [☐ Dumbbell] [☐ Kettlebell]  │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─ Tags ───────────────────────────────────────────────────┐   │
│  │ [☐ Beginner Friendly] [☐ Home Workout] [☐ No Equipment] │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│                                                                  │
│  ┌─ Feedback Messages ──────────────────────────────────────┐   │
│  │                                                          │   │
│  │ 💪 Motivational:                                         │   │
│  │ ┌────────────────────────────────────────────────────┐   │   │
│  │ │ "Excellent! Keep going!" / "ممتاز! استمر!"         │ [×]│   │
│  │ │ "Great form!" / "أداء رائع!"                       │ [×]│   │
│  │ └────────────────────────────────────────────────────┘   │   │
│  │ [+ Add Motivational]                                     │   │
│  │                                                          │   │
│  │ ⚠️ Common Mistakes:                                      │   │
│  │ ┌────────────────────────────────────────────────────┐   │   │
│  │ │ "Keep your back straight" / "حافظ على استقامة ظهرك" │ [×]│   │
│  │ └────────────────────────────────────────────────────┘   │   │
│  │ [+ Add Common Mistake]                                   │   │
│  │                                                          │   │
│  │ 💡 Tips:                                                 │   │
│  │ ┌────────────────────────────────────────────────────┐   │   │
│  │ │ "Push through your heels" / "ادفع من كعبيك"        │ [×]│   │
│  │ └────────────────────────────────────────────────────┘   │   │
│  │ [+ Add Tip]                                              │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│                                        [← Previous] [Next →]     │
└─────────────────────────────────────────────────────────────────┘
```

#### Step 8: Review & Publish
```
┌─────────────────────────────────────────────────────────────────┐
│ Step 8: Review & Publish                                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─ Exercise Summary ───────────────────────────────────────┐   │
│  │ Name: Squat / القرفصاء                                   │   │
│  │ Type: UP_DOWN                                            │   │
│  │ Category: Legs                                           │   │
│  │ Camera: Side View                                        │   │
│  │ Primary Joints: left_knee, right_knee                    │   │
│  │ Secondary Joints: spine                                  │   │
│  │ Position Checks: 3                                       │   │
│  │ Difficulty Levels: beginner, normal, advanced            │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─ Validation ─────────────────────────────────────────────┐   │
│  │ ✅ All required fields completed                         │   │
│  │ ✅ At least one primary joint configured                 │   │
│  │ ✅ upRange and downRange set for all difficulty levels   │   │
│  │ ✅ Error messages provided in both languages             │   │
│  │ ⚠️ No position checks added (optional)                   │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─ Android JSON Preview ───────────────────────────────────┐   │
│  │ ▼ Click to expand full JSON                              │   │
│  │ ┌────────────────────────────────────────────────────┐   │   │
│  │ │ {                                                  │   │   │
│  │ │   "name": { "ar": "القرفصاء", "en": "Squat" },     │   │   │
│  │ │   "countingMethod": "up_down",                     │   │   │
│  │ │   "poseVariants": [...]                            │   │   │
│  │ │ }                                                  │   │   │
│  │ └────────────────────────────────────────────────────┘   │   │
│  │                                          [Copy JSON]      │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ [Save as Draft]            [← Back]         [✓ Publish]  │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Phase 3: Auto-Save Implementation

### 3.1 Zustand Store Structure

```typescript
// src/components/wizard/WizardContext.tsx

import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface WizardState {
  // Current state
  exerciseId: string | null;  // null = new exercise
  currentStep: number;
  isDirty: boolean;
  lastSaved: Date | null;
  saveStatus: 'idle' | 'saving' | 'saved' | 'error';
  
  // Form data
  basicInfo: BasicInfoData;
  countingMethod: string;
  cameraPositions: string[];
  facingDirection: string;
  trackedJoints: TrackedJoint[];
  positionChecks: PositionCheck[];
  repConfig: RepConfig;
  attributes: AttributesData;
  feedbackMessages: FeedbackMessages;
  
  // Actions
  setStep: (step: number) => void;
  updateBasicInfo: (data: Partial<BasicInfoData>) => void;
  setCountingMethod: (method: string) => void;
  // ... more actions
  
  // Auto-save
  triggerAutoSave: () => Promise<void>;
  resetWizard: () => void;
}

export const useWizardStore = create<WizardState>()(
  persist(
    (set, get) => ({
      // ... implementation
    }),
    {
      name: 'exercise-wizard',
      partialize: (state) => ({
        // Only persist form data, not UI state
        basicInfo: state.basicInfo,
        countingMethod: state.countingMethod,
        // ...
      }),
    }
  )
);
```

### 3.2 Auto-Save Logic

```typescript
// Trigger auto-save on step navigation
const handleStepChange = async (newStep: number) => {
  const { exerciseId, isDirty, triggerAutoSave, setStep } = useWizardStore.getState();
  
  if (isDirty) {
    // If new exercise and has basic info, create draft
    if (!exerciseId && basicInfo.name.en) {
      await triggerAutoSave(); // Creates new draft, stores exerciseId
    } 
    // If existing exercise, update draft
    else if (exerciseId) {
      await triggerAutoSave();
    }
  }
  
  setStep(newStep);
};
```

#### 3.3 Auto-save correctness rules (avoid duplicates)

- **Create once**: the very first save must be a single `POST /api/exercises` call.
  - Immediately store `exerciseId` in the Zustand store.
  - All later saves must use `PUT /api/exercises/:id`.
- **Prevent double-create**:
  - if `saveStatus === "saving"` block navigation OR queue the next step change until save completes.
  - never call `POST` twice even if the user clicks quickly.
- **Draft guarantee**:
  - during wizard navigation, always persist the exercise with `status: "draft"` (server-side enforced).
- **No noisy saving**:
  - auto-save triggers on **step change**, not on each keystroke.
  - optional: add a small debounce (e.g., 150–300ms) before firing the request to reduce accidental double-click bursts.

---

## 📋 Implementation Timeline

### Week 1-2: Database & Types
- [ ] Update `schema.prisma` with new models/fields:
  - [ ] `CameraPosition.schemaCode`
  - [ ] `PoseVariant.expectedFacingDirection`
  - [ ] `PositionCheck` model
  - [ ] Move feedback messages to pose-variant level (new model or refactor existing)
- [ ] Create migrations (schema) + data migrations:
  - [ ] Rename counting method code `counter` → `hold` (data update, keep IDs)
  - [ ] Backfill `CameraPosition.schemaCode` for existing rows
  - [ ] Migrate existing feedback messages (if any) from difficulty-level to pose-variant
- [ ] Create TypeScript types matching Android JSON schema
- [ ] Create `json-builder.ts` for Android contract export (single source of truth)
- [ ] Update API contract endpoints:
  - [ ] Fix `GET /api/exercises/:id/config` to return Android contract JSON via `json-builder.ts`
  - [ ] Update `GET /api/exercises/published` to use contract-aligned codes (countingMethod + cameraPosition)

### Week 3-4: Wizard Foundation
- [ ] Install and configure `react-hook-form`, `zod`, `zustand`
- [ ] Create `WizardContext` with Zustand
- [ ] Create validation schemas with Zod
- [ ] Implement auto-save logic
- [ ] Build `AutoSaveIndicator` component

### Week 5-6: Wizard Steps
- [ ] Step 1: BasicInfoStep (update)
- [ ] Step 2: CountingMethodStep (new)
- [ ] Step 3: CameraPositionStep (rename + update)
- [ ] Step 4: JointConfigStep (new, complex)
- [ ] Step 5: PositionChecksStep (new)
- [ ] Step 6: RepConfigStep (update)
- [ ] Step 7: ExtrasStep (merge)
- [ ] Step 8: ReviewStep (update)

### Week 7: Testing & Polish
- [ ] End-to-end testing
- [ ] JSON export validation
- [ ] UI polish
- [ ] Position check templates
- [ ] Documentation

---

## ✅ Success Criteria

1. **Database**: All Android JSON fields can be stored and retrieved
2. **Wizard**: 8-step wizard with clear UX per exercise type
3. **Auto-Save**: Draft saved on every step navigation
4. **JSON Export**: Generated JSON 100% compatible with Android schema
5. **Validation**: Zod schemas catch all invalid configurations
6. **Templates**: Quick-add templates for common position checks

---

## 📚 References

- [Android JSON Schema](./Exercise-JSON-Schema.md)
- [react-hook-form v7.70.0 docs](https://react-hook-form.com/)
- [Zod v4.3.5 docs](https://zod.dev/)
- [Zustand v5.0.9 docs](https://zustand-demo.pmnd.rs/)

---

> **Next Step:** Start with Phase 1 - Database Schema Updates
