import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

// google-services.json is NOT committed (each member downloads it from the Firebase console).
// Only apply the plugin when the file exists so the project still builds without it.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Release signing configuration — keystore is NOT committed (see .gitignore).
// Add these lines to your own local.properties (never commit them).
// The keystore and its passwords live only on the release engineer's machine:
//   STORE_FILE=release-key.jks
//   STORE_PASS=<your-store-password>
//   KEY_ALIAS=<your-key-alias>
//   KEY_PASS=<your-key-password>
val signStoreFile: String? = localProps.getProperty("STORE_FILE")
val signStorePass: String? = localProps.getProperty("STORE_PASS")
val signKeyAlias: String?  = localProps.getProperty("KEY_ALIAS")
val signKeyPass: String?   = localProps.getProperty("KEY_PASS")
val hasSigningConfig = signStoreFile != null && signStorePass != null && signKeyAlias != null && signKeyPass != null

android {
    namespace = "com.campusbite"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.campusbite"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The catalogue base URL is injected through BuildConfig; local.properties may override it
        // (MOCKAPI_BASE_URL=...) without editing source. The default is the public HTTPS catalogue
        buildConfigField(
            "String",
            "MOCKAPI_BASE_URL",
            "\"${localProps.getProperty("MOCKAPI_BASE_URL", "https://cdn.jsdelivr.net/gh/bao-rblx/CampusBite@main/api/")}\""
        )
    }

    if (hasSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile     = file(signStoreFile!!)
                storePassword = signStorePass
                keyAlias      = signKeyAlias
                keyPassword   = signKeyPass
            }
        }
    }

    buildTypes {
        release {
            // M5: Enable R8 shrinking and obfuscation for the release build.
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Debug keeps shrinking off so stack traces are readable.
            isMinifyEnabled   = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.glide)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.coroutines.play.services)

    implementation(libs.security.crypto)

    // M5: LeakCanary — detects memory leaks in debug builds only (not shipped in release)
    debugImplementation(libs.leakcanary)

    testImplementation(libs.junit)
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
