# Téléchargement Android

Page : https://torpille-android-38783.web.app/

Version 2.1.0 (code 12), Android 8.0 minimum. APK signé avec le certificat de test
historique, compatible en mise à jour avec 2.0.9. Cette distribution directe ne
constitue pas une publication Google Play.

- `public/downloads/Torpille-Android-2.1.0.apk` : APK versionné.
- `public/downloads/Torpille-Android-2.0-test.apk` : alias maintenu pour les anciens liens.
- `public/release.json` : version, taille et SHA-256.
- `public/downloads/SHA256.txt` : empreintes vérifiables.

Le lien d’invitation transmet `cid` et `lang`. Après installation, revenir à
l’invitation ou toucher le lien d’ouverture de l’app sur la page Android.

Pour une prochaine version : augmenter le code et le nom dans
`app/build.gradle.kts`, compiler et tester, vérifier le même certificat,
remplacer les APK et métadonnées, puis déployer uniquement le site Android avec
`firebase deploy --only hosting --project torpille-38783 --config firebase.json`
depuis ce dossier. Ne pas publier les fonctions/règles via cette configuration.

La publication 2.1.0 utilise les versions Hosting existantes et remplace
seulement les fichiers explicitement concernés ; le domaine principal reçoit
uniquement l’association Android App Links.
