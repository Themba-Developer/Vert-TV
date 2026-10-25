plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.verttv.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.verttv.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"
        buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("VERT_API_URL").getOrElse("https://yourdomain.com")}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
