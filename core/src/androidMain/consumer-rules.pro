# kotlinx.serialization needs its generated *$Companion / serializer() methods kept under R8.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class com.dcorp.licensecore.** {
    *** Companion;
}
-keepclasseswithmembers class com.dcorp.licensecore.** {
    kotlinx.serialization.KSerializer serializer(...);
}
