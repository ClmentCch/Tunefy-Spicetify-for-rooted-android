# Keep kotlinx.serialization generated serializers for the catalog and patch models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.tunefy.android.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class app.tunefy.android.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.tunefy.android.data.model.**$$serializer { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Keep service and activity entry points referenced from the manifest.
-keep class app.tunefy.android.MainActivity { *; }
-keep class app.tunefy.android.download.DownloadService { *; }

# Patch bundles are compiled separately and loaded from their own DEX at runtime.
# They refer to the patcher, smali and Kotlin APIs by their original binary names, so
# those names must survive R8. Shrinking other engine code is still allowed.
-keep class app.morphe.patcher.** { *; }
-keep class com.android.tools.smali.** { *; }
-keep class com.reandroid.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keepnames class com.android.apksig.internal.** { *; }

# apksig builds its ASN.1 models reflectively.
-keepclassmembers class com.android.apksig.internal.** {
    <init>();
    <fields>;
}

-dontwarn android.content.res.**
-dontwarn com.google.j2objc.annotations.*
-dontwarn java.awt.**
-dontwarn javax.**
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn org.apache.**
-dontwarn org.bouncycastle.**
-dontwarn com.google.errorprone.annotations.**
