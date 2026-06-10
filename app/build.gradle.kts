plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.colorosfeiniu.bridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.colorosfeiniu.bridge"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
