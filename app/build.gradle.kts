plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.verttv.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.verttv.app"
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("armeabi-v7a","arm64-v8a","x86","x86_64") }
        targetSdk = 35
        versionCode = 12
        versionName = "2.1.0"
        buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("VERT_API_URL").getOrElse("https://yourdomain.com")}\"")
    }
    ndkVersion = "28.2.13676358"
    val releaseKey = providers.environmentVariable("VERT_KEYSTORE").orNull
    signingConfigs {
        if(releaseKey!=null)create("release") {
            storeFile = file(releaseKey)
            storePassword = providers.environmentVariable("VERT_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("VERT_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("VERT_KEY_PASSWORD").get()
        }
    }
    buildTypes { getByName("release") { isMinifyEnabled=false;if(releaseKey!=null)signingConfig=signingConfigs.getByName("release") } }
    packaging { jniLibs { useLegacyPackaging=false } }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
val buildAdblockNative by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("bash","scripts/build-native.sh")
    inputs.dir(rootProject.file("native/adblock_bridge/src"))
    inputs.file(rootProject.file("native/adblock_bridge/Cargo.toml"))
    inputs.file(rootProject.file("native/adblock_bridge/Cargo.lock"))
    inputs.file(rootProject.file("scripts/build-native.sh"))
    inputs.file(rootProject.file("scripts/rust-notices.py"))
    outputs.dir(layout.projectDirectory.dir("src/main/jniLibs"))
    outputs.file(layout.projectDirectory.file("src/main/assets/rust-dependency-licenses.txt"))
}
tasks.named("preBuild") { dependsOn(buildAdblockNative) }
tasks.matching { it.name=="packageRelease" }.configureEach { doFirst { check(System.getenv("VERT_KEYSTORE")!=null) { "Release signing is required; set VERT_KEYSTORE and signing credentials." } } }
dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("junit:junit:4.13.2")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
