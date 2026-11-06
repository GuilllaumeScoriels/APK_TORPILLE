package com.torpille.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaDebugTest {

    @Test
    fun storagePermissionDeniedMessage_isPrecise() {
        val message = MediaDebug.storageErrorMessage(
            action = "Upload du vocal",
            errorCode = -13021,
            rawMessage = "User does not have permission",
        )

        assertEquals("Upload du vocal a échoué: accès Firebase Storage refusé", message)
    }

    @Test
    fun firestorePermissionDeniedMessage_isPrecise() {
        val message = MediaDebug.firestoreErrorMessage(
            action = "Création du message audio dans Firestore",
            codeName = "PERMISSION_DENIED",
            rawMessage = "Missing or insufficient permissions.",
        )

        assertEquals(
            "Création du message audio dans Firestore a échoué: règles Firestore: accès refusé",
            message,
        )
    }

    @Test
    fun functionsInvalidArgumentMessage_isPrecise() {
        val message = MediaDebug.functionsErrorMessage(
            action = "Récupération de l'URL signée",
            codeName = "INVALID_ARGUMENT",
            rawMessage = "bad payload",
        )

        assertEquals(
            "Récupération de l'URL signée a échoué: paramètres invalides envoyés à la Cloud Function",
            message,
        )
    }
}
