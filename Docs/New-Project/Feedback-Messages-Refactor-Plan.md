# Feedback Messages Refactor Plan

## Current Architecture Analysis

### The Problem

The current system has **messages tightly coupled to exercises**. Messages are created inside each exercise, which makes reuse hard and creates duplication. Every exercise defines its own messages at multiple levels:

1. **State Messages** → per joint, per state (perfect/normal/pad/warning/danger)
2. **Position Check Messages** → per check (error messages)
3. **Feedback Messages** → per PoseVariant (motivational + tips)
4. **Hardcoded Fallbacks** → scattered across code (TrackedJoint, ReportGenerator, PerformanceMetricsBuilder, FeedbackManager)

### Current Message Flow (Illustrative, Not Fixed)

```
Exercise JSON
├── PoseVariant
│   ├── feedbackMessages
│   │   ├── motivational: [LocalizedText, ...]   ← duplicated across exercises
│   │   └── tips: [LocalizedText, ...]            ← duplicated across exercises
│   ├── TrackedJoint[]
│   │   └── stateMessages
│   │       ├── perfect: "Excellent form!"        ← example only
│   │       ├── normal: "Good, keep going"        ← example only
│   │       ├── warning: "Watch your angle"       ← example only
│   │       └── danger: "Stop! Risk of injury"    ← example only
│   └── PositionCheck[]
│       └── errorMessage: LocalizedText           ← some overlap across exercises
└── Hardcoded in code
    ├── FeedbackManager → streak msgs, hold msgs, target msgs
    ├── ReportGenerator → default tips, state msgs
    └── PerformanceMetricsBuilder → advice strings
```

### Duplication Impact

| Message Type | Estimated Duplication | Example |
|---|---|---|
| Perfect state msg | ~80% same | "Excellent form!", "Great job!" |
| Danger state msg | ~90% same | "Stop! Risk of injury" |
| Motivational msgs | ~70% same | "Keep pushing!", "You're doing great!" |
| Tips | ~50% same | "Focus on breathing", "Keep core tight" |
| Streak messages | 100% hardcoded | "Good!", "Great form!", "Excellent! Keep going!" |
| Hold messages | 100% hardcoded | "Stay in position!", "Position lost. Try again" |

---

## Proposed Architecture: Message Library System

### Core Concept

**Messages become a standalone, reusable entity** — like a "Message Library" — created independently with no fixed placement. Exercises then **assign** any message to the right context (perfect, warning, tip, etc.). This allows the same message to be reused across multiple exercises, or different messages to be chosen per exercise even for the same state.

```
┌─────────────────────────────────────┐
│         MESSAGE LIBRARY             │
│  (Centralized, Reusable, Tagged)    │
├─────────────────────────────────────┤
│  MSG_001: "Excellent form!"         │
│  MSG_002: "Watch your knee angle"   │
│  MSG_003: "Keep your back straight" │
│  MSG_004: "Great job! Keep going!"  │
│  MSG_005: "Focus on breathing"      │
│  ...                                │
└──────────┬──────────────────────────┘
           │
           │  Referenced by ID
           ▼
┌──────────────────────┐    ┌──────────────────────┐
│   Exercise: Squat    │    │  Exercise: Deadlift  │
│                      │    │                      │
│  knee.perfect → 001  │    │  hip.perfect → 001   │  ← Same message!
│  knee.warning → 002  │    │  back.warning → 003  │
│  motivational → [004]│    │  motivational → [004]│  ← Same message!
│  tips → [005]        │    │  tips → [005]        │  ← Same message!
└──────────────────────┘    └──────────────────────┘
```

---

## Database Design

### New Models

```prisma
// ===== MESSAGE LIBRARY =====

model FeedbackMessageTemplate {
  id          String   @id @default(uuid())
  
  // Content
  content     Json     // { ar: "", en: "", audioAr?: "", audioEn?: "" }
  
  // Classification
  category    String   // 'state' | 'position' | 'motivational' | 'tip' | 'system'
  context     String?  // 'perfect' | 'normal' | 'warning' | 'danger' | 'pad' | 'general'
  
  // Tagging system for smart search & filtering
  tags        String[] // ['knee', 'squat', 'form', 'angle', 'safety', ...]
  
  // Metadata
  code        String   @unique  // Human-readable code: "STATE_PERFECT_001"
  isSystem    Boolean  @default(false) // System messages (non-deletable)
  isActive    Boolean  @default(true)
  
  createdAt   DateTime @default(now())
  updatedAt   DateTime @updatedAt
  
  // Relations
  assignments FeedbackMessageAssignment[]
  
  @@index([category, context])
  @@index([isActive])
}

model FeedbackMessageAssignment {
  id          String   @id @default(uuid())
  
  // What this message is assigned to
  targetType  String   // 'joint_state' | 'position_check' | 'pose_variant' | 'exercise'
  targetId    String   // ID of the target entity
  
  // Additional context
  jointCode   String?  // For joint_state: 'left_knee', 'right_hip', etc.
  state       String?  // For joint_state: 'perfect', 'warning', etc.
  zone        String?  // For zone-specific: 'up', 'down', null (both)
  
  // Message reference
  messageId   String
  message     FeedbackMessageTemplate @relation(fields: [messageId], references: [id])
  
  // Ordering
  sortOrder   Int      @default(0)
  
  createdAt   DateTime @default(now())
  
  @@index([targetType, targetId])
  @@index([messageId])
}
```

### Migration Strategy

The existing `FeedbackMessage` model stays temporarily. New tables are added alongside. Data migration happens gradually.

---

## Message Categories & Codes

### Naming Convention

```
{CATEGORY}_{CONTEXT}_{NUMBER}

Examples:
  STATE_PERFECT_001    → "Excellent form!"
  STATE_PERFECT_002    → "Perfect execution!"
  STATE_WARNING_001    → "Watch your angle"
  STATE_DANGER_001     → "Stop! Risk of injury"
  MOT_GENERAL_001      → "Keep pushing!"
  MOT_STREAK_001       → "Great streak! Keep going!"
  TIP_BREATHING_001    → "Focus on breathing"
  TIP_CORE_001         → "Keep your core engaged"
  SYS_HOLD_START_001   → "Stay in position!"
  SYS_HOLD_FAIL_001    → "Position lost. Try again"
  SYS_TARGET_001       → "Great job! {reps} reps completed"
  POS_KNEE_001         → "Knee is going past your toes"
```

### Category Breakdown

| Category | Context | Purpose | Estimated Count |
|---|---|---|---|
| `state` | perfect, normal, pad, warning, danger | Joint state feedback | ~20-30 |
| `position` | error, warning, tip | Position check feedback | ~15-20 |
| `motivational` | general, streak, milestone | Encouragement | ~15-20 |
| `tip` | breathing, core, form, safety | Training tips | ~15-20 |
| `system` | hold, target, visibility, countdown | System events | ~10-15 |

**Total estimated: ~75-105 unique messages** (vs hundreds of duplicated ones currently)

---

## Smart Features

### 1. Template Variables (Dynamic Messages)

Messages can contain variables that are filled at runtime:

```json
{
  "code": "SYS_TARGET_001",
  "content": {
    "ar": "أحسنت! اكتملت {{reps}} تكرار",
    "en": "Great job! {{reps}} reps completed"
  }
}
```

**Supported Variables:**
| Variable | Description | Example |
|---|---|---|
| `{{reps}}` | Current rep count | 12 |
| `{{target}}` | Target reps | 15 |
| `{{seconds}}` | Hold duration in seconds | 30 |
| `{{joint}}` | Joint display name | "Knee" |
| `{{angle}}` | Current angle | 95° |
| `{{streak}}` | Consecutive correct reps | 5 |

### 2. Smart Tag System

Tags allow intelligent message selection and grouping:

```json
{
  "code": "STATE_WARNING_001",
  "tags": ["knee", "angle", "squat", "lunge", "lower-body"],
  "content": { "ar": "انتبه لزاوية الركبة", "en": "Watch your knee angle" }
}
```

**Admin UI Benefits:**
- When creating exercise → filter messages by relevant tags
- Auto-suggest messages based on selected joints

### 3. Message Variants (A/B for Engagement)

Multiple messages for the same situation, randomly selected:

```
Assignment:
  targetType: "joint_state"
  jointCode: "left_knee"
  state: "perfect"
  
  Messages (multiple assignments, same target):
    → MSG_001: "Excellent form!"        (sortOrder: 1)
    → MSG_002: "Perfect execution!"      (sortOrder: 2)
    → MSG_003: "That's how it's done!"   (sortOrder: 3)
```

**On Android:** Randomly picks one from the assigned messages → prevents repetitive feedback → better UX.

---

---

## Android JSON Output Format

### Current Format (per exercise)

```json
{
  "feedbackMessages": {
    "motivational": [
      { "ar": "استمر!", "en": "Keep going!" },
      { "ar": "أداء رائع!", "en": "Great form!" }
    ],
    "tips": [
      { "ar": "ركز على التنفس", "en": "Focus on breathing" }
    ]
  },
  "trackedJoints": [
    {
      "code": "left_knee",
      "stateMessages": {
        "perfect": { "ar": "ممتاز!", "en": "Excellent!" },
        "warning": { "ar": "انتبه للزاوية", "en": "Watch the angle" }
      }
    }
  ]
}
```

### New Format (message references + library)

**Option A: Embedded Library (Recommended for Offline)**

```json
{
  "messageLibrary": {
    "STATE_PERFECT_001": {
      "ar": "ممتاز!", "en": "Excellent!",
      "audioAr": "gs://...mp3", "audioEn": "gs://...mp3"
    },
    "STATE_PERFECT_002": {
      "ar": "أداء مثالي!", "en": "Perfect execution!",
      "audioAr": "gs://...mp3", "audioEn": "gs://...mp3"
    },
    "STATE_WARNING_001": {
      "ar": "انتبه للزاوية", "en": "Watch the angle",
      "audioAr": "gs://...mp3", "audioEn": "gs://...mp3"
    },
    "MOT_GENERAL_001": {
      "ar": "استمر!", "en": "Keep going!",
      "audioAr": "gs://...mp3", "audioEn": "gs://...mp3"
    },
    "TIP_BREATHING_001": {
      "ar": "ركز على التنفس", "en": "Focus on breathing",
      "audioAr": "gs://...mp3", "audioEn": "gs://...mp3"
    }
  },
  
  "feedbackMessages": {
    "motivational": ["MOT_GENERAL_001", "MOT_GENERAL_002"],
    "tips": ["TIP_BREATHING_001"]
  },
  
  "trackedJoints": [
    {
      "code": "left_knee",
      "stateMessages": {
        "perfect": ["STATE_PERFECT_001", "STATE_PERFECT_002"],
        "warning": ["STATE_WARNING_001"]
      }
    }
  ]
}
```

**Why Embedded Library?**
- Works offline (no API calls needed during exercise)
- Single source of truth per sync
- Audio cache can pre-download from library
- Deduplication happens at storage level (same code = cached once)

### Backward Compatibility

The `json-builder.ts` handles the transformation. Old format continues to work. New format is opt-in via API version or flag.

---

## Admin UI Changes

### New "Message Library" Page

```
/admin/messages
├── List view with filters (category, tags, search)
├── Create/Edit message
│   ├── Content (ar/en with SmartLocalizedInput)
│   ├── Category selector
│   ├── Context selector
│   ├── Tags (multi-select/create)
│   └── Audio generation (TTS button)
└── Basic list view
```

### Updated Exercise Wizard

**Current Step 6 (Extras):**
- Inline message creation (motivational + tips)

**New Step 6 (Extras):**
- **Message Picker** instead of inline creation
- Search/filter from Message Library
- Quick-add new message (creates in library + assigns)
- Drag-and-drop ordering

**Joint Config Step:**
- State message assignment via picker
- Quick preview of assigned messages
- "Use default" toggle (auto-assigns common messages)

### Message Picker Component

```
┌─────────────────────────────────────────────────┐
│  🔍 Search messages...         [Category ▼]     │
├─────────────────────────────────────────────────┤
│  ✅ STATE_PERFECT_001                           │
│     "Excellent form!" | "ممتاز!"                │
│     Tags: [general] [form]                      │
│                                                 │
│  ☐ STATE_PERFECT_002                            │
│     "Perfect execution!" | "أداء مثالي!"         │
│     Tags: [general]                             │
│                                                 │
│  ☐ STATE_PERFECT_003                            │
│     "That's how it's done!" | "هكذا يكون الأداء!"│
│     Tags: [advanced]                            │
│                                                 │
│  ──────────────────────────────────────────      │
│  + Create new message                           │
└─────────────────────────────────────────────────┘
```

---

## Android Code Changes

### New MessageLibrary Class

```kotlin
/**
 * MessageLibrary - Central repository for all feedback messages
 * 
 * Loaded once from exercise JSON, used throughout training.
 * Supports:
 * - Message lookup by code
 * - Random selection from variants
 * - Template variable substitution
 */
class MessageLibrary(
    private val messages: Map<String, LocalizedText>
) {
    
    /**
     * Get a single message by code
     */
    fun get(code: String): LocalizedText? = messages[code]
    
    /**
     * Get message with variable substitution
     */
    fun get(code: String, vars: Map<String, String>): LocalizedText? {
        val template = messages[code] ?: return null
        return template.withVariables(vars)
    }
    
    /**
     * Get random message from a list of codes
     */
    fun getRandom(codes: List<String>): LocalizedText? {
        if (codes.isEmpty()) return null
        val code = codes.random()
        return messages[code]
    }
    
    /**
     * Get all messages for a list of codes
     */
    fun getAll(codes: List<String>): List<LocalizedText> {
        return codes.mapNotNull { messages[it] }
    }
    
    companion object {
        fun fromJson(json: Map<String, Any>): MessageLibrary {
            // Parse messageLibrary from exercise JSON
        }
    }
}
```

### Updated TrackedJoint

```kotlin
// Before: Messages embedded
data class StateMessages(
    val perfect: StateMessageValue? = null,
    val warning: StateMessageValue? = null,
    // ...
)

// After: Message codes referenced
data class StateMessageRefs(
    val perfect: List<String> = emptyList(),  // ["STATE_PERFECT_001", "STATE_PERFECT_002"]
    val warning: List<String> = emptyList(),  // ["STATE_WARNING_001"]
    val danger: List<String> = emptyList(),
    val normal: List<String> = emptyList(),
    val pad: List<String> = emptyList()
)
```

### Updated FeedbackManager

```kotlin
// Before: hardcoded messages
private suspend fun triggerStreakMotivation(streak: Int) {
    val localizedText = when {
        streak >= 10 -> LocalizedText(ar = "ممتاز! استمر!", en = "Excellent! Keep going!")
        // ...
    }
}

// After: library lookup
private suspend fun triggerStreakMotivation(streak: Int) {
    val code = when {
        streak >= 10 -> "MOT_STREAK_LARGE"
        streak >= 5  -> "MOT_STREAK_MEDIUM"
        else         -> "MOT_STREAK_SMALL"
    }
    val localizedText = messageLibrary.get(code) ?: return
}
```

---

## Audio Optimization

### Current Issue
Same message text generates separate audio files for each exercise.

### With Message Library
- Audio is generated once per message code
- All exercises sharing the same message share the same audio
- Audio cache key = message code (not hash of text)

```
// Before: Audio cache keys
"hash_of_ممتاز_exercise1" → audio1.mp3
"hash_of_ممتاز_exercise2" → audio2.mp3  ← DUPLICATE!

// After: Audio cache keys  
"STATE_PERFECT_001" → audio.mp3  ← ONE file, shared
```

**Estimated Storage Saving: 40-60%** on audio cache

---

## Implementation Phases

### Phase 1: Database & API (Backend)
1. Create `FeedbackMessageTemplate` model
2. Create `FeedbackMessageAssignment` model
3. Seed default system messages (from current hardcoded values)
4. Create CRUD API for messages: `GET/POST/PUT/DELETE /api/messages`
5. Create assignment API: `POST /api/messages/assign`
6. Update `json-builder.ts` to support new format

**Estimated: 3-4 days**

### Phase 2: Admin UI (Frontend)
1. Create Message Library page (`/admin/messages`)
2. Create MessagePicker component
3. Update ExtrasStep to use MessagePicker
4. Update JointConfigStep for state message assignment
5. Add basic message list UI

**Estimated: 4-5 days**

### Phase 3: Android Integration
1. Create `MessageLibrary` class
2. Update `ExerciseConfig` parsing (support both formats)
3. Update `FormValidator` to use library lookup
4. Update `FeedbackManager` to use library (remove hardcoded msgs)
5. Update `ReportGenerator` to use library
6. Update audio cache to use message codes

**Estimated: 3-4 days**

### Phase 4: Migration & Cleanup
1. Migrate existing exercises to new format
2. Deduplicate messages (merge identical texts)
3. Generate missing audio for new messages
4. Remove deprecated code paths
5. Update sync endpoint for new format

**Estimated: 2-3 days**

---

## Summary of Benefits

| Aspect | Before | After |
|---|---|---|
| Message count (10 exercises) | ~300+ duplicated | ~80-100 unique |
| Audio files | Separate per exercise | Shared across exercises |
| Adding new exercise | Copy-paste messages | Pick from library |
| Updating a message | Edit in every exercise | Edit once, applies everywhere |
| Message quality | Inconsistent | Centralized quality control |
| Analytics | None | None |
| A/B Testing | Not possible | Multiple variants per state |
| Hardcoded messages | ~15+ scattered | 0 (all in library) |
| Localization effort | Per exercise | Per message (once) |

---

## Open Questions

1. **Versioning**: Should messages have versions? (edit message → does it affect existing exercises immediately?)
2. **Migration priority**: Which exercises to migrate first? (most used? newest?)
3. **Audio regeneration**: When message text changes, should audio be auto-regenerated?
