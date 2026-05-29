# مرجع المعالم (Landmarks) والروابط

## المعالم الـ 33 من MediaPipe

| الفهرس | الاسم | الوصف |
|-------|------|-------|
| 0 | NOSE | الأنف |
| 1 | LEFT_EYE_INNER | العين اليسرى (داخلي) |
| 2 | LEFT_EYE | العين اليسرى |
| 3 | LEFT_EYE_OUTER | العين اليسرى (خارجي) |
| 4 | RIGHT_EYE_INNER | العين اليمنى (داخلي) |
| 5 | RIGHT_EYE | العين اليمنى |
| 6 | RIGHT_EYE_OUTER | العين اليمنى (خارجي) |
| 7 | LEFT_EAR | الأذن اليسرى |
| 8 | RIGHT_EAR | الأذن اليمنى |
| 9 | MOUTH_LEFT | الفم (يسار) |
| 10 | MOUTH_RIGHT | الفم (يمين) |
| 11 | LEFT_SHOULDER | الكتف الأيسر |
| 12 | RIGHT_SHOULDER | الكتف الأيمن |
| 13 | LEFT_ELBOW | المرفق الأيسر |
| 14 | RIGHT_ELBOW | المرفق الأيمن |
| 15 | LEFT_WRIST | المعصم الأيسر |
| 16 | RIGHT_WRIST | المعصم الأيمن |
| 17 | LEFT_PINKY | الخنصر الأيسر |
| 18 | RIGHT_PINKY | الخنصر الأيمن |
| 19 | LEFT_INDEX | السبابة اليسرى |
| 20 | RIGHT_INDEX | السبابة اليمنى |
| 21 | LEFT_THUMB | الإبهام الأيسر |
| 22 | RIGHT_THUMB | الإبهام الأيمن |
| 23 | LEFT_HIP | الورك الأيسر |
| 24 | RIGHT_HIP | الورك الأيمن |
| 25 | LEFT_KNEE | الركبة اليسرى |
| 26 | RIGHT_KNEE | الركبة اليمنى |
| 27 | LEFT_ANKLE | الكاحل الأيسر |
| 28 | RIGHT_ANKLE | الكاحل الأيمن |
| 29 | LEFT_HEEL | كعب القدم اليسرى |
| 30 | RIGHT_HEEL | كعب القدم اليمنى |
| 31 | LEFT_FOOT_INDEX | إصبع القدم اليسرى |
| 32 | RIGHT_FOOT_INDEX | إصبع القدم اليمنى |

---

## المعالم الافتراضية (Virtual)

| الفهرس | الاسم | الحساب |
|-------|------|---------|
| 33 | NECK | منتصف (11, 12) |
| 34 | SPINE | منتصف (23, 24) |

---

## JointLandmarkMapping — ربط Joint ↔ Landmark

الكود المستخدم في المشروع:

```kotlin
// من joint code إلى index
JointLandmarkMapping.jointToLandmark("left_knee")  // 25

// من index إلى joint code
JointLandmarkMapping.landmarkToJoint(25)  // "left_knee"

// المعالم المطلوبة لحساب زاوية مفصل
JointLandmarkMapping.getLandmarksForAngle("left_elbow")  // [11, 13, 15]
```

### Joint Codes المدعومة للزوايا

- `left_elbow`, `right_elbow`
- `left_shoulder`, `right_shoulder`, `left_shoulder_cross`, `right_shoulder_cross`
- `left_wrist`, `right_wrist`
- `left_hip`, `right_hip`
- `left_knee`, `right_knee`
- `left_ankle`, `right_ankle`
- `neck`, `neck_left`, `neck_right`, `neck_spine`
- `spine` (fallback chain: LEFT_KNEE → RIGHT_KNEE → angle-from-vertical)

---

## BodyLandmarks — الثوابت

```kotlin
BodyLandmarks.LEFT_KNEE      // 25
BodyLandmarks.RIGHT_SHOULDER // 12
BodyLandmarks.NECK           // 33
BodyLandmarks.POSE_CONNECTIONS  // قائمة أزواج للرسم
BodyLandmarks.getMirroredIndex(index)  // للكاميرا الأمامية
```

---

## Visibility و Presence

MediaPipe يُرجع لكل معلم:

- **visibility**: مدى وضوح المعلم في الصورة (0–1)
- **presence**: احتمال وجود المعلم (0–1)

العتبات في المشروع (من `app_settings.json`):

```json
"visibility": {
  "angleCalculation": 0.6,
  "overlay": 0.5,
  "poseValidation": 0.6
}
```

---

## رسم الهيكل العظمي

استخدام `PoseLandmarker.POSE_LANDMARKS`:

```kotlin
PoseLandmarker.POSE_LANDMARKS.forEach { connection ->
    val start = connection.start()
    val end = connection.end()
    if (start < landmarks.size && end < landmarks.size) {
        val p1 = landmarks[start]
        val p2 = landmarks[end]
        if (p1.visibility >= threshold && p2.visibility >= threshold) {
            canvas.drawLine(p1.x * w, p1.y * h, p2.x * w, p2.y * h, paint)
        }
    }
}
```

---

## المراجع

- [Pose Landmarker — Landmarks](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker#output)
- `BodyLandmarks.kt`
- `JointLandmarkMapping.kt`
