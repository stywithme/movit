# Active Reference — المرجع الحي

وثائق تعكس **ما يُفترض أن يطابق الكود اليوم**. عند التعارض، الكود يفوز حتى يُحدَّث الملف هنا.

## خريطة المواضيع (Topic → SSOT)

| Topic | SSOT file | ملاحظة |
|-------|-----------|--------|
| User journey (as-built vs planned) | [`Product-Master/Journey-Index.md`](Product-Master/Journey-Index.md) | جدول القدرات |
| Trainee journey (code walkthrough) | [`Architecture-As-Built/trainee-journey-current-state/README.md`](Architecture-As-Built/trainee-journey-current-state/README.md) | 8 فصول |
| Product blueprint (future) | [`Product-Master/Unified-User-Journey-Plan.md`](Product-Master/Unified-User-Journey-Plan.md) | `ROADMAP` ضمني |
| Exercise JSON contract | [`Contracts/Exercise-JSON-Schema.md`](Contracts/Exercise-JSON-Schema.md) | Admin ↔ Android |
| REST API | [`Contracts/API_ENDPOINTS.md`](Contracts/API_ENDPOINTS.md) | + `backend/API_ENDPOINTS.md` |
| Workout naming | [`Contracts/Workout-Domain-Naming.md`](Contracts/Workout-Domain-Naming.md) | PlannedWorkout، WorkoutExecution |
| Training engine | [`Engine/training-engine.md`](Engine/training-engine.md) | KMP frame pipeline |
| Camera training (full stack) | [`Engine/Camera-Training-Engine-As-Built/README.md`](Engine/Camera-Training-Engine-As-Built/README.md) | Backend + mobile + engine + UI — **SSOT index** |
| Position checks | [`Engine/Positions-Check-Concept.md`](Engine/Positions-Check-Concept.md) | |
| Bilateral reps | [`Engine/Bilateral-Design.md`](Engine/Bilateral-Design.md) | |
| Scene detection | [`Engine/pose-scene-detection-how-it-works.md`](Engine/pose-scene-detection-how-it-works.md) | |
| Metrics (index) | [`Metrics/README.md`](Metrics/README.md) | ابدأ بـ As-Built |
| Metrics as-built | [`Metrics/Metrics-As-Built.md`](Metrics/Metrics-As-Built.md) | **SSOT للكود** |
| Metrics catalog | [`Metrics/Metrics-Complete-Reference.md`](Metrics/Metrics-Complete-Reference.md) | تعريفات |
| Metrics backlog | [`Metrics/Metrics-Final-Framework.md`](Metrics-Final-Framework.md) | `ROADMAP` |
| Post-training report | [`Product-Master/Post-Training-Report-Review.md`](Product-Master/Post-Training-Report-Review.md) | V1 UI / V2 data |
| Payment (MyFatoorah) | [`Operations/Payment-gateway/README.md`](Operations/Payment-gateway/README.md) | `00`–`06` |
| Workout domain migration | [`Operations/Workout-Domain-Migration-Runbook.md`](Operations/Workout-Domain-Migration-Runbook.md) | إنتاج |
| C4 / structure | [`Architecture-As-Built/C4-Model.md`](Architecture-As-Built/C4-Model.md) | |
| Workout run UX | [`Architecture-As-Built/workout-run-ux-flow.md`](Architecture-As-Built/workout-run-ux-flow.md) | |
| Arabic TTS research | [`Contracts/research-arabic-tts-mobile-fallback.md`](Contracts/research-arabic-tts-mobile-fallback.md) | |
| Structural changes log | [`../03-Evolution/README.md`](../03-Evolution/README.md) | ليس as-built |
| Doc standard | [`../DOCUMENT-STANDARD.md`](../DOCUMENT-STANDARD.md) | |

## لا تستخدم كمرجع حي

- `02-Roadmaps-And-Plans/` — خطط لم تُثبت
- `03-Implemented-Archive/` — وثائق الخطط القديمة
- `98-Redirects/` — إعادة توجيه فقط
- `PAYMENT-RUNBOOK.md` — **محذوف** (كان لحجز الطبيب؛ انظر [`03-Evolution/2026/2026-06-booking-doctor-removal.md`](../03-Evolution/2026/2026-06-booking-doctor-removal.md))
