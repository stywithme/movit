# التوثيق (Authentication)

> المصدر: [API Key](https://docs.myfatoorah.com/docs/api-key)

## نظرة عامة

مفتاح API مطلوب لتوثيق تطبيقك مع MyFatoorah. يُضاف في رأس الطلب كـ Bearer Token.

## عناوين API والبوابة

| البيئة / الدولة | API URL | Portal URL |
|-----------------|---------|------------|
| **Test (Sandbox)** | https://apitest.myfatoorah.com/ | https://demo.myfatoorah.com/ |
| الكويت، البحرين، الأردن، عُمان | https://api.myfatoorah.com/ | https://portal.myfatoorah.com/ |
| الإمارات | https://api-ae.myfatoorah.com/ | https://ae.myfatoorah.com/ |
| السعودية | https://api-sa.myfatoorah.com/ | https://sa.myfatoorah.com/ |
| قطر | https://api-qa.myfatoorah.com/ | https://qa.myfatoorah.com/ |
| مصر | https://api-eg.myfatoorah.com/ | https://eg.myfatoorah.com/ |

## إنشاء مفتاح API

1. تسجيل الدخول إلى بوابة MyFatoorah (Super Master Account)
2. من القائمة الجانبية: **Integration Settings → API Key**
3. النقر على **Add** لإنشاء مفتاح جديد
4. إعداد المفتاح:
   - **Name**: اسم تعريفي للمفتاح
   - **Expiry Date**: تاريخ انتهاء الصلاحية
   - **Active Status**: تفعيل/إلغاء تفعيل
   - **Permissions**: صلاحيات الوصول (مثل Create Payments, Update Payments, Super Rules)
5. النقر على **Create** ثم نسخ المفتاح

## استخدام المفتاح في الطلبات

```
Authorization: Bearer YOUR_API_TOKEN
```

مثال (cURL):

```bash
curl -X POST "https://apitest.myfatoorah.com/v3/payments" \
  -H "Authorization: Bearer YOUR_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"PaymentMethod":"INVOICE","Order":{"Amount":10}}'
```

## مفتاح الاختبار (Demo Token)

للتجربة بدون معاملات حقيقية:

1. إنشاء حساب تجريبي: [registertest.myfatoorah.com](https://registertest.myfatoorah.com/en/)
2. اختيار الكويت كدولة وتخطي بيانات البنك
3. إرسال بريد إلى [tech@myfatoorah.com](mailto:tech@myfatoorah.com) لتفعيل الحساب

**مفتاح اختبار عام (قد يتغير):**

```
SK_KWT_vVZlnnAqu8jRByOWaRPNId4ShzEDNt256dvnjebuyzo52dXjAfRx2ixW5umjWSUx
```

## ملاحظات مهمة

- يمكن إنشاء **5 مفاتيح كحد أقصى** لكل حساب
- إذا استُخدم مفتاح لـ endpoint بدون الصلاحيات المطلوبة: `401 - The token does not have the required permissions!`
- **حسابات متعددة الدول**: كل دولة لها مفتاح API مختلف (للعملة المحلية)
- تجنب تعطيل المستخدم الذي أنشأ المفتاح؛ ذلك يعطل المفتاح أيضاً
