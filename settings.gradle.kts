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
        // Morphe Patcher 2.3.0, vendored so a build needs no GitHub Packages token.
        // See libs/m2 and NOTICE.md for provenance and licensing.
        maven { url = uri("libs/m2") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "TuneFy"
include(":app")
