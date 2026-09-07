# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Hilt/Dagger, Room, Retrofit's core machinery, Coil, Lottie, AdMob and Firebase all ship their
# own consumer ProGuard rules bundled in their AARs — nothing extra is needed for those here.
# The rules below only cover this app's own reflection-based usage that those libraries can't
# know about on their own.

# --- Kotlinx Serialization ---
# AppResponse/AppResult (data/model/AppResponse.kt) are deserialized by the Retrofit
# kotlinx-serialization converter from the remote ad-config API. Without these, obfuscation
# renames their fields and the API response silently deserializes to all-null.
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.example.contactapp.data.model.**$$serializer { *; }
-keepclassmembers class com.example.contactapp.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.contactapp.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.example.contactapp.data.model.** { *; }

# --- Gson ---
# Gson reads/writes these model classes by reflection (ContactRepositoryImpl, CallReminder,
# WallpaperSelection) — obfuscating their field names breaks decoding of already-saved data.
-keep class com.example.contactapp.domain.model.DetailedContact { *; }
-keep class com.example.contactapp.util.CallReminder { *; }
-keep class com.example.contactapp.util.WallpaperDto { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
