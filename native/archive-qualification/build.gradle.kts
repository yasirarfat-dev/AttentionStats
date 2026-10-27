plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlin)
}

// Disposable primitive/envelope experiment, absent from product settings/dependencies.
android {
    namespace = "org.attentionstats.qualification.archive"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "org.attentionstats.qualification.archive"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "qualification-only"
    }
    sourceSets.getByName("main").assets.srcDir("vectors")
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
kotlin { compilerOptions { allWarningsAsErrors.set(true) } }
dependencies { implementation(libs.tink) }
dependencyLocking { lockAllConfigurations() }
