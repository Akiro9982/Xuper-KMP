# Reglas de R8/ProGuard compartidas por móvil y TV.
#
# Las librerías (Compose, Media3, OkHttp, Coil, kotlinx.serialization) publican sus
# propias reglas de consumidor; aquí se cubren los casos que R8 no puede deducir.

# Conservar metadatos que usan serialización, anotaciones y pilas de excepciones.
-keepattributes *Annotation*,InnerClasses,Signature,RuntimeVisibleAnnotations,AnnotationDefault,MethodParameters
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: los serializadores generados se resuelven por nombre.
-keep class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Clases opcionales que algunas dependencias referencian pero no se usan en Android.
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Mantener nombres de modelos propios en trazas de error (sin impedir el shrink).
-keepnames class com.xuperclean.core.model.** { *; }
