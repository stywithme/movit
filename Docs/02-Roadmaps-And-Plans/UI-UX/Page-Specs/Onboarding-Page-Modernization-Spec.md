# Onboarding Page Modernization Spec (12)

آخر تحديث: 2026-06-12

## Implementation Status

- أول نسخة KMP في `kmp-app/feature/account` — `MovitOnboardingScreen` / `MovitOnboardingViewModel` / `SharedOnboardingRepository`.
- مربوطة داخل `feature:shell` عبر `MovitInnerRoute.ProfileOnboarding` (من Profile → Training profile · أو بعد Auth عند مستخدم جديد).
- حفظ الملف عبر `PUT /api/mobile/training-profile` عند `MovitDataInstall` — mapping مطابق لـ legacy `ProfileOnboardingActivity.toPayload`.
- **Scorecard:** **85%** — التفاصيل في [`Page-Scorecards.md`](../Page-Scorecards.md) §12.

## Current Implementation

### Legacy (مرجع)

| الشاشة | الملف |
|--------|-------|
| Profile onboarding | `ProfileOnboardingActivity` — معالج 7 خطوات بعد التسجيل |

### KMP

| الملف | الدور |
|-------|-------|
| `MovitOnboardingScreen.kt` | 7 خطوات UI (About → Summary) |
| `MovitOnboardingViewModel.kt` | state machine · validation · submit |
| `OnboardingData.kt` | نموذج الإجابات + `toTrainingProfileRequest()` |
| `SharedOnboardingRepository.kt` | `MovitData.account.putTrainingProfile` |
| `MovitOnboardingRoute.kt` | Route + effect collection |

## User Goals

- مستخدم جديد بعد التسجيل: يكمل ملف التدريب قبل استخدام البرامج.
- مستخدم عائد من Profile: يعدّل تفضيلات التدريب (أيام، موقع، معدات، هدف).
- إكمال واضح: ملخص نهائي ثم العودة للـ shell.

## Wizard Steps (7)

| # | الخطوة | محتوى |
|---|--------|--------|
| 1 | About you | العمر · الجنس |
| 2 | Body metrics | الطول (سم) · الوزن (كغ) |
| 3 | Experience | مستوى المقاومة · أيام/أسبوع مستهدفة |
| 4 | Goal | هدف التدريب (chips) |
| 5 | Schedule | أيام الأسبوع (Mon-first display · index 0=Sun) |
| 6 | Location & equipment | Gym / Home + معدات (home: bodyweight default) |
| 7 | Summary | مراجعة + disclaimer + Submit |

مفتاح التقدم: `OnboardingData.STEP_COUNT` = 7 · `MovitProgressBar` + `onboarding_progress_a11y`.

## Validation Parity

| الحقل | القاعدة |
|-------|---------|
| Age | 13–90 |
| Height | 120–220 cm |
| Weight | 30–200 kg |
| Home location | `bodyweight` يُضاف تلقائياً للمعدات |
| Disclaimer | مطلوب قبل الإرسال |

رسائل الأخطاء: `onboarding_error_*` (en/ar).

## API / Data

```
PUT /api/mobile/training-profile
Body: TrainingProfilePutRequest (من OnboardingData.toTrainingProfileRequest)
```

- `SharedOnboardingRepository` → `MovitData.account.putTrainingProfile`
- عند النجاح: `MovitOnboardingEffect.Completed` → shell يغلق inner route
- عند الفشل: retry banner + `isSubmitting` يعطّل Continue

## Navigation & Effects

| المصدر | الوجهة |
|--------|--------|
| Profile → Training profile | `MovitInnerRoute.ProfileOnboarding` |
| Auth post-register | `OpenOnboarding` → push onboarding |
| Completed | pop inner stack → shell |

| Effect | السلوك |
|--------|--------|
| `Completed` | shell يغلق المعالج |
| `ShowMessage` | snackbar عبر shell |

## Accessibility

- `onboarding_progress_a11y` على شريط التقدم.
- gender / weekday / location cards — semantics جزئية (**مفتوح**: font-scale QA).

## Tests (`feature:account` commonTest)

- `OnboardingDataTest` (9) — validation · payload mapping · weekdays
- `MovitOnboardingViewModelTest` (8) — خطوات · submit · retry
- `FakeOnboardingRepository` في tests فقط

تشغيل: `./gradlew :feature:account:testDebugUnitTest`

## Scorecard Target

| المجال | الدرجة |
|--------|--------|
| Functional | 92% |
| Visual | 82% |
| DS | 93% |
| i18n/RTL | 93% |
| A11y | 45% |
| Tests | 78% |
| **الإجمالي** | **85%** |

## Prototype

[`12-profile-onboarding.html`](../prototypes/12-profile-onboarding.html) — حالات الخطوات السبع.

## Remaining Gaps

- Experience step: slider جلسات/أسبوع (legacy) وليس chips مدة التدريب في prototype HTML.
- A11y كامل + font-scale QA على grids.
- Visual QA RTL/dark على Mac.

## Out of Scope (Phase 05)

- تغيير ترتيب الخطوات أو دمجها مع Assessment (13).
- رفع صورة avatar ضمن onboarding (يبقى في Profile 11).

## Definition of Done

- [x] معالج 7 خطوات + progress bar
- [x] `PUT training-profile` + field mapping legacy
- [x] validation parity (age/height/weight/home defaults)
- [x] `onboarding_*` keys (~60 movitText)
- [x] shell routing من Profile و Auth
- [x] unit tests + iOS compile
- [ ] Experience slider parity مع prototype HTML
- [ ] A11y/font-scale QA كامل
