# Pi Web Android - R8 / ProGuard rules
# Keep kotlinx.serialization generated serializers (release build).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.piweb.android.data.** {
    *** Companion;
}
-keepclasseswithmembers class dev.piweb.android.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}