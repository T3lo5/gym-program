import java.time.Duration

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

// Robolectric baixa o android-all (~150 MB) em TEMPO DE EXECUÇÃO do teste, na
// Maven Central. No GitHub Actions esse download já pendurou o job por mais de
// uma hora (Maven Central 429 / espelho sem resposta), estourando o timeout e
// fazendo o job de release ser pulado. Aqui o jar é resolvido pelo Gradle —
// entra no cache de dependências (`~/.gradle` no CI, `.gradle-docker` no gate
// local) — e copiado para um diretório local, com o Robolectric em modo
// offline. Nenhum teste toca a rede para baixar SDK.
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

    // O MigrationTestHelper lê os schemas exportados do Room pelos assets. Para os
    // unit tests (Robolectric) o AGP usa o merge de assets do variante debug, então
    // é ali que `app/schemas` — fonte única, versionada — precisa aparecer. O teste
    // instrumentado (AppDatabaseMigrationTest) lê do assets do próprio androidTest.
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

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
            // segurar o job até o timeout de 90 min (e pular o release).
            // GitHub Actions runners são mais lentos que o Docker local (~5.5 min).
            it.timeout.set(Duration.ofMinutes(35))
        }
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
    implementation("androidx.compose.ui:ui")
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
    // android-all exigido pelo Robolectric. A API 31 vem de
    // app/src/test/resources/robolectric.properties; a API 34 é o targetSdk do
    // app e também é pedida em parte do ciclo de configuração. Os dois jars
    // ficam disponíveis em app/build/robolectric-deps (modo offline). Se o SDK
    // dos testes mudar, atualize as versões — o teste falha de forma explícita
    // se o jar não existir.
    robolectricAndroidApi31("org.robolectric:android-all-instrumented:12-robolectric-7732740-i6")
    robolectricAndroidApi34("org.robolectric:android-all-instrumented:14-robolectric-10818077-i6")
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // `ui-test` resolve o idle pelo Espresso; sem esta dependência o teste de
    // Compose no Robolectric quebra em NoClassDefFoundError antes da primeira asserção.
    testImplementation("androidx.test.espresso:espresso-core:3.5.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
