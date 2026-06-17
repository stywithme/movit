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

# Ktor client — HTTP engine + negotiation (narrow; coroutines ship consumer rules)
-keep class io.ktor.client.** { *; }
-keep class io.ktor.http.** { *; }
-keep class io.ktor.serialization.** { *; }
-keep class io.ktor.util.** { *; }
-keep class io.ktor.utils.io.** { *; }
-dontwarn io.ktor.**

# Koin DI — modules + Movit repositories wired at runtime
-keep class org.koin.core.** { *; }
-keep class org.koin.dsl.** { *; }
-keep class com.movit.core.data.di.** { *; }
-keep class com.movit.core.data.repository.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}

# Compose — @Composable entry points + ViewModels (lifecycle viewModel() reflection)
-keep @androidx.compose.runtime.Composable class com.movit.** { *; }
-keep class com.movit.feature.**.Movit*ViewModel { *; }
-keep class com.movit.host.** { *; }
-dontwarn androidx.compose.**

# Gson / Retrofit — legacy auth refresh (poc) + billing module (WS-3)
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class com.movit.storage.** { *; }
-keep class com.movit.billing.network.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
