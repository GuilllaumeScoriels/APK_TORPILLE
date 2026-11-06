package com.torpille.app.util

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.StorageException
import java.io.IOException
import java.util.concurrent.CancellationException

/** UI boundary: raw SDK messages, details and stack traces never reach Compose. */
object UserFacingError {
    const val FALLBACK = "Cette action n’a pas pu aboutir. Réessaie dans quelques instants."
    const val UNAVAILABLE = "Ce service est momentanément indisponible. Réessaie plus tard."
    const val NETWORK = "Connexion indisponible. Vérifie ta connexion Internet puis réessaie."

    fun message(error: Throwable): String = map(error, 0)

    private fun map(error: Throwable, depth: Int): String {
        if (error is CancellationException) throw error
        // Known local validation and reviewed server business messages only.
        error.message?.let { if (it in approvedMessages) return it }
        return when (error) {
            is FirebaseAuthException -> when (error.errorCode) {
                "ERROR_INVALID_EMAIL" -> "Renseigne une adresse e-mail valide."
                "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "Email ou mot de passe incorrect."
                "ERROR_USER_NOT_FOUND" -> "Aucun compte ne correspond à cet email."
                "ERROR_EMAIL_ALREADY_IN_USE" -> "Cette adresse email est déjà utilisée. Connecte-toi ou réinitialise ton mot de passe."
                "ERROR_WEAK_PASSWORD" -> "Choisis un mot de passe d’au moins 6 caractères."
                "ERROR_REQUIRES_RECENT_LOGIN" -> "Reconnecte-toi pour effectuer cette action."
                "ERROR_USER_DISABLED" -> "Ce compte est désactivé. Contacte l’assistance."
                "ERROR_INVALID_PHONE_NUMBER" -> "Indique un numéro de téléphone valide avec son indicatif international."
                "ERROR_INVALID_VERIFICATION_CODE" -> "Le code SMS est incorrect. Vérifie-le puis réessaie."
                "ERROR_SESSION_EXPIRED" -> "Le code SMS a expiré. Demande un nouveau code."
                "ERROR_CREDENTIAL_ALREADY_IN_USE" -> "Ce numéro est déjà associé à un autre compte."
                else -> UNAVAILABLE
            }
            is FirebaseFirestoreException -> when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Cette action n’est pas autorisée avec ce compte."
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Reconnecte-toi pour continuer."
                FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> NETWORK
                else -> FALLBACK
            }
            is FirebaseFunctionsException -> when (error.code) {
                FirebaseFunctionsException.Code.INVALID_ARGUMENT -> "Vérifie les informations saisies puis réessaie."
                FirebaseFunctionsException.Code.PERMISSION_DENIED -> "Tu ne peux pas effectuer cette action avec ce compte."
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Reconnecte-toi pour continuer."
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> "La limite a été atteinte. Réessaie plus tard."
                FirebaseFunctionsException.Code.NOT_FOUND, FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.INTERNAL, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                FirebaseFunctionsException.Code.UNIMPLEMENTED -> UNAVAILABLE
                else -> FALLBACK
            }
            is StorageException -> when (error.errorCode) {
                StorageException.ERROR_OBJECT_NOT_FOUND -> "Ce contenu n’est plus disponible."
                StorageException.ERROR_NOT_AUTHENTICATED -> "Reconnecte-toi pour envoyer ce contenu."
                StorageException.ERROR_NOT_AUTHORIZED -> "Tu n’as pas accès à ce contenu."
                StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> "Le transfert a pris trop de temps. Vérifie ta connexion puis réessaie."
                else -> "Impossible de transférer ce contenu pour le moment. Réessaie."
            }
            is FirebaseNetworkException, is IOException -> NETWORK
            is FirebaseTooManyRequestsException -> "Trop de tentatives. Patiente avant de réessayer."
            else -> if (depth < 5 && error.cause != null && error.cause !== error) map(error.cause!!, depth + 1) else FALLBACK
        }
    }

    private val approvedMessages = setOf(
        "Accepte les conditions et les règles de modération.",
        "Ajoute au moins un créneau ou une période.",
        "Attends la fin de l’envoi de la vidéo.",
        "Ce contenu ne respecte pas les règles de la communauté.",
        "Ce membre fait déjà partie de tes amis.",
        "Ce pseudo est déjà utilisé.",
        "Cette torpille appartient à une ancienne adhésion.",
        "Cette torpille est déjà publiée.",
        "Cette torpille ne peut plus recevoir de réponse.",
        "Choisis une photo ou une icône.",
        "Code d’invitation indisponible.",
        "Code d’invitation introuvable.",
        "Communauté introuvable",
        "Complète ton profil avant de rejoindre une communauté.",
        "Connecte-toi à nouveau.",
        "Contenu introuvable",
        "Créneau hebdomadaire invalide.",
        "Demande invalide",
        "Donne un nom à ta communauté.",
        "Durée invalide",
        "Délai dépassé : tu vas être expulsé de cette communauté.",
        "Enregistre cette torpille avant de la publier.",
        "Fuseau horaire invalide.",
        "Impossible de mémoriser l’acceptation sur cet appareil.",
        "Impossible d’ouvrir la vérification.",
        "Impossible d’écrire dans ce dossier.",
        "Indique ton numéro avec son indicatif international.",
        "La date de naissance ne peut pas être dans le futur.",
        "La durée doit être positive.",
        "La ville est limitée à 100 caractères et la présentation à 300 caractères.",
        "Les torpilles sont fermées à cet horaire. Tu peux seulement répondre à une torpille reçue.",
        "Limite quotidienne atteinte. Réessaie demain.",
        "Lis et accepte les règles avant de créer ton compte.",
        "L’ancienne demande doit être supprimée par son expéditeur.",
        "Média indisponible.",
        "Média vide.",
        "Paramètres invalides : délai de 1 seconde à 7 jours.",
        "Profil introuvable",
        "Pseudo invalide : autorisés = lettres, chiffres, _ et .",
        "Pseudo trop court (min 3 caractères)",
        "Pseudo trop long (max 20 caractères)",
        "Période datée invalide.",
        "Renseigne ton adresse e-mail.",
        "Renseigne un prénom et un nom valides (2 à 50 caractères).",
        "Renseigne une adresse e-mail valide.",
        "Réponds à ta torpille en cours avant d’en envoyer une autre.",
        "Seul l’administrateur peut modifier les paramètres.",
        "Seul l’auteur peut retirer cette torpille.",
        "Seules les torpilles enregistrées peuvent être publiées.",
        "Ton carnet contient plus de 2 000 numéros. Utilise la recherche par numéro.",
        "Tu es le dernier membre : supprime la communauté depuis ses paramètres.",
        "Tu peux publier uniquement tes propres torpilles.",
        "Téléchargement impossible. Réessaie.",
        "Une demande d’ami est déjà en attente. Consulte l’onglet Demandes.",
        "Utilisateur non connecté",
        "Utilisateur non connecté. Reconnecte-toi puis réessaie.",
        "Vidéo introuvable",
        "Vérifie ton numéro par SMS avant de devenir visible.",
        "Vérifie ton numéro par SMS avant de rechercher des contacts."
    )
}

fun userErrorMessage(error: Throwable): String = UserFacingError.message(error)

fun logDiagnostic(error: Throwable) {
    if (error is CancellationException) throw error
    if (com.torpille.app.BuildConfig.DEBUG) android.util.Log.d("TorpilleDiagnostic", "Background operation failed", error)
}
