# Metrics — فهرس المرجع

| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Metrics documentation index |
| **Verified** | 2026-05-29 |

---

## اقرأ بالترتيب

| # | الملف | الدور |
|---|------|--------|
| 1 | **[Metrics-As-Built.md](Metrics-As-Built.md)** | ما يُحسب فعلاً في الكود + خريطة الملفات (**ابدأ هنا**) |
| 2 | **[Metrics-Complete-Reference.md](Metrics-Complete-Reference.md)** | كتالوج تعريفات، أمثلة، وتشابهات (مرجع منتج/محتوى) |
| 3 | **[Metrics-Final-Framework.md](Metrics-Final-Framework.md)** | نقد علمي + **backlog** تحسينات (`ROADMAP`) |

**لا تخلط:** الكتالوج ≠ as-built. الخطة ≠ الواقع. عند التعارض → [Metrics-As-Built.md](Metrics-As-Built.md).

---

## مسار البيانات (ملخص)

```
TrainingEngine / MotionRecorder
        │
        ▼
MetricsCalculator  ──► RepMetrics / WorkoutExecutionMetrics (models)
        │
        ▼
PostTrainingReport / PerformanceSummary
        │
        ├──► PerformanceMetricsBuilder ──► UI cards (Form / Safety / Control)
        │
        └──► Execution upload ──► backend WorkoutExecutionMetrics + RepMetrics (Prisma)
```

---

## صيانة

| حدث | الإجراء |
|-----|---------|
| مقياس جديد في `MetricsCalculator` | حدّث As-Built + صف في Complete-Reference |
| تغيير عرض UI فقط | Post-Training-Report-Review + As-Built إن لزم |
| اقتراح علمي لم يُنفَّذ | Metrics-Final-Framework فقط |
