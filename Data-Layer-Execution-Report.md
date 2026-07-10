# تقرير تنفيذ طبقة البيانات — Movit Mobile (Offline-First)

**التاريخ:** 2026-07-10  
**الحالة:** منفَّذ برمجيًا في الـ working tree (بدون commits — للمراجعة)  
**الخطة المرجعية:** [`Data-Layer-Remediation-Plan.md`](./Data-Layer-Remediation-Plan.md)  
**المصادر المدموجة هنا:** سياسة البيانات · سياسة محفزات المزامنة · قياس الحمولة (P0.4) · سجل التنفيذ (§12)

هذا الملف هو **وثيقة التنفيذ الكاملة الوحيدة**. ملف الخطة يبقى مرجع التصميم والترتيب؛ هنا ما تغيّر فعليًا وكيف يعمل النظام بعد الإصلاح.

---

## 0. الخلاصة

المعمارية (SQLDelight JSON-blob + Outbox + SWR) بقيت كما هي. ما تغيّر هو **السياسات**: متى نمسح، متى نعيد المحاولة، من يفوز عند التعارض، ومتى ننزّل full.

| المرحلة | النتيجة |
|---|---|
| **P0** أساس + telemetry + baseline | مكتمل (قياس curl حي معلّق — لا باك محلي) |
| **P1** وقف فقدان البيانات (S1) | مكتمل برمجيًا |
| **P2** حمولة / أداء / اتساق | مكتمل برمجيًا (Redis اختياري محجوب) |
| **P3** جودة وتحصين | مكتمل |
| **UX** أسطح أوفلاين/معلق/فشل | مكتمل |

**إصلاحات لاحقة (2026-07-10):** F1 UX.7 UI + accept→replay · F2 catalog graph telemetry · F3 bootstrap `fetchProfile` عند auth بلا userId + log في replay.

**متبقٍ تشغيلي فقط (ليس فجوة كود):**
1. قياس `curl` حي على staging بعد seed  
2. `prisma migrate deploy` على البيئة الحقيقية  
3. سيناريو «أسبوع الجيم» اليدوي (القسم 10 في الخطة)  
4. Redis لكاش stats (اختياري — ETag Home موجود)  
5. iOS `didExpire` + prefetch أصوات لغة (ثانوي)

---

## 1. السياسة النهائية (كيف يعمل النظام الآن)

### 1.1 مبادئ حاكمة

| # | القرار |
|---|---|
| **PR-1** | الـ outbox يُحتفَظ به عبر انتهاء الجلسة، مربوط بـ `owner_user_id`. انتهاء الجلسة يمسح كاش القراءة والتوكنات فقط. |
| **PR-2** | لا ازدواج workout export + training-config المخزّن؛ الاشتقاق عند القراءة. |
| **PR-3** | تقارير planned داخل sync كدلتا (`includeReports=summary`) مع watermark آمن؛ full عند `forceRefresh` فقط. |
| **PR-4** | بيانات الضيف تُنسب للحساب بعد سؤال صريح (UX.7)؛ احتفاظ 30 يومًا؛ logout يحذفها. |
| **PR-5** | JSON-blob + outbox يبقى؛ استثناء: `transaction {}` على الـ full apply. |
| **PR-6** | Pending/in-flight outbox يفوز مؤقتًا؛ بعد SUCCEEDED السيرفر يفوز — مع حفظ التقرير الغني المحلي أمام ملخص أفقر. |
| **PR-7** | `clearReadCaches` ≠ `clearDurableWrites`. انتهاء الجلسة = قراءة فقط؛ logout صريح = الاثنان. |

### 1.2 ماذا يُخزَّن أين

| الطبقة | المحتوى | ملاحظة |
|---|---|---|
| `json_cache` | كتالوج، home، enrollments، تفضيلات، تقارير، metrics | namespaces من `MovitCacheKeys` |
| `outbox_entry` | كتابات المستخدم + guest executions | لا تُمسح مع كاش الجلسة؛ `owner_user_id` + backoff |
| `session_journal` | checkpoint جلسة حية | مصدر واحد = SQL |
| Filesystem | `audio_cache/`, `frame_captures/` | تُمسح عند logout؛ GC بعد رفع مؤكد / orphans |

### 1.3 متى يُزامن (محفزات)

| الحدث | السلوك |
|---|---|
| Cold start (مسجّل) | `bootstrapLocalCaches()` **ثم** `syncIfNeeded` |
| Resume | `syncIfNeeded` (throttled) |
| استعادة اتصال (Android) | replay outbox → sync واحد عبر shell — **مسار واحد** (لا `requestNow` من WorkManager) |
| Login / onboarding | دلتا فورية + بوابة نسب الضيف إن وُجدت صفوف |
| Pull Explore | **دلتا**؛ زر «إصلاح الكتالوج» فقط = full |
| Enroll | `GET /mobile/user-programs` خفيف — ليس full sync |
| جلسة تدريب نشطة | replay + دلتا مسموحان؛ **full مؤجَّل** (`TrainingSessionSyncGate`) |
| TrainingConfigEnsure miss | endpoint القالب المفرد + دلتا — **لا auto-full** |
| شراء Pro | `fetchProfile` → `reports.syncDashboard()` |
| Home | ETag + `If-None-Match` → 304 عند عدم التغيير |
| بعد sync ناجح | `JsonCacheMaintenance` GC + `dataRevision` / `cacheInvalidated` |

### 1.4 متى يُنظف

| الهدف | السياسة |
|---|---|
| Outbox SUCCEEDED | احتفظ 7 أيام ثم purge |
| شبكة / 5xx | لا سقف محاولات؛ backoff + jitter؛ UI إحياء |
| 4xx (عدا 409) | `FAILED_PERMANENT` + تنبيه UX |
| post-training / frames | عمر > 60 يومًا **و** لا outbox غير نهائي يشير إليه؛ orphans على القرص بعد GC |
| planned reports | آخر 90 أو 6 أشهر |
| metrics keys | سقف LRU 50 |
| effective plans | خارج أسبوع ±1 أو TTL 21 يومًا — **ما عدا** أسابيع `week_offline_cache` / `ready_*` |

### 1.5 التعارضات

| الكيان | القاعدة |
|---|---|
| Day customizations | حماية مع pending/in-flight؛ بعد SUCCESS الأحدث يفوز |
| Planned reports | متفائل حتى SUCCESS؛ دمج حقول؛ التقرير الغني لا يُمحى بملخص |
| Preferences | canonical id؛ PENDING و IN_FLIGHT يحميان |
| 409 | server-wins + rollback للكاش المتفائل (complete/plan) |
| Executions | idempotent بالـ client id / `operationId` |
| Published slug | ثابت — الباك يرفض تغييره |

### 1.6 سطح المستخدم

- شارة pending على Home/Profile  
- تنبيه فشل عند `failedCount > 0` + إعادة  
- قسم مزامنة في Profile: قائمة + retry + إصلاح كتالوج  
- حالة رفع على التقرير: معلق / مرفوع / فشل  
- Banner أوفلاين في التدريب وProgram Detail  
- حوار استئناف جلسة بعد crash (UX.3)  
- تحذير logout مع بيانات معلقة (UX.4)  
- **UX.7 نسب تدريبات الضيف — مكتمل end-to-end (2026-07-10):**  
  - بوابة البيانات كانت جاهزة سابقًا؛ **الـ UI كان ناقصًا**.  
  - بعد login/register/google: `AccountSyncRepository` يعيد `AuthenticatedSessionResult` مع الـ prompt (لا يُتجاهل).  
  - `MovitAuthViewModel` يعرض الحوار قبل OpenShell/Onboarding؛ الـ shell يعرضه أيضًا كشبكة أمان عند cold start / OpenShell.  
  - [إضافة] → `acceptGuestOutboxAttribution` ثم **`replayPending(Wait)` فورًا**؛ [حذف] → discard.  
  - strings: `auth_guest_outbox_*` (en/ar).

---

## 2. ماذا نُفِّذ بندًا بندًا

### P0 — الأساس

| بند | ماذا تغيّر |
|---|---|
| **P0.1** | أعمدة `stability`/`alignment*` nullable في Prisma + types؛ لا تحويل null→0؛ اختبارات عقد |
| **P0.2** | أول SQLDelight migration: `owner_user_id` + `next_attempt_at_epoch_ms`؛ `userId()` على المنصات؛ backfill |
| **P0.3** | تصنيف أخطاء sync (`Offline`/`Error(Decode|Http|Unknown)`)؛ outbox `RETRYABLE_NETWORK` vs `UNEXPECTED`؛ telemetry |
| **P0.4** | توثيق baseline (أدناه §4) — قياس حي معلّق |

### P1 — وقف فقدان البيانات

| بند | ماذا تغيّر |
|---|---|
| **P1.1** | refresh 401≠5xx؛ ClearScope؛ outbox يبقى عند expiry؛ لا `runBlocking`؛ UX.7 |
| **P1.2** | Mutex enqueue+replay؛ IN_FLIGHT؛ backoff بلا سقف للشبكة؛ تبعية complete↔executions |
| **P1.3** | `idempotencyKey` = outbox `operationId` على الباك + unique؛ fallback نفس اليوم UTC |
| **P1.4** | تصفير `isUserModified` بعد SUCCESS؛ hydrate بالأحدث؛ **J-N1:** `upsert`/`delete` preference يمرّران canonical id للكاش وللـ outbox معًا (slug خام → 404 دائم) |
| **P1.5** | restore journal **بلا** `start()`؛ orphan >6h؛ UX.3؛ `seedCompletedRepCount` |
| **P1.6** | `SyncMessageContentDto` ينقل `audioEn`/`audioAr` عند الدمج |
| **P1.7** | رفع Explore فوري بنفس `workoutGroupId`؛ حذف طابور RAM |
| **P1.8** | logout: flush قصير ثم تحذير إن بقي pending |
| **P1.9** | دمج تقارير حقول؛ `completedAt` ISO؛ streak من أيام فريدة |

### P2 — حمولة وأداء

| بند | ماذا تغيّر |
|---|---|
| **P2.1** | `GET /api/mobile/user-programs`؛ enroll لا يجرّ full sync |
| **P2.2** | watermark آمن؛ `includeReports=summary`؛ دلتا userPrograms/reports؛ بوابة systemMessages/audioManifest |
| **P2.3** | Explore pull = دلتا؛ توحيد `EXPLORE_LAST_SYNC` مع timestamp الـ sync |
| **P2.4** | Home ETag + If-None-Match؛ trainMode fallback؛ **Redis محجوب** |
| **P2.5** | `JsonCacheMaintenance` + تنظيف orphans لـ `frame_captures` |
| **P2.6** | دمج رسائل idempotent + fingerprint محتوى على الـ config — **انحراف موثّق:** `applySyncMessageLibrary` ما زال يعيد resolve/كتابة configs عند اختلاف fingerprint (كل السجلات المتأثرة عند تغيّر رسالة)؛ أفضل من قبل (transaction + idempotent + skip إن لم يتغيّر) لكنه ليس merge-on-read فقط كما نصّت الخطة؛ سلوكيًا آمن |
| **P2.7** | Mutex sync؛ `transaction{}` للـ apply؛ drift counts من catalog لا explore |
| **P2.8** | optimistic موحّد + rollback عند permanent/server-wins |
| **P2.9** | `imageUrl` مباشر؛ IO للبلوبات؛ memo Home/Explore |
| **P2.10** | إصلاح `dataRevision`؛ `cacheInvalidated` SharedFlow؛ locale→reload |
| **P2.11** | محفزات موحّدة + قفل تدريب + `BatteryNotLow`؛ سياسة المحفزات في §1.3 أعلاه |
| **P2.12** | لا training-config مزدوج؛ journal SQL فقط؛ برنامج تحت `id` |

#### انحراف P2.6 (موثّق)

الخطة استهدفت **merge-on-read فقط** (إيقاف دمج النصوص عند persist). التنفيذ الفعلي يبقي `applySyncMessageLibrary` في مسار sync:

- عند تغيّر بصمة المكتبة يُعاد `resolveRecords` ثم كتابة **كل** السجلات ذات البصمة القديمة (ليس slug المتأثر فقط).
- تحسينات مُسلَّمة: `transaction` على الـ apply، idempotency (استدعاء ثانٍ = 0 writes)، skip عند تطابق fingerprint، lazy rewrite عند القراءة.
- **الحكم:** سلوكيًا آمن — النصوص والصوت يصلان للتدريب؛ الفجوة هي write amplification وليس فقدان بيانات.

### P3 — جودة

| بند | ماذا تغيّر |
|---|---|
| **P3.1** | `MovitApiException(status, body)` بدل regex |
| **P3.2** | `parseIsoToEpochMs` يدعم `Z` و`±HH:MM` |
| **P3.3** | لا fingerprint محلي عند غياب stats السيرفر |
| **P3.4** | `SyncCatalogGraphReport` بعد full: إن `!isComplete` → log + `incrementCatalogGraphIncomplete` (لا يُتجاهل) |
| **P3.5** | `deletedExercise` عبر `resolveExerciseMeta` في export |
| **P3.6** | اسم تمرين حقيقي في `WorkoutExportMapper` |
| **P3.7** | رفض تغيير slug لتمرين منشور |
| **P3.8** | إزالة `pausedAt`/`totalPausedDays` الميتة من DTO |
| **P3.11** | حماية IN_FLIGHT لتفضيلات التمارين |
| **P3.12** | `rekeyPostTraining` فقط إن اختلف الـ id |

---

## 3. أهم الملفات التي لمسها التنفيذ

**موبايل (KMP):**  
`OfflineWriteQueue` · `MovitSyncOrchestrator` · `MovitData` / ClearScope · `SessionJournalStore` · `ReportsSyncRepository` · `DayCustomizationLocalStore` · `JsonCacheMaintenance` · `TrainingSessionWriteHooks` / ViewModel · features: shell/home/account/reports/library

**باك:**  
`mobile-sync.service` + watermark · `mobile-home` ETag · `user-programs` list · `workout-executions` idempotency · Prisma migrations (null metrics + idempotencyKey)

**اختبارات:** عشرات ملفات `*Test.kt` / `*.spec.ts` مضافة أو محدّثة مع البنود أعلاه (انظر الـ working tree).

---

## 4. قياس الحمولة (P0.4) — ملخص

| البند | النتيجة |
|---|---|
| قياس `curl` حي | **تعذّر** — لا باك/`.env` محلي |
| ضغط Nest/nginx | **غير مفعّل** في الكود/سكربت النشر |
| cold bundle | 229 KB خام → 21 KB gzip |
| Prisma لدلتا فارغة (قبل P2.2) | ≈ 24–25 استعلام + I/O أصوات |
| بعد P2.2 (متوقع) | دلتا فارغة غالبًا &lt; 5–15 KB raw؛ هدف gzip &lt; 30 KB |

**مكسب P2.2 الرئيسي:** إسقاط `systemMessages` الكامل في الدلتا الفارغة + عدم بناء `audioManifest` كل دورة + `includeReports=summary` + فلتر enrollments/reports.

**إعادة القياس على staging:**

```bash
TOKEN=$(curl -s -X POST http://HOST/api/mobile/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alustadh.manager@gmail.com","password":"password"}' | jq -r '.accessToken')

curl -s --compressed -w '%{size_download}\n' -o /dev/null \
  -H "Authorization: Bearer $TOKEN" -H 'Accept-Encoding: gzip' \
  "http://HOST/api/mobile/sync?includeReports=summary&updatedAfter=..."
```

---

## 5. حالة المراجعة الحالية للشجرة

- كل تعديلات التنفيذ موجودة **uncommitted** في الـ working tree (بطلب المالك للمراجعة قبل أي commit).  
- ملف الخطة: `Data-Layer-Remediation-Plan.md` (تصميم + ترتيب PRs).  
- ملف التنفيذ: **هذا الملف** فقط.  
- **F7 (تشغيلي):** أُعيد تفعيل CI (`push`/`pull_request` في `movit-android-release` و`movit-kmp-ios`)؛ `mobile-audio-manifest.service.spec.ts` يعمل بلا `DATABASE_URL` (mock لـ Prisma/storage)؛ تقارير `data-layer-review` نُقلت إلى `Docs/99-Archive/data-layer-review/`.  
- **J-N1:** `MobileWriteSyncRepository` يحلّ `resolveCanonicalExerciseId` مرة واحدة ويمرّره للكاش + enqueue (upsert/delete). `:core:data:testAndroidHostTest` أخضر بما فيه `ExercisePreferenceOutboxCanonicalIdTest`.  
- **verifyMigrations:** افتراضيًا off على ويندوز؛ CI يمرّر `-PverifySqlMigrations=true` (Android + iOS workflows). التعويض المحلي: `OutboxSchemaMigrationTest`.  
- خارج النطاق (ليست من Data Layer): `Camera-Engine-Review-Brief.md`، `Main-Screens-Flow-Audit.md`.

---

## 6. الخطوة التالية المقترحة

1. مراجعة الـ diff في IDE / PR draft.  
2. عند الموافقة: commits منطقية حسب مراحل الخطة (P0→P1→P2→P3) أو PR واحد كبير.  
3. على staging: `migrate deploy` + قياس gzip + سيناريو «أسبوع الجيم».
