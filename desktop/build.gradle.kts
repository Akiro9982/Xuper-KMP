import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    // JDK del host: JBR 25. Java y Kotlin alineados (Kotlin 2.4 soporta target 25 en JVM).
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

java {
    val javaVersion = JavaVersion.toVersion(25)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

dependencies {
    implementation(project(":core"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // Red (portal, imágenes) + DNS resiliente
    implementation("com.squareup.okhttp3:okhttp:5.2.1")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    // HWND para embeber mpv
    implementation("net.java.dev.jna:jna-platform:5.17.0")
}

/** Prueba aislada del reproductor mpv (ventana + stream real + seek). */
tasks.register<JavaExec>("runMpvTest") {
    group = "verification"
    description = "Reproduce un stream VOD real en mpv embebido y verifica decodificación"
    mainClass.set("com.xuperclean.desktop.TestMpvKt")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("printVodUrl") {
    group = "verification"
    description = "Imprime una URL VOD válida para pruebas manuales de mpv"
    mainClass.set("com.xuperclean.desktop.tools.PrintUrlKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("liveInfo") {
    group = "verification"
    description = "Muestra los campos disponibles en el catálogo de TV en vivo"
    mainClass.set("com.xuperclean.desktop.tools.LiveInfoKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("diagnose") {
    group = "verification"
    description = "Diagnóstico del portal: columnas, CDNs de directo y variantes VOD"
    mainClass.set("com.xuperclean.desktop.tools.DiagnoseKt")
    classpath = sourceSets["main"].runtimeClasspath
}

compose.desktop {
    application {
        mainClass = "com.xuperclean.desktop.MainKt"

        // El JBR de Android Studio no incluye jpackage: para empaquetar se indica
        // un JDK completo con -PpackagingJdk="C:\ruta\jdk".
        (providers.gradleProperty("packagingJdk").orNull
            ?: System.getenv("PACKAGING_JDK"))?.let { javaHome = it }

        // JNA accede a APIs nativas (HWND): evitar warnings y bloqueo futuro en JVM 25+
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Xuper"
            packageVersion = "0.1.0"
            description = "Xuper — cliente limpio de TV y VOD"
            vendor = "XuperClean"

            // mpv viaja con la app: resources/windows/mpv/mpv.exe
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            windows {
                iconFile.set(project.file("icons/xuper.ico"))
                menuGroup = "Xuper"
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }
        }
    }
}
