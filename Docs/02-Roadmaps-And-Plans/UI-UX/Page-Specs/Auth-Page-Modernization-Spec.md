# Auth Page Modernization Spec (10)

آخر تحديث: 2026-06-09

## Implementation Status

- أول نسخة KMP في `kmp-app/feature/account` — `MovitAuthScreen` / `MovitAuthViewModel` / `SharedAuthRepository`.
- مربوطة داخل `feature:shell` عبر `MovitInnerRoute.Auth` (من Profile → Sign in).
- تسجيل الدخول والتسجيل عبر `POST /api/mobile/auth/login|register` عند `MovitDataInstall`.
- **Google Sign-In:** زر UI موثّق كـ **stub** — يعرض رسالة `auth_google_unavailable` عبر `MovitAuthEffect.ShowMessage`؛ لا OAuth native في Phase 05 (المرجع: legacy `GoogleSignInHelper` مؤجل لبوابة launcher).
- **نسيت كلمة المرور:** parity مع `ForgotPasswordActivity` — نجاح يُظهر لوحة success داخل الشاشة (أيقونة + نص + «العودة لتسجيل الدخول») + toast عبر shell.
- **Splash / Intro:** bootstrap مرتبط بحالة الجلسة و`movit_auth::intro_seen` في `MovitPlatformBindings` cache.

## Current Implementation

### Legacy (مرجع)

| الشاشة | الملف |
|--------|-------|
| Splash | `SplashActivity` — تأخير + فحص `is_first_launch` / `AuthManager.isLoggedIn` |
| Intro | `OnboardingActivity` — 3 شرائح |
| Sign in | `SignInActivity` — email/password + Google + forgot link |
| Sign up | `SignUpActivity` — register + Google |
| Forgot | `ForgotPasswordActivity` — `POST forgotPassword` + success panel |

### KMP

| الملف | الدور |
|-------|-------|
| `MovitAuthScreen.kt` | Splash · Intro · SignIn · SignUp · Forgot (+ success) |
| `MovitAuthViewModel.kt` | state machine · validation · bootstrap · effects |
| `SharedAuthRepository.kt` | `MovitData.account` أو `FakeAuthRepository` |
| `MovitAuthRoute.kt` | Route + effect collection |

## User Goals

- مستخدم جديد: يرى splash قصير → intro (مرة واحدة) → تسجيل دخول أو إنشاء حساب.
- مستخدم عائد (intro_seen): يصل مباشرة لـ Sign in.
- مستخدم مسجّل بالفعل: يُوجَّه فوراً للـ shell أو onboarding profile.
- استعادة كلمة المرور: إدخال بريد → تأكيد بصري واضح.

## Bootstrap Logic

```
authHeader() present?     → OpenShell / OpenOnboarding
intro_seen OR no MovitData? → SignIn (تخطي splash/intro)
else                      → Splash (1.8s) → Intro
```

مفتاح cache: `movit_auth` / `intro_seen` = `"true"` بعد Skip أو إكمال Intro.

## Google Sign-In Stub (موثّق)

| البند | القرار |
|-------|--------|
| UI | زر `auth_google` على Sign in (مطابق `10-auth.html`) |
| السلوك | `GoogleSignInClicked` → `ShowMessage(GOOGLE_SIGN_IN_STUB_MESSAGE)` |
| i18n | `auth_google_unavailable` في `core:resources` |
| مستقبلاً | ربط `GoogleSignInHelper` / Credential Manager عبر `expect/actual` في Pre-06+ |

## Forgot Password Parity

| Legacy | KMP |
|--------|-----|
| يخفي النموذج بعد النجاح | `forgotPasswordSent = true` |
| `successContainer` + أيقونة بريد | `AuthForgotSuccessPanel` |
| `reset_link_sent` + `check_your_email` | `auth_reset_sent` + `auth_reset_sent_sub` |
| `back_to_sign_in` | `auth_back_to_sign_in` |
| Toast | `MovitAuthEffect.ShowMessage` |

## Accessibility

- `contentDescription` على شعار التطبيق وintro icons وsuccess email icon.
- `IntroDots` مع `auth_intro_page_indicator` semantics.

## Tests (`feature:account` commonTest)

- Validation: sign-in · sign-up · forgot.
- Bootstrap targets: active session · intro seen · first launch.
- Intro skip/complete يحدّث `intro_seen`.
- Google stub يصدّر effect.
- Forgot success state.
- `FakeAuthRepository` login/register.

تشغيل: `./gradlew :feature:account:testDebugUnitTest`

## Scorecard Target (Post-gap closure)

| المجال | قبل | بعد |
|--------|-----|-----|
| Functional | 76% | **86%** |
| Visual | 75% | **78%** |
| A11y | 30% | **75%** |
| Tests | 50% | **75%** |
| **الإجمالي** | 76% | **~82%** |

## Prototype

[`10-auth.html`](../prototypes/10-auth.html) — حالات: splash · intro · signin · signup · forgot.

## Out of Scope (Phase 05)

- Google OAuth فعلي على Android/iOS.
- Splash كـ launcher رئيسي (يبقى `SplashActivity` حتى WS-G).
- رسائل validation مترجمة داخل ViewModel (نمط مشترك مع بقية account features).
