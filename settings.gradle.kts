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
        // Guardian Project raw GitHub Maven (hosts info.guardianproject:arti-mobile-ex)
        maven { url = uri("https://raw.githubusercontent.com/guardianproject/gpmaven/master") }
        // Vosk offline speech recognition
        maven { url = uri("https://alphacephei.com/maven/") }
    }
}

rootProject.name = "disastermesh-android"
include(":app")
include(":disastermesh-ai")
// Using published Arti AAR; local module not included
