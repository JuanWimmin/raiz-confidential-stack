// Sobre del Barrio — Session 1 spike harness (NOT the final wallet).
// Minimal single-module Android project: one Activity + full-screen WebView
// pointed at the CT demo dev server, to time in-WebView proving (GO/NO-GO gate).
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

rootProject.name = "sobre-wallet-spike"
include(":app")
