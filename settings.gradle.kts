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
rootProject.name = "BossDrive"
include(":core", ":fonts", ":phone", ":radio")
include(":addons:trip", ":addons:fuel", ":addons:podcasts", ":addons:webradio", ":addons:service")
