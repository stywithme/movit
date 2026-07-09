# Assets

ملفات غير نصية أو مراجع بصرية — **ليست SSOT للمنطق**.

| مجلد | المحتوى | ملاحظة |
|------|---------|--------|
| [`UI-Docs/`](UI-Docs/) | شعار، خط Alexandria، لقطات | ~26 ملف |
| [`Examples/`](Examples/) | مراجع UI خارجية | فبراير 2026 |
| [`ML-Training-Images/`](ML-Training-Images/) | صور `train/` (Standing/Sitting/Lying) | ~458 صورة — ليست توثيقاً نصياً |
| [`Config-Samples/`](Config-Samples/) | عينات OAuth JSON | **أسرار — لا تُرفع للإنتاج ولا تُنسخ لمستودعات عامة** |

## تحذير: Config-Samples

مجلد `Config-Samples/google-auth-json/` يحتوي `client_secret_*.json`. هذه مفاتيح OAuth حقيقية أو شبه حقيقية:

- لا تُستخدم في build الإنتاج من هذا المسار
- يُفضّل استبدالها بعينات وهمية أو إزالتها من Git عند التنظيف التالي
- لا تُشارك خارج الفريق

## Workout_flow (محذوف من هنا)

`Examples/Workout_flow/` كان يحتوي كود PHP/GraphQL من مشروع Laravel (طلبات/مخزون) — **ليس Movit**.

نُقل إلى [`99-Archive/Superseded/non-movit-snippets/`](../99-Archive/Superseded/non-movit-snippets/) ومُعلَّم للحذف النهائي.
