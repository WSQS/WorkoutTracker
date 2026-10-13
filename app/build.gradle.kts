import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

/**
 * Signing credentials are read from keystore.properties, which is deliberately NOT in the
 * repository: a published signing key would let anyone build an APK that Android accepts
 * as a legitimate update over an installed copy. Copy keystore.properties.example to
 * keystore.properties and point storeFile at your own .jks. Without that file the release
 * build still runs, it just produces an unsigned APK.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val keystoreFile = keystoreProperties.getProperty("storeFile")?.let { rootProject.file(it) }
val hasSigningKey = keystoreFile?.exists() == true

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.sopho.workout"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.sopho.workout"
        minSdk = 26
        targetSdk = 36
        // CI passes the version derived from the release tag (-PversionName=1.2.0 -PversionCode=10200);
        // local builds just use these defaults. versionCode must keep growing, otherwise Android
        // refuses to install the new APK over the old one as an update.
        versionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 10200
        versionName = (findProperty("versionName") as String?)?.removePrefix("v") ?: "1.2.0"
    }

    signingConfigs {
        if (hasSigningKey) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // With -PsignDebugWithReleaseKey the debug APK gets the SAME signature as the release
            // APK. Two APKs that share an applicationId but not a signature cannot be installed
            // over each other: Android forces an uninstall, which wipes the workout records.
            // Signed with one key they can be swapped freely. Off by default for local builds.
            if (hasSigningKey && project.hasProperty("signDebugWithReleaseKey")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            if (hasSigningKey) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)

    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.ui.tooling.preview)

    testImplementation(libs.junit)
}
