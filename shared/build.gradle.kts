plugins {
    id("com.android.library") version "8.5.0"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

android {
    namespace = "com.treinos.shared"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.24")
    implementation("androidx.annotation:annotation:1.7.1")
}
