| | |
|---|---|
| **Status** | `ROADMAP` |
| **SSOT for** | Retention engine loops — code map |
| **As-built** | [`Journey-Index`](../../00-Active-Reference/Product-Master/Journey-Index.md) |
| **Business context** | [`13-Retention-Engine.md`](../../01-Business-Planning/13-Retention-Engine.md) |
| **Verified** | 2026-06-22 |

# Retention Engine Code Map

## الهدف

ربط حلقات البقاء (ملف 13) بالكود والشاشات الفعلية: ما هو موجود، ما هو ناقص، وأين يُبنى.

## الوضع الحالي لكل حلقة

### Loop 1 — التقدم الملموس: موجود جزئياً

- موجود: `HomeStatsData` (avgFormScore، streak، thisWeekExecutions)، `WeeklyReportActivity`، تاب Reports (`HistoryFragment`، `ReportsTrendsFragment`)، ومقاييس غنية (`WorkoutExecutionMetrics`: avgStability، avgRom، formConsistency، fatigueIndex).
- الناقص: **ترجمة الأرقام إلى معنى**. النظام يملك الأرقام، لكن الرسالة يجب أن تكون جملة: "ثباتك زاد من X% إلى Y%".
- يُبنى في: `WeeklyReportActivity` + `MetricDisplayBuilder` + بطاقة في `HomeFragment`. أضِف "delta عن آخر أسبوع/تمرين" كجملة مفهومة.

### Loop 2 — المدرب الذي يعرفني: البيانات موجودة، الـ UX ناقص

- موجود: `WorkoutRunSummary.commonErrors`، `RecentPlannedWorkoutData`، آخر تمرين في `ProgramWorkoutReportStore`.
- الناقص: استدعاء آخر تمرين قبل/أثناء تمرين اليوم بشكل شخصي.
- يُبنى في: `PreWorkoutActivity` / بداية `TrainingActivity` — اقرأ آخر `commonErrors` واعرض رسالة: "آخر مرة كانت ركبتك تميل — ركّز عليها اليوم".

### Loop 3 — العادة (Streak): موجود غالباً

- موجود فعلاً: `HomeStatsData.streak`، `ReportsModels.currentStreak` / `longestStreak`، `TrainFragment.getStreakDays`، نصوص "🔥 %d day streak"، و alert type `streak_at_risk`.
- الناقص: تعزيز بصري + ربط بـ Loop 4 (تنبيه قبل كسر السلسلة).
- يُبنى في: `HomeFragment` (تعزيز) + الإشعارات (تحت).

### Loop 4 — التذكير (Triggers): غير موجود

- موجود: نوع alert `streak_at_risk` داخل التطبيق فقط (feed)، ليس push.
- الناقص: **نظام تذكير/push فعلي**. لا WorkManager / AlarmManager / FCM للتذكير.
- يُبنى: WorkManager محلي للتذكير في أيام التمرين (`TrainModeData.status = active`)، برسالة مرتبطة بالقيمة. (FCM لاحقاً للموجة الباردة.)

### Loop 5 — المتابعة المباشرة: خارج الكود (تشغيلي، انظر ملف 20).

## "الخطوة الواحدة الواضحة" — موجودة فعلاً

`TrainModeData.status` يرمّز الحالة: `no_assessment / no_plan / rest_day / active / program_complete / reassessment_due`. هذا تطبيق مباشر لمبدأ One Clear Action.

تأكد أن `HomeFragment` يترجم كل حالة إلى زر واحد واضح:

- no_assessment ← "ابدأ التقييم"
- active ← "ابدأ تمرين اليوم (X دقيقة)"
- rest_day ← "يوم راحة"
- program_complete ← "أعد التقييم"

## أولوية البناء للتجربة

1. Loop 4 (تذكير) — الناقص الأكبر وأرخص أثر.
2. Loop 2 (شخصنة آخر تمرين) — تفرّدك، والبيانات جاهزة.
3. Loop 1 (ترجمة لمعنى) — تحسين عرض على موجود.
4. Loop 3 (streak) — تعزيز بسيط.
