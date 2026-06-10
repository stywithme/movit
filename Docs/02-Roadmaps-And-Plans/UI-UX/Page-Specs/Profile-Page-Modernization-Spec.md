# Profile Page Modernization Spec

آخر تحديث: 2026-06-09

## Implementation Status

- تم تنفيذ `MovitProfileScreen` داخل `feature:account` وربطها بتبويب Profile في `feature:shell`.
- إعدادات Legacy المنقولة: اللغة، المظهر (فاتح/داكن/النظام)، إشارات صوتية، اهتزاز (عبر `notifications` API)، حالة Pro/Free، اشتراك، تسجيل خروج مع تأكيد.
- روابط التنقل: Onboarding (ملف التدريب)، Assessment، Level، Subscription.
- بطاقة Hero + Pro/Free مطابقة لـ `11-profile.html` (شارة Gold لـ Pro، Unlock Pro للمجاني).
- اللغة والمظهر يحدّثان `MovitPlatformBindings`؛ الـ shell يعيد تطبيق `MovitLocaleProvider` و`MovitTheme`.

## Current Implementation

- **Legacy:** `ProfileActivity` — avatar، Pro، language، theme، voice، training profile dialog، logout.
- **Prototype:** `prototypes/11-profile.html` — hero، pro-card free/pro، preferences، account، subscription sub-screen.
- **KMP:**
  - `MovitProfileScreen.kt` · `MovitProfileViewModel.kt` · `MovitProfileRoute.kt`
  - `MovitSubscriptionScreen.kt` (خطط Pro)
  - `SharedProfileRepository` → `AccountSyncRepository`

## User Goals

- عرض هوية الحساب (اسم، بريد، صورة placeholder).
- إدارة التفضيلات (لغة، مظهر، صوت، اهتزاز).
- فهم حالة الاشتراك والترقية إلى Pro.
- الوصول لملف التدريب، التقييم، المستوى.
- تسجيل خروج آمن.

## Content Inventory

| منطقة | محتوى |
|--------|--------|
| Hero | Avatar · اسم · بريد · زر تعديل (قريباً) |
| Pro card | Free: Unlock Pro + View plans · Pro: شارة PRO + تجديد + Manage |
| Preferences | Language · Appearance · Audio cues · Haptic |
| Account | Edit profile · Training profile · Assessment · Level · Sign out |
| Subscription | شاشة داخلية (سعر، ميزات، annual) |

## UX Target

- مطابقة `11-profile.html`: مجموعات إعدادات بعناوين uppercase، pro-card قبل preferences.
- Sign out بلون error بدون chevron.
- حوارات اختيار واحد للغة والمظهر؛ تأكيد قبل logout.
- حالة signed-out: `MovitEmptyState` + CTA تسجيل الدخول.

## Layout Spec

- `MovitScaffold` + scroll عمودي.
- `MovitListCard` لكل مجموعة إعدادات.
- لا `MovitTheme` داخل الشاشة (حدود theme boundary).

## Navigation & Effects

| Event | Effect |
|-------|--------|
| Sign in | `OpenAuth` |
| Training profile | `OpenOnboarding` |
| Assessment | `OpenAssessment` |
| Level | `OpenLevel` |
| View/Manage subscription | `OpenSubscription` (overlay داخلي) |
| Language saved | `LanguageChanged` → shell `localeRevision++` |
| Theme saved | `ThemeModeChanged` → shell `themeMode` |
| Logout | `LoggedOut` → Auth inner route |

## API / Platform

- `GET /api/mobile/auth/profile` · `PATCH /api/mobile/auth/settings`
- `MovitPlatformBindings`: `themeMode`, `setThemeMode`, `applyPreferredLanguage`
- Android debug: `AppThemeManager` + `AppCompatDelegate` عبر `MovitDataInstall`

## Tests

- `MovitProfileViewModelTest` — signed-in/out، language، theme، haptic، logout
- `ProfileApiMapperTest` — Pro renewal formatting
- `MovitAppShellStateTest` — LanguageChanged · ThemeModeChanged
- `MovitAccountThemeBoundaryTest` — لا MovitTheme في الشاشة

## Remaining Gaps

- Edit profile / avatar upload (legacy: toast قريباً).
- Exercise settings dialog (legacy: مؤجل — ليس في prototype 11).
- Google Play billing حقيقي في Subscription.
- training profile summary من `GET training-profile` (حالياً نص افتراضي من mapper).

## Definition of Done

- [x] Language + Appearance فعّالان
- [x] Haptic toggle فعّال
- [x] Pro/Free hero card مع MovitTag Gold
- [x] Logout confirmation
- [x] روابط Onboarding / Assessment / Level / Subscription
- [x] a11y لأيقونات avatar/edit
- [x] Page spec + scorecard محدّث
- [x] unit tests + shell theme/locale wiring
