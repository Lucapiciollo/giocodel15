import java.util.Properties

plugins {
    id("com.android.application")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.lucapiciollo.giocodel15"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lucapiciollo.giocodel15"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            // Preferred: local keystore.properties (git-ignored, developer machine only).
            // Fallback: environment variables / CI secrets.
            val storeFilePath = keystoreProperties.getProperty("storeFile")
                ?: System.getenv("RELEASE_STORE_FILE")
            if (!storeFilePath.isNullOrEmpty() && rootProject.file(storeFilePath).exists()) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                    ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                    ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                    ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Debug build: no minification, fast iteration.
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signing config is only applied when the release keystore is actually available
            // (see signingConfigs.release above). Without it, release build still succeeds but
            // produces an unsigned AAB/APK.
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    implementation("com.google.android.gms:play-services-nearby:19.4.0")
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:app-update:2.1.0")
    testImplementation("junit:junit:4.13.2")
}
