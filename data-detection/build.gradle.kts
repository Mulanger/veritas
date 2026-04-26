plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.veritas.data.detection"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    implementation(project(":domain-detection"))
    implementation(project(":feature-detect-image"))

    implementation("com.github.contentauth:c2pa-android:0.0.9")

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
