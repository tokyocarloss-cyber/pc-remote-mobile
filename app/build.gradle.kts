import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI

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
        versionCode = 6
        versionName = "2.0.0-beta1"
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
    testImplementation("junit:junit:4.13.2")
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

val audioRes = layout.buildDirectory.dir("generated/nexusAudio/res")
android.sourceSets.getByName("main").res.srcDir(audioRes)
val generateNexusAudio by tasks.registering {
    outputs.dir(audioRes)
    doLast {
        val raw = audioRes.get().dir("raw").asFile.apply { mkdirs() }
        val bases = listOf(660.0,392.0,880.0,220.0,740.0,523.0,466.0,784.0,587.0,988.0)
        val durations = listOf(.045,.03,.08,.12,.13,.16,.12)
        val multipliers = listOf(1.0,1.25,.75,1.5,.55,1.25,.75)
        bases.forEachIndexed { theme, base -> durations.forEachIndexed { event, duration ->
            val rate = 22050; val count = (rate * duration).toInt()
            val bytes = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put("RIFF".toByteArray()).putInt(36+count*2).put("WAVEfmt ".toByteArray()).putInt(16)
            bytes.putShort(1.toShort()).putShort(1.toShort()).putInt(rate).putInt(rate*2).putShort(2.toShort()).putShort(16.toShort())
            bytes.put("data".toByteArray()).putInt(count*2)
            repeat(count) { i ->
                val t=i.toDouble()/rate; val phase=t/duration
                val envelope=min(1.0,t/.008)*(1-phase)*(1-phase)
                val f=base*multipliers[event]
                val tone=sin(2*PI*f*t)+.2*sin(2*PI*f*1.5*t)
                bytes.putShort((9500*envelope*tone).toInt().toShort())
            }
            raw.resolve("nexus_${theme}_${event}.wav").writeBytes(bytes.array())
        } }
    }
}
tasks.named("preBuild").configure { dependsOn(generateNexusAudio) }
