plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.produktivitaet.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.produktivitaet.app"
        minSdk = 26
        targetSdk = 35
        // Jeder GitHub-Build bekommt eine höhere Versionsnummer, damit Updates sauber installiert werden.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.$versionCode"
    }

    // Fester Schlüssel, damit neue Versionen über die alte installiert werden können (Daten bleiben erhalten).
    signingConfigs {
        create("app") {
            storeFile = file("produktivitaet.jks")
            storePassword = "produktivitaet"
            keyAlias = "produktivitaet"
            keyPassword = "produktivitaet"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("app")
        }
        debug {
            signingConfig = signingConfigs.getByName("app")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation("junit:junit:4.13.2")
}
