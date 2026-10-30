plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.aisendiri.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.aisendiri.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Play needs a higher versionCode for every upload: the release workflow passes its own counter.
        versionCode = (System.getenv("AISENDIRI_VERSION_CODE") ?: System.getenv("GITHUB_RUN_NUMBER"))?.toIntOrNull() ?: 1
        versionName = System.getenv("AISENDIRI_VERSION_NAME") ?: "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        // A shared debug key, so each new test build installs over the previous one
        // and keeps the downloaded AI. Never use this key for Play Store releases.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The Play upload key. Khai keeps the keystore; CI gets it from GitHub secrets
        // (see docs/RELEASE-ANDROID.md). Without it, release builds are left unsigned.
        val uploadKeystore = System.getenv("AISENDIRI_UPLOAD_KEYSTORE")
        if (uploadKeystore != null) {
            create("release") {
                storeFile = file(uploadKeystore)
                storePassword = System.getenv("AISENDIRI_UPLOAD_STORE_PASSWORD")
                keyAlias = System.getenv("AISENDIRI_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("AISENDIRI_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            // Readable native crash reports in Play Console.
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
        }
    }

    packaging {
        // llama.cpp picks its CPU backend by scanning the native library folder,
        // so native libraries must be extracted on install.
        jniLibs { useLegacyPackaging = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
}
