import java.util.Properties

/*
 * Rôle :
 * - Configuration du module Android “app” : SDK, packaging, Jetpack Compose,
 *   dépendances Firebase, et plugin google-services.
 *
 * Composants clés :
 * - android { ... } :
 *   - namespace / applicationId : identifiant unique Android (doit matcher Firebase).
 *   - compileSdk / minSdk / targetSdk : compatibilité Android.
 *   - buildFeatures.compose = true : active Jetpack Compose.
 *
 * - dependencies :
 *   - Firebase Auth : connexion / création de compte.
 *   - Firestore : base de données (communautés, messages, membres…).
 *   - Storage : upload des vidéos & photos de profil.
 *   - Functions : appeler la fonction “sendTorpilleNotification”.
 *   - Messaging : FCM pour recevoir les notifications “Torpille !”.
 *
 * Pourquoi comme ça :
 * - Compose est utilisé pour l’UI (écrans + navigation).
 * - Firebase sert de backend “serverless” : stockage + données + notifications.
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

val localConfiguration = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

val playSigning = Properties().apply {
    rootProject.file("signing.properties").inputStream().use { load(it) }
}

android {
    namespace = "com.torpille.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.torpille.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "2.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["MAPS_API_KEY"] =
            providers.gradleProperty("MAPS_API_KEY").orNull
                ?: providers.environmentVariable("MAPS_API_KEY").orNull
                ?: localConfiguration.getProperty("MAPS_API_KEY", "")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("playRelease") {
            storeFile = file(playSigning.getProperty("storeFile"))
            storePassword = playSigning.getProperty("storePassword")
            keyAlias = playSigning.getProperty("keyAlias")
            keyPassword = playSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("playRelease")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    // Material Icons (for message actions / video icon)
    implementation("androidx.compose.material:material-icons-extended:1.7.6")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation(platform("com.google.firebase:firebase-bom:34.10.0"))
    implementation("com.google.firebase:firebase-analytics")

    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-functions")

    implementation("androidx.core:core:1.15.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("io.coil-kt:coil-compose:2.7.0")

    // Video playback in Compose (messages)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.7.6")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.6")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.6")

    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.maps.android:maps-compose:6.1.2")
}
