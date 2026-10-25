plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "uz.tarjimon.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "uz.tarjimon.app"
        minSdk = 24
        targetSdk = 34
        versionCode = build
        versionName = "1.0.$build"
    }
    signingConfigs {
        create("release") {
            storeFile = file("tarjimon.keystore")
            storePassword = "tarjimon"; keyAlias = "tarjimon"; keyPassword = "tarjimon"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    androidResources { noCompress += listOf("onnx") }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")
    implementation("com.google.mlkit:translate:17.0.3")
}
