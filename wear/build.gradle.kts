import java.time.Duration

plugins {
    // H4-9: mesmo AGP do :app — 8.9.1 é o mínimo exigido por
    // `androidx.health.connect:connect-client:1.1.0`, dependência nova do :app.
    id("com.android.application") version "8.9.1"
    id("org.jetbrains.kotlin.android") version "1.9.24"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
    // H4-16: Room no relógio (modo standalone) — kapt para o compiler.
    id("org.jetbrains.kotlin.kapt") version "1.9.24"
}

// Robolectric offline (mesmo padrão do :app): o android-all vem das
// configurações abaixo, resolvido pelo Gradle e copiado para build/robolectric-
// deps — o teste nunca baixa dependência em tempo de execução.
val robolectricAndroidApi31 by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
    isCanBeResolved = true
}

val robolectricAndroidApi34 by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
    isCanBeResolved = true
}

val robolectricDepsDir = layout.buildDirectory.dir("robolectric-deps")

val prepareRobolectricDeps = tasks.register<Copy>("prepareRobolectricDeps") {
    description = "Copia o android-all do Robolectric do cache do Gradle para uso offline."
    // `clean` também roda neste build; sem a ordem, ele pode apagar o destino
    // depois da cópia quando o build paralelo está ligado.
    mustRunAfter("clean")
    from(robolectricAndroidApi31)
    from(robolectricAndroidApi34)
    into(robolectricDepsDir)
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
        applicationId = "com.treinos.app"
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

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric baixa o android-all para o Maven local em user.home.
            // Em containers com uid sem entrada no /etc/passwd, user.home vira
            // "?" e o teste falha antes de rodar. Usa um diretório gravável.
            val home = System.getProperty("user.home").orEmpty()
            val writableHome = home.takeIf { it.isNotBlank() && File(it).canWrite() }
                ?: System.getenv("ROBOLECTRIC_USER_HOME")
                ?: System.getProperty("java.io.tmpdir")
            it.systemProperty("user.home", writableHome)

            // O android-all vem das configurações robolectricAndroidApi31/34
            // (resolvidas pelo Gradle, cacheadas) copiadas por
            // prepareRobolectricDeps. offline=true impede qualquer tentativa de
            // download durante os testes.
            it.dependsOn(prepareRobolectricDeps)
            it.systemProperty("robolectric.offline", "true")
            it.systemProperty(
                "robolectric.dependency.dir",
                robolectricDepsDir.get().asFile.absolutePath,
            )

            // Trava de segurança: se um teste travar, falha em 35 min em vez de
            // segurar o job até o timeout (mesmo padrão do :app).
            it.timeout.set(Duration.ofMinutes(35))
        }
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

    // H4-16: Room no relógio — snapshot da sessão do dia e séries offline
    // (modo standalone). Versão fixa igual à do :app; schema versionado em
    // wear/schemas (mesmo padrão de app/schemas).
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Compose BOM (2024.04.01 = Compose 1.6.8, compatible with Kotlin 1.9.24)
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.wear.compose:compose-material:1.3.1")
    // Robolectric (mesmo padrão do :app): regressão do crash "abre e fecha" —
    // a activity abre, o botão dispara o caminho GMS e o processo sobrevive.
    testImplementation("org.robolectric:robolectric:4.12.2")
    // android-all exigido pelo Robolectric. A API 31 vem de
    // src/test/resources/robolectric.properties; a API 34 é o targetSdk. Os
    // jars ficam em build/robolectric-deps (modo offline).
    robolectricAndroidApi31("org.robolectric:android-all-instrumented:12-robolectric-7732740-i6")
    robolectricAndroidApi34("org.robolectric:android-all-instrumented:14-robolectric-10818077-i6")
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // `ui-test` resolve o idle pelo Espresso; sem esta dependência o teste de
    // Compose no Robolectric quebra em NoClassDefFoundError antes da primeira asserção.
    testImplementation("androidx.test.espresso:espresso-core:3.5.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// H4-16: schema exportado do Room do relógio, versionado (mesmo padrão de
// app/schemas) — fonte única versionada do schema do banco do pulso.
kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}
