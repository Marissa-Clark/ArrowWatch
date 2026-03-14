plugins {
    alias(libs.plugins.android.library)
    // No kotlin-compose: shared has no Compose UI. AGP 9 built-in Kotlin handles compilation.
}

android {
    namespace = "com.archery.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
}
