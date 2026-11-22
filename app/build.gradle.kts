plugins { id("com.android.application") }
android {
    namespace = "com.ghostdrop.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ghostdrop.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation("com.google.android.gms:play-services-nearby:19.5.1") }
