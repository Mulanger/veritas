import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.roborazzi) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        ignoreFailures = false
        parallel = true
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "17"
    }
}

tasks.register("precommitCheck") {
    group = "verification"
    description = "Runs ktlint and detekt across all modules."
    dependsOn(
        ":app:ktlintCheck",
        ":core-common:ktlintCheck",
        ":core-design:ktlintCheck",
        ":data-detection:ktlintCheck",
        ":domain-detection:ktlintCheck",
        ":feature-history:ktlintCheck",
        ":feature-home:ktlintCheck",
        ":feature-onboarding:ktlintCheck",
        ":feature-settings:ktlintCheck",
        ":app:detekt",
        ":core-common:detekt",
        ":core-design:detekt",
        ":core-design:verifyRoborazziDebug",
        ":data-detection:detekt",
        ":domain-detection:detekt",
        ":feature-history:detekt",
        ":feature-home:detekt",
        ":feature-onboarding:detekt",
        ":feature-settings:detekt",
    )
}

tasks.register("test") {
    group = "verification"
    description = "Runs all unit tests for Phase 0 modules."
    dependsOn(
        ":app:testDebugUnitTest",
        ":core-common:test",
        ":core-design:testDebugUnitTest",
        ":data-detection:testDebugUnitTest",
        ":domain-detection:test",
        ":feature-history:testDebugUnitTest",
        ":feature-home:testDebugUnitTest",
        ":feature-onboarding:testDebugUnitTest",
        ":feature-settings:testDebugUnitTest",
    )
}
