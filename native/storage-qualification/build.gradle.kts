plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.ksp)
}

// This standalone APK is a disposable experiment, never an app dependency.
android {
    namespace = "org.attentionstats.qualification.storage"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "org.attentionstats.qualification.storage"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "qualification-only"
        ndk { abiFilters += "arm64-v8a" }
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("../../.cache/android-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents { beforeVariants(selector().withBuildType("release")) { it.enable = false } }

dependencies {
    implementation(libs.room.runtime)
    implementation(libs.sqlite)
    implementation(libs.sqlcipher)
    implementation(libs.coroutines)
    ksp(libs.room.compiler)
}

// Match the scaffold's explicit compiler pairing; AGP 10 migration stays open.
kotlin { compilerOptions { allWarningsAsErrors.set(true) } }
dependencyLocking { lockAllConfigurations() }
