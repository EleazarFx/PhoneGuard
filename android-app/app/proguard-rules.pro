# Ktor
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class * implements kotlinx.serialization.KSerializer { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# ML Kit Face Detection
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.android.gms.vision.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Coroutines
-keepattributes InnerClasses, Signature, *Annotation*
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Timber
-keep class timber.log.Timber* { *; }
