# الاختبار

> المصدر: [Test Cards](https://docs.myfatoorah.com/docs/test-cards) | [Get Started - Demo](https://docs.myfatoorah.com/docs/get-started)

## بيئة الاختبار (Sandbox)

| العنصر | القيمة |
|--------|--------|
| API URL | https://apitest.myfatoorah.com/ |
| Portal URL | https://demo.myfatoorah.com/ |

## إنشاء حساب تجريبي

1. التسجيل: [registertest.myfatoorah.com](https://registertest.myfatoorah.com/en/)
2. اختيار **الكويت** كدولة
3. تخطي خطوة بيانات البنك
4. إرسال بريد إلى [tech@myfatoorah.com](mailto:tech@myfatoorah.com) لتفعيل الحساب وتمكين الميزات المطلوبة

## مفتاح الاختبار العام

```
SK_KWT_vVZlnnAqu8jRByOWaRPNId4ShzEDNt256dvnjebuyzo52dXjAfRx2ixW5umjWSUx
```

> قد يتغير هذا المفتاح؛ يُفضل استخدام مفتاح من حسابك التجريبي بعد التفعيل.

## بطاقات الاختبار

> ليست كل البوابات توفر بطاقات اختبار. القائمة محدثة حسب ما توفره MyFatoorah.

### Visa / Mastercard

| الحقل | القيمة |
|-------|--------|
| Card Number | 5123450000000008 (أو ما يرد في الوثائق) |
| Expiry | أي تاريخ مستقبلي |
| CVV | أي 3 أرقام |
| Card Holder | أي اسم من جزئين (مثل "test test") |

### Apple Pay

راجع: [Apple Pay Sandbox Testing](https://developer.apple.com/apple-pay/sandbox-testing/)

### KNET

> لا تدعم كل الدول بوابة KNET للاختبار.

### OTP (للمصادقة 3DS)

استخدم **1234** عند طلب OTP في بيئة الاختبار.

## Test Token

يُستخدم لمحاكاة بيئة الإنتاج دون دفع فعلي. التفاصيل من بوابة MyFatoorah.

## التحقق من التكامل

1. إنشاء فاتورة عبر `POST /v3/payments`
2. التأكد من استلام `PaymentURL`
3. فتح الرابط وإتمام الدفع ببطاقة اختبار
4. التحقق من وصول Webhook وتحديث الحالة
5. التحقق من GET Payment Details وإرجاع `Status: SUCCESS`
