# ===========================================================================
# CampusBite — ProGuard / R8 rules
# Milestone 05: Code Shrinking, Obfuscation & Release Packaging
# ===========================================================================

# ---------- Kotlin ----------
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# ---------- Room (database entities & DAOs must not be renamed) ----------
-keep class com.campusbite.data.local.entity.** { *; }
-keep interface com.campusbite.data.local.dao.** { *; }

# ---------- Retrofit & Gson (network DTOs & interface must not be renamed) ----------
-keep class com.campusbite.data.remote.dto.** { *; }
-keep interface com.campusbite.data.remote.CampusBiteApi { *; }
# Gson: keep generic-type information
-keepattributes Signature
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---------- Firebase (Auth + Firestore) ----------
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ---------- Glide ----------
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.GeneratedAppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ---------- ViewBinding (generated classes must survive shrinking) ----------
-keep class com.campusbite.databinding.** { *; }

# ---------- Domain / OrderRules (enum values used in Room DB) ----------
-keepclassmembers enum com.campusbite.data.local.entity.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------- EncryptedSharedPreferences / Jetpack Security ----------
-keep class androidx.security.crypto.** { *; }

# ---------- WorkManager ----------
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# ---------- OkHttp ----------
-dontwarn okhttp3.**
-dontwarn okio.**

# ---------- Coroutines ----------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
