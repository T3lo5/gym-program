# ProGuard rules for Treinos App

# Hilt
-keep class dagger.hilt.** { *; }
-keep class com.treinos.app.di.** { *; }

# Room
-keep class com.treinos.shared.data.** { *; }

# Kotlinx Serialization
-keep class kotlinx.serialization.** { *; }
-keep class com.treinos.shared.util.** { *; }

# Retrofit/OkHttp
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }

# Coil
-keep class coil.** { *; }

# Wear OS
-keep class com.google.android.gms.wearable.** { *; }