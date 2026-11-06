/*
 * Rôle :
 * - Déclare le nom logique du projet Gradle (“Torpille”) et inclut le module Android “:app”.
 *
 * Composants :
 * - rootProject.name : le nom affiché côté Gradle/IDE.
 * - include(":app") : active le module Android principal.
 *
 * Pourquoi comme ça :
 * - Un projet Android standard est multi-modules. Ici on garde une structure simple :
 *   un module "app" qui contient l’APK et tout le code Jetpack Compose.
 */
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Torpille"
include(":app")
