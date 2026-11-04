plugins {
    alias(libs.plugins.android.application)
}

android {
    // The N9 toolbar library (vendor/etesian/toolbar), as the ROM build links it.
    sourceSets["main"].java.srcDir("../../toolbar/src")
    namespace = "com.bompotis.etesian.launcher"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.bompotis.etesian.launcher"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The ROM uses AOSP test keys; signing debug builds with its platform key lets
    // `adb install -r` update the copy built into the ROM.
    val platformKey = file(System.getProperty("user.home") + "/android/etesian/keys/platform.p12")
    signingConfigs {
        if (platformKey.exists()) create("platform") {
            storeFile = platformKey
            storePassword = "android"
            keyAlias = "platform"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            if (platformKey.exists()) signingConfig = signingConfigs.getByName("platform")
        }
        release {
            optimization {
                // R8 merged Kotlin classes across packages in a way ART rejects at runtime
                // (IllegalAccessError in ProcessLifecycleInitializer), so ship unoptimized.
                enable = false
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.preference:preference-ktx:1.2.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}