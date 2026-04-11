# طرق التكامل التفصيلية

> المصدر: [Choose Your Payment Integration](https://docs.myfatoorah.com/docs/choose-your-payment-integration)

## 1. Embedded Payment (موصى به)

الدفع يتم داخل صفحة الدفع الخاصة بك مع إعادة توجيه محدودة.

### المتطلبات

- استدعاء **InitiateSession** للحصول على `SessionId` و `CountryCode` لكل عملية دفع
- `SessionId` صالح لدفعة واحدة فقط

### Endpoint

```
POST /v3/payments/initiate-session
```

راجع: [Embedded Payment](https://docs.myfatoorah.com/docs/embedded-payment)

---

## 2. Hosted Payment Page

إعادة توجيه العميل لصفحة MyFatoorah لإتمام الدفع.

### التدفق

1. إنشاء فاتورة عبر `POST /v3/payments`
2. استلام `PaymentURL` في الاستجابة
3. إعادة توجيه العميل إلى `PaymentURL`
4. بعد الدفع، إعادة التوجيه إلى `Redirection` URL مع `PaymentId`

---

## 3. Invoicing / Payment Links

إنشاء رابط دفع وإرساله للعميل عبر البريد أو SMS.

### التدفق

1. إنشاء فاتورة مع `PaymentMethod: "INVOICE"`
2. استلام `PaymentURL`
3. إرسال الرابط للعميل (Email / SMS / كليهما)
4. العميل يفتح الرابط ويدفع بأي طريقة مفعلة

### NotificationOption

| القيمة | الوصف |
|--------|-------|
| EMAIL | إرسال الرابط بالبريد فقط |
| SMS | إرسال الرابط برسالة SMS فقط |
| ALL | البريد و SMS |
| LINK | بدون إشعارات (تستخدم الرابط يدوياً) |

---

## 4. Direct Payment

إرسال بيانات البطاقة مباشرة إلى MyFatoorah. **يتطلب شهادة PCI**.

راجع: [Direct Payment](https://docs.myfatoorah.com/docs/v3-direct-payment)
