# Add project specific ProGuard rules here.

# Keep Gson classes
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# Keep Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep our data classes
-keep class com.opensourcebhaiya.otpsync.data.** { *; }
-keep class com.opensourcebhaiya.otpsync.ui.LinkedDevice { *; }
-keep class com.opensourcebhaiya.otpsync.ui.OtpEntity { *; }
