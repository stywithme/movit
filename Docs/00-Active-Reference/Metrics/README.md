# Metrics — فهرس المرجع

| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Metrics documentation index |
| **Verified** | 2026-06-22 |

---

## اقرأ بالترتيب

| # | الملف | الدور |
|---|------|--------|
| 1 | **[Metrics-As-Built.md](Metrics-As-Built.md)** | Calc / UI / DB / Sync — **ابدأ هنا** |
| 2 | **[Metrics-Complete-Reference.md](Metrics-Complete-Reference.md)** | كتالوج تعريفات ومنتج (18 مقياس + composites) |
| 3 | **[Metrics-Final-Framework.md](../../02-Roadmaps-And-Plans/Metrics/Metrics-Final-Framework.md)** | نقد علمي + backlog (`ROADMAP`) |

**لا تخلط:** الكتالوج ≠ as-built. الخطة ≠ الواقع. عند التعارض → [Metrics-As-Built.md](Metrics-As-Built.md).

---

## مسار البيانات (ملخص)

```
MovitTrainingEngine / MotionRecorder
        │
        ▼
MetricsCalculator  ──► RepMetrics / WorkoutExecutionMetrics
        │
        ├──► MovitPostTrainingReportBuilderV2 + ReportQualityScoring
        │         └──► MovitPostTrainingReport (local JSON + optional legacyReport)
        │
        ├──► MovitSessionReportUiMapper ──► ReportDetailScreen (4 tabs)
        │
        └──► WorkoutUploadMapper ──► POST /mobile/workout-executions
                  └──► WorkoutExecutionMetrics + RepMetrics (Prisma)
```

**فجوة معروفة:** `velocityLoss` و `tempoConsistency` تُحسب على الجهاز ولا تُزامَن للباك إند — [Metrics-As-Built.md](Metrics-As-Built.md).

---

## صيانة

| حدث | الإجراء |
|-----|---------|
| مقياس جديد في `MetricsCalculator` | As-Built matrix + عمود في Complete-Reference index |
| تغيير عرض UI | [Post-Training-Report-Review.md](../Product-Master/Post-Training-Report-Review.md) + As-Built |
| اقتراح علمي لم يُنفَّذ | Metrics-Final-Framework فقط |
