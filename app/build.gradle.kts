plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.abdo.ps4monitor"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.abdo.ps4monitor"
        minSdk = 26; targetSdk = 30; versionCode = 48; versionName = "6.0.0"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore"); storePassword = "android"
            keyAlias = "androiddebugkey"; keyPassword = "android"
        }
    }
    buildTypes {
        // A debug APK runs Compose several times slower (debuggable, no optimisation). Install the RELEASE build: ./gradlew assembleRelease
        // It is signed with the same debug keystore, so it installs and updates exactly like the debug build did.
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")   // installs the ready-made (ahead-of-time) profiles of Compose & co. so the first minutes are not slow
}
