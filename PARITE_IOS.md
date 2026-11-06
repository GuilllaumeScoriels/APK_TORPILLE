# Suivi de parité iOS / Android

Référence : sources locales `TorpilleIOS_v56_2`, consultées pendant l’adaptation. Ce document décrit l’implémentation et ses limites ; il ne certifie pas une identité parfaite entre les plateformes.

## Version actuelle : Android 2.1.0 — 25 octobre 2026

Les dernières corrections iOS de notifications et de démarrage sont reprises,
les invitations persistent avant connexion et les sources Firebase sont à jour.
L’APK garde le certificat 2.0.9. Le détail des changements, validations et limites
figure dans `verification/ANDROID_2_1_0.md` ; les résultats effectifs sont dans
`verification/validation.json`. Les paragraphes suivants conservent les audits
des versions précédentes.

## Changements réalisés

### Profil — version Android 2.0.1

Le profil reprend l’ordre des sections iOS : bannière avec aperçu du pseudo et de la photo, avatar centré, choix de photo et grille d’emoji à quatre colonnes uniquement sans photo, informations obligatoires, informations facultatives, e-mail et langue, puis bouton illustré vert d’enregistrement. La date de naissance est facultative avec un interrupteur et un calendrier. Les acceptations déjà enregistrées ne sont plus redemandées dans le profil. Les utilisateurs bloqués, leur confirmation de déblocage, les boutons corail de déconnexion/suppression et l’enregistrement explicite de la visibilité ont été ajoutés. Les sélecteurs système et le rendu des polices restent natifs Android.

| Domaine | Adaptation Android |
| --- | --- |
| Authentification | EULA et confidentialité avant la connexion ; confirmation du mot de passe à l’inscription ; vérification de l’e-mail ; réinitialisation |
| Profil | Prénom, nom, pseudo unique normalisé, date, sexe, ville, bio, photo/avatar ; confidentialité et langue ; propagation vers les membres |
| Communautés | Liste et accès corrigés, création, adhésion via la fonction commune, demandes privées et modération par l’administrateur |
| Torpilles | Envoi via `createVideoTorpille`, champs Firebase communs, tag vidéo, réponse et mise à jour des XP |
| Messages / Memories | Texte, image, audio avec durée, vidéo, sauvegarde, publication des torpilles sauvegardées par leur auteur, retrait, filtres de visibilité |
| Social | Demandes d’amitié, acceptation/refus, retrait, blocage, masquage et signalement |
| Notifications | Préférences par communauté, événements sociaux, notifications locales par compte et tokens FCM multiples |
| Carte | Avatars et positions partagés, activité récente, mode fantôme, profil public puis choix de communauté pour torpiller |
| Présentation | Logo iOS, palette, bannières, cartes Memories, typographie et hiérarchie, six langues et textes EULA issus d’iOS |
| Backend | Fichiers serveur et règles copiés depuis iOS, aucun déploiement |

## Vérifications

Les tests unitaires couvrent l’identité obligatoire, le calcul des XP, la normalisation des pseudos, la rétention des contenus, la modération, le tri de l’accueil et les erreurs média. Les résultats de la dernière exécution sont dans `app/build/reports/tests/testDebugUnitTest/`.

Les builds debug et release ont réussi. Les 14 tests unitaires passent. Le lint ne contient plus d’erreur : il conserve 45 avertissements et 2 informations, notamment sur les dépendances, des analyseurs lint tiers et les icônes. La release n’est pas signée. Le rapport lint est disponible dans `app/build/reports/lint-results-debug.html`.

Un émulateur Android 15 a servi à vérifier le lancement, la lecture des règles, l’acceptation des documents, l’accès à la connexion et l’apparition de la confirmation de mot de passe au premier clic sur Créer un compte. Le journal de crash était vide après ce parcours. Les captures sont dans `verification/`. Aucun compte ni contenu de production n’a été créé pour ces essais.

## Validation restante avant de déclarer la parité complète

- La clé Maps a été renseignée dans `local.properties` et sa présence a été vérifiée dans les APK debug et release reconstruits. Le SDK est activé côté Cloud d’après la confirmation de l’utilisateur. Il reste à vérifier les restrictions package/certificat et l’affichage réel des tuiles dans une session authentifiée.
- Tester avec deux comptes autorisés, sur Android et iOS : profil, adhésion privée, ami, torpille/réponse, XP, sauvegarde/publication, blocage, signalement et suppression.
- Vérifier App Check et la réception des notifications sur un appareil Android physique, application ouverte puis fermée.
- Comparer les écrans authentifiés sur les deux plateformes et plusieurs tailles d’écran. Les icônes, polices, sélecteurs système et contrôles natifs Android peuvent encore différer d’iOS ; une identité au pixel près n’a pas été démontrée.
- Compléter les traductions des messages dynamiques et des erreurs : le catalogue iOS et les libellés statiques ont été intégrés, mais certaines chaînes assemblées restent en français.
- Tester caméra, microphone, permissions refusées, réseau interrompu et reprise des envois. La réponse à une torpille et l’envoi suivant sont deux opérations, comme dans la référence consultée ; une interruption entre les deux reste à tester.
- Configurer la signature release et les App Links du certificat de distribution avant publication.

## Conservation des sources

### Correctif 2.0.2 — communautés d’un compte existant

Android fusionne désormais `users/{uid}.communityIds` avec la recherche des adhésions, puis observe directement chaque communauté et son document `members/{uid}`. Une recherche globale refusée ne masque plus les communautés présentes dans le profil iOS. Le chargement est commun à l’accueil, à la carte, au classement et aux XP ; il redémarre lors d’un changement de compte. Aucun droit d’adhésion ni document de production n’est modifié par ce correctif. Si aucune source ne peut être lue, l’erreur est propagée à l’interface au lieu d’être silencieusement ignorée.

Cinq tests de régression couvrent la fusion, les profils iOS avec recherche globale indisponible, les anciennes adhésions, les comptes vides et les erreurs sans source de secours. Le parcours authentifié avec le compte Apple concerné reste à confirmer sur appareil.

Le projet iOS et l’ancien dossier Android du Bureau sont conservés. La version adaptée se trouve dans le dossier de travail indiqué dans le README. Les tests et les captures ne remplacent pas les validations croisées listées ci-dessus.
