plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.veritas.feature.detect.image"
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
    implementation(project(":data-detection-ml"))
    implementation(project(":domain-detection"))

    implementation(libs.androidx.exifinterface)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
