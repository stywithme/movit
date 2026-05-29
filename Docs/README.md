# POSE — فهرس الوثائق

> **آخر تنظيم:** مايو 2026  
> الهدف: مرجع واحد واضح — ما يُقرأ للتشغيل، ما يُؤرشف، وما حُذف (لوجات وتكرارات).

---

## معيار الوثائق (مايو 2026)

- **[`DOCUMENT-STANDARD.md`](DOCUMENT-STANDARD.md)** — ترويسة `ACTIVE` / `ARCHIVED` / `ROADMAP`
- **[`00-Active-Reference/Product-Master/Journey-Index.md`](00-Active-Reference/Product-Master/Journey-Index.md)** — as-built vs مخطط المنتج
- **[`00-Active-Reference/Metrics/README.md`](00-Active-Reference/Metrics/README.md)** — فهرس المقاييس + Code map

## ابدأ من هنا

| إذا كنت… | اقرأ أولاً |
|----------|------------|
| مالك منتج / تخطيط تجاري | [`01-Business-Planning/README.md`](01-Business-Planning/README.md) |
| مطوّر Android / Backend | [`00-Active-Reference/README.md`](00-Active-Reference/README.md) |
| مدرب / محتوى | [`05-Guides/Trainer-Guide/Complete-System-Guide-AR.md`](05-Guides/Trainer-Guide/Complete-System-Guide-AR.md) |
| تريد فهم المنتج كما هو مبني اليوم | [`00-Active-Reference/Architecture-As-Built/trainee-journey-current-state/README.md`](00-Active-Reference/Architecture-As-Built/trainee-journey-current-state/README.md) |
| تريد الرؤية المستقبلية للمنتج | [`00-Active-Reference/Product-Master/Unified-User-Journey-Plan.md`](00-Active-Reference/Product-Master/Unified-User-Journey-Plan.md) |

---

## هيكل المجلدات

```
Docs/
├── 00-Active-Reference/     ← الحقيقة الحالية (كود + عقود + تشغيل)
├── 01-Business-Planning/    ← منتج، سوق، beta، retention
├── 02-Roadmaps-And-Plans/   ← خطط لم تُنفَّذ أو قيد النقاش
├── 03-Implemented-Archive/  ← خطط لميزات بُنيت (مرجع تاريخي)
├── 04-Research/             ← أوراق، ACSM، زوايا، برامج
├── 05-Guides/               ← MediaPipe، دليل المدرب
├── 06-Assets/               ← صور، خطوط، بيانات تدريب ML، أمثلة UI
└── 99-Archive/              ← نسخ قديمة، نقد، ميزات ملغاة
```

---

## 00 — Active Reference (مرجع حي)

| مسار | المحتوى |
|------|---------|
| [`Engine/`](00-Active-Reference/Engine/) | محرك التدريب، Position Checks، Bilateral، scene detection |
| [`Metrics/`](00-Active-Reference/Metrics/) | **SSOT للمقاييس:** `Metrics-Complete-Reference.md` + `Metrics-Final-Framework.md` |
| [`Contracts/`](00-Active-Reference/Contracts/) | `Exercise-JSON-Schema.md`, `API_ENDPOINTS.md`, TTS |
| [`Architecture-As-Built/`](00-Active-Reference/Architecture-As-Built/) | رحلة المتدرب في الكود، UX الجلسة، C4، هيكل المشروع |
| [`Product-Master/`](00-Active-Reference/Product-Master/) | `Unified-User-Journey-Plan.md`, `Post-Training-Report-Review.md` |
| [`Operations/Payment-gateway/`](00-Active-Reference/Operations/Payment-gateway/) | تكامل الدفع + RUNBOOK |

---

## 01 — Business Planning

22 وثيقة مرقّمة (`00`–`21`) + `18-Retention-Engine-Code-Map.md` كجسر للكود.

- **ملغى:** نموذج الحجوزات/الأطباء — انظر `99-Archive/Cancelled-Features/`
- **دمج:** `Buyer-Persona.md` → مضمّن في `02-ICP-and-Personas.md` (نسخة Persona في الأرشيف)

---

## 02 — Roadmaps & Plans

| مجلد | أمثلة |
|------|--------|
| `Platform/` | معمارية المنصة، Body Scan، البرامج |
| `UI-UX/` | إعادة تصميم الشاشات، Setup Pose |
| `Engine-Future/` | State Machine، Arc، Refactor Visuals |

> لا تُعامل هذه الملفات كـ «ما يعمل الآن» إلا بعد التحقق من الكود.

---

## 03 — Implemented Archive

خطط نُفِّذت أو أُغلقت — للمراجعة التاريخية فقط:

- `Hold/`, `Position-Checks/`, `Feedback/`, `Post-Training-Report/`, `Admin-Exercise/`, `Video-Mode/`

**مرجع التقارير الحالي:** `00-Active-Reference/Product-Master/Post-Training-Report-Review.md`  
(لا تستخدم `03-Implemented-Archive/Post-Training-Report/*` كمصدر قرار.)

---

## 04 — Research

| مجلد | المحتوى |
|------|---------|
| `ACSM-Prescription/` | تلخيص ورقة ACSM 2026 + مواصفة التقدم |
| `AI-Models/` | PDFs (~70) — pose estimation |
| `Body-Scan/` | أبحاث Body Scan |
| `Training-Programs/` | Charter، Blueprint، نقد الأهداف |
| `Joint-Angle/` | مشكلة المرفق، حلول ML/هندسية |
| `Misc/` | Eccentric exercise، كتالوج حلول الزوايا |

---

## 05 — Guides

- [`MediaPipe-Wiki/`](05-Guides/MediaPipe-Wiki/) — تكامل Pose Landmarker على Android
- [`Trainer-Guide/`](05-Guides/Trainer-Guide/) — دليل النظام بالعربية

---

## 06 — Assets

| مجلد | ملاحظة |
|------|--------|
| `UI-Docs/` | شعار، خط Alexandria، لقطات WhatsApp |
| `Examples/` | مراجع UI من تطبيقات أخرى (فبراير 2026) |
| `ML-Training-Images/` | صور `train/` (Standing/Sitting/Lying) — **ليست توثيقاً نصياً** |
| `Config-Samples/` | عينات OAuth — **لا تُرفع للإنتاج** |

---

## 99 — Archive

| مجلد | المحتوى |
|------|---------|
| `Superseded/` | ملخصات مكررة، مقاييس قديمة، FlexFit القديم |
| `Cancelled-Features/` | booking / حجوزات |
| `Criticism-Reviews/` | مراجعات داخلية لخطط |
| `Legacy-Plans/` | وثائق قديمة متفرقة |

---

## ما تم حذفه (مايو 2026)

ملفات **ليست توثيقاً** — لوجات أجهزة وتشخيص:

- `On-Camera-Log`, `A13-LOG-*`, `S22U-LOG-*`, `replay-log`, `Payment-log`, `training-log`, `Exercise-Log`, `Audio-log`
- `elbow-debug.md`, تصديرات `cursor_*.md` من AI-Models
- `State_Range.md`, `Greet-Feedback-Ideas.md`, `Main-Metrics.md` (مكرر)

---

## إعادة التوجيه (مسارات قديمة)

| المسار القديم | المسار الجديد |
|---------------|----------------|
| `Docs/Exercise-JSON-Schema.md` | `00-Active-Reference/Contracts/Exercise-JSON-Schema.md` |
| `Docs/New-Project/Planning/` | `01-Business-Planning/` |
| `Docs/American-College-Prescription/` | `04-Research/ACSM-Prescription/` |
| `Docs/New-Project/` | **أُلغي** — انظر الجدول أعلاه |
| `Docs/train/` | `06-Assets/ML-Training-Images/` |

---

## قواعد صيانة الوثائق

1. **مرجع حي واحد لكل موضوع** — لا تُنشئ ملفاً ثالثاً للمقاييس أو التقارير.
2. **خطة منفذة** → انقلها إلى `03-Implemented-Archive/` مع تاريخ في أول السطر.
3. **لوج تشخيص** → لا يدخل `Docs/`؛ استخدم أدوات التتبع أو issues.
4. **قرار منتج** → `01-Business-Planning/` + تحديث `12-Open-Questions.md`.
5. **تغيير عقد JSON** → حدّث `Contracts/Exercise-JSON-Schema.md` و types في Backend/Dashboard.

---

## إحصاء سريع

| النوع | تقريباً |
|-------|---------|
| ملفات Markdown منظّمة | ~145 |
| PDFs بحثية | ~70 |
| صور تدريب ML | ~458 |
| أصول UI | ~40 |
