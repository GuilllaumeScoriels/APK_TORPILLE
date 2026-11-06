# Logcat audio debug

Filtre conseillé dans Logcat :

`tag:MEDIA_DEBUG OR tag:VIDEO_DEBUG`

Cas attendus :

- Upload Storage OK puis Firestore KO :
  - `upload success ...`
  - puis `Création du message audio dans Firestore a échoué: règles Firestore: accès refusé`
- Upload Storage KO :
  - `Upload du vocal a échoué: ...`
- Lecture audio KO côté Storage :
  - `Lecture Firebase Storage (communities/.../audios/...) a échoué: ...`
- Fallback Cloud Function KO :
  - `Récupération de l'URL signée (...) a échoué: ...`
- Snapshot Firestore KO dans le fil :
  - `Lecture du fil de discussion (...) a échoué: règles Firestore: accès refusé`

Le correctif appliqué tente désormais d'ouvrir les vocaux directement via Firebase Storage avant de retomber sur l'URL signée backend. Cela évite qu'un problème côté Cloud Function bloque l'affichage/lecture des audios.
