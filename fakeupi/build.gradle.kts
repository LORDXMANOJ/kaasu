// Test-only stand-in for GPay/PhonePe so the pay hand-off can be tested on an emulator.
// Never installed on a real phone.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.manoj.fakeupi"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.manoj.fakeupi"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
