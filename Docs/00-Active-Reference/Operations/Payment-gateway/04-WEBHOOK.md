# Webhook — إعداد واستقبال الإشعارات

> المصدر: [Webhook](https://docs.myfatoorah.com/docs/webhook) | [Webhook V2](https://docs.myfatoorah.com/docs/webhook-v2) | [Webhook Signature](https://docs.myfatoorah.com/docs/webhook-signature)

## المقدمة

Webhook يسمح لـ MyFatoorah بإرسال إشعارات فورية لتطبيقك عند حدوث أحداث (دفع ناجح، فشل، استرداد، إلخ).

> **مطلوب**: HTTPS بشهادة SSL صالحة

## إعداد Webhook في البوابة

1. الدخول إلى **Integration Settings → Webhook Settings**
2. تفعيل **Webhook Feature**
3. إضافة **Endpoint URL** (مثال: `https://yoursite.com/api/webhooks/myfatoorah`)
4. اختيار **Webhook Version** (V2 موصى به)
5. اختيار أنواع الأحداث المطلوبة
6. تفعيل **Secure Key** للتحقق من التوقيع
7. حفظ الإعدادات

## أنواع الأحداث (Webhook V2)

| Code | الاسم | الوصف |
|------|-------|-------|
| 1 | PAYMENT_STATUS_CHANGED | تغيير حالة الدفع |
| 2 | REFUND_STATUS_CHANGED | تغيير حالة الاسترداد |
| 3 | BALANCE_TRANSFERRED | تحويل الرصيد |
| 4 | SUPPLIER_STATUS_CHANGED | تغيير حالة المورد |
| 5 | RECURRING_UPDATES | تحديثات الدفع المتكرر |
| 6 | DISPUTE_STATUS_CHANGED | تغيير حالة النزاع |
| 7 | SUPPLIER_UPDATE_REQUEST_CHANGED | طلب تحديث المورد |

## هيكل الطلب (PAYMENT_STATUS_CHANGED)

```json
{
  "Event": {
    "Code": 1,
    "Name": "PAYMENT_STATUS_CHANGED",
    "CountryIsoCode": "KWT",
    "CreationDate": "2026-01-04T08:15:00.9500000Z",
    "Reference": "WH-626519"
  },
  "Data": {
    "Invoice": {
      "Id": "6409988",
      "Status": "PAID",
      "Reference": "2026000073",
      "ExternalIdentifier": "booking-123"
    },
    "Transaction": {
      "Id": "86781",
      "Status": "SUCCESS",
      "PaymentMethod": "VISA/MASTER",
      "PaymentId": "07076409988323998875"
    }
  }
}
```

### حالات Transaction.Status

| القيمة | المعنى |
|--------|--------|
| SUCCESS | الدفع ناجح |
| FAILED | الدفع فشل |
| AUTHORIZE | تم التفويض (للمدفوعات المؤجلة) |
| CANCELED | ملغى |

## التحقق من التوقيع (Webhook Signature)

1. ترتيب خصائص الـ Data حسب الترتيب المحدد في الوثائق
2. بناء نص: `key=value,key2=value2,...`
3. تشفير النص بـ **HMAC SHA-256** باستخدام المفتاح السري من البوابة
4. ترميز الناتج بـ **Base64**
5. مقارنة الناتج مع قيمة هيدر `myfatoorah-signature`

### مثال ترتيب التوقيع (PAYMENT_STATUS_CHANGED)

```
Invoice.Id=6409988,Invoice.Status=PAID,Transaction.Status=SUCCESS,Transaction.PaymentId=07076409988323998875,Invoice.ExternalIdentifier=booking-123
```

> إذا كانت قيمة أي خاصية `null` استخدم سلسلة فارغة `""`

## سلوك الخادم المطلوب

- **إرجاع HTTP 200 OK** فوراً بعد استلام الطلب
- معالجة البيانات بشكل غير متزامن إن أمكن
- MyFatoorah يعيد المحاولة حتى **4 مرات** (حوالي 100 ثانية لكل محاولة، 10 ثوانٍ بينها)
- بعد 4 فشول لا تُعاد المحاولة
