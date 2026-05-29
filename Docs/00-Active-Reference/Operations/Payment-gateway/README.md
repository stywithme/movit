# MyFatoorah — توثيق منصة الدفع

توثيق تكامل منصة MyFatoorah للدفع الإلكتروني في المشروع.

**الوثائق الرسمية:** [docs.myfatoorah.com](https://docs.myfatoorah.com/docs/get-started)

---

## الملفات

| الملف | المحتوى |
|-------|---------|
| [00-OVERVIEW.md](./00-OVERVIEW.md) | نظرة عامة، طرق التكامل، العملات |
| [01-AUTHENTICATION.md](./01-AUTHENTICATION.md) | التوثيق، مفاتيح API، عناوين الخوادم |
| [02-INTEGRATION-METHODS.md](./02-INTEGRATION-METHODS.md) | Embedded، Hosted، Invoicing، Direct |
| [03-CREATING-PAYMENTS.md](./03-CREATING-PAYMENTS.md) | إنشاء الفواتير، روابط الدفع، ربط مع Bookings |
| [04-WEBHOOK.md](./04-WEBHOOK.md) | إعداد Webhook، التوقيع، أنواع الأحداث |
| [05-PAYMENT-STATUS.md](./05-PAYMENT-STATUS.md) | تحديث الحالة، Redirection، GET Payment Details |
| [06-TESTING.md](./06-TESTING.md) | بيئة الاختبار، بطاقات الاختبار، التحقق |

---

## سير العمل السريع

1. **التوثيق** — إنشاء مفتاح API من البوابة ([01-AUTHENTICATION](./01-AUTHENTICATION.md))
2. **إنشاء الدفع** — `POST /v3/payments` مع Order و Customer و IntegrationUrls ([03-CREATING-PAYMENTS](./03-CREATING-PAYMENTS.md))
3. **استقبال النتيجة** — Webhook + Redirection + GET Payment Details ([04-WEBHOOK](./04-WEBHOOK.md), [05-PAYMENT-STATUS](./05-PAYMENT-STATUS.md))
4. **الاختبار** — استخدام Sandbox وبطاقات الاختبار ([06-TESTING](./06-TESTING.md))
