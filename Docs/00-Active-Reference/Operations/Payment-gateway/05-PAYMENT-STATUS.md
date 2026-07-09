# تحديث حالة الدفع و GET Payment Details

> المصدر: [Updating Payment Status Guidelines](https://docs.myfatoorah.com/docs/v3-updating-payment-status-guidelines) | [Get Payment Details](https://docs.myfatoorah.com/docs/get-payment-details)

## نظرة عامة

بعد إتمام الدفع، تحديث حالة الطلب في نظامك يعتمد على:

1. **RedirectionUrl** — العميل يُعاد توجيهه مع `PaymentId`
2. **Webhook** — إشعار فوري من الخادم إلى الخادم

يُوصى باستخدام **الاثنين معاً** لضمان موثوقية التحديث.

---

## RedirectionUrl

بعد الدفع، MyFatoorah يعيد توجيه العميل إلى الـ URL المُمرر مع إلحاق `PaymentId`.

### الخطوات

1. استقبال الطلب على صفحة الـ callback
2. استخراج `PaymentId` من الـ query
3. استدعاء **GET /v3/payments/{PaymentId}** للتأكد من الحالة
4. عرض النتيجة للعميل أو إعادة التوجيه لصفحة مناسبة

> **تحذير**: الاعتماد على Redirection فقط قد يفشل إذا أغلق العميل الصفحة قبل إتمام التوجيه أو كان الاتصال بطيئاً.

---

## Webhook

يُرسل MyFatoorah طلب POST إلى endpoint الـ Webhook مع تفاصيل الحدث.

### الخطوات

1. التحقق من التوقيع (`myfatoorah-signature`)
2. تحديث حالة الطلب في نظامك
3. إرجاع **HTTP 200 OK**

> غالباً يصل Webhook قبل Redirection لأنه اتصال خادم-خادم.

---

## ترتيب التحديث الموصى به

### إذا وصل Redirection قبل Webhook

1. التحقق من أن الحالة محدثة من Webhook
2. إن لم تكن محدثة: استدعاء GET Payment Details وتحديث الحالة
3. إعادة التوجيه لصفحة النتيجة

### إذا وصل Webhook قبل Redirection

1. التحقق من أن الحالة محدثة من GET Payment Details
2. إن لم تكن محدثة: تنفيذ خطوات Webhook
3. إعادة التوجيه لصفحة النتيجة

---

## GET Payment Details

```
GET /v3/payments/{paymentId}
```

### استجابة نموذجية

```json
{
  "IsSuccess": true,
  "Data": {
    "Invoice": {
      "Id": "6389662",
      "Status": "PAID",
      "Reference": "2025060928"
    },
    "Transaction": {
      "Id": "104690",
      "Status": "SUCCESS",
      "PaymentMethod": "VISA/MASTER",
      "PaymentId": "07076389662322472373"
    }
  }
}
```

### Invoice.Status

| القيمة | المعنى |
|--------|--------|
| PAID | الفاتورة مدفوعة (يوجد معاملة ناجحة) |
| PENDING | معلقة (كل المعاملات فاشلة أو قيد التنفيذ أو ملغاة) |

---

## المرونة (Resilience)

### إذا كان GET Payments غير متاح

1. إعادة المحاولة **3 مرات** مع 5 ثوانٍ بين كل محاولة
2. ضبط timeout لا يقل عن **30 ثانية**
3. إن فشلت كل المحاولات: تعيين الحالة `Pending_Status_Update` واتخاذ إجراء يدوي أو جدولة إعادة المحاولة

### ملاحظة عن Webhook المتعدد

قد يصل أكثر من webhook لنفس المعاملة (مثلاً مع KNET). **حالة SUCCESS نهائية** ولا تُستبدل؛ إذا وصلت SUCCESS يمكن تحديث الطلب كمدفوع بأمان.
