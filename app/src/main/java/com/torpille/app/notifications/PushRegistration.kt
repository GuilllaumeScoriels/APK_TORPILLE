package com.torpille.app.notifications

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.torpille.app.util.UserFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

data class PushRegistrationState(val registered: Boolean = false, val registering: Boolean = false, val error: String? = null)

/** One registration path for login, foreground refresh and FCM token rotation. */
object PushRegistration {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    fun onTokenRefreshed(token: String) { backgroundScope.launch { registerCurrentDevice(token) } }
    private val mutableState = MutableStateFlow(PushRegistrationState())
    val state = mutableState.asStateFlow()

    suspend fun registerCurrentDevice(refreshedToken: String? = null) = mutex.withLock {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: run { reset(); return@withLock }
        mutableState.value = PushRegistrationState(registering = true)
        try {
            val token = refreshedToken ?: FirebaseMessaging.getInstance().token.await()
            check(auth.currentUser?.uid == uid) { "Le compte connecté a changé." }
            val db = FirebaseFirestore.getInstance()
            val ref = db.collection("users").document(uid)
            db.runTransaction { tx ->
                val snapshot = tx.get(ref)
                check(snapshot.exists()) { "Complète ton profil pour activer les notifications." }
                val existing = (snapshot.get("fcmTokens") as? List<*>)?.filterIsInstance<String>().orEmpty()
                val tokens = PushTokens.refreshed(existing, snapshot.getString("fcmToken"), token)
                tx.set(ref, mapOf("uid" to uid, "fcmToken" to token.trim(), "fcmTokens" to tokens,
                    "updatedAt" to Timestamp.now()), SetOptions.merge())
            }.await()
            if (auth.currentUser?.uid == uid) mutableState.value = PushRegistrationState(registered = true)
        } catch (cancelled: CancellationException) {
            mutableState.value = PushRegistrationState()
            throw cancelled
        } catch (error: Exception) {
            if (auth.currentUser?.uid == uid) mutableState.value = PushRegistrationState(error = UserFacingError.message(error))
            android.util.Log.w("TorpillePush", "Device registration failed: ${error.javaClass.simpleName}")
        }
    }

    fun reset() { mutableState.value = PushRegistrationState() }
}
