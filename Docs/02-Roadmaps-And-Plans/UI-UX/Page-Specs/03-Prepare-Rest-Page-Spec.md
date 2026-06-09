# 03 — Prepare & Rest Page Spec

آخر تحديث: 2026-06-09

## Implementation Status

- `ExercisePrepareScreen` + `ExercisePrepareViewModel` في `feature:library`.
- حالتان UI: **Prepare** (قبل التمرين) و **Rest** (مؤقت راحة ثابت).
- Start → `MovitAppShellEffect.LaunchLegacyCameraTraining` → `LegacyTrainingLauncher` على Android debug pilot.
- لا كاميرا حية داخل KMP؛ المرآة لـ `PreWorkoutActivity` → `TrainingActivity`.

## Legacy Reference

| Legacy | الدور |
|--------|------|
| `PreWorkoutActivity` | معاينة التمرين، تعليمات، عضلات، إعداد كاميرا، Start Camera |
| `TrainingWorkoutModeController` | مؤقت الراحة أثناء الجلسة (Phase 16) |

**KMP scope (صفحة 03 فقط):** pre-workout + rest timer UI — ليس camera overlay ولا workout sequencer.

## Prototype Reference

`Docs/.../prototypes/03-prepare.html` — `state-switch`: `prepare` / `rest`.

## User Goals

- مراجعة التمرين القادم (اسم، فئة، sets/reps/rest، معدات).
- فهم وضعية الكاميرا قبل البدء (3-axis merged + distance tip).
- قراءة تعليمات مرقّمة وعضلات مستهدفة.
- في الراحة: رؤية التمرين التالي + مؤقت + Skip / +15 / Pause (عرض ثابت مقبول في Phase 05).

## Content Inventory

- Hero preview (placeholder حرف/صورة لاحقاً).
- Stats strip: Sets · Reps/Duration · Rest · Equipment.
- Camera setup card (axes + distance).
- Instructions (numbered).
- Target muscle chips.
- Prepare dock: Ready to train + session summary + **Start**.
- Rest dock: `MM:SS` + Pause + **+15** + **Skip** + شارة Up Next.

## UX Target

| حالة | Header | Progress | Dock |
|------|--------|----------|------|
| Prepare | Prepare Workout | ~20% | Ready to train + Start |
| Rest | Rest Interval | ~40% | Timer controls + Skip |

`Start` يطلق legacy camera training عبر shell effect (Android) أو رسالة bridge غير متاح (iOS / بدون handler).

## Layout Spec

- `MovitInnerPageHeader` + `MovitProgressBar`.
- Scroll: hero → title → `MovitStatTileRow` → setup card → instructions → muscles.
- Bottom: `PrepareStartDock` أو `RestTimerDock` (ليس `MovitActionDock` بمؤقت 0:00).

## Strings (`prepare_*`)

مفاتيح في `:core:resources` — EN + AR. يُعاد استخدام `session_ready_to_train` و `session_start` في dock التحضير.

## Tests

`ExercisePrepareStateTest` — تحميل preview، rest mode، عناصر تحكم الراحة، `legacySlug`، `formatRestTimer`.

## Out of Scope (Phase 05)

- عدّاد حي للراحة (tick كل ثانية).
- تحميل `ExerciseConfig` من assets/API.
- اختيار pose variant متعدد (legacy buttons).
- دخول Rest تلقائياً من sequencer (Phase 16).

## Verification

```text
:feature:library:testDebugUnitTest
:feature:shell:compileKotlinIosSimulatorArm64
```

Visual: Explore/Library → تمرين → Prepare؛ adb pilot → Start → `TrainingActivity`.
