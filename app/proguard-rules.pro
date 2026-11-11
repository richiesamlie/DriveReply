# Add project specific ProGuard rules here.
# By default, the active ProGuard rules are in:
# C:\Users\dewa5\AppData\Local\Android\Sdk\tools\proguard\proguard-android.txt
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Optimization rules for Jetpack Compose and Room
# (Note: Room AAR contains default Proguard settings automatically, but you can add exceptions here if needed)

# Keep kotlinx.serialization @Serializable classes and generated serializers (Navigation3 NavKey destinations)
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.drivereply.app.**$$serializer { *; }
-keepclassmembers class com.drivereply.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.drivereply.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Navigation3 NavKey destination types
-keep class * implements androidx.navigation3.runtime.NavKey { *; }

# Strip verbose/debug Android log calls from release builds to prevent accidental PII leakage
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
