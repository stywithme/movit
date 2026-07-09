# Research — مرجع علمي فقط

| | |
|---|---|
| **Status** | `RESEARCH` |
| **SSOT for** | أدبيات علمية وتحليلات — **ليست** مواصفات تنفيذ |
| **Integration gaps** | [`00-Active-Reference/Engine/`](../00-Active-Reference/Engine/) |
| **As-built** | [`trainee-journey-current-state/`](../00-Active-Reference/Architecture-As-Built/trainee-journey-current-state/) |

هذا المجلد يحتفظ **بالعلم فقط**: أوراق، تحليلات، نقد، وتقارير مشاكل. أي ربط بمشروع Movit أو فجوات تنفيذ تعيش في **Active Reference → Engine**.

---

## المحتويات

| مجلد | المحتوى | ملاحظة |
|------|---------|--------|
| [`ACSM-Prescription/`](ACSM-Prescription/) | تحليل ACSM 2026 (01–08) + تعمق التقدم (10) | 09/11 نُقلا إلى Engine |
| [`Training-Programs/`](Training-Programs/) | Blueprint، بحث RE-Search، نقد أهداف | Charter مُدمج في Blueprint |
| [`Body-Scan/`](Body-Scan/) | أبحاث المسح البدني | |
| [`Joint-Angle/`](Joint-Angle/) | تقرير زاوية المرفق والحلول المقترحة | ابدأ بـ `elbow-angle-problem-report.md` |
| [`Misc/`](Misc/) | مواضيع متفرقة (زوايا، eccentric…) | |

---

## ACSM 2026 (`ACSM-Prescription/`)

| الملف | نوع |
|--------|-----|
| [01–08](ACSM-Prescription/01-Paper-Overview.md) | تحليل الورقة والمراجع |
| [10-Progression-Prescription-Deep-Dive.md](ACSM-Prescription/10-Progression-Prescription-Deep-Dive.md) | بحث: عوامل التقدم والتوصيف |
| [09](ACSM-Prescription/09-Movit-Integration-Roadmap.md) · [11](ACSM-Prescription/11-Final-Progression-Spec.md) | **redirect** → [Engine](../00-Active-Reference/Engine/) |

---

## برامج تدريبية (`Training-Programs/`)

| الملف | نوع |
|--------|-----|
| [Program-Blueprint.md](Training-Programs/Program-Blueprint.md) | تصميم منظومة البرامج + جداول حالة التنفيذ |
| [Program-research-1.md](Training-Programs/Program-research-1.md) · [Program-Research-2.md](Training-Programs/Program-Research-2.md) | بحث RE-Search |
| [Program-objective-criticism-1.md](Training-Programs/Program-objective-criticism-1.md) · [Program-objective-criticism-2.md](Training-Programs/Program-objective-criticism-2.md) | نقد أهداف البرنامج |
| [Program-Charter.md](Training-Programs/Program-Charter.md) | **redirect** → Blueprint |

---

## أين لا تبحث هنا

| الموضوع | المسار الصحيح |
|---------|----------------|
| فجوات ربط ACSM بالكود | [`Engine/Integration-Gap-Tracker.md`](../00-Active-Reference/Engine/Integration-Gap-Tracker.md) |
| فجوات التقدم والاقتراح | [`Engine/Progression-Spec-Gaps.md`](../00-Active-Reference/Engine/Progression-Spec-Gaps.md) |
| رحلة المتدرب كما في الكود | [`trainee-journey-current-state/`](../00-Active-Reference/Architecture-As-Built/trainee-journey-current-state/) |
| خطط UI/منصة | [`02-Roadmaps-And-Plans/`](../02-Roadmaps-And-Plans/) |

---

## Joint-Angle — مسار القراءة

1. [`elbow-angle-problem-report.md`](Joint-Angle/elbow-angle-problem-report.md)
2. [`Solutions.md`](Joint-Angle/Solutions.md) أو [`Smart-Solutions.md`](Joint-Angle/Smart-Solutions.md)
3. خطط تقنية أعمق: `lifting-model-solution-plan.md`, `elbow-correction-mlp-plan.md`
