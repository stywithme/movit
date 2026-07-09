# المحور I — دورة حياة الجلسة والحساب (Auth & Data Lifecycle)
**المراجع:** independent review agent · **التاريخ:** 2026-07-09 · **الملفات المقروءة بالكامل:**
`MovitData.kt`, `MovitHttpClientAuth.kt`, `MovitHttpClientConfig.kt`, `AccountSyncRepository.kt`, `PlatformMovitAuthTokenStore.kt`, `SecureSessionStore.kt`, `AndroidSecureSessionStore.kt`, `IosKeychainSecureSessionStore.kt`, `MovitDataModule.kt`, `MovitPlatformBindings.kt`, `AndroidMovitPlatform.kt` (auth/clear), `IosMovitPlatform.kt` (auth/clear), `SqlDelightMovitLocalStore.kt` (`clearAllUserData`), `InMemoryMovitLocalStore.kt`, `Outbox.sq`, `MobileWriteSyncRepository.kt` (guest upload), `OfflineWriteQueue.kt` (replay auth gate), `ColdOfflineBundleSeeder.kt`, `AudioManifestCache.kt`, `MovitCacheKeys.kt` (absence of userId), `MovitAppShellViewModel.kt` (onSessionExpired + auth effects), `AuthBootstrapContext` / `MovitAuthViewModel.kt`, `SharedAuthRepository.kt`, `SharedProfileRepository.kt`, `TrainingStartResolver.kt`, واختبارات: `MovitDataClearAllUserDataTest.kt`, `TokenLifecycleIntegrationTest.kt`, `MovitHttpClientAuthTest.kt`, `AccountSyncRepositorySecureSessionTest.kt`, `MobileWriteSyncRepositoryTest.kt` (guest enqueue).

---

## 1. الحكم التنفيذي (≤ 10 أسطر)

**كسر جوهري على هدف Offline-First (S1: فقدان بيانات تدريب).** انتهاء الجلسة (أي فشل في `refreshAndPersist` — بما فيه 5xx/شبكة/timeout) يستدعي `notifySessionExpired` → `runBlocking { clearAllUserData() }` فيمسح **كل** الـ outbox المعلق + الجورنال + كاش المستخدم فورًا، ثم يدفع شاشة Auth. Logout الطوعي يفعل نفس المسح بلا flush. الكاش بلا `userId`؛ التبديل الآمن يعتمد كليًا على `clearAllUserData`، لكن مسار الدخول لا يمسح قبل `persistAuthSession` — خطر تلوث إن بقي كاش من جلسة سابقة. Guest uploads تُنسب للحساب التالي الذي يسجّل دخولًا على الجهاز (مقصود جزئيًا، خطر عند تبديل حساب).

**أهم 3 نتائج:** (1) H01 مؤكدة — مسح outbox عند session expiry. (2) أي فشل refresh (ليس فقط 401) = انتهاء جلسة مدمّر. (3) H19 مؤكدة جزئيًا — `runBlocking` على مسار Auth interceptor خطر ANR/deadlock.

---

## 2. إجابات الأسئلة

### I1. انتهاء الجلسة يمسح كل شيء بما فيه الـ outbox المعلق

**الإجابة:** نعم. أي فشل في `refreshAndPersist` (بما فيه 5xx مؤقت، timeout شبكة، جسم غير صالح) يُعامل كانتهاء جلسة نهائي ويمسح البيانات المحلية كلها بما فيها PENDING outbox.

**مسار الاستدعاء:**

1. Ktor Bearer `refreshTokens` يستدعي `refreshAndPersist`؛ عند `null` → `handleSessionExpired()`.
2. `handleSessionExpired` يمسح التوكنات ثم يستدعي `onSessionExpired`.
3. في DI: `onSessionExpired = { MovitData.notifySessionExpired() }`.
4. `notifySessionExpired` يعمل `runBlocking { clearAllUserData() }` ثم callback الـ shell → شاشة Auth.

**الدليل — فشل refresh بلا تمييز حالة HTTP:**

```44:71:kmp-app/core/network/src/commonMain/kotlin/com/movit/core/network/MovitHttpClientAuth.kt
internal suspend fun MovitHttpClientConfig.refreshAndPersist(client: HttpClient): BearerTokens? {
    val refreshToken = tokenStore.readRefreshToken()?.takeIf { it.isNotBlank() } ?: return null
    val response = runCatching {
        client.post(refreshUrl()) {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequestDto(refreshToken))
        }
    }.getOrNull() ?: return null

    if (!response.status.isSuccess()) return null
    val body = runCatching { response.body<AuthApiResponse<AuthTokensDto>>() }.getOrNull() ?: return null
    if (!body.success) return null
    // ...
}

internal fun MovitHttpClientConfig.handleSessionExpired() {
    tokenStore.clearTokens()
    onSessionExpired()
}
```

```90:94:kmp-app/core/network/src/commonMain/kotlin/com/movit/core/network/MovitHttpClientAuth.kt
            refreshTokens {
                config.refreshAndPersist(config.refreshHttpClient) ?: run {
                    config.handleSessionExpired()
                    null
                }
            }
```

**الدليل — المسح الشامل يشمل outbox:**

```83:101:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/MovitData.kt
    internal fun notifySessionExpired() {
        if (isInstalled) {
            runBlocking { clearAllUserData() }
        }
        onSessionExpired?.invoke()
    }

    suspend fun clearAllUserData() {
        if (!isInstalled) return
        val koin = koin()
        koin.get<MovitLocalStore>().clearAllUserData()
        koin.get<AudioManifestCache>().clear()
        LegacyAnalyticsPendingCleaner.clearIfRegistered()
        koin.get<MovitPlatformBindings>().clearLegacyUserCaches()
    }
```

```119:125:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/local/SqlDelightMovitLocalStore.kt
    override suspend fun clearAllUserData() {
        withContext(Dispatchers.IO) {
            jsonQueries.deleteAll()
            syncQueries.deleteAll()
            outboxQueries.deleteAll()
            journalQueries.deleteAllJournals()
        }
    }
```

```51:52:kmp-app/core/data/src/commonMain/sqldelight/com/movit/core/data/db/Outbox.sq
deleteAll:
DELETE FROM outbox_entry;
```

**سلك الـ DI (Brief: MovitDataModule ~67):**

```63:68:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/di/MovitDataModule.kt
            auth = MovitHttpClientConfig(
                tokenStore = tokenStore,
                baseUrlProvider = { bindings.apiBaseUrl() },
                refreshHttpClient = refreshClient,
                onSessionExpired = { MovitData.notifySessionExpired() },
            ),
```

**البيانات غير المرفوعة التي تُمحى:**
| مخزن | ماذا يُفقد |
|---|---|
| `outbox_entry` | كل PENDING/IN_FLIGHT/FAILED — بما فيها `WORKOUT_EXECUTION_UPLOAD` (تدريبات كاميرا) وplanned complete/report والتخصيصات |
| `session_journal_entry` | checkpoints جلسات تدريب حية/يتيمة |
| `json_cache_entry` | home/explore/reports/preferences/day customizations/post-training reports/enrollments… |
| `sync_metadata` | طوابع الدلتا |
| `AudioManifestCache` | metadata فقط (ملفات الصوت على القرص لا تُحذف هنا) |
| legacy prefs | عبر `clearLegacyUserCaches` |

**ملاحظة ترتيب التوكن:** `handleSessionExpired` يستدعي `tokenStore.clearTokens()` **قبل** `onSessionExpired()` → `PlatformMovitAuthTokenStore.clearTokens` → `clearAuthSession()`. ثم `clearAllUserData` يمسح SQL. النتيجة: التوكنات تُمسح أولًا، ثم البيانات — لا فرصة لـ flush بعد انتهاء الجلسة.

**هل 5xx على /refresh = انتهاء جلسة؟** نعم. `!response.status.isSuccess()` يغطي 500/502/503؛ و`runCatching { post }.getOrNull() ?: return null` يغطي timeout/DNS. لا يوجد retry مخصص للـ refresh ولا تمييز بين `invalid_grant` وخطأ سيرفر مؤقت.

**الحكم:** **كسر S1** لهدف «تدريب يصل للباك لاحقًا». المقترح: (أ) عزل مسح الكاش عن مسح outbox، أو (ب) تصدير/الاحتفاظ بصفوف `WORKOUT_EXECUTION_UPLOAD` (وربما planned writes) مربوطة بـ `userId`، أو (ج) على فشل refresh غير-401: لا تستدعِ `handleSessionExpired` — أعد المحاولة لاحقًا واترك الجلسة.

---

### I2. Logout الطوعي + جلسة تدريب نشطة

**الإجابة:** Logout/deleteAccount يمسحان فورًا بلا flush للـ outbox. لا يوجد تنسيق مع جلسة تدريب حية عند session expiry.

**الدليل — logout → clear بدون replay:**

```94:124:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/AccountSyncRepository.kt
    suspend fun logout(): AppResult<Unit> {
        val bindings = platform()
        val refresh = bindings.refreshToken()
        val auth = bindings.authHeader()
        if (auth != null && refresh != null) {
            api.logout(LogoutRequestDto(refresh), authorization = auth)
        }
        clearLocalSession(bindings)
        return AppResult.Success(Unit)
    }
    // ...
    private suspend fun clearLocalSession(bindings: MovitPlatformBindings) {
        if (MovitData.isInstalled) {
            MovitData.clearAllUserData()
        }
        bindings.clearAuthSession()
    }
```

- استدعاء `api.logout` best-effort (لا يفحص النتيجة قبل المسح المحلي).
- لا استدعاء لـ `offlineWrites.replayPending()` قبل المسح.
- `deleteAccount` نفس `clearLocalSession`.

**جلسة تدريب نشطة أثناء انتهاء الجلسة:**
- `onSessionExpired` في الـ shell يدفع Auth و`popAllInner()` فقط — لا يوقف المحرك ولا يحفظ journal إضافيًا:

```49:52:kmp-app/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitAppShellViewModel.kt
            MovitData.onSessionExpired = {
                popAllInner()
                pushInner(MovitInnerRoute.Auth)
            }
```

- أي checkpoint موجود في SQL يُحذف داخل `clearAllUserData` → `deleteAllJournals`.
- تقارير `PostTrainingReportLocalStore` داخل `json_cache` تُمحى أيضًا؛ مسارات ملفات اللقطات على القرص **لا** تُنظَّف صراحة في هذا المسار (يتيمات filesystem — خارج نطاق المسح SQL).

**الحكم:** Logout الطوعي: مسح كامل مقبول خصوصيةً **بعد** flush اختياري أو تحذير «بيانات معلقة ستُفقد». Session expiry أثناء تدريب حي = فقدان الجلسة + أي uploads معلّقة — أسوأ من logout لأن المستخدم لم يختر ذلك.

---

### I3. `runBlocking` داخل `notifySessionExpired` (مسار interceptor)

**الإجابة:** خطر حقيقي لـ ANR على Android وخطر deadlock إن استُدعي من coroutine يملك قفلًا ينتظره `clearAllUserData`/Dispatchers.IO بشكل دائري. H19 مؤكدة كـ S3 مع احتمال تصعيد لـ S2 تحت ضغط main-thread.

**الدليل:**

```83:88:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/MovitData.kt
    internal fun notifySessionExpired() {
        if (isInstalled) {
            runBlocking { clearAllUserData() }
        }
        onSessionExpired?.invoke()
    }
```

- `notifySessionExpired` **ليست** `suspend`؛ تُستدعى من `handleSessionExpired` (غير suspend) من داخل كتلة `refreshTokens` الخاصة بـ Ktor Auth.
- `clearAllUserData` → SQLDelight على `Dispatchers.IO` + مسح legacy prefs — عمل I/O متزامن عبر `runBlocking`.
- بعد المسح، `onSessionExpired?.invoke()` يعدّل UI state (`popAllInner` / push Auth) من خيط الشبكة إن لم يُرحَّل — الـ shell لا يلفّه بـ `viewModelScope.launch`.

**الحكم:** يجب إزالة `runBlocking`: إما `CoroutineScope(SupervisorJob+IO).launch` مع ضمان ترتيب (مسح ثم UI)، أو جعل callback suspend عبر قناة/SharedFlow يستهلكه الـ shell. لا تلمس UI من خيط الـ interceptor مباشرة.

---

### I4. تعدد الحسابات على نفس الجهاز + guest uploads

**الإجابة:** الكاش/outbox **بلا userId**. الأمان بين الحسابات يعتمد على أن كل مسار تبديل يمر بـ `clearAllUserData`. Logout/expiry يمرّان. **مسار login/register/google لا يمسح قبل الكتابة** — يعتمد على أن الجلسة السابقة مُسحت. Guest `WORKOUT_EXECUTION_UPLOAD` يبقى في outbox ويُرفع بعد أول sign-in ناجح على الجهاز (أي حساب).

**الدليل — مفاتيح عامة بلا userId:**

`MovitCacheKeys.kt` لا يحتوي أي `userId` / scoping؛ المفاتيح مثل `home_data_json`, `post_training_report_$reportId`, `pref_<id>` عامة للجهاز.

**الدليل — login يثبت الجلسة دون clear مسبق:**

```30:40:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/AccountSyncRepository.kt
    suspend fun login(email: String, password: String): AppResult<AuthSessionSnapshot> {
        // ...
        val snapshot = data.toSnapshot()
        platform().persistAuthSession(snapshot)
        return AppResult.Success(snapshot)
    }
```

`SharedAuthRepository` و`handleAuthEffect(OpenShell)` يطلقان sync فقط — لا `clearAllUserData` عند الدخول.

**الدليل — guest enqueue بدون auth:**

```190:204:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/MobileWriteSyncRepository.kt
    /**
     * Guest sessions enqueue locally without auth (same durable outbox path as offline);
     * [OfflineWriteQueue.replayPending] uploads after sign-in.
     */
    suspend fun uploadWorkoutExecution(...): AppResult<String> {
        val id = operationId ?: request.id
        offlineWrites.enqueueWorkoutExecutionUpload(request, operationId = id)
        return AppResult.Success(id)
    }
```

```137:139:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/outbox/OfflineWriteQueue.kt
    suspend fun replayPending(): OutboxReplayResult {
        val auth = platform().authHeader()
            ?: return OutboxReplayResult(0, 0, 0, 0)
```

**سيناريو تلوث حسابات:**
1. مستخدم A يتدرب أوفلاين → PENDING executions في outbox.
2. Session expiry أو logout → `clearAllUserData` يمسحها (فقدان لـ A — I1).
3. بديل أخطر إن تغيّر المسار لاحقًا أو فشل المسح جزئيًا: بقايا كاش A تُعرض لـ B لأن المفاتيح عامة.
4. Guest يتدرب → enqueue → يسجّل دخول بحساب B → `replayPending` يرفع تدريبات الضيف إلى B (مقصود للمنتج إن الضيف = نفس الشخص؛ خطر إن جهاز مشترك).

**الحكم:** مع المسح الحالي عند logout/expiry، تبديل A→B بعد logout نظيف نسبيًا. الثغرة المتبقية: (1) لا clear-on-login دفاعًا، (2) guest uploads تُنسب للحساب التالي بلا ربط userId، (3) H01 يجعل «الاحتفاظ بـ outbox عبر انتهاء الجلسة» غير موجود أصلًا.

---

### I5. أول تشغيل ضيف / بدون جلسة — ماذا يعمل أوفلاين؟

**الإجابة:** البذرة الباردة تزرع كتالوج/configs/رسائل فقط. الـ shell **لا** يستدعي `bootstrapLocalCaches` إلا مع `hasActiveSession`. التدريب من المكتبة يستدعي bootstrap عند Start. لا Home مستخدم، لا تقارير شخصية، لا enrollments. Guest يستطيع نظريًا التدرب إن وصل لمسار Start (config من البذرة) ويُصفّ الرفع في outbox؛ بقية الكتابات تُرفض بدون auth.

**الدليل — البذرة: لا home مستخدم:**

```14:20:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/cache/ColdOfflineBundleSeeder.kt
 * The bundled JSON ships real catalog/training config + system messages only; [BundledColdOfflineDto.home]
 * is intentionally null because home dashboard data is user-specific and must come from sync.
```

تزرع عند النقص: explore، exercise configs، message library، system messages؛ `home` فقط إن وُجد في الـ bundle (عادة null).

**الدليل — bootstrap مشروط بجلسة نشطة في الـ shell:**

```63:68:kmp-app/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitAppShellViewModel.kt
            if (bootstrap.hasActiveSession) {
                viewModelScope.launch {
                    MovitData.bootstrapLocalCaches()
                }
                requestSyncIfNeeded()
```

```307:309:kmp-app/feature/account/src/commonMain/kotlin/com/movit/feature/account/MovitAuthViewModel.kt
            return AuthBootstrapContext(
                // ...
                hasActiveSession = !platform.authHeader().isNullOrBlank(),
```

مستخدم غير مسجّل → startup stack = Auth (Splash/Intro/SignIn) — لا يصل لـ Home/Train tabs بدون جلسة.

**الدليل — Start التدريب يستدعي bootstrap:**

```18:19:kmp-app/feature/library/src/commonMain/kotlin/com/movit/feature/library/TrainingStartResolver.kt
    if (!MovitData.isInstalled) return null
    MovitData.bootstrapLocalCaches()
```

**مصفوفة قدرات الضيف/أول تشغيل بدون نت:**

| قدرة | متاحة؟ | ملاحظة |
|---|---|---|
| تصفح Explore من البذرة | جزئيًا | فقط إن وُصل للشاشة واستُدعي bootstrap (shell لا يفعل ذلك بدون جلسة؛ Prepare/Start يفعلان) |
| Home / برنامج اليوم | لا | home user-specific؛ sync محجوب بدون auth |
| تدريب كاميرا من config محلي | نعم إن وُجد slug في البذرة ومسار UI يسمح | upload يدخل outbox |
| تقارير شخصية / dashboard | لا | تحتاج auth + sync؛ التقارير المحلية تُمسح عند clear |
| planned complete / preferences | لا | `hasAuth()` يرفض |

**الحكم:** «ضيف أوفلاين كامل» غير مدعوم كمنتج في الـ shell الحالي (بوابة Auth). مسار guest upload في طبقة البيانات موجود ومستعد لـ replay بعد الدخول — لكن I1 يهدد هذا المسار إن انتهت جلسة لاحقًا قبل الرفع.

---

## 3. نتائج الفرضيات المسندة (H..)

| فرضية | الحكم | الدليل | سيناريو الإعادة خطوة بخطوة | الأثر الفعلي |
|---|---|---|---|---|
| **H01** | **مؤكدة** | `MovitData.kt:83-101`, `MovitDataModule.kt:67`, `SqlDelightMovitLocalStore.kt:119-125`, `Outbox.sq:51-52` | (1) مستخدم يتدرب أسبوعًا أوفلاين → PENDING executions في outbox. (2) يعود أونلاين؛ access منتهٍ. (3) أي طلب API → 401 → refresh. (4) `/refresh` يفشل (401 أو 503 أو timeout). (5) `handleSessionExpired` → `notifySessionExpired` → `deleteAll` على outbox. (6) المستخدم يرى Auth؛ التدريبات غير المرفوعة اختفت محليًا بلا أثر. | **S1 فقدان صامت** لبيانات تدريب — يكسر هدف المنتج 2.1 |
| **H19** | **مؤكدة** (S3، مع تصعيد محتمل) | `MovitData.kt:85` `runBlocking { clearAllUserData() }` من مسار Auth refresh | (1) 401 على خيط شبكة Ktor. (2) refresh يفشل. (3) `notifySessionExpired` يحجب الخيط بـ `runBlocking` أثناء مسح SQL + legacy prefs. (4) على Android إن وصل الاستدعاء لـ main أو أغلق خيطًا حرجًا → ANR/تجمّد؛ الـ UI callback يعمل مباشرة بعد الحجب. | ANR/deadlock محتمل؛ ترتيب غير آمن للـ UI من خيط غير UI |

---

## 4. نتائج جديدة غير مذكورة في الـ Brief

| ID | الخطورة | الفرضية الجديدة | الدليل | سيناريو | الأثر |
|---|---|---|---|---|---|
| **I-N1** | **S1** | أي فشل refresh (5xx/شبكة/parse) ≡ session expiry مدمّر — لا تمييز عن refresh token باطل | `MovitHttpClientAuth.kt:51-53,91-93` | سيرفر auth يعيد 503 أثناء صيانة قصيرة → مسح outbox كامل رغم أن refresh token ما زال صالحًا | فقدان بيانات بسبب خطأ مؤقت |
| **I-N2** | **S2** | Login/register لا يستدعيان `clearAllUserData` قبل `persistAuthSession` — الاعتماد الوحيد على مسح الجلسة السابقة | `AccountSyncRepository.kt:30-40,70-81`؛ `SharedAuthRepository`؛ لا clear في `handleAuthEffect` | إن بقي كاش/outbox لسبب (فشل جزئي، نسخة قديمة، مسار جديد بدون clear) → حساب B يرى بيانات A | تلوث خصوصية/بيانات بين حسابات |
| **I-N3** | **S2** | Guest/pending `WORKOUT_EXECUTION_UPLOAD` بلا `userId` تُرفع لأول حساب يسجّل دخولًا بعد الـ enqueue | `MobileWriteSyncRepository.kt:190-204`؛ `OfflineWriteQueue.replayPending:137-139` | ضيف يتدرب على جهاز مشترك → يدخل حساب آخر → replay ينسب التنفيذات للحساب الجديد | إسناد تدريب لحساب خاطئ |
| **I-N4** | **S3** | `onSessionExpired` في الـ shell يعدّل الـ navigation مباشرة من callback الشبكة بلا `viewModelScope`/`Main` dispatcher | `MovitAppShellViewModel.kt:49-52` | انتهاء جلسة أثناء sync خلفي → تحديث Compose state من خيط غير UI | Undefined UI behavior / crashes نادرة |
| **I-N5** | **S3** | `clearAllUserData` لا يحذف ملفات لقطات الفريمات/الصوت على filesystem — orphan files بعد logout/expiry | `MovitData.clearAllUserData` يمسح SQL + manifest metadata فقط؛ `MovitPeakFrameCapture.localPath` على القرص | تدريب كثير + logout متكرر → نمو ملفات يتيمة | تضخم تخزين (يرتبط بمحور A) |

---

## 5. التوصيات

| ID | الخطورة | الإصلاح المقترح (ملفات محددة) | الجهد | مخاطر التنفيذ |
|---|---|---|---|---|
| **I-R1** | S1 | **فصل انتهاء الجلسة عن مسح الـ outbox:** في `MovitData.notifySessionExpired` / `clearAllUserData` أضف سياسة `ClearScope` (tokens+user caches vs durable writes). احتفظ على الأقل بـ `WORKOUT_EXECUTION_UPLOAD` (+ اختياريًا planned writes) مربوطة بـ `userId` في payload أو عمود جديد. امسحها فقط عند logout صريح بعد تأكيد، أو بعد replay ناجح لحساب آخر مختلف. ملفات: `MovitData.kt`, `SqlDelightMovitLocalStore.kt`, `Outbox.sq`, `AccountSyncRepository.kt`. | M | تحتاج قرار منتج: هل logout يحتفظ ببيانات معلّقة؟ اختبارات ترحيل schema |
| **I-R2** | S1 | **تمييز فشل refresh:** في `refreshAndPersist` — 401/403 أو `success=false` → session expired؛ 5xx/timeout/IO → أرجع خطأ retryable **بدون** `handleSessionExpired`. ملفات: `MovitHttpClientAuth.kt` + اختبارات في `MovitHttpClientAuthTest.kt` / `TokenLifecycleIntegrationTest.kt`. | S | قد تترك access منتهيًا مؤقتًا؛ يحتاج backoff على الطلبات التالية |
| **I-R3** | S3→S2 | **إزالة `runBlocking`:** انقل المسح إلى scope مخصص؛ اجعل `onSessionExpired` يُستهلك على Main عبر `SharedFlow` في الـ shell. ملفات: `MovitData.kt`, `MovitAppShellViewModel.kt`. | S | يجب ضمان عدم سباق طلبات API بعد مسح التوكن وقبل اكتمال المسح |
| **I-R4** | S2 | **Clear-on-login الدفاعي + ربط outbox بـ userId:** عند `persistAuthSession` إن تغيّر `userId` عن الأخير المخزّن → `clearAllUserData` أو امسح كاش المستخدم فقط واحتفظ بـ guest executions إن `ownerUserId==null` ثم انسبها. ملفات: `AccountSyncRepository.kt`, `AndroidMovitPlatform`/`IosMovitPlatform`, outbox payload. | M | guest→account attribution يحتاج قاعدة منتج صريحة |
| **I-R5** | S2 | **Logout: flush أو تحذير:** قبل `clearLocalSession`، إن وُجد PENDING: حاول `replayPending()` مرة؛ إن بقي شيء اعرض «N عناصر لم تُرفع — الخروج سيحذفها» أو صدّر لملف طوارئ. ملفات: `AccountSyncRepository.kt`, `MovitProfileViewModel.kt`. | S–M | UX + حالات شبكة أثناء الخروج |

---

## 6. فجوات الاختبارات

**موجودة وراجعتُها:**
- `MovitDataClearAllUserDataTest` — يثبت أن `clearAllUserData` يمسح outbox/json/sync/audio metadata (يدعم H01 كسلوك مقصود حاليًا، لا كخلل).
- `MovitHttpClientAuthTest.failedRefresh_clearsSessionAndNotifies` — فشل refresh → clear tokens + notify (لا يختبر مسح outbox لأن `onSessionExpired` وهمي).
- `TokenLifecycleIntegrationTest.homeSync_failedRefresh_clearsSessionAndNotifies` — يمر عبر `MovitData.notifySessionExpired` لكن **بدون** `MovitData.install` ببيانات outbox حقيقية في نفس السيناريو.
- `AccountSyncRepositorySecureSessionTest.logout_clearsPersistedSecureTokens` — يمسح التوكنات فقط؛ **لا** يثبت مسح outbox عند logout مع `MovitData.install`.
- `MobileWriteSyncRepositoryTest.uploadWorkoutExecution_withoutAuth_enqueuesForReplayAfterSignIn` — يغطي guest enqueue.

**ناقصة (مقترحة):**
| ملف مقترح | ماذا يغطي |
|---|---|
| `SessionExpiryPreservesOutboxTest.kt` (أو توسيع `TokenLifecycleIntegrationTest`) | تثبيت H01 كـ regression بعد I-R1: refresh fail + PENDING execution **لا** يُحذف (أو يُحذف فقط حسب السياسة الجديدة) |
| `RefreshServerErrorDoesNotExpireSessionTest.kt` | 503 على `/auth/refresh` → لا `clearAllUserData` ولا `onSessionExpired` |
| `LogoutFlushesOrWarnsOutboxTest.kt` | سلوك I-R5 |
| `AccountSwitchClearsForeignCacheTest.kt` | login بـ userId مختلف يمسح كاش المستخدم السابق |
| `NotifySessionExpiredDoesNotRunBlockingOnCallerTest.kt` | أو اختبار هيكل: callback لا يحجب؛ مسح على IO scope |
| `GuestUploadAttributedAfterLoginTest.kt` | بعد sign-in، replay يرسل execution؛ وثّق قاعدة الإسناد |
