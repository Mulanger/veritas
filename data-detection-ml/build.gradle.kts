plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.veritas.data.detection.ml"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(project(":domain-detection"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.play.services.tflite.gpu)
    implementation(libs.play.services.tflite.java)
    implementation(libs.play.services.tflite.support)

    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
