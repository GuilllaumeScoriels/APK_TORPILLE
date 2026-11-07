# TORPILLE · Android

A social application built with **Kotlin and Jetpack Compose**, featuring communities, multimedia messaging, location sharing, and leaderboards.

[iOS version](https://github.com/GuilllaumeScoriels/TORPILLE-iOS) · [Android download](https://torpille-android-38783.web.app/)

## Features

- Sign up, sign in, and manage your profile.
- Create communities, share invitations, and manage membership requests.
- Exchange text, photos, audio, and video; send and reply to torpilles.
- Manage friends, blocking, reports, and privacy settings.
- View members on a map, use ghost mode, and share location on demand.
- Notifications, leaderboards, XP, and a multilingual interface.

## Technologies and structure

**Kotlin · Jetpack Compose · Firebase Authentication · Firestore · Storage · Cloud Functions · Google Maps**

| Directory or file | Purpose |
| --- | --- |
| `app/` | Android application |
| `functions/` | Cloud Functions |
| `invitation-hosting/` | Invitation hosting |
| `android-download/` | Android download page |
| `firestore.rules`, `firestore.indexes.json` | Firestore rules and indexes |
| `storage.rules` | Firebase Storage rules |

## Getting started

Requirements: Android Studio, Java 17, and Android SDK 35. Use the Gradle wrapper included in this repository.

1. Clone the repository.
2. Add your Firebase configuration to `app/google-services.json`.
3. Configure the SDK and Maps key in `local.properties`, which is ignored by Git:

```properties
sdk.dir=/path/to/Android/sdk
MAPS_API_KEY=YOUR_ANDROID_MAPS_KEY
```

Enable Maps SDK for Android and restrict the key to the `com.torpille.app` package and the SHA-1 fingerprint of the signing certificate. You can also provide the key through the `MAPS_API_KEY` environment variable or Gradle property. Keep signing files, passwords, and local configuration out of version control.

## Build and checks

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Release distribution requires signing and Firebase/App Check configuration matching the certificate.

## Backend shared with iOS

Firebase data models, rules, and functions must remain compatible across both applications. Callable functions use `europe-west1`. Coordinate all rule, index, and function deployments with the iOS version.

Debug builds use the App Check debug provider, which may require registration in Firebase. Release builds use Play Integrity.

## Status and validation

Documented version: **2.1.0 — October 25, 2026**. A successful build alone does not validate visual parity or cross-device interactions. See [PARITE_IOS.md](PARITE_IOS.md) for compatibility tracking. Release notes also reference `verification/ANDROID_2_1_0.md` and `verification/validation.json`; check their availability in the version you are viewing.
