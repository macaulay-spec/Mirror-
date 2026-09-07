import java.util.Properties

/**
 * Build-time secret resolution.
 *
 * FIX (audit P0-B): this file used to read ONLY `System.getenv(...)`, while CI wrote
 * the values into `local.properties` -- which Gradle does not export as environment
 * variables and this script never parsed. Every key therefore compiled in as an empty
 * string, so `BuildConfig.GEMINI_API_KEY` / `ELEVENLABS_API_KEY` / `TOOLKIT_SECRET_KEY`
 * were blank in every distributed APK. That made the Gemini branch of the provider
 * chain dead and `CloudSttEngine` inert ("no ElevenLabs key configured"), which is why
 * devices without a Google recognizer had no voice input at all.
 *
 * Resolution order is now: environment (CI secrets) -> local.properties (developer
 * machine) -> empty. Both sources are read through Gradle providers so the
 * configuration cache is invalidated when either changes.
 *
 * Keys are NEVER committed. `local.properties` is gitignored and CI injects env vars
 * from repository secrets.
 */
val localSecrets = Properties().apply {
    providers.fileContents(layout.projectDirectory.file("local.properties"))
        .asText.orNull?.let { text -> load(text.reader()) }
}

fun buildSecret(name: String): String {
    val raw = providers.environmentVariable(name).orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: localSecrets.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: ""
    // buildConfigField emits a Java string literal; escape anything that would break it.
    return raw.replace("\\", "\\\\").replace("\"", "\\\"")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.rork.jarvisaiassistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rork.jarvisaiassistant"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"

        // Keys are injected from CI secrets (env) or local.properties at compile time.
        // Never committed. Absent keys compile to "" and the affected provider is
        // reported unavailable at runtime rather than silently misbehaving.
        buildConfigField("String", "GEMINI_API_KEY", "\"" + buildSecret("GEMINI_API_KEY") + "\"")
        buildConfigField("String", "NVIDIA_API_KEY", "\"" + buildSecret("NVIDIA_API_KEY") + "\"")
        buildConfigField("String", "ELEVENLABS_API_KEY", "\"" + buildSecret("ELEVENLABS_API_KEY") + "\"")
        // Legacy Rork Toolkit gateway (abandoned; retained so BuildConfig stays stable).
        buildConfigField("String", "TOOLKIT_URL", "\"" + buildSecret("EXPO_PUBLIC_TOOLKIT_URL") + "\"")
        buildConfigField("String", "TOOLKIT_SECRET_KEY", "\"" + buildSecret("EXPO_PUBLIC_RORK_TOOLKIT_SECRET_KEY") + "\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.koin.androidx.compose)

    // JARVIS: memory + preferences
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    // JARVIS: networking / AI
    implementation(libs.okhttp)

    // JARVIS: camera / OCR
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    debugImplementation(libs.androidx.ui.tooling)
}
