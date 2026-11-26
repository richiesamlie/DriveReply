# Add project specific ProGuard rules here.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Optimization rules for Jetpack Compose and Room
# (Note: Room AAR contains default Proguard settings automatically, but you can add exceptions here if needed)

# Keep kotlinx.serialization @Serializable classes and generated serializers (Navigation3 NavKey destinations)
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.drivereply.app.**$$serializer { *; }
-keepclasseswithmembers class com.drivereply.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Navigation3 NavKey destination types
-keep class * implements androidx.navigation3.runtime.NavKey { *; }

# Strip verbose/debug/info/warn Android log calls from release builds to prevent accidental PII leakage
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
