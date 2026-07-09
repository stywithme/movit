# إنشاء الفواتير وروابط الدفع

> المصدر: [Create Payment API](https://docs.myfatoorah.com/reference/create-payment)

## Endpoint

```
POST /v3/payments
```

## رأس الطلب

```
Authorization: Bearer YOUR_API_TOKEN
Content-Type: application/json
```

## هيكل الطلب الأساسي

```json
{
  "PaymentMethod": "INVOICE",
  "Order": {
    "Amount": 10,
    "Currency": "KWD",
    "ExternalIdentifier": "order-123"
  },
  "Customer": {
    "Name": "أحمد محمد",
    "Mobile": { "CountryCode": "+20", "Number": "1234567890" },
    "Email": "customer@example.com",
    "Reference": "order-456"
  },
  "IntegrationUrls": {
    "Redirection": "https://yoursite.com/payment/callback",
    "Webhook": "https://yoursite.com/api/webhooks/myfatoorah"
  },
  "Language": "AR",
  "MetaData": {
    "UDF1": "orderId",
    "UDF2": "userId"
  }
}
```

## الحقول الأساسية

| الحقل | النوع | مطلوب | الوصف |
|-------|-------|-------|-------|
| PaymentMethod | string | لا | `INVOICE` (كل الطرق)، `CARD`، `KNET`، `APPLE_PAY`، `GOOGLE_PAY` |
| Order.Amount | number | نعم | المبلغ (أكبر من 0) |
| Order.Currency | string | لا | KWD, SAR, AED, EGP, QAR, BHD, JOD, OMR |
| Order.ExternalIdentifier | string | لا | معرف خارجي (مثل orderId) يُرجع في Webhook |
| Customer.Name | string | لا | اسم العميل |
| Customer.Mobile | object | حسب NotificationOption | رقم الجوال |
| Customer.Email | string | حسب NotificationOption | البريد الإلكتروني |
| IntegrationUrls.Redirection | string | موصى به | URL لإعادة التوجيه بعد الدفع |
| IntegrationUrls.Webhook | string | لا | URL للـ Webhook (إن لم يُضف يُستخدم المُعد في البوابة) |
| Language | string | لا | EN أو AR |

## استجابة ناجحة

```json
{
  "IsSuccess": true,
  "Message": "",
  "ValidationErrors": null,
  "Data": {
    "InvoiceId": "6309730",
    "PaymentId": null,
    "PaymentURL": "https://demo.MyFatoorah.com/KWT/ie/01072630973041-4f6031ae",
    "PaymentCompleted": false,
    "TransactionDetails": null
  }
}
```

| الحقل | الوصف |
|-------|-------|
| InvoiceId | معرف الفاتورة |
| PaymentId | معرف الدفعة (يُضاف بعد إتمام الدفع) |
| PaymentURL | رابط صفحة الدفع — أرسله للعميل أو وجّهه إليه |
| PaymentCompleted | هل اكتمل الدفع فوراً (مثلاً عند تعطيل 3DS) |
