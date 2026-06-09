# Reports Page Modernization Spec

آخر تحديث: 2026-06-09

## Implementation Status

- تم تنفيذ `MovitReportsScreen` داخل `android-poc/feature/reports`.
- مربوطة في `feature:shell` كتبويب `MovitAppDestination.Reports`.
- الربط مع API عبر `SharedReportsRepository` + `MovitData.reports.syncDashboard`.
- التنقل إلى Report Detail (17) عبر `MovitReportsEffect.OpenReportDetail` → `MovitInnerRoute.ReportDetail`.
- Pro gate عبر `platform.isProUser()` قبل جلب البيانات.

## Current Implementation

### Legacy

- `HistoryFragment` — hub مع `SwipeRefreshLayout` + `TabLayout` (underline) + `ViewPager2`.
- `ReportsHubViewModel` — حالات Idle/Loading/Locked/Empty/Success/Error.
- تبويبات فرعية: `ReportsOverviewFragment`, `ReportsExercisesFragment`, `ReportsTrendsFragment`.

### KMP

| ملف | دور |
|-----|-----|
| `MovitReportsScreen.kt` | UI: 3 تبويبات، KPI، charts، Pro/empty states |
| `MovitReportsViewModel.kt` | تحميل، تبويب، effects |
| `MovitReportsRoute.kt` | ربط VM + refresh/retry |
| `SharedReportsRepository.kt` | Pro gate + API + cache fallback |
| `ReportsApiMapper.kt` | DTO → `ReportsDashboardUi` |

## User Goals

- مراجعة أداء التدريب عبر نظرة عامة، تمارين، واتجاهات.
- فهم KPIs (أيام، تكرارات، حجم، وقت).
- الانتقال لتفاصيل تمرين محدد (صفحة 17).
- تحديث البيانات بسحب للأسفل (مطابقة legacy).

## UX Target (prototype `09-reports.html`)

- Header: `Reports` + `Performance & progress`.
- تبويبات **underline** (ليس pill): Overview · Exercises · Trends.
- حالات: loading (مع تلميح pull-to-refresh)، empty، locked (Pro)، data.
- Overview: KPI grid + form journey line + weekly bars.
- Exercises: قائمة بـ score pills ملونة + جلسات.
- Trends: insight + improvement rate + volume bars + attendance + fatigue card.

## Layout Spec

- `MovitScaffold` + `MovitUnderlineTabRow` + `PullToRefreshBox` + scroll عمودي.
- لا `MovitTheme` داخل الشاشة (حدود theme من shell).
- Charts عبر `MovitLineChart` / `MovitBarChart` مع `contentDescription` على البطاقات.

## Events & Effects

| Event | Effect / سلوك |
|-------|----------------|
| `TabSelected` | تحديث التبويب في state |
| `RefreshRequested` | `load(isRefresh = true)` |
| `RetryClicked` | `load(isRefresh = false)` |
| `ExerciseReportClicked` | `OpenReportDetail(reportId)` |
| `StartTrainingClicked` | `OpenTrain` |
| `UpgradeClicked` | `OpenUpgrade` |

## Data Contract

- Endpoint: reports dashboard (`ReportsDashboardApiResponse`).
- Mapper يملأ: KPIs، `formScoreByWeek`، `attendanceByWeek`، `volumeByWeek`، exercises، insights.
- `improvementRatePercent` يُحسب من أول/آخر نقطة في `formScoreByWeek`.

## Tests

- `MovitReportsStateTest` — load، refresh، tabs، effects.
- `ReportsApiMapperTest` — KPIs، improvement rate.
- `MovitReportsThemeBoundaryTest` — لا MovitTheme في الشاشة.
- `MovitAppShellStateTest` — `OpenReportDetail` يدفع inner route.

## فجوات متبقية

- Share/Export من hub (غير مطلوب في 09).
- Upsell flow كامل لـ Pro (يفتح subscription عبر shell effect).
- A11y متقدمة على charts (قراءة قيم نقطة بنقطة).
- Prototype state `NoActiveProgram` — legacy فقط؛ KMP يعرض Empty.

## Gradle / iOS

- `:feature:reports` — `androidTarget` + `iosArm64` + `iosSimulatorArm64`.
- لا `iosX64`.
- تحقق: `:feature:reports:testDebugUnitTest` + `compileKotlinIosSimulatorArm64`.
