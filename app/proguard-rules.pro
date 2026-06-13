# Jetpack Compose Proguard Rules
-keepclassmembers class * extends androidx.compose.ui.node.Owner { *; }
-keep class androidx.compose.ui.platform.AndroidComposeView { *; }

# Room Database Rules
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Retrofit Rules
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, AnnotationDefault

# OkHttp Rules
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**

# Moshi Rules
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Coil Rules
-keep class coil.** { *; }
-dontwarn coil.**

# Application Custom Classes to prevent any reflections issues or database entity mapping issues
-keep class com.example.data.** { *; }
-keep class com.example.service.** { *; }
-keep class com.example.viewmodel.** { *; }
-keep class com.example.ui.** { *; }

# For Kotlin Serialization if used / reflection
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# General Keep Attributes
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
