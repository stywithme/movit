الحل 1: Body-Plane Projection (إسقاط على مستوى الجسم)
الفكرة: بدل حساب الزاوية في coordinate system الكاميرا، نحسبها في coordinate system الجسم نفسه.

كيف يعمل:

نبني محاور الجسم من landmarks موثوقة (كتفين + أرجل):
X_body (جانبي) = اتجاه الكتف الأيمن → الأيسر
Y_body (رأسي) = اتجاه الـ hip midpoint → shoulder midpoint
Z_body (أمامي) = cross product(X, Y)
نحول الـ shoulder, elbow, wrist إلى body coordinates
نحسب الزاوية في الـ sagittal plane (Y_body, Z_body) - المستوى اللي بيحصل فيه flexion/extension
لماذا ممكن يشتغل:

من الـ frontal view: الـ body Z يعتمد أساسا على world Z (لكن الذراع بتتحرك في image plane فالخطأ تأثيره أقل)
من الـ side view: الـ body Z يعتمد أساسا على world X (اللي هو موثوق!) - لأن forward direction الجسم = اتجاه X الكاميرا من الجنب
النتيجة: الـ rotation في reference frame بتعوض الضعف في Z بقوة X
المميزات:

حل نظري أنيق ومنطقي هندسيا
لا يحتاج calibration
يعمل لكل التمارين (مش bicep curls فقط)
العيوب:

يعتمد على رؤية الكتفين الاثنين + الهيب (لازم يكونوا visible)
لو world landmarks نفسها camera-dependent بشكل كبير (وهو كذلك)، الـ body axes هتكون غير دقيقة
يحتاج right shoulder data لحساب elbow أيسر - ممكن يكون مش ظاهر
التطبيق: تعديل في AngleCalculator.kt - function جديدة calculateElbowAngleBodyPlane() تستخدم landmarks 11, 12, 23, 24 (كتفين + هيب) لبناء body frame ثم تحسب.

الحل 2: Segment-Length-Constrained Z Reconstruction
الفكرة: نعرف طول الـ upper arm والـ forearm الحقيقي (ثابت تشريحيا). نستخدم الـ 2D positions الموثوقة + أطوال العظام المعروفة لإعادة بناء الـ Z بدل الاعتماد على MediaPipe Z.

كيف يعمل:

Calibration phase: نحسب أطوال العظام من أقصى طول XY ظاهر (لما الذراع في مستوى الكاميرا)
L_ua = max observed |shoulder-elbow|_XY (upper arm)
L_fa = max observed |elbow-wrist|_XY (forearm)
كل frame: نحسب الـ 2D projected lengths
l_ua = |shoulder-elbow|_2D
l_fa = |elbow-wrist|_2D
نحسب الـ depth component لكل segment:
dz_ua = sqrt(L_ua² - l_ua²) (لو l_ua < L_ua)
dz_fa = sqrt(L_fa² - l_fa²)
نعيد بناء الـ 3D vectors مع الـ Z المحسوب
نحسب الزاوية من الـ vectors المعاد بناؤها
المميزات:

يستخدم الـ 2D (الموثوق) كأساس
يحسب Z بناء على constraint هندسي حقيقي (طول العظام ثابت)
لا يعتمد على MediaPipe Z إطلاقا
العيوب:

Sign ambiguity: مش عارفين الـ segment بيروح ناحية الكاميرا ولا بعيد عنها (اتجاهين ممكنين)
يحتاج calibration period (أول ثواني الاستخدام)
أطوال العظام في normalized space تتغير مع المسافة من الكاميرا
حل الـ Sign ambiguity:

نستخدم MediaPipe world Z للاتجاه فقط (sign) مش للقيمة
أو نستخدم anatomical priors (في bicep curl مثلا الـ forearm دايما أمام الـ upper arm)
الحل 3: Adaptive Z-Weight Attenuation (تخفيف وزن Z التكيفي)
الفكرة: بدل استخدام X, Y, Z بنفس الوزن، نقلل تأثير Z ديناميكيا بناء على مدى موثوقيته.

كيف يعمل:

نحسب dzShare لكل segment كالعادة
نحسب imbalance = |dzShare_BA - dzShare_BC|
نحسب max dzShare = max(dzShare_BA, dzShare_BC)
نحسب zWeight:
val zWeight = when {
    maxDzShare < 0.25 -> 1.0f    // Z reliable, use full 3D
    maxDzShare > 0.55 -> 0.0f    // Z dominant/unreliable, use pure 2D
    else -> 1.0f - ((maxDzShare - 0.25f) / 0.30f)  // smooth transition
}
نطبق الوزن على Z لكلا الـ segments بالتساوي:
BA = (baX, baY, baZ * zWeight)
BC = (bcX, bcY, bcZ * zWeight)
angle = acos(dot(BA, BC) / (|BA| * |BC|))
المميزات:

بسيط جدا في التطبيق
smooth transition بدون jumps
لما Z موثوق (زي الركبة) يشتغل 3D كامل
العيوب:

من الـ side view، حتى World XY (بدون Z أصلا) يعطي 85.8° وهي غلط - يعني إزالة Z لوحدها مش كافية
الـ threshold values تحتاج tuning
مش بيحل المشكلة الأساسية، بس بيقلل تأثيرها
تقييم واقعي: هذا الحل سيحسن الحالات اللي فيها dzShare عالي (frontal views) لكن لن يحل الـ side view problem لأن المشكلة هناك في XY نفسها.

الحل 4: Camera-Angle Detection + View-Specific Correction
الفكرة: نكتشف اتجاه الكاميرا بالنسبة للجسم، وبناء عليه نختار أفضل طريقة حساب أو نطبق correction مخصص.

كيف يعمل:

نحسب body orientation من الكتفين في الـ screen space:

shoulder_screen_width = |left_shoulder.x - right_shoulder.x| (normalized)
facing_ratio = shoulder_screen_width / max_observed_shoulder_width
facing_ratio ≈ 1.0 = frontal view
facing_ratio ≈ 0.0 = side view
بناء على الـ facing_ratio:

Frontal (>0.7): استخدم 2D angle (الأدق من الأمام)
Side (<0.3): استخدم YZ projection أو 3D with correction
بينهم: weighted blend
Apply correction curve لكل نطاق (مبني على بيانات تجريبية)

المميزات:

عملي جدا لتطبيق تمارين
يستفيد من أفضل ما في كل طريقة حسب الموقف
يمكن تحسينه تدريجيا بإضافة بيانات calibration
العيوب:

يحتاج بيانات تجريبية كثيرة لبناء الـ correction curves
الـ correction curves تختلف حسب الزاوية الحقيقية (مش linear)
Shoulder width estimation ممكن يكون غير دقيق
الحل 5: YZ-Projection + Adaptive Scaling
ملاحظة مهمة من البيانات: الـ YZ projection هو الأكثر ثباتا عبر اتجاهات الكاميرا!

اتجاه	YZ	Real	Error
Front-side	72.2°	~35°	+37°
Frontal	65.3°	~35°	+30°
Front-behind	57.4°	~35°	+22°
Side	58.9°	~35°	+24°
مدى 15° فقط (مقارنة بـ 75° للـ 2D و29° للـ 3D). ودائما overestimates.

الفكرة: استخدم YZ projection كأساس مع correction factor ديناميكي.

لماذا YZ أكثر ثباتا: المحور Y (الرأسي) ثابت دائما بغض النظر عن اتجاه الكاميرا. والـ Z يتغير لكن تأثيره في YZ plane أقل تأثرا بالدوران.

كيف يعمل:

حساب زاوية YZ projection
حساب correction factor بناء على dzShare imbalance ونسبة segment lengths
تطبيق الـ correction
المميزات:

أبسط حل يستغل الـ insight من البيانات
أكثر ثباتا من أي projection أخرى
العيوب:

الـ overestimation ليس ثابتا (22° إلى 37°)
الـ correction factor سيختلف حسب الزاوية الحقيقية
بيانات نقطة واحدة مش كافية لبناء correction model كامل
الحل 6: ML-Based 3D Lifting (استبدال MediaPipe 3D)
الفكرة: استخدم MediaPipe للـ 2D detection فقط، وطبق model منفصل لرفع الـ 2D إلى 3D بدقة أعلى.

الخيارات:

MotionBERT: state of the art في 2D-to-3D lifting، دقة عالية جدا
VideoPose3D: خفيف، يعمل على temporal sequences
MoveNet + depth estimator: خيار Google الرسمي
المميزات:

حل المشكلة من جذرها (3D دقيق)
مبني على أبحاث متقدمة
العيوب:

يحتاج model إضافي (حجم + ذاكرة + وقت)
قد لا يعمل real-time على كل الأجهزة
تكامل معقد مع الكود الحالي
ممكن يأثر على الزوايا الأخرى اللي شغالة كويس
