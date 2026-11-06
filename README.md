# TORPILLE · Android

Application sociale Android en **Kotlin et Jetpack Compose** : communautés, messagerie multimédia, géolocalisation et classements.

[Version iOS](https://github.com/GuilllaumeScoriels/TORPILLE-iOS) · [Téléchargement Android](https://torpille-android-38783.web.app/)

## Fonctionnalités

- Inscription, connexion et gestion du profil.
- Communautés, invitations et demandes d’adhésion.
- Discussions texte, photo, audio et vidéo ; envoi et réponse aux torpilles.
- Amis, blocage, signalements et paramètres de confidentialité.
- Carte des membres, mode fantôme et localisation à la demande.
- Notifications, classements, XP et interface multilingue.

## Technologies et structure

**Kotlin · Jetpack Compose · Firebase Authentication · Firestore · Storage · Cloud Functions · Google Maps**

| Dossier ou fichier | Rôle |
| --- | --- |
| `app/` | Application Android |
| `functions/` | Cloud Functions |
| `invitation-hosting/` | Hébergement des invitations |
| `android-download/` | Page de téléchargement Android |
| `firestore.rules`, `firestore.indexes.json` | Règles et index Firestore |
| `storage.rules` | Règles Firebase Storage |

## Installation

Prérequis : Android Studio, Java 17 et Android SDK 35. Utiliser le wrapper Gradle fourni.

1. Cloner le dépôt.
2. Ajouter votre configuration Firebase dans `app/google-services.json`.
3. Configurer le SDK et la clé Maps dans `local.properties`, ignoré par Git :

```properties
sdk.dir=/chemin/vers/Android/sdk
MAPS_API_KEY=VOTRE_CLE_MAPS_ANDROID
```

Activer Maps SDK for Android et restreindre la clé au package `com.torpille.app` et à l’empreinte SHA-1 du certificat utilisé. La clé peut aussi être fournie par la variable d’environnement ou propriété Gradle `MAPS_API_KEY`. Les fichiers de signature, mots de passe et configurations locales restent hors du dépôt.

## Compilation et vérifications

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
```

L’APK debug est généré dans `app/build/outputs/apk/debug/app-debug.apk`. La distribution release nécessite la signature et une configuration Firebase/App Check correspondant au certificat.

## Backend partagé avec iOS

Les modèles de données, règles et fonctions Firebase doivent rester compatibles entre les deux applications. Les fonctions appelables utilisent `europe-west1`. Coordonner tout déploiement des règles, index et fonctions avec la version iOS.

En debug, App Check utilise le fournisseur de développement ; son enregistrement Firebase peut être nécessaire. En release, l’application utilise Play Integrity.

## État et validation

Version documentée : **2.1.0 — 25 octobre 2026**. La compilation seule ne valide pas la parité visuelle ni les échanges entre appareils. Consulter [PARITE_IOS.md](PARITE_IOS.md) pour le suivi de compatibilité. Les bilans référencés par les notes de version sont `verification/ANDROID_2_1_0.md` et `verification/validation.json` ; vérifier leur présence dans la version consultée.
