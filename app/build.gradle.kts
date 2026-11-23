plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.cricketmechanic"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.cricketmechanic"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2-testing"
    }
}
dependencies {
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
}
