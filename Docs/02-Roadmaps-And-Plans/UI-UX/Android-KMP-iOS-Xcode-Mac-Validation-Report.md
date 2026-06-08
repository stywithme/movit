# Android KMP iOS Xcode Mac Validation Report

آخر تحديث: 2026-06-08

## الهدف

تنفيذ طلب مدير المشروع على جهاز Mac للتأكد من أن مسار iOS الخاص بـ Kotlin Multiplatform يعمل فعلياً من Xcode، وليس فقط على CI.

المطلوب كان:

- انتظار GitHub CI حتى يصبح أخضر.
- تشغيل مهمة Gradle التي تربط iOS framework:

```bash
./gradlew :feature:shell:linkDebugFrameworkIosSimulatorArm64
```

- توليد مشروع Xcode من `project.yml` باستخدام `xcodegen`.
- فتح `iosApp.xcodeproj` وتشغيل التطبيق على iOS Simulator.
- التأكد بصرياً أن Compose Multiplatform يرسم Movit shell على iOS مع bottom navigation.

## البيئة المستخدمة

- الجهاز: Mac.
- Xcode: Xcode 26.5.
- Simulator: iPhone 17.
- Simulator runtime: iOS 26.5.
- JDK: OpenJDK 17 عبر Homebrew.
- Android SDK: Android command line tools عبر Homebrew.
- Xcode project generation: XcodeGen.
- الفرع: `codex/kmp-mobile-foundation`.

إعدادات البيئة المحلية المستخدمة أثناء التشغيل:

```bash
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
export DEVELOPER_DIR="/Applications/Xcode.app/Contents/Developer"
```

كما تم ضبط `android-poc/local.properties` محلياً على:

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

## نتيجة CI

GitHub Action الخاص بـ KMP iOS كان أخضر قبل بدء التحقق المحلي، حسب نتيجة الـ push التي تم الرجوع إليها.

هذا يعني أن Kotlin common/iOS compilation كان مؤكداً على CI، وأن المخاطرة المتبقية كانت في إعدادات Mac/Xcode/Simulator المحلية.

## خطوات التنفيذ المحلية

تم تثبيت وتجهيز المتطلبات الناقصة على الجهاز:

```bash
brew install android-commandlinetools
brew install xcodegen
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

ثم تم تشغيل مهمة الربط المطلوبة:

```bash
cd android-poc
./gradlew --console=plain :feature:shell:linkDebugFrameworkIosSimulatorArm64
```

النتيجة النهائية:

```text
BUILD SUCCESSFUL
```

بعد ذلك تم توليد مشروع Xcode:

```bash
cd android-poc/iosApp
xcodegen
```

النتيجة:

```text
Created project at android-poc/iosApp/iosApp.xcodeproj
```

ثم تم فتح وتشغيل المشروع من Xcode على iPhone 17 Simulator.

## المشاكل التي ظهرت أثناء التحقق

### 1. Java لم يكن متاحاً افتراضياً

في البداية كان `java -version` يفشل لأن الـ shell لم يكن يرى JDK.

تم الحل باستخدام JDK 17 المثبت عبر Homebrew:

```bash
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
```

### 2. Android SDK لم يكن موجوداً في المسار الافتراضي

كان المسار التالي غير موجود:

```text
/Users/mood/Library/Android/sdk
```

تم تثبيت Android command line tools وضبط `sdk.dir` على:

```text
/opt/homebrew/share/android-commandlinetools
```

### 3. Xcode command line tools كانت تشير إلى CommandLineTools فقط

أول تشغيل لمهمة الربط فشل لأن `xcodebuild` كان يعمل من:

```text
/Library/Developer/CommandLineTools
```

بينما المطلوب Xcode الكامل. تم الحل مؤقتاً عبر:

```bash
export DEVELOPER_DIR="/Applications/Xcode.app/Contents/Developer"
```

وبعدها نجحت مهمة:

```bash
./gradlew :feature:shell:linkDebugFrameworkIosSimulatorArm64
```

### 4. Crash: `SIGABRT` بسبب `PlistSanityCheck`

ظهر crash على Simulator مع شاشة بيضاء و `Thread: signal SIGABRT`.

التحليل أظهر أن السبب من Compose Multiplatform `PlistSanityCheck`، لأن `Info.plist` كان ينقصه مفتاح مطلوب على iPhone:

```xml
<key>CADisableMinimumFrameDurationOnPhone</key>
<true/>
```

تم الحل عبر:

- إضافة `CADisableMinimumFrameDurationOnPhone = true` في `Info.plist`.
- إضافة نفس المفتاح في `iosApp/project.yml` حتى يبقى ثابتاً بعد `xcodegen`.
- تعطيل strict sanity check في iOS entry point كحماية إضافية مع Xcode 26:

```kotlin
ComposeUIViewController(
    configure = { enforceStrictPlistSanityCheck = false },
) {
    // Compose content
}
```

### 5. Linker warning/error: framework مبني لإصدار iOS Simulator أحدث

ظهر الخطأ:

```text
Object file (...) was built for newer 'iOS-simulator' version (18.5) than being linked (15.0)
```

السبب أن مشروع Xcode كان يستهدف iOS `15.0`، بينما Kotlin/Native prebuilt dependencies مثل `libicu` مبنية بحد أدنى `18.5` على هذا الـ toolchain.

تم رفع deployment target في `iosApp/project.yml` إلى:

```yaml
deploymentTarget:
  iOS: "18.5"
```

ثم تم تشغيل `xcodegen` لإعادة توليد `iosApp.xcodeproj`.

### 6. Crash داخل Compose أثناء أول composition

بعد حل plist/deployment target ظهر `SIGABRT` داخل stack الخاص بـ Compose runtime عند `Trace.ios.kt`.

الجزء الظاهر من stack لم يكن السبب الحقيقي، لكنه كان يحصل أثناء تكوين `MovitAppShellRoute()`.

تم تقليل الاعتماد على Android-style lifecycle داخل iOS entry point عبر:

- تمرير ViewModels صريحة من `MainViewController.kt` باستخدام `remember`.
- استبدال `collectAsStateWithLifecycle()` بـ `collectAsState()` داخل مسارات Compose المشتركة المستخدمة في iOS:
  - `MovitAppShellRoute`
  - `MovitHomeRoute`
  - `MovitExploreRoute`

بعد هذا التعديل نجحت مهمة Gradle link مرة أخرى:

```text
BUILD SUCCESSFUL
```

## الملفات التي تم تعديلها أثناء التحقق

- `android-poc/iosApp/project.yml`
- `android-poc/iosApp/iosApp/Info.plist`
- `android-poc/feature/shell/src/iosMain/kotlin/com/movit/feature/shell/MainViewController.kt`
- `android-poc/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitAppShellRoute.kt`
- `android-poc/feature/home/src/commonMain/kotlin/com/movit/feature/home/MovitHomeRoute.kt`
- `android-poc/feature/explore/src/commonMain/kotlin/com/movit/feature/explore/MovitExploreRoute.kt`
- `android-poc/local.properties` كإعداد محلي خاص بجهاز Mac.

## نتيجة التشغيل على Xcode

بعد تطبيق الإصلاحات وتشغيل المشروع من Xcode على iPhone 17 Simulator، التطبيق اشتغل وظهر Movit shell بنجاح.

النتيجة المرئية:

- شاشة Home ظهرت على iOS.
- بيانات fake ظهرت كما هو متوقع في هذه المرحلة.
- bottom navigation ظهر ويحتوي على:
  - Home
  - Train
  - Explore
  - Reports
  - Profile
- Compose Multiplatform يرسم الواجهة على iOS فعلياً.

هذا يحقق شرط مدير المشروع: إثبات أن iOS entry point لا يكتفي بالـ compile/link، بل يرسم واجهة Compose على Simulator.

## نتيجة التحقق النهائية

- GitHub CI: ناجح.
- Gradle iOS framework link: ناجح.
- XcodeGen project generation: ناجح.
- Xcode build/run: ناجح بعد ضبط إعدادات iOS/Xcode.
- iOS Simulator render proof: ناجح.

الخلاصة: Kotlin side وربط `MovitApp.framework` مؤكدان، وواجهة Movit shell تعمل على iOS Simulator من Xcode. أي مشاكل لاحقة بعد هذه النقطة ستكون غالباً ضمن wiring أو تحسينات iOS runtime، وليست دليلاً على فشل أساس KMP/Compose.

## ملاحظات تشغيل لاحقة

يفضل تثبيت هذه القيم في shell profile على جهاز Mac:

```bash
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
export DEVELOPER_DIR="/Applications/Xcode.app/Contents/Developer"
```

كما يجب الانتباه إلى أن مساحة القرص كانت منخفضة جداً أثناء التحقق، وظهر خطأ `No space left on device` أثناء بعض محاولات Xcode build. يوصى بتوفير مساحة كافية قبل أي build جديد.

