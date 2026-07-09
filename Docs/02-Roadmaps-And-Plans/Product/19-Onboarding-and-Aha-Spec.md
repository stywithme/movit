| | |
|---|---|
| **Status** | `ROADMAP` |
| **SSOT for** | Onboarding flow and Aha moment — implementation spec |
| **As-built** | [`Journey-Index`](../../00-Active-Reference/Product-Master/Journey-Index.md) · [`08-kmp-mobile`](../../00-Active-Reference/Architecture-As-Built/trainee-journey-current-state/08-kmp-mobile.md) |
| **Business context** | [`04-MVP-Scope.md`](../../01-Business-Planning/04-MVP-Scope.md), [`09-Risk-Register.md`](../../01-Business-Planning/09-Risk-Register.md) |
| **Verified** | 2026-06-22 |

# Onboarding and Aha Spec

## الهدف

تصميم أول تجربة توصّل المستخدم للحظة «ده فاهمني» بأسرع وقت وأقل احتكاك.

## لحظة الـ Aha (التعريف)

أول **تصحيح فوري دقيق ومفهوم** على حركة المستخدم بالكاميرا. كل ما قبلها طريق لها، وكل ما بعدها بناء عادة.

## المسار الحالي (من الكود)

`MovitMainActivity` → `MovitInnerRoute.Auth` (إن لزم) → `MovitOnboardingScreen` (7 خطوات، `PUT /mobile/training-profile`) → Shell tabs (Home / Train / Explore / Reports / Profile) → `MovitAssessmentScreen` (PAR-Q → body scan بالكاميرا → Results) → `GET /mobile/home` (`trainMode`) → `MovitInnerRoute.WorkoutSession` (`TrainingSessionViewModel`).

**فجوة Aha:** لا يوجد مسار «حركة واحدة» مصغّر قبل التقييم الكامل — **غير منفّذ**.

## المشكلة المحتملة

لحظة الـ Aha (أول تصحيح) تأتي **متأخرة** — بعد onboarding كامل + PAR-Q + تقييم body scan. يخالف «اجعل أول قيمة قريبة جدًا» (ملف 09).

## المقترح: لحظة Aha مصغّرة قبل التقييم الكامل

- بعد onboarding، **«جرّب حركة واحدة»** (20–30 ثانية) بتصحيح فوري — قبل PAR-Q الكامل والتقييم.
- السلامة: إجابات خطر في onboarding → تخطّ التجربة وحوّل لـ PAR-Q فورًا.

## مبادئ الـ Onboarding (قرارات تصميم)

- **أقل الأسئلة**: الهدف، مستوى النشاط، الوقت، أيام التدريب، الطول/الوزن، قيود (ملف 04).
- **خطوة واحدة لكل شاشة** — منفّذ في `MovitOnboardingScreen`.
- **اشرح لماذا تسأل** — جزئي في النصوص الحالية.
- **الخصوصية مبكراً**: «الفيديو لا يخرج من جهازك» قبل الكاميرا (ملف 03).

## المقاييس (تُربط بملف 17)

- `onboarding_completed` rate.
- الزمن من install حتى أول `workout_first_rep`.
- نسبة من يصل للـ Aha المصغّر.

**فجوة events:** تتبع الأحداث **غير منفّذ** في KMP — [`17-Events-Implementation-Ticket.md`](17-Events-Implementation-Ticket.md).

## معايير القبول

- أول تصحيح فوري في أقل من 90 ثانية (المسار المصغّر المقترح) — **لم يُنفَّذ**.
- onboarding ضمن عدد الأسئلة المحدد — **منفّذ** (7 خطوات).
- بعد التقييم، خطوة واحدة واضحة عبر `trainMode.status` — **جزئي** (منطق خادم موجود؛ UX Today's Mission أضعف).
