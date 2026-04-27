pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Veritas"

include(
    ":app",
    ":core-common",
    ":core-design",
    ":data-detection",
    ":data-detection-ml",
    ":domain-detection",
    ":feature-detect-audio",
    ":feature-detect-image",
    ":feature-detect-video",
    ":feature-home",
    ":feature-history",
    ":feature-onboarding",
    ":feature-settings",
)
