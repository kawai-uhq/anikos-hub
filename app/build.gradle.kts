plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.anikoshub.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.anikoshub.app"
        minSdk = 24
        targetSdk = 35

        versionCode =
            System.getenv("GITHUB_RUN_NUMBER")
                ?.toIntOrNull() ?: 1

        versionName =
            "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "1"}"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            val keystoreFile =
                file("release-key.jks")

            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword =
                    System.getenv("KEYSTORE_PASSWORD")
                keyAlias =
                    System.getenv("KEY_ALIAS")
                keyPassword =
                    System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig =
                signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(
        platform(
            "androidx.compose:compose-bom:2024.12.01"
        )
    )

    implementation(
        "androidx.activity:activity-compose:1.10.0"
    )

    implementation(
        "androidx.compose.ui:ui"
    )

    implementation(
        "androidx.compose.ui:ui-tooling-preview"
    )

    implementation(
        "androidx.compose.material3:material3"
    )

    implementation(
        "androidx.lifecycle:lifecycle-runtime-compose:2.8.7"
    )

    implementation(
        "androidx.navigation:navigation-compose:2.8.5"
    )

    implementation(
        "androidx.webkit:webkit:1.12.1"
    )

    implementation(
        "io.coil-kt:coil-compose:2.7.0"
    )

    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0"
    )

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )
}
