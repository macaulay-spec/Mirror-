import java.util.Properties

/**
 * Build-time secret resolution.
 *
 * FIX (audit P0-B): this file used to read ONLY `System.getenv(...)`, while CI wrote
 * the values into `local.properties` -- which Gradle does not export as environment
 * variables and this script never parsed. Every key therefore compiled in as an empty
 * string, so `BuildConfig.GEMINI_API_KEY` was blank in every distributed APK and the
 * whole Gemini branch of the provider chain was dead.
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

        // The Gemini key is injected from CI secrets (env) or local.properties at
        // compile time and is never committed. If absent it compiles to "" and the
        // Gemini provider is reported unavailable at runtime rather than silently
        // misbehaving. (NVIDIA's key is hardcoded in ApiConfig.kt -- see below.)
        buildConfigField("String", "GEMINI_API_KEY", "\"" + buildSecret("GEMINI_API_KEY") + "\"")
        // NVIDIA_API_KEY is no longer a BuildConfig field: ApiConfig hardcodes it by
        // owner decision, and nothing read this one. One copy, one place to rotate.
        // REMOVED (owner decision, 2026-09-07): ELEVENLABS_API_KEY and the abandoned
        // Rork TOOLKIT_URL / TOOLKIT_SECRET_KEY fields. No code references them.
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

// FIX (audit P0-D): AppDatabase declared exportSchema = false, so every schema version
// was thrown away at build time. With no history there is nothing to write a migration
// against, which is why the database was built with fallbackToDestructiveMigration() and
// every version bump silently deleted the user's memories. Schemas are now exported here
// and committed under app/schemas/ -- see Migrations.kt for the policy.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
