pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = uri("https://maven.agora.io/repository")) {
            // Restrict Agora repo to only resolve Agora artifacts, avoid AndroidX lookups here
            content {
                includeGroupByRegex("io\\.agora(\\..*)?")
            }
        }
    }
}

rootProject.name = "SMD-A1"
include(":app")
 