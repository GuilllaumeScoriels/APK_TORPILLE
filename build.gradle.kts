/*
 * Rôle :
 * - Script Gradle racine : déclare les plugins (Android + Kotlin) et leurs versions
 *   pour l’ensemble du projet, sans les appliquer automatiquement à tous les modules.
 *
 * Composants :
 * - plugins { ... apply false } :
 *   - Déclare les versions, puis chaque module (ex : :app) décide de les appliquer.
 *
 * Pourquoi comme ça :
 * - Permet une gestion cohérente des versions (AGP/Kotlin/Google services),
 *   et évite des conflits. Ici, Kotlin a été aligné sur la compatibilité
 *   Compose Compiler utilisée par le module app.
 */
// Top-level build file
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false

    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false

    id("com.google.gms.google-services") version "4.4.4" apply false
}