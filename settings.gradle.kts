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
    }
}

rootProject.name = "Veritas"

include(
    ":app",
    ":core-common",
    ":core-design",
    ":data-detection",
    ":domain-detection",
    ":feature-home",
)

