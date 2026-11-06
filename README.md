# Torpille Android

Application sociale Android en Kotlin et Jetpack Compose : communautés, messagerie multimédia, géolocalisation et classements.

Version iOS : [TORPILLE-iOS](https://github.com/GuilllaumeScoriels/TORPILLE-iOS).

## Construire

Utiliser Android Studio avec Java 17, Android SDK 35 et la distribution Gradle fournie :

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
```

L’APK debug se trouve dans `app/build/outputs/apk/debug/app-debug.apk`. L’APK release produit est **non signé** ; la distribution nécessite la signature de l’application et les paramètres Firebase/App Check correspondant à ce certificat.

## Configuration Maps

Maps SDK for Android doit être activé sur le projet Google Cloud. L’application attend également une clé API autorisée pour `com.torpille.app` et l’empreinte SHA-1 du certificat utilisé.

La clé peut être fournie dans `local.properties` (ignoré par Git) :

```properties
sdk.dir=/chemin/vers/Android/sdk
MAPS_API_KEY=REMPLACER_PAR_LA_CLE_ANDROID
```

Un `local.properties` ignoré par Git a été préparé pour cette session, avec le SDK temporaire de validation. La clé Maps fournie localement est intégrée aux APK reconstruits. Remplacer le chemin du SDK si nécessaire dans Android Studio.

La clé peut aussi être fournie par la variable d’environnement / propriété Gradle `MAPS_API_KEY`. Ne pas committer de clé privée ni de fichier de signature. L’activation du service seule ne renseigne pas la clé dans l’APK.

## Backend partagé avec iOS

Le fichier Android `google-services.json` cible le même projet et le même bucket que le fichier Firebase iOS fourni. Les collections et les noms de champs reprennent ceux d’iOS, ainsi que les fonctions appelables dans `europe-west1` : demandes d’adhésion, suppression de communauté, création de torpille vidéo et lecture des médias.

`firestore.rules`, `firestore.indexes.json`, `storage.rules` et `functions/` sont synchronisés depuis la référence iOS. **Aucun déploiement n’a été effectué pendant cette adaptation.** Le backend est partagé : un déploiement doit être coordonné avec iOS, pas effectué uniquement pour installer l’APK Android.

En debug, App Check utilise le fournisseur de développement ; son enregistrement Firebase peut être nécessaire avant les essais authentifiés. En release, l’application utilise Play Integrity.

## Parcours portés

- Accueil légal multilingue, acceptations EULA/confidentialité, connexion, inscription avec confirmation et vérification d’e-mail.
- Identité complète, pseudo normalisé unique, photo ou avatar, paramètres de confidentialité et langue.
- Communautés, invitations, demandes d’adhésion privées, administration et préférences de notifications.
- Discussions texte/photo/audio/vidéo, torpilles, réponses, XP, contenus enregistrés et publication Memories.
- Amis, demandes, blocage, masquage, signalements, profils publics et classements.
- Carte avec avatars, sélection de communauté, activité récente, mode fantôme et lancement d’une torpille.
- Palette, bannières, cartes, textes légaux et traductions repris de la référence iOS.

Consulter `PARITE_IOS.md` pour distinguer les changements réalisés des validations restant à effectuer. La compilation ne constitue pas une validation complète de parité visuelle ou des échanges entre appareils.


## Version Android 2.1.0 — 25 octobre 2026

Mise à jour compatible avec les sources iOS actuelles : enregistrement et
réglages des notifications, activité sans doublon de torpille, invitations
persistantes, localisation à la demande, traductions et téléchargement versionné.
Le package et le certificat historique sont conservés pour les mises à jour.

Téléchargement : https://torpille-android-38783.web.app/

Le bilan et ses limites sont dans `verification/ANDROID_2_1_0.md` ; résultats,
empreinte de l’APK et publication effective dans `verification/validation.json`.

## Configuration après clonage

Ajouter votre propre `app/google-services.json` depuis Firebase et configurer le SDK Android dans `local.properties`. Les clés Maps, fichiers de signature et mots de passe restent locaux et sont exclus du dépôt. Le backend utilise Firebase Authentication, Firestore, Storage et Cloud Functions.
