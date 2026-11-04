plugins {
    id("com.android.application") version "8.5.0"
    id("org.jetbrains.kotlin.android") version "1.9.24"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
}

android {
    namespace = "com.treinos.wear"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.treinos.wear"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES,LICENSE,LICENSE.txt,NOTICE,NOTICE.txt}"
        }
    }
}

dependencies {
    // Project
    implementation(project(":protocol"))

    // Wear OS Core
    implementation("androidx.wear.compose:compose-material:1.3.1")
    implementation("androidx.wear.compose:compose-foundation:1.3.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Wear Data Layer
    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    // Material Components (tema do wear)
    implementation("com.google.android.material:material:1.11.0")

    // Serialização (estado do Data Layer)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Compose BOM (2024.04.01 = Compose 1.6.8, compatible with Kotlin 1.9.24)
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.wear.compose:compose-material:1.3.1")
}
