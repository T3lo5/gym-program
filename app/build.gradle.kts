plugins {
    id("com.android.application") version "8.5.0"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

// Release signing is driven by environment variables.
val releaseKeystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")
val hasReleaseSigning = !releaseKeystorePath.isNullOrBlank() && file(releaseKeystorePath).exists()

android {
    namespace = "com.treinos.app"
    compileSdk = 34

    val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
    val appVersionName = (project.findProperty("versionName") as String?) ?: "1.0.0"

    defaultConfig {
        applicationId = "com.treinos.app"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(releaseKeystorePath!!)
                    storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                    keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                }
            }
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
}
