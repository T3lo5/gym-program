plugins {
    id("com.android.application") version "8.5.0"
    id("org.jetbrains.kotlin.android") version "1.9.24"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
}

// Mesma keystore versionada do :app (H3-48): a assinatura do relógio passa a ser
// estável em qualquer ambiente e updates instalam por cima sem desinstalar.
val debugKeystoreFile = file("../app/debug.keystore")
val debugKeystorePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: "treinos123"
val debugKeyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: "treinos-debug"
val debugKeyPassword = System.getenv("DEBUG_KEY_PASSWORD") ?: "treinos123"

// Fonte única do versionName quando o build não recebe `-PversionName` (ex.:
// Android Studio). O gate e o CI passam `-PversionName` vindo de
// scripts/version.sh, que lê este mesmo arquivo.
val rootVersionName: String = rootProject.file("version.properties")
    .takeIf { it.exists() }
    ?.readLines()
    ?.firstOrNull { it.startsWith("versionName=") }
    ?.removePrefix("versionName=")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "1.0.0"

android {
    namespace = "com.treinos.wear"
    compileSdk = 34

    val wearVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
    val wearVersionName = (project.findProperty("versionName") as String?) ?: rootVersionName

    defaultConfig {
        applicationId = "com.treinos.wear"
        minSdk = 30
        targetSdk = 34
        versionCode = wearVersionCode
        versionName = wearVersionName
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

    signingConfigs {
        if (debugKeystoreFile.exists()) {
            signingConfigs.named("debug").configure {
                storeFile = debugKeystoreFile
                storePassword = debugKeystorePassword
                keyAlias = debugKeyAlias
                keyPassword = debugKeyPassword
            }
        }
        // Sem keystore versionada (checkout parcial): mantém a keystore do
        // ambiente — o gate reprova pelo fingerprint esperado (ci-android.sh).
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.named("debug").get()
        }
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

    // H4-14: tile do relógio — API oficial de Wear Tiles (estável 2024-05-01,
    // compatível com minSdk 30) e componentes Material do protolayout.
    implementation("androidx.wear.tiles:tiles:1.4.0")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.0")

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
