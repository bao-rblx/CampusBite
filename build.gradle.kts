plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied to :app (AGP 9 has built-in Kotlin) — only forces the KGP version on the classpath.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
}
