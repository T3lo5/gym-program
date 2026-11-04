plugins {
    id("com.android.application") version "8.5.0"
    id("org.jetbrains.kotlin.android") version "1.9.24"
    id("org.jetbrains.kotlin.kapt") version "1.9.24"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
}

kapt {
    correctErrorTypes = true
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}

// Signing configuration - same keystore for debug and release so app updates
// install over each other without requiring uninstall.
val debugKeystoreFile = file("app/debug.keystore")
val debugKeystorePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: "treinos123"
val debugKeyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: "treinos-debug"
val debugKeyPassword = System.getenv("DEBUG_KEY_PASSWORD") ?: "treinos123"

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

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    signingConfigs {
        if (debugKeystoreFile.exists()) {
            signingConfigs.named("debug").configure {
                storeFile = debugKeystoreFile
                storePassword = debugKeystorePassword
                keyAlias = debugKeyAlias
                keyPassword = debugKeyPassword
            }
        }
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseKeystorePath!!)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.named("debug").get()
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.named("release").get()
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Wear OS Data Layer (comunicação celular ↔ relógio)
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    implementation(project(":protocol"))

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
