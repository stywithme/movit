> **Status:** `ARCHIVED` â€” superseded, cancelled, or historical review only.
> **Current SSOT:** `Docs/00-Active-Reference/README.md`
> **Archived:** 2026-05-29

# Mobile UI/UX Design Specifications (Full Product)

## نظرة عامة
هذه الوثيقة تحتوي على المواصفات الكاملة لواجهات تطبيق الموبايل (Android/iOS) للمشروع النهائي، بناءً على الـ C4 Model ومتطلبات جهات الاعتماد.
التطبيق يعتمد على `Bottom Navigation Bar` للتنقل الرئيسي بعد الدخول.

---

## 1. Authentication & Onboarding (البداية)

### 1.1 Splash Screen
- **الهدف:** شاشة ترحيبية تظهر أثناء تحميل التطبيق.
- **العناصر:** الشعار (Logo) في المنتصف، مؤشر تحميل (Spinner) بسيط بالأسفل، رقم الإصدار.

### 1.2 Onboarding (First Time User)
- **الهدف:** شرح ميزات التطبيق وشروط عمل الـ AI بشكل صحيح.
- **النوع:** Slider / Carousel مع زر "Next/Skip".
- **الشاشات الفرعية:**
  1.  **الميزات:** "مدربك الشخصي بالذكاء الاصطناعي.. حلل أداءك وصحح أخطاءك".
  2.  **الإضاءة والبيئة:** رسم توضيحي يظهر أهمية الإضاءة الجيدة والخلفية غير المزدحمة.
  3.  **الملابس:** تنبيه بضرورة ارتداء ملابس رياضية غير فضفاضة جدًا (ليتمكن الـ AI من رؤية المفاصل).
  4.  **تثبيت الموبايل:** توضيح كيفية تثبيت الموبايل على الأرض أو حامل، والبعد المناسب (يظهر كامل الجسم).
- **CTA:** زر "Start Training" في الشاشة الأخيرة.

### 1.3 Login Screen
- **الهدف:** دخول المستخدمين المسجلين.
- **العناصر:**
  - حقول إدخال: `Email`، `Password`.
  - زر `Forgot Password?`.
  - زر أساسي `Sign In`.
  - فاصل "Or continue with".
  - زر `Google Sign In` (Social Auth).
  - رابط `Don't have an account? Sign Up`.
- **حالات:** Loading state، Error messages (Invalid credentials).

### 1.4 Register Screen
- **الهدف:** إنشاء حساب جديد.
- **العناصر:**
  - حقول: `Full Name`، `Email`، `Password`، `Confirm Password`.
  - زر أساسي `Create Account`.
  - زر `Google Sign In`.
  - رابط `Already have an account? Sign In`.

---

## 2. Main Navigation (التصفح الرئيسي)
يعتمد التطبيق على شريط تنقل سفلي (Bottom Bar) يحتوي على:
1.  **Home:** لوحة القيادة والملخص.
2.  **Exercises:** مكتبة التمارين والبرامج.
3.  **History:** سجل التقارير.
4.  **Profile:** الإعدادات والاشتراكات.

---

## 3. Home & Exercises (التدريب)

### 3.1 Home / Dashboard
- **الهدف:** نظرة سريعة على نشاط المستخدم.
- **العناصر:**
  - ترحيب: "Welcome back, [Name]".
  - **Weekly Stats:** بطاقة تعرض عدد التمارين هذا الأسبوع، الدقائق، متوسط الدقة.
  - **Quick Start:** زر لاستكمال آخر برنامج تدريبي أو تمرين مقترح.
  - **Recent Activity:** قائمة مختصرة لآخر 3 تمارين تم أداؤها.

### 3.2 Exercise Library
- **الهدف:** استعراض واختيار التمارين (نفس مواصفات الـ MVP السابقة مع تحسينات).
- **العناصر:**
  - Header: عنوان `Exercise Library`.
  - Search Bar: للبحث عن تمرين بالاسم.
  - Filter Chips: (All, Legs, Arms, Core, Cardio).
  - **Workout Programs Card:** بطاقة عريضة مميزة تقود لصفحة `Workout List`.
  - **Exercise Grid:** قائمة التمارين (Cards) تحتوي على الأيقونة والاسم والعضلة المستهدفة.

### 3.3 Workout List (Programs)
- **الهدف:** اختيار برنامج كامل (Circuit/Set).
- **العناصر:** قائمة بطاقات `Workout Card` تعرض: الاسم، النوع (AMRAP/EMOM)، المدة المتوقعة، الصعوبة.

### 3.4 Exercise Detail (Preparation)
- **الهدف:** تجهيز المستخدم للتمرين المختار.
- **العناصر:**
  - Header: صورة توضيحية أو فيديو قصير (Loop) لكيفية الأداء الصحيح.
  - Info Card: الاسم، العضلات، المعدات.
  - **Instructions:** خطوات الأداء كنقاط.
  - **Target:** "Target: 12 Reps" (أو Time للـ Hold) - *ملاحظة: الصعوبة موحدة Unified evaluation*.
  - **Camera Setup:** اختيار `Variant` (مثلاً Side View / Front View) إذا وجد.
  - **Start Options:** زرين عريضين `📷 Start Camera` و `🎬 Analyze Video`.

---

## 4. Active Training Session (التنفيذ)

### 4.1 Training (Camera Mode)
- **الهدف:** الشاشة الرئيسية أثناء التدريب.
- **الطبقات (Layers):**
  1.  **الكاميرا:** Live Preview ملء الشاشة.
  2.  **Skeleton:** رسم الهيكل العظمي (أخضر للصحيح، أحمر للخطأ).
  3.  **Vignette:** توهج أحمر/أصفر على الحواف للتنبيه دون تشتيت الانتباه.
  4.  **UI Overlay (Glassmorphic):**
      - **Top:** زر إنهاء، اسم التمرين، الحالة (Going Down / Up).
      - **Center:** عداد عملاق (Hero Counter) للعدات الصحيحة.
      - **Bottom:** رسائل التوجيه (Feedback Pills) مثل "Lower your hips".
- **Setup Phase:** قبل البدء، تظهر تعليمات "قف في هذا المكان" مع مؤشر لضبط المسافة.

### 4.2 Training (Video Mode)
- **الهدف:** تحليل فيديو مسجل مسبقًا.
- **العناصر:** مشغل فيديو (TextureView) بدلاً من الكاميرا، شريط تحكم (Seekbar)، زر `Save Results`.

### 4.3 Workout Session Orchestrator
- **الهدف:** إدارة التنقل بين التمارين في البرامج (Workouts).
- **العناصر:**
  - شاشات بينية (Interstitials): `Rest Time` (عداد تنازلي)، `Get Ready for [Next Exercise]`.
  - شريط تقدم: يظهر التقدم في البرنامج (مثلاً التمرين 3 من 5).

---

## 5. Post-Training & History (النتائج)

### 5.1 Post-Training Report (Immediate)
- **الهدف:** التقرير الفوري بعد انتهاء الجلسة.
- **Header:** تقييم عام (Excellent/Good/Needs Work) مع لون خلفية متدرج.
- **Stats Card:** العداد الكلي، الدقة %، المدة.
- **Tabs:**
  1.  **Best:** عرض أفضل تكرار (Best Rep) مع صورة (Screenshot) وتوضيح لماذا هو الأفضل.
  2.  **Errors:** قسم `🚨 Danger Alerts` (للأخطاء الخطيرة) وقسم `Improvements` للأخطاء الشائعة.
  3.  **States:** رسم بياني يوضح توزيع الأداء (كم % كان ممتاز، كم % كان سيء).
  4.  **Tips:** نصائح نصية للتحسين في المرة القادمة.
- **Actions:** زر `Save & Finish`، زر `Train Again`.

### 5.2 History (Sijil)
- **الهدف:** سجل تاريخي لكل التمارين السابقة.
- **العناصر:**
  - قائمة (List View) مرتبة بالتاريخ (الأحدث أولاً).
  - **History Item:** اسم التمرين/البرنامج، التاريخ والوقت، سكور الدقة (Badge ملون)، المدة.
  - النقر على أي عنصر يفتح تفاصيل التقرير القديم (نفس شاشة Report ولكن بوضع القراءة فقط).

---

## 6. Profile & Settings (المستخدم)

### 6.1 Profile Overview
- **الهدف:** إدارة الحساب والاشتراكات.
- **العناصر:**
  - **Header:** صورة المستخدم (Avatar)، الاسم، البريد الإلكتروني.
  - **Membership Card:** بطاقة تعرض الخطة الحالية (Free/Pro)، تاريخ التجديد، زر `Manage Subscription`.
  - **Stats Summary:** إجمالي التمارين، إجمالي الدقائق.

### 6.2 Plans & Subscriptions (Billing)
- **الهدف:** عرض خطط الأسعار والترقية.
- **العناصر:**
  - قائمة بالخطط (Monthly / Yearly).
  - مقارنة الميزات (Features list).
  - زر `Subscribe / Upgrade` (مربوط بـ Google Play Billing / Stripe).

### 6.3 Settings
- **الهدف:** إعدادات التطبيق.
- **العناصر:**
  - **General:** اللغة (العربية/الإنجليزية)، الثيم (Dark/Light).
  - **Notifications:** تفعيل/إيقاف التنبيهات.
  - **Voice Feedback:** تفعيل صوت المدرب الآلي، مستوى الصوت.
  - **Account:** تغيير كلمة المرور، تسجيل الخروج، حذف الحساب.

---

## ملاحظات التصميم العامة
- **اللغة:** الواجهات تدعم العربية والإنجليزية (RTL/LTR Support).
- **التصميم:** Dark Mode هو الافتراضي (لإبراز الفيديو والـ Skeleton).
- **الأسلوب:** Modern, Clean, Glassmorphic elements for overlays.
