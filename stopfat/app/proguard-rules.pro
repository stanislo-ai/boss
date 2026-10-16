# --- Anthropic Java SDK (Jackson + kotlin-reflect based JSON mapping) ---
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <init>(...);
    @com.fasterxml.jackson.annotation.* <methods>;
}
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-keep class kotlin.reflect.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# Optional / server-side dependencies of the SDK that are never used on Android
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn com.github.victools.**
-dontwarn io.swagger.**
-dontwarn io.opentelemetry.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.slf4j.**
-dontwarn com.standardwebhooks.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# --- ML Kit / CameraX are shipped with their own consumer rules ---

# --- App: Room entities and DataStore are handled by their consumer rules ---
-keep class com.stopfat.app.data.db.** { *; }
-dontwarn java.lang.reflect.AnnotatedType
