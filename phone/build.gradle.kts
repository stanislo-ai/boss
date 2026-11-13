plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "pl.boss.drive.phone"
    compileSdk = 35
    defaultConfig {
        applicationId = "pl.boss.drive.phone"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/bossdrive.jks")
            storePassword = "bossdrive"
            keyAlias = "bossdrive"
            keyPassword = "bossdrive"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
        }
        debug { signingConfig = signingConfigs.getByName("shared") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { checkReleaseBuilds = false; abortOnError = false }
    dependenciesInfo { includeInApk = false; includeInBundle = false }
}
dependencies { implementation(project(":core")); implementation(project(":fonts")) }

// ---- bundle the add-on APKs so the head unit's store can install them over the car link ----
val addonModules = listOf("trip", "fuel", "podcasts", "webradio", "service")
val addonAssetsDir = layout.buildDirectory.dir("generated/addonAssets")
val bundleAddons by tasks.registering(Copy::class) {
    addonModules.forEach { m ->
        dependsOn(":addons:$m:assembleRelease")
        from(rootProject.file("addons/$m/build/outputs/apk/release")) {
            include("*.apk")
            rename { "pl.boss.drive.addon.$m.apk" }
        }
    }
    into(addonAssetsDir.map { it.dir("addons") })
}
android.sourceSets["main"].assets.srcDir(addonAssetsDir)
tasks.named("preBuild") { dependsOn(bundleAddons) }
