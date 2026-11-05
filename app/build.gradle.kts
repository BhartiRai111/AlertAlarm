plugins {
    id("com.android.application")
}

android {
    namespace = "com.bharti.alertalarm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bharti.alertalarm"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "2.5"
    }

    // Same key on every GitHub build, so a new APK installs over the old one (no uninstall needed)
    signingConfigs {
        getByName("debug") {
            storeFile = file("alertalarm-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core:1.13.1")
    implementation("com.google.android.material:material:1.12.0")
    // Reads and writes PDF files (import + fillable sample form)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
