> **Status:** `ARCHIVED` â€” superseded, cancelled, or historical review only.
> **Current SSOT:** `Docs/00-Active-Reference/README.md`
> **Archived:** 2026-05-29

# وثيقة الهيكلة التقنية لمشروع Movit

**الإصدار:** 1.0  
**التاريخ:** يناير 2026  
**التقنية المختارة:** Kotlin Multiplatform (KMP)

---

## 1. الملخص التنفيذي
يهدف المشروع لبناء تطبيق لياقة بدنية ذكي (**Movit**) يعمل على نظامي Android و iOS بكفاءة عالية (Native Performance) مع مشاركة المنطق البرمجي (Business Logic) بنسبة تزيد عن 80%. يعتمد التطبيق على تقنية Pose Estimation لتوجيه المستخدمين في الوقت الحقيقي.

---

## 2. مبررات اختيار التقنية (KMP)

تم اختيار **Kotlin Multiplatform (KMP)** للأسباب التالية:
1.  **إعادة استخدام الكود الحالي:** الـ Engine الحالي مكتوب بـ Kotlin ويمكن نقله بنسبة 90% مباشرة إلى الكود المشترك (Shared Module).
2.  **أداء Native حقيقي:** لا يوجد "جسر" (Bridge) يبطئ نقل البيانات، وهو أمر حيوي لتطبيقات Pose Detection التي تعالج 30 إطاراً في الثانية.
3.  **تجربة مستخدم مثالية:** استخدام واجهات Native (Jetpack Compose للـ Android و SwiftUI للـ iOS) يضمن أفضل تجربة وسلاسة.
4.  **دعم MediaPipe:** كلا المنصتين تدعمان MediaPipe بشكل Native، مما يسهل التكامل المباشر.

---

## 3. الهيكلة العامة للمشروع (Architecture Overview)

ينقسم المشروع إلى ثلاث طبقات رئيسية:

### أ. الطبقة المشتركة (Shared Module - Kotlin)
هذا هو "العقل المدبر" للتطبيق، ويعمل على Android و iOS بنفس الكود. يحتوي على:
*   **Training Engine:** محرك التدريب (StateMachine, RepCounter, HoldTimer).
*   **Form Validator:** منطق التحقق من صحة التمرين وحساب الزوايا.
*   **Data Models:** نماذج البيانات (ExerciseConfig, Planned Workout, JointState).
*   **Business Logic:** إدارة الجلسات، التقييم، المنطق الرياضي.

### ب. طبقة Android (Native Module)
*   **UI:** Jetpack Compose.
*   **Camera:** CameraX.
*   **Pose Detection:** MediaPipe Android SDK.
*   **Integration:** استدعاء الـ Shared Engine وتمرير الـ Landmarks له.

### ج. طبقة iOS (Native Module)
*   **UI:** SwiftUI.
*   **Camera:** AVFoundation.
*   **Pose Detection:** MediaPipe iOS SDK.
*   **Integration:** استدعاء الـ Shared Engine وتمرير الـ Landmarks له.

---

## 4. تدفق البيانات (Data Flow)

```
[Camera Input]
      ↓
[MediaPipe Native SDK] (Android/iOS)
      ↓
[Landmarks Data] (Raw X,Y,Z)
      ↓
---------------- (Boundary) ----------------
      ↓
[Shared Training Engine] (Kotlin Common)
  1. Angle Calculation
  2. Smoothing
  3. State Machine Update
  4. Rep Counting / Validation
      ↓
[State & Feedback] (Joint Colors, Messages, Counts)
      ↓
---------------- (Boundary) ----------------
      ↓
[Native UI] (Compose/SwiftUI)
  • Draw Skeleton Overlay
  • Update Counters
  • Play Audio Feedback
```

---

## 5. هيكلة المجلدات المقترحة (Project Structure)

```
Movit/
├── shared/                          # الكود المشترك (Common Logic)
│   ├── src/commonMain/kotlin/com/movit/
│   │   ├── engine/                  # (المنقول من المشروع الحالي)
│   │   │   ├── TrainingEngine.kt
│   │   │   ├── FormValidator.kt
│   │   │   ├── PhaseStateMachine.kt
│   │   │   └── ...
│   │   ├── models/                  # نماذج البيانات
│   │   └── config/                  # إعدادات التمارين
│   └── build.gradle.kts
│
├── androidApp/                      # تطبيق أندرويد
│   ├── src/main/java/com/movit/android/
│   │   ├── ui/                      # Jetpack Compose UI
│   │   ├── pose/                    # MediaPipe Android Wrapper
│   │   └── MainActivity.kt
│   └── build.gradle.kts
│
├── iosApp/                          # تطبيق iOS
│   ├── iosApp/
│   │   ├── UI/                      # SwiftUI Views
│   │   ├── Pose/                    # MediaPipe iOS Wrapper
│   │   └── iOSApp.swift
│   └── iosApp.xcodeproj
│
└── backend/                         # (المشروع الحالي Next.js)
    └── (يبقى كما هو لإدارة المحتوى)
```

---

## 6. خطة التنفيذ (Implementation Roadmap)

1.  **إعداد المشروع:** إنشاء مشروع KMP جديد باستخدام JetBrains Wizard.
2.  **نقل الـ Engine:** نسخ الملفات المنطقية (Pure Kotlin) من المشروع الحالي إلى `shared/commonMain`.
3.  **إعداد MediaPipe:**
    *   تنفيذ `MediaPipeHelper` في `androidApp` (Kotlin).
    *   تنفيذ `PoseDetector` في `iosApp` (Swift).
4.  **بناء الواجهات (MVP):**
    *   شاشة الكاميرا والـ Overlay في Android (Compose).
    *   شاشة الكاميرا والـ Overlay في iOS (SwiftUI).
5.  **الربط:** توصيل بيانات الـ Pose من الطبقة Native إلى الـ Shared Engine وعرض النتائج.

---

## 7. الملاحظات التقنية
*   **الأداء:** نقل البيانات بين Native و Shared سريع جداً ولا يسبب Bottleneck.
*   **الصوت:** سيتم التعامل مع Text-to-Speech (TTS) في الطبقات Native واستدعاؤه من المشترك عبر واجهة `Expect/Actual`.
*   **قاعدة البيانات:** يمكن استخدام SQLDelight (مكتبة KMP) لقاعدة بيانات محلية مشتركة، أو الاعتماد على API.

---
**الخلاصة:** هذا الهيكل يضمن لنا بناء تطبيق واحد قوي، قابل للتوسع، يستفيد من كل ما بنيناه سابقاً، ويفتح الباب لدعم iOS والساعات الذكية بكفاءة قصوى.
