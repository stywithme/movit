# Visual Parity Tracker — Prototype ↔ KMP (عنصر بعنصر)

**بدأ:** 2026-06-11 · **النطاق الحالي:** الصفحات الرئيسية (Home · Train · Explore · Reports) ثم مسارات Explore الداخلية (Programs/Program-detail · Workouts/Workout-detail/Edit · Exercises/Exercise-detail normal+rest).
**المرجع الذهبي:** [`prototypes/`](prototypes/) + [`prototypes/app.css`](prototypes/app.css) (1090 سطر — كل الـ tokens والمكوّنات).

> **قيد التحقّق:** لا أستطيع التقاط screenshot من رندر Compose (أدوات المعاينة للويب فقط). كل بند هنا **مُتحقَّق منه بنيوياً مقابل الـ prototype HTML/CSS + يُكمبّل**؛ التحقّق البصري النهائي (light/dark/RTL) **على الجهاز من طرفك أو QA**.
> **تنسيق الحالة:** ✅ مطابق · 🟡 موجود لكن مُتباعد (يحتاج ضبط) · ❌ غائب (يُبنى) · 🔵 مؤجَّل بقرار.

---

## سجل التغييرات المنفّذة

| # | التاريخ | التغيير | الملفات | الأثر | كيفية التراجع |
|---|---------|---------|---------|------|----------------|
| 1 | 2026-06-11 | **عائلة `MovitDashboardHero` بتدرّج فاتح** (Default/Lime/Level/Ink) مطابقة لـ `.dashboard-hero` family في app.css. كان المكوّن يرسم إمّا ink داكن أو سطحاً مسطّحاً بلا تدرّج. | `core/designsystem/.../MovitDashboardHero.kt` + 5 مستدعين | Home greeting → Default · Home level card → Level · Train active → Default · Level screen → Lime · Onboarding → Default. (Assessment يبقى عبر back-compat `inkStyle=false`→Default — لم يُلمس لأنه ملف Body Scan الجاري.) | كل مستدعٍ: غيّر `variant` للقيمة المطلوبة، أو `inkStyle=true` للداكن السابق |
| 2 | 2026-06-11 | **`ProgramCopyCard` بتدرّج primary-tint + حدّ primary** مطابق لـ `.copy-card` (كان سطحاً مسطّحاً `MovitCard(Filled)`). | `feature/library/.../components/ProgramDetailComponents.kt` | بطاقة «نسختك الشخصية» في Program detail صارت بالتدرّج المميّز | استبدل غلاف `Box(gradient)` بـ `MovitCard(Filled)` |
| 3 | 2026-06-11 | **`MovitStatTileRow` تلوين أرقام بالموضع** (primary/lime/coral) + خط W800، اختياري `coloredValues` — مطابق `.metric-row`. مُفعّل في Home metric strip. | `core/designsystem/.../MovitStatTileRow.kt` + `MovitHomeScreen.kt` | أرقام Home (هذا الأسبوع/متوسط/streak) ملوّنة كالـ prototype | `coloredValues = false` يعيد المحايد |
| 4 | 2026-06-11 | **`SetupGuideCard` بتدرّج primary-tint + حدّ primary** مطابق لـ `.setup-guide-card` (كان `MovitCard(Outlined)` مسطّح). | `feature/library/.../ExercisePrepareScreen.kt` | بطاقة «Camera Setup Guide» في Prepare صارت بالتدرّج المميّز | استبدل غلاف `Box(gradient)` بـ `MovitCard(Outlined)` |
| 5 | 2026-06-11 | **`MovitProgramCard` مشترك جديد (image-top + gpills)** مطابق لـ `.prog-card` — يحلّ نمط 🟡 (أ). استُبدل `MovitMediaCard` الأفقي في Explore programs + Programs list. | `core/designsystem/.../MovitProgramCard.kt` (جديد) + `MovitExploreScreen.kt` + `ProgramListScreen.kt` | بطاقات البرامج صارت صورة-علوية + pills (badge/weeks/level) بدل الأفقي | أعِد `MovitMediaCard(...)` في الموضعين |
| 6 | 2026-06-11 | **`MovitStatsStrip` مشترك جديد (خلايا tinted lime/lime/coral/primary)** مطابق لـ `.stats-strip` — يحلّ نمط 🟡 (ب). استُبدل `MovitStatTileRow` المحايد في Session + Prepare. | `core/designsystem/.../MovitStatsStrip.kt` (جديد) + `WorkoutSessionScreen.kt` + `ExercisePrepareScreen.kt` | شرائط الإحصاءات صارت ملوّنة الخلفية كالـ prototype | أعِد `MovitStatTileRow(...)` في الموضعين |
| 7 | 2026-06-11 | **Coil 3 multiplatform لـ `MovitRemoteImage`** — يحلّ نمط 🟡 (ج). كان expect/actual: Android (Coil 2) يحمّل · **iOS placeholder دائماً**. صار commonMain واحد يحمّل صوراً حقيقية على **Android + iOS**. | `gradle/libs.versions.toml` (coil3=3.2.0) + `core/designsystem/build.gradle.kts` + `MovitRemoteImage.kt` (commonMain) + حذف الـ android/ios actuals | كل بطاقات الصور (Media/Program/Exercise/Hero) تحمّل شبكياً على iOS أيضاً عند توفّر URL | راجع التبعيات + أعِد expect/actual القديم |

> back-compat: `MovitDashboardHero(inkStyle=…)` ما زال يعمل (`true→Ink`, `false→Default`) فلا ينكسر أي مستدعٍ لم يُحدَّث.

---

## 08 — Home (`MovitHomeScreen`) مقابل `08-home.html`

| عنصر prototype | CSS/مرجع | KMP | حالة |
|----------------|----------|-----|------|
| App header (avatar ring) | `.app-header .avatar--ring` | `MovitScaffold(userName, onProfileClick)` | ✅ |
| Greeting hero (eyebrow + display name + sub) | `.dashboard-hero` (light) | `HomeHeroSummary` → **Default light** (بعد التغيير #1) | ✅ (كان ink — صُحِّح) |
| Metric strip (3 أرقام ملوّنة: primary/lime/coral) | `.metric-row` (نص ملوّن لكل عمود) | `MovitStatTileRow` | 🟡 تحقّق من تلوين الأرقام الثلاثة (primary/lime-deep/coral) مطابقاً |
| Level card (lime+primary gradient + ring/progress) | `.dashboard-hero--level` | `HomeLevelCard` → **Level gradient** (بعد #1) | ✅ (كان سطح مسطّح — صُحِّح) |
| Alert banner (warning insight + chevron) | `.insight--warning` | `MovitInsightCard(Warning)` | ✅ |
| Active program (state-card + outline btn) | `.state-card` | `HomeActiveProgramSection` (MovitCard Outlined) | ✅ |
| Today plan (label/title/sub + primary CTA + "1 of 2") | `.state-card.stack` | `TodayPlanCard` | ✅ |
| Body scan CTA (accent--lime + dark btn + glyph) | `.accent--lime` | `MovitAccentBlock(Lime)` | ✅ |
| Empty: no program (ring-ic + browse) | `.empty-state` | `MovitEmptyState` | ✅ |
| Journey rows (trophy lime + cal + tag Soon) | `.list-row` + `.tag--coral` | `MovitListGroup`/`MovitListRow` | ✅ |
| Recent activity (act-row بشريط لون جانبي `--c`) | `.act-row::before` شريط ملوّن | `MovitListRow` عادي | 🟡 الـ prototype له **شريط لون عمودي** + أيقونة دائرية ملوّنة؛ KMP صف عادي — مرشّح `MovitActivityRow` |
| Quick actions (quick-grid 2-col) | `.quick-grid` | `HomeQuickActions` | ✅ |

**عمل Home المتبقّي:** (1) تأكيد تلوين أرقام الـ metric strip؛ (2) ترقية Recent activity لاستخدام `MovitActivityRow` (شريط جانبي ملوّن) بدل `MovitListRow`.

---

## 01 — Train (`MovitTrainScreen`) — كل الحالات — مقابل `01-train.html`

> الخلاصة: **Train منقول بأمانة عالية.** الهيكل والمكوّنات تطابق الـ prototype عبر الحالات الخمس. الفجوات قليلة ومحدّدة.

### الحالة: No program (`noprogram`)
| عنصر | KMP | حالة |
|------|-----|------|
| Lime hero "Browse programs / Pick a guided plan" | `TrainStatusBanner` NoPlan → label + `MovitAccentBlock(Lime)` | 🟡 الـ prototype يستخدم `dashboard-hero--lime` بعنوان `type-display` كبير؛ KMP يستخدم accent block أصغر |
| بطاقتا برنامج (media + gpills Featured/weeks/level + name/desc/pmeta + Start) | `TrainNoPlanSection` → `TrainFeaturedProgramCard` | 🟡 صورة placeholder (حرف أول) · gpills في KMP tags بزاوية مختلفة — يُغلق عند ربط الصور الشبكية |

### الحالة: Active training day (`active`)
| عنصر | KMP | حالة |
|------|-----|------|
| Hero (program name + Week/Day + progress + foot: 12 days/streak/grade A/35%) | `MovitDashboardHero` **Default light** (بعد #1) + `MovitMetricRow` + tag row | ✅ لون الـ hero صُحِّح (كان ink) · 🟡 الـ foot في prototype سطر واحد inline؛ KMP يفصله metric-row + tag row |
| Week calendar (June·Week2 + nav + 7 days بحالات + legend) | `TrainWeekPreview` → `MovitWeekStrip` | ✅ |
| "Today — Day 3" + sess-cards (قابلة للطي + stepper + Start) | `TrainTodayCard` → `MovitSessionCard` | ✅ |
| Form score trend (chart-panel + delta + area chart أخضر) | `TrainReportSection` → `MovitLineChart` | ✅ |
| Program report (stat-grid 6 خلايا + insight + View full reports) | `MovitKpiGrid` + `MovitInsightCard` | 🟡 الـ prototype `stat-grid` 3×2؛ `MovitKpiGrid` 2-col — تحقّق من التخطيط |
| "Browse all programs →" | `MovitButton(Text)` | ✅ |

### الحالة: Rest day (`restday`)
| عنصر | KMP | حالة |
|------|-----|------|
| state-card (moon + Rest day + Recovery + Tomorrow preview) | `RestDayCard` (Bedtime icon) | ✅ |

### الحالة: Day complete (`daydone`)
| عنصر | KMP | حالة |
|------|-----|------|
| sess-cards مكتملة (tag Done lime + form footer) | `SessionList(isCompleted, footerNote)` | ✅ |
| banner--success "Day complete! 🎉 2/2 · 38min" + View summary | `MovitBanner(Success)` + Text btn | ✅ |

### الحالة: Program complete (`complete`)
| عنصر | KMP | حالة |
|------|-----|------|
| banner--complete (trophy ring lime + title + summary + View journey + What's next) | `ProgramCompleteCard` | ✅ |

**عمل Train المتبقّي (كله 🟡 ثانوي):** (1) No-program hero → `dashboard-hero--lime` كبير؛ (2) active hero foot inline بدل تكرار streak في metric-row+tag؛ (3) تأكيد تخطيط KpiGrid 3-col. **لا فجوات ❌.** أولوية منخفضة — Train جاهز فعلياً.

---

## 04 — Explore (`MovitExploreScreen`) — مقابل `04-explore.html`

> الخلاصة: **مطابق هيكلياً** (toolbar · filter chips · Recommended · Workouts · Exercises · Programs). الفروق 🟡 تفاصيل بطاقات.

| عنصر prototype | KMP | حالة |
|----------------|-----|------|
| library-toolbar (search + filter-btn + tabs All/Ex/Workouts/Programs) | `ExploreSearchSection` + `ExploreFilterSection` | ✅ |
| Recommended `feature-card` (image + lime badge "Smart pick" + title + sub، كامل clickable) | `ExploreHero` → `MovitHeroCard` | 🟡 KMP يضيف **CTA pill + membersLabel + playFab** غير موجودة في الـ prototype؛ والـ badge نص عادي بدل lime pill |
| Workouts `wide-media-card` (img 86px + focus pill + topline[name+tag] + desc + meta caps) | `MovitMediaCard` (img 96dp أفقي + focus/badge surfaces) | 🟡 الـ tag في prototype على سطر الاسم؛ KMP badge منفصل · لا variant `is-featured` متدرّج |
| muscle-strip (chips أفقية All/Legs/Core… مع is-key lime) | `ExploreMuscleStrip` | 🟡 تأكيد حالة `is-key` (lime tint) |
| Exercises `media-card` grid 2-col (square img + k-badge ملوّن + name + meta) | `ExploreExerciseList` → `MovitExerciseCard` (img-top 120dp + badge + name + meta) | 🟡 الـ k-badge ملوّن حسب الفئة (lime/coral/primary)؛ KMP ثابت secondaryContainer · صورة 120dp بدل square 1:1 |
| Programs `prog-card` (image-top 150px + gpills weeks/level + body) | `MovitMediaCard` (أفقي img-left) | 🟡 **اتجاه مختلف**: prototype image-top؛ KMP أفقي — مرشّح `MovitProgramCard` مشترك (image-top + gpills) |

**عمل Explore (أولوية متوسطة، كله 🟡):** الأوضح = (1) program card image-top؛ (2) تلوين k-badge للتمارين حسب الفئة؛ (3) تبسيط Recommended hero ليقارب feature-card. لا فجوات ❌.

## 07 — Program detail (`ProgramDetailScreen`) — مقابل `07-program.html`

> الخلاصة: **مُنفّذ بعمق** — أعلى بكثير من تقدير التدقيق (45%). Overview + Edit mode كلاهما حاضر بمعظم أقسام الـ prototype.

### Overview
| عنصر prototype | KMP | حالة |
|----------------|-----|------|
| float-pill Back + Customize/Save | `MovitInnerPageHeader` (back + action) | ✅ |
| `program-hero` (ink image + kicker pills + title + desc) | `ProgramHeroSection` | ✅ |
| `program-tabs` (Overview/Customize) | `MovitSegmentedControl` | ✅ |
| `stat-grid` 2×2 (label/value/hint) | `ProgramStatGrid` | ✅ · 🟡 labels إنجليزية من mapper (لا `program_*`) |
| `copy-card` (primary-tint gradient + tags + 2 actions) | `ProgramCopyCard` | ✅ **صُحِّح للتدرّج (تغيير #2)** |
| week-strip (week pills + progress-track) | `ProgramWeekStrip` | ✅ |
| week-card current (timeline-card days Done/Next/Rest) | `ProgramWeekCard` → `ProgramDayRow` | 🟡 KMP يستخدم day-row لكل الأيام؛ prototype current week يستخدم `timeline-card` بنقاط متصلة |
| week-card preview (day-timeline rows) | `ProgramWeekCard` | ✅ |
| `detail-grid` (Goal/Session/Calendar cards) | `ProgramDetailCardsSection` | ✅ |
| action-dock (Week·Day + Start next session) | `ProgramStartDock` | ✅ |
| «Weekly report» link | `onActionClick = {}` | 🟡 غير موصول (صفحة 15) |

### Edit / Customize
| عنصر prototype | KMP | حالة |
|----------------|-----|------|
| edit-note + save-toast | `ProgramEditPanel` (Surface lime + toast) | ✅ |
| `reason-grid` 2×2 | `ProgramReasonGrid` | ✅ |
| `scope-grid` (4 scope-cards مرقّمة) | `ProgramScopeList` | ✅ |
| `impact-card` (impact-rows) | `ProgramImpactCard` | ✅ |
| `form-stack` (start date / weekly stepper / pause toggle) | `ProgramSettingsStack` | ✅ |
| **«Selected day» editor**: `edit-day` + `session-edit` (drag + mini-actions Rename/Move/Swap/Remove + `exercise-line` + `param-row` sets/reps/weight/rest + Add exercise) | — | ❌ **غائب** — أعمق جزء في Edit؛ يحتاج حالة تحرير في ViewModel (feature work لا مجرد بصري) |
| action-dock edit (Cancel / Save) | bottomBar Row | ✅ |

**عمل Program detail المتبقّي:** ❌ بناء «Selected day» editor (drag/reorder + param editing) — **مؤجَّل** (يحتاج ViewModel state، خارج نطاق المطابقة البصرية البحتة، يطابق ملاحظة scorecard «no drag/reorder»). 🟡: stat labels i18n · weekly-report nav · current-week timeline style.

## 07/15 — Programs list (`ProgramListScreen`) — مقابل `15-program-flow.html`
| عنصر | KMP | حالة |
|------|-----|------|
| header + filter chips + قائمة برامج | `MovitInnerPageHeader` + `MovitFilterRow` + `MovitMediaCard` | ✅ هيكلياً · 🟡 بطاقات أفقية بدل `prog-card` image-top (نفس 🟡 Explore) |

## 02/16 — Session / Workout detail + Edit mode (`WorkoutSessionScreen`) — مقابل `02-session.html`

> الخلاصة: **مُنفّذ بالكامل** — من أكثر الشاشات اكتمالاً. View + Edit mode + الـ 4 sheets كلها حاضرة.

| عنصر prototype | KMP | حالة |
|----------------|-----|------|
| sess-top (Back + Edit/Done pill) | `MovitInnerPageHeader` (toggle Edit/Done) | ✅ |
| day-head (title + sub + «editing») | عنوان + subtitle + tertiary «editing/saving» | ✅ |
| `stats-strip` 4 خلايا ملوّنة الخلفية | `MovitStatTileRow` (3 خلايا محايدة) | 🟡 الـ prototype خلفيات tinted (lime/lime/coral)؛ KMP محايد |
| `sec-label` (Warm-up/Main/Cool-down + count + line) | `MovitSectionHeader` | 🟡 نمط مختلف (header بدل label + خط) |
| `sx-card` (thumb+num + name + cat + stat-chips sets×reps/weight/rest) | `SessionExerciseCard` | ✅ · 🟡 صور placeholder |
| **Edit: ex-actions** (swap/edit/delete/**drag-reorder**) | `SessionExerciseCard(isEditMode)` + onMoveUp/Down | ✅ **كامل + reorder** |
| `rest-block` (coral + clock) + edit actions | `SessionRestBlock` | ✅ |
| action-dock view (Ready to train + Start) | `SessionStartDock` | ✅ |
| action-bar edit (Add exercise / Rest / Done) | bottomBar Row | ✅ |
| **Swap sheet** (filters + search + Recommended/Alternatives) | `WorkoutSessionSheets` (swap) | ✅ |
| **Edit details sheet** (steppers Sets/Reps/Weight/Rest + Change exercise) | `WorkoutSessionSheets` (edit) | ✅ |
| Add exercise sheet + Edit rest sheet | `WorkoutSessionSheets` | ✅ |

**عمل Session المتبقّي:** 🟡 فقط — stats-strip خلفيات ملوّنة · sec-label style · صور thumbnails. **لا ❌.** الشاشة جاهزة فعلياً (أغنى من Program-detail edit — فيها reorder + كل الـ sheets).

## 05/03 — Exercise prepare (`ExercisePrepareScreen`) normal + rest — مقابل `03-prepare.html`

> الخلاصة: **مُنفّذ بالكامل بالحالتين** (Prepare + Rest).

| عنصر prototype | KMP | حالة |
|----------------|-----|------|
| sess-top (Back + center label + progress-session-bar) | `MovitInnerPageHeader` + `MovitProgressBar` تحته | ✅ · 🟡 progress تحت العنوان لا في الوسط |
| `up-next-flag` coral (rest mode) | `MovitTag(Coral)` «Up Next» | ✅ |
| `hero-media` مربّع 1:1 + «Looping Preview» pill | `ExerciseHeroPreview` (280dp + pill) | ✅ · 🟡 صورة placeholder (حرف أول) |
| title (display) + category | عنوان + category | ✅ |
| `stats-strip` 4 خلايا (Sets/Reps/Rest/Equipment) | `MovitStatTileRow` (4 خلايا) | 🟡 محايد بدل tinted؛ equipment «🏋» emoji |
| **`setup-guide-card`** (تدرّج primary + pose-ref-box + Camera Setup + 3-axis + distance) | `SetupGuideCard` | ✅ **صُحِّح للتدرّج (تغيير #4)** · 🟡 pose-ref «📷» emoji بدل صورة |
| Instructions مرقّمة (دوائر lime) | `InstructionsCard` → `InstructionStep` | ✅ |
| Target muscles (tags blue) | `TargetMusclesRow` | ✅ |
| dock Prepare (Ready to train + Start) | `PrepareStartDock` | ✅ |
| **dock Rest** (digits 00:30 + pause + +15 + Skip + low-time coral) | `RestTimerDock` | ✅ **مطابق** (عدّاد حيّ) |

**عمل Prepare المتبقّي:** 🟡 فقط — صور hero/pose (placeholder) · stats-strip tinted · equipment/camera glyphs (emoji). **لا ❌.** (الكاميرا الحقيقية = Phase 07، خارج المطابقة البصرية.)

## 05 — Exercises list (`ExercisesLibraryScreen`) — مقابل `05-exercises.html`
| عنصر | KMP | حالة |
|------|-----|------|
| grid تمارين (ex-card image-bg + diff-dots) | library grid (`MovitExerciseCard`/media grid) | 🟡 الـ prototype `ex-card` صورة-خلفية + overlay + diff-dots؛ KMP بطاقة فاتحة image-top — اختلاف أسلوب |

---

## ملخص الحالة العامة (2026-06-11)

**النتيجة الكبرى:** طبقة التصفّح/اللوحات/التفاصيل **منقولة بأمانة عالية** عبر كل الشاشات المفحوصة — أعلى بكثير من تقديرات التدقيق الأولية. معظم الفجوات **🟡 تفاصيل** (صور placeholder · بطاقات أفقية بدل image-top · stats tinted) لا **❌ غياب**.

**الفجوات ❌ الحقيقية الوحيدة:**
1. **Program-detail → «Selected day» editor** (تحرير معاملات التمرين drag/param) — feature work يحتاج ViewModel state.
2. الكاميرا/التدريب الحيّ (Prepare/Workout-run/Assessment) — **Phase 07** (خارج المطابقة البصرية).

**6 إصلاحات DS مؤكَّدة بالبناء** (سجل التغييرات أعلاه): عائلة hero بتدرّج (Default/Lime/Level) × Home/Train/Level/Onboarding · copy-card · setup-guide-card · metric coloring.

**أنماط 🟡 متكرّرة — كلها أُنجِزت:**
- ~~(أ) `MovitProgramCard` image-top مشترك~~ ✅ **(تغيير #5)** — Explore + Programs list.
- ~~(ب) `MovitStatsStrip` بخلفيات tinted~~ ✅ **(تغيير #6)** — Session + Prepare.
- ~~(ج) صور media شبكية بدل placeholder~~ ✅ **(تغيير #7)** — Coil 3 multiplatform؛ Android متحقَّق بالبناء.
  > ⚠️ **iOS لم يُتحقَّق محلياً** (يحتاج macOS): شغّل `:core:designsystem:compileKotlinIosSimulatorArm64` على الـ CI/Mac للتأكيد. إن فشل linking على iOS، التراجع = استعادة expect/actual القديم + تبعيات coil2.
