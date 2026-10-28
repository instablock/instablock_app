import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.instablock"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.instablock"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.5"
    }

    // Release key and passwords live in .signing/ (not shared, not in git). Keep a backup:
    // updates only install over an existing copy if they're signed with the same key.
    val signing = rootProject.file(".signing/keystore.properties")
    signingConfigs {
        if (signing.exists()) {
            val p = Properties().apply { signing.inputStream().use { load(it) } }
            create("release") {
                storeFile = file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Compile with JDK 17 whatever JDK runs Gradle (Gradle finds an installed one).
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
