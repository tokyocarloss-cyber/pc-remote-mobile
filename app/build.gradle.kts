plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val nexusKeystorePath = System.getenv("NEXUS_KEYSTORE_PATH")
val nexusKeystorePassword = System.getenv("NEXUS_KEYSTORE_PASSWORD")
val nexusKeyAlias = System.getenv("NEXUS_KEY_ALIAS")
val nexusKeyPassword = System.getenv("NEXUS_KEY_PASSWORD")

android {
    namespace = "com.pcremote.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pcremote.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.1"
    }

    signingConfigs {
        if (
            !nexusKeystorePath.isNullOrBlank() &&
            !nexusKeystorePassword.isNullOrBlank() &&
            !nexusKeyAlias.isNullOrBlank() &&
            !nexusKeyPassword.isNullOrBlank()
        ) {
            create("release") {
                storeFile = file(nexusKeystorePath)
                storePassword = nexusKeystorePassword
                keyAlias = nexusKeyAlias
                keyPassword = nexusKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// Responsive UI rebuild validated through CI.
