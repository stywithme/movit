# Training Live Screen — Movit Spec (Phase 07 WS-0)

آخر تحديث: **2026-06-11** · المالك: `feature:training` · المرجع: legacy `TrainingActivity` + نظام Movit.

## الحالات (9)

| الحالة | وصف | مكوّنات Movit المستهدفة |
|--------|-----|-------------------------|
| setup | إرشاد وضعية + تقدم % | `SetupPosePanel`, `MovitGlassMessage` |
| countdown | 3-2-1 مع تجميد | `CountdownOverlay`, `MovitGlassMessage` |
| live | HUD + كاميرا + skeleton | `TrainingHud`, `MovitScoreRing`, `MovitSkeletonOverlay` |
| paused | إيقاف يدوي | `TrainingHud` + `MovitActionDock` |
| auto-paused | NoPose / visibility | `VignetteEffect`, `MovitGlassMessage` |
| resume-setup | إعادة إعداد بعد انحراف | `SetupPosePanel` |
| resume-countdown | عدّ استئناف | `CountdownOverlay` |
| rest | راحة بين sets | `RestPanel`, `SetIndicator` |
| complete | إنهاء تمرين/جلسة | `WorkoutCompletePanel`, زر تقرير |

## تخطيط الشاشة (live)

```text
┌─────────────────────────────────────┐
│ MovitInnerPageHeader (رجوع + عنوان) │
├─────────────────────────────────────┤
│  [Camera preview full-bleed]        │
│   ├─ MovitSkeletonOverlay (Canvas)  │
│   ├─ LiveMetricsOverlay (POC اليوم) │
│   └─ MovitGlassMessage stack        │
├─────────────────────────────────────┤
│ MovitActionDock (pause · إعدادات)   │
└─────────────────────────────────────┘
```

## POC الحالي (07.1)

- `ExerciseLiveScreen`: كاميرا + `LiveMetricsOverlay` (phase, reps, form %).
- التكوين من `TrainingConfigRepository` (ليس blueprint).
- الجسر اليدوي `KmpTrainingSessionBridge` يُستبدل في WS-4.

## قبول بصري (07.4)

- RTL + Dark + font scale 200%.
- مطابقة [Visual-QA-Checklist](../Android-KMP-Mobile-Visual-QA-Checklist.md).
