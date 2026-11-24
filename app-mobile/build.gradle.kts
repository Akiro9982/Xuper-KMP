import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val versionProps = Properties().apply {
    val archivo = rootProject.file("version.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

/** Versión inyectable por el workflow: -PappVersionName=0.2.0. */
val appVersionName = (project.findProperty("appVersionName") as String?)
    ?: versionProps.getProperty("app.versionName")
    ?: "0.0.0"

/** Misma fórmula que com.xuperclean.core.update.AppVersion.versionCode. */
val appVersionCode = (project.findProperty("appVersionCode") as String?)?.toInt()
    ?: run {
        val m = Regex("""^(\d+)\.(\d+)\.(\d+)$""").matchEntire(appVersionName)
        requireNotNull(m) { "version.properties: app.versionName inválido: $appVersionName" }
        m.groupValues[1].toInt() * 1_000_000 + m.groupValues[2].toInt() * 1_000 + m.groupValues[3].toInt()
    }


android {
    namespace = "com.xuperclean.mobile"
    compileSdk = 37

    // Firma de release: keystore.properties + xuper-release.jks (ambos ignorados por Git).
    // Sin ellos se usa la firma de depuración para que assembleRelease siga produciendo
    // una APK instalable en pruebas.
    val signingProps = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    val hasReleaseSigning = signingProps.getProperty("storeFile") != null &&
        rootProject.file(signingProps.getProperty("storeFile")).exists()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.xuperclean.mobile"
        minSdk = 23
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.activity:activity-compose:1.13.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Reproducción: Media3/ExoPlayer + datasource OkHttp (DNS DoH compartido)
    val media3 = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // Imágenes
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
}
