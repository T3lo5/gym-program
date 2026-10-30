plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin.kapt")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.treinos.wear"
    compileSdk = 34

    defaultConfig {
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
        kotlinCompilerExtensionVersion = "1.6.7"
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

    // Wear OS Core
    implementation("androidx.wear:wear-compose-material:1.3.1")
    implementation("androidx.wear:wear-compose-foundation:1.3.1")
    implementation("androidx.wear:wear-tiles:1.2.1")
    implementation("androidx.wear:wear-watchface:1.2.1")
    implementation("androidx.wear:wear-watchface-complications-rendering:1.2.1")
    implementation("androidx.wear:wear-watchface-data:1.2.1")

    // Wear Data Layer
    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui:1.6.7")
    implementation("androidx.compose.foundation:foundation:1.6.7")
    implementation("androidx.compose.runtime:runtime-livedata:1.6.7")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Coil for Wear
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.wear:wear-compose-material:1.3.1")
}