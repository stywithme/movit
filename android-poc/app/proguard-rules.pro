# Add project specific ProGuard rules here.

# MediaPipe
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# TensorFlow Lite (legacy)
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# LiteRT (replaces TFLite)
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn com.google.ai.edge.litert.**
