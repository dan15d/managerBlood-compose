import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

// ---------- Configuración (Supabase, modelo de IA) ----------
// Se lee de las variables de entorno (GitHub Actions) o del .env.local de la web, el mismo que usa Vite.
val envFile = Properties().apply {
    rootProject.file(".env.local").takeIf { it.exists() }?.reader()?.use { load(it) }
}
fun setting(name: String): String = System.getenv(name) ?: envFile.getProperty(name) ?: ""

abstract class GenerateAppConfig : DefaultTask() {
    @get:Input abstract val supabaseUrl: Property<String>
    @get:Input abstract val supabaseAnonKey: Property<String>
    @get:Input abstract val aiModel: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDir.get().file("es/dan15d/grimorio/AppConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package es.dan15d.grimorio
            |
            |/** Generado por Gradle desde .env.local o las variables de entorno. */
            |object AppConfig {
            |    const val SUPABASE_URL = "${supabaseUrl.get()}"
            |    const val SUPABASE_ANON_KEY = "${supabaseAnonKey.get()}"
            |    const val AI_MODEL = "${aiModel.get()}"
            |}
            |""".trimMargin(),
        )
    }
}

val generateAppConfig by tasks.registering(GenerateAppConfig::class) {
    supabaseUrl.set(setting("VITE_SUPABASE_URL"))
    supabaseAnonKey.set(setting("VITE_SUPABASE_ANON_KEY"))
    aiModel.set(setting("VITE_AI_MODEL"))
    outputDir.set(layout.buildDirectory.dir("generated/appConfig/kotlin"))
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("grimorio")
        browser {
            commonWebpackConfig { outputFileName = "grimorio.js" }
        }
        binaries.executable()
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppConfig)
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
                implementation(libs.compose.material3)
                implementation(libs.compose.components.resources)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor)
                implementation(libs.multiplatform.settings)
                implementation(project.dependencies.platform(libs.supabase.bom))
                implementation(libs.supabase.postgrest)
                implementation(libs.supabase.auth)
                implementation(libs.supabase.realtime)
                implementation(libs.supabase.functions)
                implementation(libs.qrose)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

android {
    namespace = "es.dan15d.grimorio"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "es.dan15d.grimorio"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "es.dan15d.grimorio.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Grimorio"
            packageVersion = "1.0.0"
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "es.dan15d.grimorio.resources"
}
