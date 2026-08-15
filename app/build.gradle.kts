plugins {
    id("com.android.application")
}

android {
    namespace = "id.pakkom.exambro"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.pakkom.exambro"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "3.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
