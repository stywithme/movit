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

# --- Movit KMP / Phase 06 G-5 (release + R8) ---

# Kotlin metadata (KMP + reflection used by Koin / serialization)
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# kotlinx.serialization — DTOs in core:network + generated $$serializer companions
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.movit.core.network.dto.**$$serializer { *; }
-keepclassmembers class com.movit.core.network.dto.** {
    *** Companion;
}
-keep @kotlinx.serialization.Serializable class com.movit.core.network.dto.** { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor client (auth, negotiation, okhttp engine on Android)
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn io.ktor.**

# Koin DI — modules + Movit repositories wired at runtime
-keep class org.koin.** { *; }
-keep class com.movit.core.data.** { *; }
-keep class com.movit.core.data.di.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}

# Movit shell / features (ViewModels, platform bindings)
-keep class com.movit.** { *; }
-keep class com.movit.designsystem.** { *; }
-keep class com.movit.feature.** { *; }
-keep class com.movit.resources.** { *; }

# Compose runtime — consumer rules ship with artifacts; keep Movit @Composable entry points
-keep @androidx.compose.runtime.Composable class com.movit.** { *; }
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Legacy Gson / Retrofit models (release minify with flag off)
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class com.trainingvalidator.poc.network.** { *; }
-keep class com.trainingvalidator.poc.models.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
