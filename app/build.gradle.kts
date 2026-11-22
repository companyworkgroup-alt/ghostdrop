plugins { id("com.android.application") }
android {
    namespace = "com.ghostdrop.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ghostdrop.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2-wifi-fix"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("com.google.android.gms:play-services-nearby:19.5.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
