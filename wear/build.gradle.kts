import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.theveloper.pixelplay"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.theveloper.pixelplay"
        minSdk = 30
        targetSdk = 37
        versionCode = (project.findProperty("APP_VERSION_CODE") as? String)?.toInt() ?: 1
        versionName = (project.findProperty("APP_VERSION_NAME") as? String) ?: "1.0.0"
    }

    val keystoreProperties = Properties().apply {
        val propFile = rootProject.file("keystore.properties")
        if (propFile.exists()) {
            propFile.inputStream().use { load(it) }
        }
    }
    val releaseStorePath = keystoreProperties.getProperty("storeFile")?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.gradleProperty("KEYSTORE_FILE").orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.environmentVariable("KEYSTORE_FILE").orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: "$rootDir/vz-pixelplay.jks"
    val releaseKeystoreFile = file(releaseStorePath)
    val releaseStorePassword = keystoreProperties.getProperty("storePassword")?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.gradleProperty("KEYSTORE_PASSWORD").orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.environmentVariable("KEYSTORE_PASSWORD").orNull?.trim()?.takeIf { it.isNotEmpty() }
    val releaseKeyAlias = keystoreProperties.getProperty("keyAlias")?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.gradleProperty("KEY_ALIAS").orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.environmentVariable("KEY_ALIAS").orNull?.trim()?.takeIf { it.isNotEmpty() }
    val releaseKeyPassword = keystoreProperties.getProperty("keyPassword")?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.gradleProperty("KEY_PASSWORD").orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.environmentVariable("KEY_PASSWORD").orNull?.trim()?.takeIf { it.isNotEmpty() }
    val hasReleaseSigningCredentials = releaseKeystoreFile.exists() &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null

    if (hasReleaseSigningCredentials) {
        signingConfigs {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isShrinkResources = false
        }

        release {
            if (hasReleaseSigningCredentials) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-opt-in=com.google.android.horologist.annotations.ExperimentalHorologistApi")
    }
}

gradle.taskGraph.whenReady {
    val releasePackageTasks = setOf(
        "${project.path}:assembleRelease",
        "${project.path}:bundleRelease",
        "${project.path}:packageRelease",
        "${project.path}:packageReleaseBundle"
    )
    if (allTasks.any { it.path in releasePackageTasks }) {
        val keystoreProps = Properties().apply {
            val propFile = rootProject.file("keystore.properties")
            if (propFile.exists()) propFile.inputStream().use { load(it) }
        }
        val storePath = keystoreProps.getProperty("storeFile")?.trim()?.takeIf { it.isNotEmpty() }
            ?: providers.gradleProperty("KEYSTORE_FILE").orNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: providers.environmentVariable("KEYSTORE_FILE").orNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: "$rootDir/vz-pixelplay.jks"
        val keystoreFile = file(storePath)
        val hasStorePassword = !keystoreProps.getProperty("storePassword").isNullOrBlank() ||
            !providers.gradleProperty("KEYSTORE_PASSWORD").orNull.isNullOrBlank() ||
            !providers.environmentVariable("KEYSTORE_PASSWORD").orNull.isNullOrBlank()
        val hasKeyAlias = !keystoreProps.getProperty("keyAlias").isNullOrBlank() ||
            !providers.gradleProperty("KEY_ALIAS").orNull.isNullOrBlank() ||
            !providers.environmentVariable("KEY_ALIAS").orNull.isNullOrBlank()
        val hasKeyPassword = !keystoreProps.getProperty("keyPassword").isNullOrBlank() ||
            !providers.gradleProperty("KEY_PASSWORD").orNull.isNullOrBlank() ||
            !providers.environmentVariable("KEY_PASSWORD").orNull.isNullOrBlank()
        if (!keystoreFile.exists() || !hasStorePassword || !hasKeyAlias || !hasKeyPassword) {
            throw GradleException(
                "Wear release build requires release keystore and signing credentials " +
                    "(keystore.properties or KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD)."
            )
        }
    }
}

dependencies {
    implementation(project(":shared"))

    // Wear OS Compose
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    // Horologist
    implementation(libs.horologist.compose.layout)
    implementation(libs.horologist.media.ui)
    implementation(libs.horologist.audio.ui)
    implementation(libs.horologist.composables)

    // Data Layer
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Compose core
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.foundation)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Image loading
    implementation(libs.coil.compose)

    // Logging
    implementation(libs.timber)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Material icons for Wear
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    // Room (local database for transferred songs)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Media3 ExoPlayer (standalone local playback)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.mediarouter)

    constraints {
        // Fix vulnerabilities in transitive dependencies
        implementation(libs.netty.common)
        implementation(libs.netty.handler)
        implementation(libs.netty.codec.http)
        implementation(libs.netty.codec.http2)
        implementation(libs.bouncycastle.bcprov)
        implementation(libs.bouncycastle.bcpkix)
        implementation(libs.commons.lang3)
        implementation(libs.jdom2)
        implementation(libs.jose4j)
        implementation(libs.apache.httpclient)
    }
}
