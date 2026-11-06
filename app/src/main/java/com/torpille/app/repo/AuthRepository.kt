/**
 * Rôle :
 * - Accès unifié à Firebase Authentication (email/mot de passe) et état de session.
 *
 * Composants :
 * - authState(): Flow<String?> :
 *   - observe les changements de connexion (login/logout) via AuthStateListener.
 * - signUp/signIn : wrappers suspend (await) pour des appels Firebase asynchrones.
 * - signOut : déconnexion.
 *
 * Pourquoi comme ça :
 * - Un repository découple l’UI (Compose) du SDK Firebase.
 * - Permet de tester/adapter plus facilement (ex : autre provider Auth).
 */
package com.torpille.app.repo

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface AuthenticationActions {
    suspend fun signIn(email: String, password: String)
    suspend fun signUp(email: String, password: String, acceptsRules: Boolean)
    suspend fun sendPasswordResetEmail(email: String)
}

interface EmailVerificationActions {
    val currentEmail: String
    suspend fun refreshVerification(): Boolean
    suspend fun resendVerification()
}

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AuthenticationActions, EmailVerificationActions {
    val currentUid: String? get() = auth.currentUser?.uid

    fun authState(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { fa ->
            trySend(fa.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.uid)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signUp(email: String, password: String, acceptsRules: Boolean) {
        require(acceptsRules) { "Lis et accepte les règles avant de créer ton compte." }
        require(password.length >= 6) { "Choisis un mot de passe d’au moins 6 caractères." }
        try {
            auth.createUserWithEmailAndPassword(email, password).await()
        } catch (collision: FirebaseAuthUserCollisionException) {
            // A retry after a failed email delivery must authenticate the same account.
            auth.signInWithEmailAndPassword(email, password).await()
        }
        try {
            val user = checkNotNull(auth.currentUser) { "Connecte-toi à nouveau." }
            com.torpille.app.util.DeviceLegalAcceptance.record(user.uid)
            if (!user.isEmailVerified) resendVerification()
        } catch (error: Exception) {
            auth.signOut()
            throw error
        }
    }

    override suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    override val currentEmail: String get() = auth.currentUser?.email.orEmpty()

    override suspend fun refreshVerification(): Boolean {
        val user = checkNotNull(auth.currentUser) { "Connecte-toi à nouveau." }
        user.reload().await()
        user.getIdToken(true).await()
        return user.isEmailVerified
    }

    override suspend fun resendVerification() {
        val user = checkNotNull(auth.currentUser) { "Connecte-toi à nouveau." }
        auth.setLanguageCode(com.torpille.app.util.AppLanguage.code)
        user.sendEmailVerification().await()
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        val cleanEmail = email.trim()
        require(cleanEmail.isNotEmpty()) { "Renseigne ton adresse e-mail." }
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) { "Renseigne une adresse e-mail valide." }
        auth.setLanguageCode(com.torpille.app.util.AppLanguage.code)
        auth.sendPasswordResetEmail(cleanEmail).await()
    }

    suspend fun signOut() {
        val uid = auth.currentUser?.uid
        try {
            if (uid != null) {
                val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                val ref = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid)
                val snapshot = ref.get().await()
                if (snapshot.exists()) {
                    val payload = mutableMapOf<String, Any>("fcmTokens" to com.google.firebase.firestore.FieldValue.arrayRemove(token))
                    if (snapshot.getString("fcmToken") == token) payload["fcmToken"] = com.google.firebase.firestore.FieldValue.delete()
                    ref.update(payload).await()
                }
            }
        } catch (_: Exception) {
            // A failed token cleanup must never trap the user in a signed-in session.
        } finally { auth.signOut(); com.torpille.app.notifications.PushRegistration.reset() }
    }
}
