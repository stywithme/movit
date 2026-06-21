# Report Detail Page Modernization Spec

آخر تحديث: 2026-06-09

## Implementation Status

- تم تنفيذ `ReportDetailScreen` داخل `kmp-app/feature/reports` مع 4 صفحات (Overview / Form / Fatigue / Tips).
- البيانات الحقيقية عبر `SharedReportDetailRepository` → `GET /api/mobile/reports/metrics?scope=exercise`.
- Share / Export يطلقان effect إلى shell → `ShowLocalizedMessage` (placeholder موثق؛ legacy يشارك screenshot + نص).
- Joint analysis: UI جاهز + `JointMetricsDto` في DTO؛ API backend لا يُرجع joints بعد — fallback نصي موضّح.
- Preview/fixture data (`ReportDetailPreviewData`) تُحمّل من `core:resources` ar/en — للمعاينة وnon-Pro فقط.

## Current Implementation

- **Legacy:** `WorkoutReportActivity` + `ReportViewModel` + ViewPager2 (single/multi exercise).
- **KMP:**
  - `ReportDetailScreen.kt` — UI Compose
  - `ReportDetailViewModel.kt` — state + effects
  - `ReportDetailApiMapper.kt` — metrics → UI
  - `SharedReportDetailRepository.kt` — MovitData + fallback
  - `MovitInnerHost.kt` — inner route + share/export messages

## User Goals

- مراجعة أداء تمرين واحد بعد التدريب: درجة الشكل، المجموعات، الإجهاد، نصائح.
- التنقل السريع بين 4 صفحات (dots + tab labels).
- مشاركة أو تصدير التقرير (placeholder حتى ربط platform share sheet).

## Content Inventory

| صفحة | محتوى |
|------|--------|
| Overview | form score، badge، sets/reps/duration، insight |
| Form | joint bars، best/worst set compare |
| Fatigue | fatigue index، drop-off message، form-by-set chart |
| Tips | coaching tips من insights API، export CTA |

## UX Target (prototype `17-report-detail.html`)

- Float pills: back + share أعلى الشاشة.
- Page dots (8dp / 24dp selected) + tab labels.
- Hero score 56sp، `MovitTag` للـ personal best.
- Best/worst cards بخلفية `successTint` / `coralTint`.
- Bar chart مع highlight بلون success.

## Layout Spec

- Full-screen Box + overlay toolbar.
- Vertical scroll لكل صفحة؛ لا horizontal pager (tabs بديل أبسط).
- Padding أفقي `MovitSpacing.lg`، top 56dp تحت float pills.

## Data & API

```
GET /api/mobile/reports/metrics
  ?programId=…&scope=exercise&exerciseSlug=…
```

Mapper fields: `averageFormScore`, `sets`, `dropOffRate`, `formRating`, `insights`, optional `jointBreakdown`.

## Accessibility

- Tab role + `contentDescription` على page labels.
- Heading semantics على form score hero.
- Joint rows، rep compare، fatigue card، bar chart — أوصاف مجمّعة ar/en.
- Float pills: back/share `contentDescription`.

## i18n

- جميع UI strings في `core:resources` (`report_detail_*`).
- Preview keys: `report_detail_preview_*` (debug/fixture فقط).

## Tests

- `ReportDetailStateTest` — preview load، page selection، Arabic preview.
- `ReportDetailApiMapperTest` — metrics mapping، joint breakdown.

## Known Gaps / Deferred

- Share/export platform integration (Phase 06+).
- Joint data من backend عند توفر الحقل.
- Multi-exercise vertical pager (legacy mode) — خارج نطاق Report Detail per-exercise.

## Scorecard Target

**92%+** — Functional 92 · Visual 90 · DS 93 · i18n 95 · A11y 90 · Tests 85 · iOS 100.
