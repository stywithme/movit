# Add project specific ProGuard rules here.

# MediaPipe
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Preserve JNI callback methods used by MediaPipe tasks (ImageSegmenter LIVE_STREAM)
-keep class * implements com.google.mediapipe.framework.PacketCallback {
    public void process(com.google.mediapipe.framework.Packet);
}
-keep class * implements com.google.mediapipe.framework.PacketListCallback {
    public void process(java.util.List);
}
-keep class * implements com.google.mediapipe.framework.PacketWithHeaderCallback {
    public void process(com.google.mediapipe.framework.Packet, com.google.mediapipe.framework.Packet);
}

# TensorFlow Lite (legacy)
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# LiteRT (replaces TFLite)
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn com.google.ai.edge.litert.**
