> **Status:** `BUSINESS` — تخطيط منتج/سوق؛ ليس مواصفة تقنية.

# Business Planning Documents

> المسار: `Docs/01-Business-Planning/` (سابقاً `Docs/New-Project/Planning/`)

هذا الفولدر يحتوي وثائق التخطيط التجاري والمنتجي للمشروع (الاسم التجاري: **Movit**).

الهدف من هذه الوثائق: تحويل المنتج من **محرك تمرين ناضج ورحلة منتج MVP قيد الإنجاز** إلى منتج قابل للفهم، الاختبار، البيع، والتوسع.

## واقع المنتج (Product Reality)

وثائق هذا المجلد تصف **القرارات والفرضيات التجارية** — لماذا نبني ماذا، وليس كيف يعمل الكود.

للحقيقة التقنية (ما يعمل اليوم مقابل ما هو مخطَّط):

- [`Journey-Index`](../00-Active-Reference/Product-Master/Journey-Index.md) — جدول as-built vs planned
- [`trainee-journey-current-state`](../00-Active-Reference/Architecture-As-Built/trainee-journey-current-state/) — تفاصيل الرحلة في الكود

المواصفات التنفيذية (أحداث، Aha، خريطة البقاء) في [`02-Roadmaps-And-Plans/Product/`](../02-Roadmaps-And-Plans/Product/).

## الحالة الحالية

محرك التمرين ناضج وتم اختباره وأداؤه جيد. **رحلة المنتج MVP ما زالت قيد الإنجاز** — المرحلة الحالية: تحسين UI/UX، إضافة المحتوى، وبناء محرك البقاء، ثم تجربة مبدئية لاختبار العودة والتطور.

## القرارات المحسومة

- المنتج: مدرب ذكي للمبتدئ في البيت (برنامج 28 يوم). المحرك مبني ومُختبَر، لا نبني من الصفر.
- الاسم التجاري: **Movit** (مُختار ومعتمد). Deep links: `movit://`.
- السوق: مصر كسوق تجريبي لإنضاج المنتج، ثم التوسع للوطن العربي.
- البوابة الحالية: Retention (هل يرجع؟ هل يتطور؟). البوابة التجارية (الدفع) مؤجَّلة عمداً — انظر [`06-Pricing-Strategy.md`](06-Pricing-Strategy.md).
- الخصوصية: الفيديو لا يخرج من جهاز المستخدم إطلاقاً.
- العينة الأولى: ~20 من المعارف (عينة دافئة)، ثم موجة باردة لاحقاً.
- الموارد: الشركة البرمجية تبني المنتج، bootstrapped، وفريق تسويق شركة التجارة الإلكترونية أصل متاح للمحتوى والـ GTM لاحقاً. Product Owner متفرّغ قيد البحث.

## ترتيب القراءة المقترح

1. `00-Core-Principles.md`
2. `01-Product-Thesis.md`
3. `02-ICP-and-Personas.md`
4. `03-Value-Proposition.md`
5. `04-MVP-Scope.md`
6. `05-Validation-Plan.md`
7. `06-Pricing-Strategy.md`
8. `07-Go-To-Market.md`
9. `08-Success-Metrics.md`
10. `09-Risk-Register.md`
11. `10-Partner-Strategy.md`
12. `11-Market-Research-Plan.md`
13. `12-Open-Questions.md`
14. `13-Retention-Engine.md`
15. `14-Beta-Measurement.md`
16. `15-Resources-and-Team.md`
17. `16-Correct-Session-Definition.md`
18. [`17-Events-Implementation-Ticket.md`](../02-Roadmaps-And-Plans/Product/17-Events-Implementation-Ticket.md) *(مواصفة تنفيذية)*
19. [`18-Retention-Engine-Code-Map.md`](../02-Roadmaps-And-Plans/Product/18-Retention-Engine-Code-Map.md) *(مواصفة تنفيذية)*
20. [`19-Onboarding-and-Aha-Spec.md`](../02-Roadmaps-And-Plans/Product/19-Onboarding-and-Aha-Spec.md) *(مواصفة تنفيذية)*
21. `20-Beta-Protocol-and-Scripts.md`
22. `21-Content-Readiness.md`

## قاعدة العمل

أي قرار جديد في المنتج يجب أن يجاوب على سؤالين:

- هل يخدم العميل الأول؟
- هل يثبت فرضية تجارية مهمة الآن؟

إذا كانت الإجابة لا، يتم تأجيله.
