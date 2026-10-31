plugins {
    id("com.android.library") version "8.5.0"
    id("org.jetbrains.kotlin.android") version "1.9.24"
    id("org.jetbrains.kotlin.kapt") version "1.9.24"
}

android {
    namespace = "com.treinos.wear"
    compileSdk = 34

    defaultConfig {
        minSdk = 30
        targetSdk = 34
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
    implementation(project(":shared"))

    // Wear OS Core (correct artifact coordinates)
    implementation("androidx.wear.compose:compose-material:1.3.1")
    implementation("androidx.wear.compose:compose-foundation:1.3.1")
    implementation("androidx.wear.tiles:tiles:1.2.0")
    implementation("androidx.wear.watchface:watchface:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-rendering:1.2.1")
    implementation("androidx.wear.watchface:watchface-data:1.2.1")

    // Material Components (for Theme.MaterialComponents)
    implementation("com.google.android.material:material:1.11.0")

    // Wear Data Layer
    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    // Compose BOM (2024.04.01 = Compose 1.6.8, compatible with Kotlin 1.9.24)
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime-livedata")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Coil for Wear
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.wear.compose:compose-material:1.3.1")
}