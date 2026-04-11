# خطة تجربة المستخدم الشاملة — Programs Experience

---

## الفلسفة التصميمية

### من هو المستخدم؟
**أحمد، 25 سنة** — لم يتمرن في حياته بشكل منتظم. لا يعرف الفرق بين Set و Rep. لا يعرف لماذا يحتاج راحة بين التمارين. يشعر بالخجل في الجيم لأن الجميع يبدون وكأنهم يعرفون ما يفعلون. حمّل التطبيق لأن صديقه أخبره أنه "زي ما يكون عندك مدرب شخصي في جيبك".

**ما الذي يحتاجه أحمد:**
- "أعمل إيه دلوقتي؟" — إجابة واحدة واضحة
- "أنا بعمله صح؟" — تأكيد مستمر
- "عملت كويس؟" — تقدير بعد كل إنجاز
- "أنا بتحسن؟" — دليل ملموس على التقدم
- "ليه أكمّل؟" — هدف ومعنى لكل خطوة

### المبادئ الأساسية
1. **شاشة واحدة = فعل واحد** — لا نعرض كل شيء مرة واحدة. المستخدم لا يحتاج يقرر، يحتاج يبدأ
2. **التطبيق يقود، المستخدم يتبع** — التطبيق لا ينتظر المستخدم يكتشف الخطوة التالية، بل يقدمها له مباشرة
3. **التقدم مرئي ومحسوس** — ليس مجرد أرقام، بل شعور بالإنجاز
4. **اللغة بسيطة وتشجيعية** — لا مصطلحات رياضية معقدة بدون شرح. نتكلم معاه كصديق يشجعه
5. **المعلومات تتكشف تدريجيًا** — نعرض القليل أولاً، ونكشف المزيد كلما تقدم المستخدم (Progressive Disclosure)
6. **التعامل مع الفشل بلطف** — لو ما قدر يكمل، لو غاب أيام، لو دقته ضعيفة — التطبيق يتفهم ويدفعه للأمام

---

## 1. شاشة Programs (الشاشة الرئيسية للبرنامج)

### المفهوم
هذه **ليست قائمة برامج** — هذه هي **الشاشة الأولى التي يراها المستخدم كل يوم**.
الفكرة: المستخدم يفتح التطبيق، يرى ماذا عليه اليوم، ويضغط زر واحد ليبدأ. بدون تفكير، بدون تنقل، بدون قرارات.

### التقسيم (من الأعلى للأسفل)

---

### A. Coach Card — "مرحبًا، هذا يومك"

> **التحسين**: بدلاً من "Hero Section" مليئة بالبيانات، نبدأ بـ **بطاقة المدرب** — رسالة شخصية + فعل واحد واضح.

بطاقة كبيرة بزوايا مستديرة (تشبه بطاقة الدفع) بخلفية gradient لطيفة:

```
┌──────────────────────────────────────┐
│                                      │
│  💪 Good morning, Ahmad!             │
│                                      │
│  Today: Upper Body                   │
│  5 exercises · ~20 min               │
│                                      │
│  ▓▓▓▓▓▓░░░░░░░░  Day 3 of 7        │
│                                      │
│  ┌────────────────────────────────┐  │
│  │     ▶  Start Today's Session  │  │
│  └────────────────────────────────┘  │
│                                      │
└──────────────────────────────────────┘
```

**ما يظهر فقط:**
- تحية شخصية (حسب الوقت: صباح / مساء / ليل)
- اسم تركيز اليوم (بدون اسم البرنامج — المبتدئ لا يهتم باسم البرنامج، يهتم بـ "أعمل إيه")
- عدد التمارين + الوقت التقديري (هذا يُطمئن المبتدئ: "20 دقيقة بس، أقدر")
- شريط تقدم خفيف (Day X of Y) — يعطي إحساس بالسياق بدون أرقام كثيرة
- **زر CTA واحد بارز: ▶ Start** — هذا هو الشيء الوحيد المطلوب من المستخدم

**ما لا يظهر هنا:**
- اسم البرنامج (ليس مهم للمبتدئ يوميًا)
- نسبة الدقة أو إحصائيات سابقة (قد تُحبط المبتدئ)
- عدد الـ Sets (مصطلح قد لا يفهمه)

**لماذا؟** المبتدئ لا يحتاج أن يعرف أن عنده "14 sets و 42 reps". يحتاج أن يعرف أن عنده "5 تمارين في 20 دقيقة". هذه لغة يفهمها.

---

### B. Week Strip — شريط تقدم الأسبوع

> **التحسين**: تبسيط الحالات المرئية وإضافة إحساس "الرحلة".

7 دوائر أفقية، كل دائرة تمثل يوم:

```
  Mon   Tue   Wed   Thu   Fri   Sat   Sun
  ✅    ✅    🔵    ○     ○     ○    ☁️
                ↑ أنت هنا
```

**الحالات (مبسطة لتقليل الحمل المعرفي):**

| الحالة | الشكل | الشرح |
|--------|-------|-------|
| مكتمل بالكامل | ✅ دائرة خضراء مع ✓ | أنجزت كل تمارين اليوم |
| اليوم الحالي (لم يبدأ) | 🔵 دائرة بإطار primary نابض | "أنت هنا — ابدأ!" |
| اليوم الحالي (بدأ جزئيًا) | 🔵 مع arc يمثل نسبة الإنجاز | "كملت نص الطريق!" |
| يوم قادم | ○ دائرة رمادية فاتحة | "هذا اليوم قادم" |
| يوم راحة | ☁️ أيقونة سحابة أو قمر | "استرح — هذا جزء من الخطة" |

**تبسيط مهم**: أزلنا حالة "مكتمل جزئيًا" لأيام سابقة. لأن عرض يوم سابق بنصف إنجاز يُحبط المبتدئ. الأيام السابقة إما ✅ (أنجزها) أو تُعامل كما لو كانت يوم راحة (بدون إشارة سلبية).

تحت الشريط:
```
Week 2 of 4 · You're doing great!
```
> ملاحظة: بدل "3/7 days done" — رسالة تشجيعية ديناميكية حسب الأداء.

**الضغط على أي دائرة** → يفتح Day Detail Bottom Sheet (ليس شاشة جديدة)

---

### C. Today's Plan — قائمة تمارين اليوم

> **التحسين**: تحويل القائمة من "بيانات" إلى "رحلة مرئية" يمكن للمبتدئ متابعتها.

**عنوان:** `Today's Plan` بخط واضح

القائمة تظهر كـ **خط زمني عمودي** (Timeline) — خط يربط التمارين ببعضها يعطي إحساس بالرحلة:

```
┌──────────────────────────────────────┐
│                                      │
│  Today's Plan                        │
│                                      │
│  ● ── Push-ups                       │
│  │    5 rounds · ~3 min              │
│  │    ✅ Done — Great form!          │
│  │                                   │
│  │ ── Rest 60s ──                    │
│  │                                   │
│  ◉ ── Squats              ← Next    │
│  │    5 rounds · ~4 min              │
│  │    Tap "Start" above to begin     │
│  │                                   │
│  │ ── Rest 60s ──                    │
│  │                                   │
│  ○ ── Plank                          │
│  │    2 rounds · 30s each            │
│  │                                   │
│  ○ ── Lunges                         │
│  │    4 rounds · ~3 min              │
│  │                                   │
│  ○ ── Cool Down Stretch              │
│       1 round · 20s                  │
│                                      │
└──────────────────────────────────────┘
```

**قرارات تصميمية مهمة:**

1. **"rounds" بدل "sets"**: كلمة "rounds" أبسط للمبتدئ من "sets × reps". "5 rounds" تعني "هتعمله 5 مرات". (يمكن حتى نستخدم "5 times" أو "5 مرات")

2. **الوقت التقديري**: بدل "3 sets × 10 reps" نقول "~3 min" — المبتدئ يفهم الوقت أكثر من مفاهيم مجردة

3. **التمرين المكتمل**: يظهر بجانبه رسالة إيجابية مختصرة ("Great form!" / "Strong start!") — وليس نسبة دقة

4. **التمرين التالي**: يظهر بارز مع إشارة ← Next

5. **التمارين القادمة**: تظهر باهتة (reduced opacity) — لا نريد المستخدم يقلق بشأن ما هو قادم

6. **فترات الراحة**: تظهر في الـ Timeline كعناصر صغيرة مدمجة — ليست بطاقات كبيرة (المبتدئ لا يحتاج يعرف مدة الراحة مسبقًا)

**الضغط على تمرين مكتمل**: يفتح ملخص التمرين (Exercise Summary Bottom Sheet) — بسيط وإيجابي

**الضغط على التمرين التالي**: نفس فعل زر CTA الرئيسي في الأعلى

---

### D. Motivation Strip — شريط التحفيز

> **التحسين**: بدل "Quick Stats Bar" المليء بالأرقام، نعرض **رسالة واحدة ديناميكية** + مؤشر streak.

```
┌──────────────────────────────────────┐
│  🔥 3 days in a row!                 │
│  "Consistency beats perfection"       │
└──────────────────────────────────────┘
```

**القواعد:**
- لو عنده Streak (أيام متتالية): يظهر عدد الأيام + رسالة تحفيزية عن الاستمرارية
- لو أول يوم: "Day 1 — Every journey starts with a single step"
- لو رجع بعد غياب: "Welcome back! Your body remembers more than you think"
- لو يوم راحة: "Recovery is part of getting stronger"

**لماذا حذفنا الأرقام (sets/reps/avg)؟**
المبتدئ لا يعرف هل "12/18 sets" رقم جيد أم سيء. لا يعرف هل "87% accuracy" تعني أنه يتمرن بشكل صح أم لا. هذه الأرقام ستكون متاحة في التقارير لمن يريد، لكن الشاشة الرئيسية يجب أن تكون **تحفيزية، ليست إحصائية**.

---

### E. Program Card (مطوية — للفضوليين فقط)

> بطاقة قابلة للتوسيع في أسفل الشاشة — لمن يريد رؤية الصورة الكبيرة.

```
┌──────────────────────────────────────┐
│  📋 Starter 4-Week Program     ▼    │
│     Week 2 of 4 · 35% complete      │
└──────────────────────────────────────┘
```

عند التوسيع:
- Progress Ring: 35% مع animation
- إحصائيات البرنامج (الأسابيع، الأيام، الصعوبة)
- زر View Full Program

**لماذا مطوية؟** معظم المبتدئين لا يحتاجون رؤية البرنامج كاملاً يوميًا. يحتاجون فقط أن يعرفوا ماذا عليهم اليوم. البطاقة المطوية تطمئنهم أن هناك خطة، بدون أن تشتتهم.

---

## 2. الحالات الخاصة

### حالة: لا يوجد برنامج نشط

> **التحسين**: بدل شاشة فارغة بزر "Browse Programs"، نصنع تجربة **onboarding لطيفة**.

```
┌──────────────────────────────────────┐
│                                      │
│       [illustration: person           │
│        starting a journey]           │
│                                      │
│   Ready to start training?           │
│                                      │
│   We've built a program just         │
│   for beginners like you.            │
│   20 minutes a day is all            │
│   you need.                          │
│                                      │
│  ┌────────────────────────────────┐  │
│  │   ▶  Start My First Program   │  │
│  └────────────────────────────────┘  │
│                                      │
│      Browse other programs →         │
│                                      │
└──────────────────────────────────────┘
```

**التحسينات:**
- **رسالة طمأنة**: "20 minutes a day" — يزيل الخوف من الالتزام الكبير
- **زر أساسي واحد**: "Start My First Program" — لا يحتاج يختار، نختار له البرنامج المناسب (isDefault)
- **رابط ثانوي**: "Browse other programs" لمن يريد الاختيار بنفسه
- **بدون مصطلحات**: لا "Enroll" ولا "Browse Programs" — لغة بسيطة مباشرة

### حالة: يوم راحة

```
┌──────────────────────────────────────┐
│                                      │
│   ☁️ Rest Day                        │
│                                      │
│   Your muscles are growing           │
│   while you rest. Seriously!         │
│                                      │
│   You've trained 3 days this week.   │
│   That's amazing for week 2!        │
│                                      │
│   Tomorrow: Lower Body Focus         │
│   5 exercises · ~20 min              │
│                                      │
│   [ 👀 Preview Tomorrow ]           │
│                                      │
└──────────────────────────────────────┘
```

**التحسينات:**
- **تعليم ضمني**: "muscles are growing while you rest" — يشرح لماذا الراحة مهمة بدون أن يبدو كدرس
- **تعزيز الإنجاز**: يعرض كم يوم تدرب هذا الأسبوع
- **تطلع للمستقبل**: يعرض ماذا ينتظره غدًا (يبقي الحماس)
- **Preview بدون إلزام**: يمكنه رؤية تمارين الغد بدون ضغط

### حالة: انتهاء البرنامج

```
┌──────────────────────────────────────┐
│                                      │
│   🏆 You Did It!                    │
│                                      │
│   4 weeks ago, you started           │
│   something amazing. Look how        │
│   far you've come:                   │
│                                      │
│   ┌────────────────────────────┐    │
│   │  📅 16 training days       │    │
│   │  💪 You got stronger       │    │
│   │  📈 Your form improved     │    │
│   └────────────────────────────┘    │
│                                      │
│   [ 📊 See Your Journey ]           │
│   [  🚀 Start Next Challenge  ]     │
│                                      │
└──────────────────────────────────────┘
```

**التحسينات:**
- **سرد قصصي**: "4 weeks ago, you started something amazing" — يجعلها قصة شخصية
- **إحصائيات إنسانية**: "You got stronger" بدل "156 total reps" — المبتدئ يهتم بالشعور أكثر من الأرقام
- **خطوة واضحة**: "Start Next Challenge" بدل "Start New Program" — كلمة "Challenge" أكثر تحفيزًا

### حالة: المستخدم غائب (لم يفتح التطبيق لأيام)

> **إضافة جديدة**: حالة لم تكن في الخطة الأصلية — وهي مهمة جدًا للمبتدئين

```
┌──────────────────────────────────────┐
│                                      │
│   👋 Welcome back!                   │
│                                      │
│   No worries — we adjusted           │
│   your plan. Let's pick up           │
│   where you left off.                │
│                                      │
│   [ ▶ Continue from Day 4 ]         │
│                                      │
│   Or start the week fresh →          │
│                                      │
└──────────────────────────────────────┘
```

**لماذا هذا مهم؟**
أكبر سبب لترك المبتدئين للتطبيقات هو الشعور بالذنب بعد الغياب. "فاتني 3 أيام، خلاص ضيعت البرنامج". يجب أن نوصل رسالة: **"مفيش مشكلة، نكمل من هنا"**. وأن التطبيق ذكي بما يكفي ليعدل الخطة.

---

## 3. Day Detail Bottom Sheet

> يظهر عند الضغط على أي يوم في Week Strip.

```
┌──────────────────────────────────────┐
│  ─── Day 3 — Upper Body ──────      │
│                                      │
│  Morning Session          ✅ Done    │
│  3 exercises · 12 min               │
│  "You nailed it!"                    │
│  [View Summary]                      │
│                                      │
│  ─────────────────────────────       │
│                                      │
│  Evening Stretch        ○ Not done   │
│  2 exercises · 5 min                │
│  [Start Session]                     │
│                                      │
└──────────────────────────────────────┘
```

**التحسين عن الخطة الأصلية:**
- **رسالة إيجابية** بعد الجلسة المكتملة بدل نسبة الدقة (89%)
- **"View Summary"** بدل **"View Report"** — كلمة Report توحي بشيء رسمي ومخيف
- **الوقت والعدد فقط**: لا sets ولا reps في هذا المستوى

---

## 4. Session Detail (ProgramSessionActivity)

### قبل بدء الجلسة

> **التحسين**: إعادة تصميم كواجهة "Ready to Go" — وليست واجهة "إعدادات"

```
┌──────────────────────────────────────┐
│  ← Morning Session                   │
│     Week 2 · Day 3                   │
│                                      │
│  ┌────────────────────────────────┐  │
│  │  5 exercises · ~20 min         │  │
│  │  Your AI coach will guide you  │  │
│  │  through each one.             │  │
│  └────────────────────────────────┘  │
│                                      │
│  ── What you'll do today ──         │
│                                      │
│  1. Push-ups         5 rounds        │
│  2. Rest             60s             │
│  3. Squats           5 rounds        │
│  4. Rest             60s             │
│  5. Plank            2 rounds        │
│                                      │
│  ✏️ Customize →                      │
│                                      │
│  ┌────────────────────────────────┐  │
│  │     ▶  Let's Go!              │  │
│  └────────────────────────────────┘  │
│                                      │
└──────────────────────────────────────┘
```

**الفروقات عن الخطة الأصلية:**
- **"What you'll do today"**: بدل timeline تقني، قائمة بسيطة مرقمة. المبتدئ يفهم القوائم المرقمة
- **"Your AI coach will guide you"**: طمأنة أن التطبيق سيقوده خطوة بخطوة
- **"rounds" بدل "sets × reps"**: لغة أبسط
- **"Customize" كرابط ثانوي**: معظم المبتدئين لن يحتاجوا التعديل. ليس زر رئيسي
- **"Let's Go!"** بدل "Start Session": أكثر حماسة وأقل رسمية

### وضع التعديل (Customize Mode)

> **التحسين مهم**: وضع التعديل في الخطة الأصلية كان مليئًا بالخيارات. المبتدئ لا يحتاج كل هذا.

**مستويان من التعديل:**

**المستوى الأول — التعديل البسيط (افتراضي):**
```
┌──────────────────────────────────────┐
│  ← Customize                         │
│                                      │
│  Push-ups                            │
│       ← [ 5 rounds ] →              │
│       "Feeling strong? Add more!"    │
│                                      │
│  Squats                              │
│       ← [ 5 rounds ] →              │
│                                      │
│  Plank                               │
│       ← [  2 rounds ] →             │
│       ← [ 30s each  ] →             │
│                                      │
│  ─────────────────────               │
│  Advanced options ▼                  │
│  ─────────────────────               │
│                                      │
│  [   Save & Go Back   ]             │
└──────────────────────────────────────┘
```

- **تعديل عدد الـ rounds فقط** (+ و -)
- **رسائل ذكية**: "Feeling strong? Add more!" أو "It's OK to do less — you'll build up!"
- **لا drag & drop، لا حذف، لا إضافة** في المستوى الأول

**المستوى الثاني — Advanced Options (مخفي افتراضيًا):**
عند الضغط على "Advanced options":
- ☰ سحب وإفلات (Drag) لترتيب التمارين
- ⇄ استبدال تمرين بآخر
- + Add Exercise / + Add Rest
- 🗑 حذف تمرين
- تعديل أوقات الراحة

**لماذا مستويان؟**
المبتدئ يحتاج أن يثق بالبرنامج. لو عرضنا له كل خيارات التعديل مباشرة، سيشعر أنه يحتاج يعدل شيء — وهو لا يعرف ماذا يعدل. خيارات التعديل المتقدمة موجودة لمن يتقدم ويريد تخصيص أكثر.

---

## 5. Replace Exercise Bottom Sheet

> **التحسين**: إضافة سياق "لماذا" وتبسيط الاختيار

```
┌──────────────────────────────────────┐
│  Replace: Push-ups              ✕    │
│                                      │
│  ── Easier alternatives ──          │
│  [img] Wall Push-ups                │
│        "Same muscles, less strain"   │
│        ★ Recommended for beginners  │
│                                      │
│  [img] Knee Push-ups                │
│        "Build up to full push-ups"   │
│                                      │
│  ── Similar level ──                │
│  [img] Incline Push-ups             │
│                                      │
│  ── More challenging ──             │
│  [img] Diamond Push-ups             │
│  [img] Decline Push-ups             │
│                                      │
│  🔍 Search all exercises            │
└──────────────────────────────────────┘
```

**التحسينات:**
- **تصنيف حسب الصعوبة**: Easier / Similar / Harder — المبتدئ يعرف أيها يناسبه
- **"Recommended for beginners"**: إشارة واضحة للبديل الأفضل
- **وصف مختصر**: جملة واحدة توضح لماذا هذا البديل مفيد
- **البحث في الأسفل**: ليس في الأعلى — لأن معظم المبتدئين سيختارون من التوصيات

---

## 6. التقارير (3 مستويات — Emotion First, Data Second)

> **التحسين الجوهري**: التقارير في الخطة الأصلية كانت **مبنية على الأرقام**. المبتدئ لا يعرف هل 87% رقم جيد أم سيء. نعيد بناءها لتكون **مبنية على المشاعر أولاً، ثم الأرقام لمن يريد**.

### 6A. Exercise Summary (Bottom Sheet) — عند الضغط على تمرين مكتمل

```
┌──────────────────────────────────────┐
│  Push-ups                       ✕    │
│                                      │
│  ⭐ Great job!                       │
│                                      │
│  You completed all 3 rounds.         │
│  Your form was solid — keep it up!   │
│                                      │
│  ── Details ──                       │
│  Round 1: 10/10 · Excellent form     │
│  Round 2: 10/10 · Good form          │
│  Round 3:  8/10 · Getting tired      │
│            (totally normal!)          │
│                                      │
│  💡 Tip: Focus on keeping your       │
│     back straight in the last round  │
└──────────────────────────────────────┘
```

**التحسينات:**
- **"Great job!"** أولاً — قبل أي بيانات
- **"Your form was solid"** — بدل "92% accuracy". المبتدئ يفهم "solid" أكثر من "92%"
- **"Getting tired (totally normal!)"** — بدل "88% accuracy" يعني أنه تعب (وهذا طبيعي)
- **نصيحة عملية واحدة**: بدل "Most common error: Elbow angle" التقنية — نصيحة بسيطة يقدر ينفذها

### 6B. Session Summary (شاشة كاملة) — بعد انتهاء الجلسة

```
┌──────────────────────────────────────┐
│       🎉 Session Complete!           │
│                                      │
│  "You showed up and gave it your     │
│   all. That's what champions do."    │
│                                      │
│  ┌────────────────────────────────┐  │
│  │  ⏱ 18 min  │  💪 5/5 done    │  │
│  └────────────────────────────────┘  │
│                                      │
│  ── How you did ──                   │
│                                      │
│  Push-ups     ⭐ Excellent           │
│  ▓▓▓▓▓▓▓▓▓░  3/3 rounds             │
│                                      │
│  Squats       ⭐ Good                │
│  ▓▓▓▓▓▓▓▓░░  3/3 rounds             │
│                                      │
│  Plank        ⭐ Solid               │
│  ▓▓▓▓▓▓▓░░░  2/2 rounds             │
│                                      │
│  ── Your next session ──            │
│  Tomorrow: Lower Body · 5 exercises  │
│                                      │
│  [    Back to Program    ]           │
│  [    Share Achievement  ]           │
└──────────────────────────────────────┘
```

**التحسينات:**
- **رسالة تحفيزية شخصية** في الأعلى — تتغير حسب الأداء والسياق
- **مؤشران فقط**: الوقت + عدد التمارين المكتملة (واضحان ومفهومان)
- **تقييم بالكلمات**: "Excellent" / "Good" / "Solid" / "Keep Practicing" — بدل نسب مئوية
- **الخطوة التالية**: يعرض ماذا ينتظره غدًا — يحافظ على الاستمرارية
- **Share**: يمكن مشاركة الإنجاز — تعزيز اجتماعي مهم للمبتدئين

**ملاحظة**: التفاصيل الرقمية (accuracy %, rep details, weight history) يمكن الوصول إليها من شاشة Reports المخصصة — وليس هنا. هنا هدفنا الاحتفاء بالإنجاز.

### 6C. Weekly Summary (شاشة كاملة)

```
┌──────────────────────────────────────┐
│  ← Week 2 Summary                    │
│                                      │
│  ┌─────────────────────────────┐    │
│  │     🏆 Great Week!          │    │
│  │     5 out of 7 days         │    │
│  │     ▓▓▓▓▓▓▓░░░ 71%         │    │
│  └─────────────────────────────┘    │
│                                      │
│  ── Highlights ──                    │
│  🔥 Best day: Day 3                 │
│  📈 You're getting better at form   │
│  💪 Total training: 2h 15m          │
│                                      │
│  ── Your Week ──                     │
│  Mon  ✅  "Strong start"            │
│  Tue  ✅  "Crushed it"              │
│  Wed  ☁️  Rest                       │
│  Thu  ✅  "Your best day!"          │
│  Fri  ✅  "Kept the momentum"       │
│  Sat  ✅  "Finished strong"         │
│  Sun  ☁️  Rest                       │
│                                      │
│  ── vs Last Week ──                  │
│  "You trained one more day           │
│   and your form improved!"           │
│                                      │
└──────────────────────────────────────┘
```

**التحسينات:**
- **"Great Week!"** — تقييم عاطفي أولاً
- **رسالة شخصية لكل يوم**: بدل أرقام جافة
- **المقارنة بالأسبوع الماضي**: بجملة واحدة وليس جدول أرقام
- **"You're getting better at form"**: بدل "Accuracy: 82% → 89% (↑7%)" — نفس المعنى، لغة أبسط

---

## 7. نظام التحفيز (Motivation System) — محسَّن

### 7A. Micro-celebrations (كما هي — ممتازة في الخطة الأصلية)
- عند إكمال Round: ✅ مع اهتزاز خفيف
- عند إكمال Exercise: "Exercise Done!" مع صوت خفيف
- عند إكمال Session: 🎉 مع confetti animation
- عند إكمال Day: شارة اليوم تتحول لأخضر مع animation
- عند إكمال Week: 🏆 بطاقة خاصة

### 7B. Streak Tracking (محسَّن)

**الإضافة**: الـ Streak لا ينكسر فورًا بعد يوم واحد:
- بعد 1 يوم غياب: "Your streak is waiting — don't let it go!"
- بعد 2 يوم غياب: "You can still save your streak! Train today."
- بعد 3+ أيام: الـ Streak ينكسر — ولكن برسالة لطيفة: "Every expert was once a beginner. Let's build a new streak!"

**لماذا؟** كسر الـ Streak فورًا بعد يوم واحد يُحبط المبتدئ. "فاتني يوم واحد وضاع كل شي". نعطيه فرصة ثانية.

### 7C. Encouraging Messages (محسَّن — سياقية)

**بدل رسائل ثابتة، نجعلها ديناميكية حسب سلوك المستخدم:**

| السياق | الرسالة |
|--------|---------|
| أول تمرين في البرنامج | "This is your Day 1. Future you will thank you." |
| أداء ممتاز | "Your form is getting really good. Can you feel it?" |
| أداء ضعيف (لا تُحبط — وجّه) | "Focus on going slow. Quality over speed!" |
| رجع بعد غياب | "Welcome back! Picking up is harder than starting. Respect." |
| أكمل نصف البرنامج | "Halfway! Most people give up by now. Not you." |
| آخر أسبوع | "Final week. You've done what most people only talk about." |
| يوم الراحة | "Rest is when your body gets stronger. Enjoy it." |
| كسر الـ Streak | "Streaks break. Commitment doesn't. Let's go again." |
| أكمل كل الأيام في أسبوع | "Perfect week! You're building a real habit!" |

### 7D. Coach Nudges (إضافة جديدة)

> **إضافة مهمة**: نصائح تعليمية ضمنية تظهر في السياق المناسب

هذه رسائل قصيرة تظهر في أماكن مختلفة لتعلّم المبتدئ مفاهيم اللياقة بدون أن يشعر أنه في درس:

| أين تظهر | مثال |
|-----------|------|
| قبل أول set في اليوم | "Warm-up tip: Start the first round slow" |
| في شاشة الراحة بين التمارين | "Did you know? Rest helps your muscles prepare for the next round" |
| بعد تمرين plank | "Core exercises like Plank help with every other exercise" |
| عند استبدال تمرين بأسهل | "Smart choice! Build your foundation first" |
| عند زيادة الـ rounds | "Challenging yourself — that's the spirit!" |
| في يوم الراحة | "Today your body repairs and grows. That's science!" |

**القاعدة**: لا تتكرر نفس النصيحة أكثر من مرة. لكل نصيحة flag: `shown = true`.

---

## 8. معالجة لحظات الصعوبة (Struggle Handling)

> **إضافة جديدة بالكامل**: الخطة الأصلية لم تتناول ماذا يحدث عندما يعاني المبتدئ

### 8A. أثناء التمرين — أداء ضعيف
لو المستخدم لا يستطيع إكمال الـ reps المطلوبة أو دقته ضعيفة مستمرة:
- **رسالة تشجيعية**: "It's OK to go slow. Quality matters more than speed"
- **اقتراح ذكي**: بعد الجلسة، يقترح التطبيق بديل أسهل: "Push-ups were tough today. Want to try Wall Push-ups next time?"
- **لا نعرض نسبة الدقة المنخفضة بشكل بارز** — نقول "Keep Practicing" بدل "45%"

### 8B. ترك الجلسة قبل الانتهاء
```
┌──────────────────────────────────────┐
│                                      │
│       💪                             │
│  You've done 2 exercises already.    │
│  That's more than doing nothing!     │
│                                      │
│  ┌────────────────────────────────┐  │
│  │   Keep Going — Almost Done!   │  │ ← Primary
│  └────────────────────────────────┘  │
│                                      │
│     Save Progress & Finish Later     │ ← Secondary
│                                      │
│     Skip Today                       │ ← Text link, small
│                                      │
└──────────────────────────────────────┘
```

**التحسينات عن الخطة الأصلية:**
- **"That's more than doing nothing!"** — يعترف بما أنجز بدل ما لم ينجز
- **"Save Progress & Finish Later"**: بدل "Do it later" — يوضح أن التقدم محفوظ
- **ترتيب الأزرار**: الاستمرار أولاً وأكبر، الخروج في الأسفل وأصغر

### 8C. غياب مستمر (3+ أيام)
التطبيق لا يرسل رسائل سلبية. بدل ذلك:
- **إعادة ضبط التوقعات**: "Let's restart the week — no need to catch up on missed days"
- **تقليل الحمل**: يقترح جلسات أقصر أول يومين للعودة
- **لا شعور بالذنب**: لا نعرض "You missed 5 days" — نعرض "Welcome back! Let's start fresh"

---

## 9. خريطة التنقل الكاملة (Navigation Map)

```
Programs Tab
│
├── [Active Program Dashboard]
│   ├── Coach Card → CTA → TrainingActivity (session mode)
│   │
│   ├── Week Strip → Day Detail Bottom Sheet
│   │   └── Session → ProgramSessionActivity
│   │       ├── Customize (basic) → rounds +/-
│   │       ├── Customize (advanced) → Add/Replace/Drag/Delete
│   │       └── Let's Go! → TrainingActivity (session mode)
│   │           └── Session Complete → Session Summary
│   │               ├── Back to Programs Tab
│   │               └── Share Achievement
│   │
│   ├── Today's Plan → (tap completed exercise) → Exercise Summary Bottom Sheet
│   ├── Today's Plan → (tap next exercise) → same as CTA
│   │
│   ├── Motivation Strip → (informational only)
│   │
│   └── Program Card (expand) → View Full Program → Program Detail
│       ├── Week → Day Detail
│       │   └── Session → ProgramSessionActivity
│       └── Weekly Summary
│
├── [No Active Program]
│   ├── Start My First Program → auto-enroll in default → Dashboard
│   └── Browse Other Programs → Program List
│       └── Program → Enroll → Dashboard
│
├── [Program Complete]
│   ├── See Your Journey → Final Report
│   └── Start Next Challenge → Program List
│
└── [User Returned After Absence]
    ├── Continue from Day X → Dashboard (adjusted)
    └── Start Week Fresh → Dashboard (reset week)
```

---

## 10. ملخص الشاشات والمكونات المطلوبة

| الشاشة/المكون | الوضع الحالي | المطلوب |
|---|---|---|
| ProgramsFragment | قائمة برامج بسيطة | Dashboard: Coach Card + Week Strip + Timeline + Motivation |
| Coach Card | غير موجود | بطاقة تحية + CTA + تقدم اليوم |
| Week Strip | غير موجود | 7 دوائر مع حالات مبسطة |
| Today's Plan Timeline | غير موجود | خط زمني بالتمارين مع حالات |
| Motivation Strip | غير موجود | Streak + رسالة تحفيزية ديناميكية |
| Day Detail | شاشة كاملة منفصلة | Bottom Sheet سريع |
| ProgramSessionActivity | Edit mode أساسي | مستويان: Basic (rounds +/-) + Advanced (full edit) |
| Exercise Summary | غير موجود | Bottom Sheet بسيط وإيجابي |
| Session Summary | أساسي | محسَّن: عاطفي أولاً + تقييم بالكلمات |
| Weekly Summary | أساسي | محسَّن: سردي + مقارنة بجملة |
| Replace Exercise Sheet | غير موجود | مصنف بالصعوبة + توصية للمبتدئين |
| No Active Program | شاشة فارغة | Onboarding لطيف مع زر واحد |
| Return After Absence | غير موجود | شاشة ترحيب + خيار الاستمرار |
| Struggle Handling | غير موجود | اقتراحات ذكية + رسائل لطيفة |
| Coach Nudges | غير موجود | نصائح سياقية تعليمية |
| Motivation System | غير موجود | Streak مرن + Celebrations + رسائل ديناميكية |

---

## 11. الأولويات المقترحة للتنفيذ

### المرحلة 1 — الأساس (Core Dashboard)
> **الهدف**: المستخدم يفتح التطبيق ← يرى ماذا عليه ← يبدأ بضغطة واحدة

1. إعادة بناء ProgramsFragment كـ Dashboard
2. Coach Card مع CTA
3. Week Strip مع حالات الأيام المبسطة
4. Today's Plan Timeline مع حالات التمارين
5. حالة "No Active Program" مع onboarding

### المرحلة 2 — التعديل والتخصيص
> **الهدف**: المستخدم يستطيع تعديل برنامجه بثقة

6. Customize Mode (المستوى الأول — Basic)
7. Customize Mode (المستوى الثاني — Advanced)
8. Replace Exercise Bottom Sheet مع تصنيف الصعوبة
9. Day Detail Bottom Sheet

### المرحلة 3 — التقارير العاطفية
> **الهدف**: بعد كل جلسة، يشعر المستخدم بالإنجاز

10. Exercise Summary Bottom Sheet (عاطفي)
11. Session Summary (محسَّن — Emotion First)
12. Weekly Summary مع المقارنة السردية

### المرحلة 4 — التحفيز ومعالجة الصعوبات
> **الهدف**: المستخدم لا يستسلم أبدًا

13. Motivation Strip (Streak + Dynamic Messages)
14. Micro-celebrations (اهتزاز، صوت، confetti)
15. Coach Nudges (نصائح سياقية)
16. Struggle Handling (اقتراحات ذكية عند الأداء الضعيف)
17. Return After Absence Flow

### المرحلة 5 — اللمسات النهائية
> **الهدف**: التطبيق يشعر كأنه مدرب شخصي حقيقي

18. حالة إكمال البرنامج + Final Journey
19. Share Achievement
20. Program Card المطوية
