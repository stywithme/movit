# Assessment Page Modernization Spec

آخر تحديث: 2026-06-09

## Implementation Status

- تم تنفيذ **Phase 05** لصفحة Assessment داخل `kmp-app/feature/account`.
- التدفق: **PAR-Q (7 أسئلة)** → **Body scan (placeholder UI)** → **Results** (fake أو `GET level-profile` عند `MovitDataInstall`).
- **لا كاميرا حية** — متعمد (Phase 07).
- الاختبارات: `MovitAssessmentViewModelTest` · `AssessmentApiMapperTest`.

## Current Implementation

- **Legacy:** `PreScreeningActivity` → `AssessmentSessionActivity` → `AssessmentResultActivity`
- **KMP:** `MovitAssessmentScreen` · `MovitAssessmentViewModel` · `MovitAssessmentRoute`
- **Repository:** `SharedAssessmentRepository` (يعيد استخدام `LevelProfileDetailDto` للنتائج عند توفر API)
- **Prototype:** [`13-assessment.html`](../prototypes/13-assessment.html)

## User Goals

- التأكد من السلامة عبر PAR-Q+ قبل فحص الجسم.
- إجراء body scan (واجهة فقط في Phase 05).
- رؤية body score، المستوى، درجات المناطق/المجالات، ورؤى قابلة للفهم.
- الانتقال إلى Explore أو Home بعد النتائج.

## Content Inventory

| Phase | محتوى |
|-------|--------|
| PreScreening | عنوان، تحذير PAR-Q+، 7 أسئلة نعم/لا، شريط تقدم، Continue |
| BodyScan | إطار منقط، تلميح الحركة (2/3)، Hold position، بطاقة Camera active + % |
| Results | Hero body score، domains، region tiles، insights، Browse / Home |

## UX Target

- مطابقة [`13-assessment.html`](../prototypes/13-assessment.html): hierarchy، scan stage، region good/warn tiles.
- زر رجوع في كل phase (inner back قبل shell pop).
- تحذير طبيب عند أي إجابة «نعم» — snackbar مترجم.
- نتائج من API عند تسجيل الدخول؛ وإلا `FakeAssessmentPreviewData`.

## Layout Spec

- `Column` + `verticalScroll` + `MovitSpacing.lg`.
- Header: `IconButton` back + عنوان مركزي حسب phase.
- PAR-Q: `MovitInsightCard` · `MovitProgressBar` · `MovitFilterChip` لكل سؤال.
- Body scan: `Box` ink 340dp · إطار dashed 180×280 · `MovitCard` للكاميرا.
- Results: `MovitDashboardHero` · `FlowRow` للمجالات والمناطق · `MovitCard` للرؤى.

## State & Events

- `AssessmentPhase`: PreScreening · BodyScan · Results
- Events: `ParqAnswered` · `ContinueToBodyScan` · `CompleteBodyScan` · `BackClicked` · `BrowseProgramsClicked` · `GoHomeClicked`
- Effects: `NavigateBack` · `OpenExplore` · `OpenHome` · `ShowLocalizedMessage`

## API / Data

| مصدر | استخدام |
|------|---------|
| `FakeAssessmentPreviewData` | معاينة / غير مسجّل |
| `MovitData.account.fetchLevelProfile()` | bodyScore · domains · regions · limitingFactors → `AssessmentApiMapper` |

لا upload assessment في Phase 05 (يتطلب كاميرا + engine).

## i18n

- جميع النصوص عبر `assessment_*` في `core:resources` (en + ar).
- الرؤى: مفاتيح `assessment_insight_*` مع معاملات عند الحاجة.

## Tests

- ViewModel: phases، PAR-Q warning، repository results، back navigation.
- Mapper: level label، domains، limiting insights.

## Out of Scope (Phase 07)

- `AssessmentEngine` مشترك · CameraX · MediaPipe · رفع نتائج فحص حي.

## Definition of Done

- [x] 3 phases PreScreening / BodyScan / Results
- [x] 7 أسئلة PAR-Q (parity مع legacy `ParqQuestions`)
- [x] Body scan placeholder (بدون كاميرا)
- [x] Results مع domains + regions + insights
- [x] `SharedAssessmentRepository` + mapper
- [x] ViewModel + mapper unit tests (≥50% bucket)
- [x] `contentDescription` على back + scanner
- [x] iOS compile (ضمن `feature:account`)
