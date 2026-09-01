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

        // Read API keys from local.properties
        val localProperties = java.util.Properties().apply {
            val localPropertiesFile = project.file("local.properties")
            if (localPropertiesFile.exists()) {
                localPropertiesFile.inputStream().use { input ->
                    this.load(input)
                }
            }
        }

        buildConfigField("String", "XAI_API_KEY", "\"${localProperties.getProperty("XAI_API_KEY", "")}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "ELEVENLABS_API_KEY", "\"${localProperties.getProperty("ELEVENLABS_API_KEY", "")}\"")
        buildConfigField("String", "RORK_TOOLKIT_KEY", "\"${localProperties.getProperty("RORK_TOOLKIT_KEY", System.getenv("EXPO_PUBLIC_RORK_TOOLKIT_SECRET_KEY") ?: "")}\"")
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
