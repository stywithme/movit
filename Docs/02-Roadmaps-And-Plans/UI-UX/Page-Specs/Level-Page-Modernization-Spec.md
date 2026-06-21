# Level & Plan Page Modernization Spec

آخر تحديث: 2026-06-09

## Implementation Status

- تم تنفيذ `MovitLevelScreen` داخل `kmp-app/feature/account` مع تبويبي **Level profile** و **Your plan**.
- البيانات تُحمَّل عبر `SharedLevelRepository`: `GET /api/mobile/level-profile` + `GET /api/mobile/plan` + `GET /api/mobile/reassessment/upcoming` (الأخيران اختياريان للخطة وإعادة التقييم).
- عند غياب الجلسة أو فشل API يُستخدم `FakeLevelRepository` للمعاينة.
- CTA **Retake body scan** يصدر `MovitLevelEffect.OpenAssessment`؛ **Browse programs** يصدر `OpenExplore`.
- اختبارات: `MovitLevelViewModelTest`, `LevelApiMapperTest`.

## Legacy Reference

| Legacy | KMP |
|--------|-----|
| `LevelProfileActivity` | تبويب Level profile |
| `PlanOverviewActivity` | تبويب Your plan |
| `14-level-plan.html` | مرجع بصري |

## User Goals

- رؤية المستوى الحالي ودرجة الجسم وحلقة التقدم.
- فهم تفصيل المجالات (mobility, stability, strength, …).
- إعادة فحص الجسم لتحديث التقييم.
- متابعة مسار الخطة (مكتمل · قيد التنفيذ · قادم).

## Content Inventory

- Level number + name, body score ring, points to next level.
- Domain breakdown bars.
- Retake scan accent block.
- Plan timeline phases مع progress للمرحلة النشطة.
- Empty plan message عند غياب برامج.
- Browse recommended programs (outline CTA).

## API Mapping

| مصدر | حقل UI |
|------|--------|
| `level-profile` | level, bodyScore, domains |
| `plan` | `planPhases` (programs → timeline) |
| `reassessment/upcoming` | `reassessmentLabel`, highlight للمرحلة النشطة |

## Navigation

- Home → Level card → `MovitInnerRoute.LevelProfile`
- Profile → Level & plan → نفس المسار
- Effects: Assessment, Explore (programs)

## Out of Scope (Phase 05)

- Level-up celebration overlay (legacy).
- Recommended programs list row (prototype فقط).
- Region breakdown / limiting factors (legacy إضافي).

## Test Plan

- [x] ViewModel: load success/failure, tab switch, effects.
- [x] Mapper: domains, plan phases, reassessment label.
- [ ] RTL visual smoke (ar).
- [ ] Dark mode smoke.
- [ ] iOS `compileKotlinIosSimulatorArm64`.
