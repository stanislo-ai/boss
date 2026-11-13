plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "pl.boss.drive.addon.service"
    compileSdk = 35
    defaultConfig {
        applicationId = "pl.boss.drive.addon.service"
        minSdk = 26
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/bossdrive.jks")
            storePassword = "bossdrive"; keyAlias = "bossdrive"; keyPassword = "bossdrive"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), rootProject.file("addons/proguard-rules.pro"))
            signingConfig = signingConfigs.getByName("shared")
        }
        debug { signingConfig = signingConfigs.getByName("shared") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint { checkReleaseBuilds = false; abortOnError = false }
    dependenciesInfo { includeInApk = false; includeInBundle = false }
}
// no :fonts here on purpose — add-ons borrow Boss Drive's fonts at runtime
dependencies { implementation(project(":core")) }
