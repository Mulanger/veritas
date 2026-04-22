plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.veritas.feature.home"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    buildFeatures {
        compose = true
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
    implementation(project(":core-design"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
