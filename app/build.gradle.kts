plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ivangames.textflow"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ivangames.textflow"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("../my-upload-key.jks") 
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        // Включаем десугаринг для Readium
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.0.0")

    // Readium Toolkit для EPUB
    implementation("org.readium.kotlin-toolkit:readium-shared:3.0.2")
    implementation("org.readium.kotlin-toolkit:readium-streamer:3.0.2")
    implementation("org.readium.kotlin-toolkit:readium-navigator:3.0.2")
    implementation("org.readium.kotlin-toolkit:readium-opds:3.0.2")

    // Библиотека для десугаринга (требуется Readium)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
}
