# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Room, Coil, AndroidX Navigation Compose and androidx.transition all ship their own consumer
# ProGuard rules bundled in their AARs — nothing extra is needed for those here.

# --- kotlinx.serialization ---
# The library's own consumer rules keep @Serializable classes, but R8 can still strip a data
# class's generated $$serializer companion / fields when nothing else in the app references them
# directly by name (exactly this app's AppResponse/AppResult, which are only ever referenced
# through the Json decoder, never constructed by name elsewhere) - without this, the remote config
# JSON can silently fail to decode only in a minified release build, never in debug.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.phone.contact.calls.dialer.**$$serializer { *; }
-keepclassmembers class com.phone.contact.calls.dialer.** {
    *** Companion;
}
-keepclasseswithmembers class com.phone.contact.calls.dialer.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Retrofit / OkHttp ---
# Retrofit's own consumer rules cover its core reflection use; these extra ones silence R8 notes
# that otherwise show up with Kotlin suspend endpoints + the kotlinx.serialization converter.
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions

# --- uCrop ---
# uCrop's own README explicitly recommends these (https://github.com/Yalantis/uCrop) rather than
# relying solely on its bundled consumer rules.
-dontwarn com.yalantis.ucrop**
-keep class com.yalantis.ucrop** { *; }
-keep interface com.yalantis.ucrop** { *; }