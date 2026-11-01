# kotlinx.serialization (backup format)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.manoj.kaasu.**$$serializer { *; }
-keepclassmembers class com.manoj.kaasu.** {
    *** Companion;
}
-keepclasseswithmembers class com.manoj.kaasu.** {
    kotlinx.serialization.KSerializer serializer(...);
}
