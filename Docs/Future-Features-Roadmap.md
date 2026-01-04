# 🔮 Future Features Roadmap (Post-MVP)

هذا المستند يحتوي على الأفكار والتحسينات المتقدمة التي تخص منطق التطبيق الكامل (Full App Logic) وتجربة التمرين المتكاملة، والتي سيتم تنفيذها بعد استقرار مرحلة الـ PoC والـ MVP.

---

## 1. منطق التمرين (Workout Logic) 🏋️‍♂️

### 🔄 Phase-Based Rep Counting (State Machine)
بدلاً من عد التكرارات التقليدي المعتمد على الزوايا فقط، نستخدم نظام "مراحل الحركة" (Phases).
*   **المبدأ:** تقسيم التمرين إلى مراحل (Start -> Eccentric -> Bottom -> Concentric -> Top).
*   **التطبيق:** لا يتم احتساب العدة إلا إذا مر المستخدم بجميع المراحل بالترتيب الصحيح. هذا يمنع "الغش" في التمرين (Cheating) أو العدات غير الكاملة.

### 👤 Head Visibility & Posture Check
قبل السماح ببدء التمرين، يجب التأكد من الوضعية الصحيحة للرأس والرقبة.
*   **التطبيق:** التأكد من ظهور `NOSE` و `EARS` بوضوح، وأن الرقبة ليست منحنية بشكل خطر (Text Neck) أثناء تمارين الوقوف.

### 🎯 Optimal View per Exercise (أفضل زاوية لكل تمرين)
*   **المبدأ:** تحديد زاوية الكاميرا المثلى لكل تمرين.
*   **التطبيق:** عرض توجيه للمستخدم قبل التمرين (مثل: "ضع الكاميرا على يسارك لتمرين الـ Squat").
*   **المرجع:** [AI-Powered Exercise Form Validator PDF] - *"for squats or push-ups, usually a side profile is recommended"*.

### 🎚️ Strictness Levels (مستويات الصرامة)
*   **المبدأ:** إضافة وضع "صارم" (Strict) و"عادي" (Casual) لتقييم الأداء.
*   **التطبيق:** المبتدئون يستخدمون الوضع العادي (سماحية أكبر في الزوايا)، والمحترفون يستخدمون الوضع الصارم.
*   **المرجع:** [AI-Powered Exercise Form Validator PDF] - *"The app could even offer a 'strict mode' vs 'casual mode' for feedback"*.

---

## 2. التحليلات المتقدمة (Advanced Analytics) 📊

### 🤖 LLM-Based Form Analysis
بعد انتهاء التمرين، إرسال بيانات الـ Landmarks (كسلسلة زمنية) إلى نموذج لغوي (LLM) لتحليل "نمط" الخطأ.
*   **مثال:** "لقد لاحظت أن ركبتك اليمنى تميل للداخل في العدات الأخيرة عندما تشعر بالتعب."

### 📉 Fatigue Detection
اكتشاف التعب العضلي من خلال تحليل:
*   بطء سرعة الحركة (Velocity Drop).
*   اهتزاز المفاصل (Tremor).
*   تدهور ثبات الجذع (Core Stability).

### 📊 Form Score per Rep (تقييم رقمي لكل عدة)
*   **المبدأ:** بدلاً من "صحيح/خطأ"، نعطي كل عدة درجة من 0-100.
*   **التطبيق:** حساب متوسط الانحراف عن الزوايا المثالية وتحويله لدرجة.

---

## 3. تجربة المستخدم المتقدمة (Advanced UX) 🎮

### 🗣️ Voice Feedback & Coaching
تحويل رسائل الخطأ إلى توجيهات صوتية فورية.
*   **مثال:** بدلاً من رسالة نصية، يقول التطبيق: "ارفع صدرك قليلاً" أو "افرد ركبتك".

### 📹 AR Guides (Ghost Overlay)
عرض "شبح" (Ghost) لمدرب افتراضي يقوم بالتمرين بشكل صحيح فوق صورة المستخدم، ليحاول المستخدم مطابقة حركته معه.

### 🎬 Annotated Video Playback (تسجيل وعرض الفيديو)
*   **المبدأ:** تسجيل التمرين وإعادة عرضه مع رسم الزوايا والأخطاء فوق الفيديو.
*   **الفائدة:** يسمح للمستخدم بمراجعة أدائه بعد التمرين.
*   **المرجع:** [AI-Powered Exercise Form Validator PDF] - *"overlaying the user's video with angles or colored lines for correct/incorrect alignment"*.

---

## 4. التكامل (Integration) 🔗

### ⌚ Wearable Integration (Heart Rate)
ربط التطبيق بالساعات الذكية لقراءة معدل ضربات القلب ودمجه مع بيانات الحركة لتقدير حرق السعرات بدقة أكبر (Active Calories).

### ☁️ Cloud Sync & History
مزامنة بيانات التمارين مع السحابة لحفظ التاريخ وتتبع التقدم على مدار الأسابيع والشهور.

---

## 5. الذكاء الاصطناعي المتقدم (Advanced AI) 🧠

### 🎓 ML-Based Error Classification
بدلاً من القواعد الثابتة (Rule-Based)، تدريب نموذج ML للتعرف على أنماط الأخطاء.
*   **المبدأ:** تغذية الموديل ببيانات Landmarks + تصنيف الخطأ (من مدربين خبراء).
*   **الفائدة:** اكتشاف أخطاء دقيقة يصعب برمجتها بقواعد (مثل عدم استقرار الجذع).
*   **المرجع:** [AI-Powered Exercise Form Validator PDF] - *"train a machine learning model to classify or score the exercise form... can capture subtle, multi-joint patterns"*.

### 🔄 Hybrid Analysis (Rule-Based + ML)
*   **المبدأ:** استخدام القواعد الثابتة للأخطاء الواضحة، واستخدام ML للحالات الحدية.
*   **المرجع:** [AI-Powered Exercise Form Validator PDF] - *"apply basic angle filters to detect obvious issues and only use ML for borderline cases"*.
