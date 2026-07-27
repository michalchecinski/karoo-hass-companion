import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
}

ktlint {
    android = true
    verbose = true
}

val releaseVersion = providers.environmentVariable("RELEASE_VERSION").orNull?.takeIf { it.isNotBlank() }
val buildNumber = providers.environmentVariable("BUILD_NUMBER").orNull?.toIntOrNull()
val releaseSigningRequired = providers.environmentVariable("REQUIRE_RELEASE_SIGNING").orNull == "true"
val releaseSigningValues =
    mapOf(
        "KEY_ALIAS" to providers.environmentVariable("KEY_ALIAS").orNull,
        "KEY_PASSWORD" to providers.environmentVariable("KEY_PASSWORD").orNull,
        "KEYSTORE_PASSWORD" to providers.environmentVariable("KEYSTORE_PASSWORD").orNull,
        "KEYSTORE_BASE64" to providers.environmentVariable("KEYSTORE_BASE64").orNull,
    )
val configuredSigningValues = releaseSigningValues.filterValues { !it.isNullOrBlank() }
val hasCompleteReleaseSigning = configuredSigningValues.size == releaseSigningValues.size

if (configuredSigningValues.isNotEmpty() && !hasCompleteReleaseSigning) {
    val missingValues = releaseSigningValues.filterValues { it.isNullOrBlank() }.keys.sorted()
    throw GradleException("Incomplete release signing configuration. Missing: ${missingValues.joinToString()}")
}
if (releaseSigningRequired && !hasCompleteReleaseSigning) {
    throw GradleException("A signed release was requested, but release signing credentials are unavailable.")
}

val temporaryReleaseKeystore =
    if (hasCompleteReleaseSigning) {
        File.createTempFile("karoo-hass-companion-", ".jks").apply {
            writeBytes(Base64.getDecoder().decode(releaseSigningValues.getValue("KEYSTORE_BASE64")))
        }
    } else {
        null
    }

@Suppress("DEPRECATION")
gradle.buildFinished {
    temporaryReleaseKeystore?.delete()
}

android {
    namespace = "com.karoohass"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.karoohass"
        minSdk = 23
        targetSdk = 34
        versionCode = buildNumber ?: 1
        versionName = releaseVersion ?: "0.1.0-dev"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (hasCompleteReleaseSigning) {
            create("release") {
                keyAlias = releaseSigningValues.getValue("KEY_ALIAS")
                keyPassword = releaseSigningValues.getValue("KEY_PASSWORD")
                storeFile = temporaryReleaseKeystore
                storePassword = releaseSigningValues.getValue("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    // AGP 8.2 cannot 16 KB zip-align uncompressed native dependencies. Compressing
    // them is the Android-documented compatibility path until the build plugin moves.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.androidx.lifeycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose.ui)
    testImplementation(libs.junit)
}
