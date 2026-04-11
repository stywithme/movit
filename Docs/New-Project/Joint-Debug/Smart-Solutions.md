رابعاً: الحلول الإبداعية — إعادة ترتيب وتقييم
سأرتب الحلول من الأكثر إبداعاً والأقل تكلفة إلى الأكثر جذرية:

الحل 1: Multi-View Consistency Loss (الأذكى والأسهل)
الفكرة: لا نغير الـ features ولا الـ architecture. نغير طريقة حساب الخسارة فقط.

Loss = MAE(prediction, true_angle) + λ × Variance(predictions across cameras for same pose)
لماذا ذكي:

بيانات FIT3D عندنا فيها 4 كاميرات لنفس اللحظة
الـ frame_ids و cameras موجودين أصلاً في الـ .npz
هذا يُجبر الموديل على إيجاد تركيبة features تعطي نتيجة ثابتة مهما تغيرت الكاميرا
في الاستدلال: كاميرا واحدة فقط، بدون أي تغيير
مدعوم بورقة: "Two Views Are Better Than One" — نفس الفكرة بالضبط.

التكلفة: تعديل train_fit3d_elbow_mlp.py فقط — إضافة custom loss. التأثير المتوقع: تقليل multi-view spread من 21.8° إلى ربما 8-12°. تقييم: ★★★★★ — يجب تنفيذه أولاً.

الحل 2: Residual Prediction + Confidence Gating
الفكرة: بدل ما الموديل يتنبأ بالزاوية مباشرة، يتنبأ بـ:

Δ = true_angle − ang3d_xyz (التصحيح فوق تقدير MediaPipe ثلاثي الأبعاد)
σ (مدى ثقة الموديل)
final_angle = ang3d_xyz + MLP_delta
if σ > threshold: use heuristic instead
لماذا ذكي:

الـ residual أصغر وأسهل في التعلم من الزاوية الكاملة
ang3d_xyz يحمل المعلومات الأساسية — الموديل يتعلم "التصحيح" فقط
الـ confidence يسمح بالتراجع للـ heuristic عندما المدخلات غير موثوقة
التكلفة: تعديل بسيط في الـ training script + تعديل Android inference. تقييم: ★★★★☆

الحل 3: قانون جيب التمام (Creative Geometric Approach)
فكرة مبتكرة تماماً:

زاوية الكوع تُحدد رياضياً بثلاث نقاط. بقانون جيب التمام:

cos(θ) = (a² + b² - c²) / (2ab)
حيث:

a = طول العضد (shoulder→elbow) — ثابت تقريباً لنفس الشخص
b = طول الساعد (elbow→wrist) — ثابت تقريباً
c = المسافة بين الكتف والرسغ — المتغير الوحيد
الاستراتيجية:

من بيانات التدريب: حساب متوسط أطوال العظام (a, b) من MediaPipe world landmarks
في الاستدلال: قياس c (shoulder-wrist distance) من world landmarks
حساب الزاوية بقانون جيب التمام
لماذا قد ينجح:

أطوال العظام ثابتة فيزيائياً — MediaPipe يقدرها بدقة معقولة
المسافة c أقل حساسية للمنظور من الزاوية نفسها
لا يحتاج ML أصلاً — هندسة بحتة
لماذا قد لا يكفي وحده:

MediaPipe 3D distances ليست مثالية
لكن يمكن دمجه كـ feature إضافي مع الحلول الأخرى
التكلفة: منخفضة جداً. تقييم: ★★★★☆ كـ feature إضافي، ★★★☆☆ كحل مستقل.

الحل 4: Body-Frame Canonicalization (مستوحى من 3DPCNet)
الفكرة: قبل حساب أي feature، نحول كل الـ landmarks إلى إطار مرجعي ثابت على الجسم:

Origin = hip_center
Y-axis = hip_center → shoulder_center (العمود الفقري)
X-axis = left_shoulder → right_shoulder (عرض الأكتاف)  
Z-axis = cross_product(X, Y) (للأمام)
ثم نحسب كل الـ features في هذا الإطار الجديد.

لماذا مهم:

في الإطار الحالي، نفس حركة الذراع تنتج features مختلفة من كاميرات مختلفة
في body frame: الذراع عند 90° ستبدو متشابهة من أي كاميرا
هذا يحل المشكلة في المصدر بدل محاولة تعويضها
الفرق عن 3DPCNet:

3DPCNet يستخدم GCN+Transformer (أثقل)
نستطيع عمل نسخة مبسطة بتحويل هندسي بحت (rotation matrix)
لا يحتاج تدريب إضافي
التكلفة: تعديل feature_engineering.py + إعادة extraction + training. تقييم: ★★★★★ — يعالج السبب الجذري.

الحل 5: KPE-Style Position Encoding
مستوحى من ورقة KPE و PriorFormer:

new_features = [
    atan2(bbox_center_x - image_center_x, focal_length),  # θx
    atan2(bbox_center_y - image_center_y, focal_length),  # θy  
    bbox_width / image_width,   # relative person size
    approximate_focal_length    # from device camera API
]
لماذا مفيد:

Perspective distortion يعتمد على أين الشخص في الصورة
شخص في وسط الصورة أقل تشوهاً من الحافة
Android يوفر camera FOV من CameraCharacteristics
التكلفة: إضافة 3-4 features جديدة. تقييم: ★★★☆☆ — مفيد كإضافة لكن ليس حلاً جذرياً.

الحل 6: Lightweight 2D→3D Arm Lifter
الفكرة: بدل الاعتماد على MediaPipe 3D (غير الموثوق)، ندرب MLP صغير:

Input: MediaPipe 2D landmarks (shoulder, elbow, wrist) + body context + bone priors
Output: corrected 3D positions (shoulder, elbow, wrist)
Then: compute angle from corrected 3D using law of cosines
مدعوم بـ: PriorFormer (1800μs على CPU — سريع جداً).

لماذا مختلف عن النهج الحالي:

حالياً: نأخذ features مشتقة → نتنبأ بالزاوية مباشرة
الجديد: نصحح الـ 3D positions أولاً → ثم الزاوية حسابية بحتة
أكثر قابلية للتعميم — يمكن استخدامه لأي مفصل وليس الكوع فقط
التكلفة: متوسطة-عالية. تقييم: ★★★★☆ — حل مبدئي قوي لكن أكبر في النطاق.

الحل 7: Temporal Smoothing (مستوحى من JAR/BLAPose)
الفكرة: بدل التنبؤ إطار بإطار، نستخدم نافذة زمنية:

Input: features from frames [t-N ... t ... t+N]
Output: smoothed angle at frame t
أو نسخة أبسط: GRU أحادي الاتجاه (causal) يأخذ الإطارات السابقة فقط.

لماذا مفيد:

القفزات المفاجئة في الزاوية ناتجة عن نويز MediaPipe وليس حركة فعلية
التنعيم الزمني يقلل هذه القفزات بشكل كبير
مدعوم بـ JAR و BLAPose
لكن: لا يحل مشكلة الـ bias (التحيز المنهجي) بين الكاميرات — يحل فقط الـ variance.

التكلفة: متوسطة. تقييم: ★★★☆☆ — مكمل وليس حلاً رئيسياً.

خامساً: الخطة المقترحة (بالترتيب)
بعد إعادة التقييم الشامل، أرى أن الحل الأمثل ليس حلاً واحداً بل تراكب 3 حلول متوافقة:

الأولوية	الحل	التأثير المتوقع	الجهد
1	Body-Frame Canonicalization	يعالج السبب الجذري	1-2 يوم
2	Multi-View Consistency Loss	يُجبر على ثبات عبر الكاميرات	نصف يوم
3	Residual over ang3d + Law of Cosines feature	يحسن الدقة المطلقة	نصف يوم
لماذا هذا الترتيب:

Body-Frame Canonicalization يعالج المشكلة في المصدر — يجعل الـ features نفسها أقل اعتماداً على الكاميرا. هذا أذكى من محاولة "تعويض" الفرق بالتدريب.

Multi-View Consistency Loss يكمل الأول — حتى بعد التحويل، قد يبقى بعض الفرق. الـ consistency loss يضغط على الموديل ليقلله.

Residual + Law of Cosines يحسن الدقة النهائية — الموديل يتعلم "تصحيح صغير" بدل "تخمين كبير".

الثلاثة معاً يمكن تنفيذهم في 2-3 أيام بتعديل الملفات الموجودة فقط، بدون إعادة بناء البنية التحتية.

سادساً: لماذا لا أوصي بحلول أخرى الآن
الحل	السبب
Full 2D→3D Lifter	مشروع كبير ومستقل — نجربه فقط إذا فشلت الحلول أعلاه
Multi-Hypothesis (MDN)	تعقيد إضافي بدون ضمان أنه يحل مشكلة الـ bias
Temporal (GRU)	يحل الـ jitter وليس الـ bias — مكمل لاحقاً
SMPL/BioPose	ثقيل جداً للموبايل — غير عملي
