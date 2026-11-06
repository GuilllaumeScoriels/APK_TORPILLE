# Page d’invitation Torpille

Site principal : https://torpille-38783.web.app/join?cid=IDENTIFIANT

Android partage `/join?cid=…` ; iOS partage `/join.html?cid=…&lang=…`. Firebase Hosting conserve la redirection cleanUrls et les paramètres de requête. Le bouton d’ouverture utilise `torpille://join?cid=…`, reconnu par les deux applications.

- Téléchargement Android : https://torpille-android-38783.web.app/ (avec `cid` et `lang` pour revenir à l’invitation).
- App Store : https://apps.apple.com/fr/app/torpille/id6761060711, identifié dans le catalogue Apple par le bundle `com.torpille.ios` et le vendeur Guillaume L. M. Scoriels.

Les fichiers accueil, confidentialité et support ont été récupérés depuis la version Hosting en production et conservés à l’identique. Seule la page d’invitation est modifiée. Les ressources réservées `/__/firebase/` restent gérées par Firebase. Le fichier `pre-deploy-sha256.json` conserve les empreintes des pages avant cette modification.

La source `Hosting/public/join.html` dans le projet iOS a reçu les mêmes corrections pour éviter qu’un déploiement ultérieur rétablisse les anciens liens.

Déploiement depuis ce dossier :

```sh
firebase deploy --only hosting --project torpille-38783 --config firebase.json --non-interactive
```

Avant un futur déploiement depuis cette copie, vérifier que les autres pages n’ont pas changé en production depuis leur récupération.
