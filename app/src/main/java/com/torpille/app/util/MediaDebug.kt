package com.torpille.app.util

object MediaDebug {
    const val TAG = "MEDIA_DEBUG"

    fun storageErrorMessage(
        action: String,
        errorCode: Int?,
        rawMessage: String?,
    ): String {
        val detail = when (errorCode) {
            -13010 -> "objet introuvable"
            -13020 -> "authentification Firebase manquante"
            -13021 -> "accès Firebase Storage refusé"
            -13030 -> "quota Firebase Storage dépassé"
            -13031 -> "checksum Firebase Storage invalide"
            -13040 -> "opération annulée"
            else -> rawMessage?.takeIf { it.isNotBlank() } ?: "erreur inconnue"
        }
        return "$action a échoué: $detail"
    }

    fun firestoreErrorMessage(
        action: String,
        codeName: String?,
        rawMessage: String?,
    ): String {
        val detail = when (codeName) {
            "PERMISSION_DENIED" -> "règles Firestore: accès refusé"
            "UNAUTHENTICATED" -> "utilisateur non authentifié"
            "NOT_FOUND" -> "document introuvable"
            else -> rawMessage?.takeIf { it.isNotBlank() } ?: "erreur inconnue"
        }
        return "$action a échoué: $detail"
    }

    fun functionsErrorMessage(
        action: String,
        codeName: String?,
        rawMessage: String?,
    ): String {
        val detail = when (codeName) {
            "UNAUTHENTICATED" -> "session expirée ou authentification invalide"
            "PERMISSION_DENIED" -> "la Cloud Function a refusé l'accès"
            "NOT_FOUND" -> "fichier introuvable côté backend"
            "INVALID_ARGUMENT" -> "paramètres invalides envoyés à la Cloud Function"
            else -> rawMessage?.takeIf { it.isNotBlank() } ?: "erreur inconnue"
        }
        return "$action a échoué: $detail"
    }
}
