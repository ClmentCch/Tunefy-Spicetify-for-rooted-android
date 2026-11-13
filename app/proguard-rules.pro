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
