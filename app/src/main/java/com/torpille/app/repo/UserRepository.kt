package com.torpille.app.repo

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.StorageMetadata
import com.torpille.app.TorpilleApp
import com.torpille.app.data.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("europe-west1"),
    private val notificationRepo: NotificationRepository = NotificationRepository(),
) {
    private fun users() = db.collection("users")
    private fun pseudos() = db.collection("pseudos")

    fun observeMe(): Flow<UserProfile?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val reg = users().document(uid).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.toObject(UserProfile::class.java)?.copy(uid = uid))
        }
        awaitClose { reg.remove() }
    }

    suspend fun getMeOrThrow(): UserProfile {
        val uid = auth.currentUser?.uid ?: error("Utilisateur non connecté")
        return users().document(uid).get().await().toObject(UserProfile::class.java)?.copy(uid = uid)
            ?: error("Profil introuvable")
    }

    suspend fun ensureFcmTokenStored() {
        com.torpille.app.notifications.PushRegistration.registerCurrentDevice()
    }

    private suspend fun downloadUrlWithRetry(
        refPath: String,
        attempts: Int = 5,
        firstDelayMs: Long = 200,
    ): String {
        val ref = storage.reference.child(refPath)
        var delayMs = firstDelayMs
        var lastError: Exception? = null

        repeat(attempts) {
            try {
                return ref.downloadUrl.await().toString()
            } catch (e: Exception) {
                lastError = e
                delay(delayMs)
                delayMs *= 2
            }
        }
        throw lastError ?: IllegalStateException("Failed to get downloadUrl for $refPath")
    }

    private fun normalizePseudoKey(pseudo: String): String = com.torpille.app.util.normalizePseudoKey(pseudo)

    private fun validatePseudo(pseudo: String) {
        val p = pseudo.trim()
        if (p.length < 3) error("Pseudo trop court (min 3 caractères)")
        if (p.length > 20) error("Pseudo trop long (max 20 caractères)")
        val ok = p.all { it.isLetterOrDigit() || it == '_' || it == '.' }
        if (!ok) error("Pseudo invalide : autorisés = lettres, chiffres, _ et .")
    }

    suspend fun uploadProfilePhoto(photoUri: Uri): String {
        val uid = auth.currentUser?.uid ?: error("Utilisateur non connecté")
        val timestamp = System.currentTimeMillis()
        val refPath = "profiles/$uid/profile_$timestamp"
        val ref = storage.reference.child(refPath)

        val contentType = TorpilleApp.appContext.contentResolver.getType(photoUri)
        val metadata = StorageMetadata.Builder()
            .setContentType(contentType ?: "image/jpeg")
            .build()

        ref.putFile(photoUri, metadata).await()
        return downloadUrlWithRetry(refPath)
    }

    suspend fun saveIdentity(profile: UserProfile, acceptsTerms: Boolean) {
        val uid = auth.currentUser?.uid ?: error("Utilisateur non connecté")
        val pseudo = profile.pseudo.trim()
        validatePseudo(pseudo)
        com.torpille.app.util.validateCommunityText(listOf(pseudo, profile.firstName, profile.lastName, profile.city.orEmpty(), profile.bio.orEmpty()).joinToString(" "))
        require(profile.firstName.trim().length in 2..50 && profile.lastName.trim().length in 2..50) {
            "Renseigne un prénom et un nom valides (2 à 50 caractères)."
        }
        require(profile.city.orEmpty().length <= 100 && profile.bio.orEmpty().length <= 300) {
            "La ville est limitée à 100 caractères et la présentation à 300 caractères."
        }
        require(profile.birthDate == null || profile.birthDate.toDate().time <= System.currentTimeMillis()) {
            "La date de naissance ne peut pas être dans le futur."
        }
        require(!profile.photoUrl.isNullOrBlank() || !profile.profileIcon.isNullOrBlank()) { "Choisis une photo ou une icône." }
        val key = normalizePseudoKey(pseudo)
        val userRef = users().document(uid)
        val now = com.google.firebase.Timestamp.now()
        db.runTransaction { tx ->
            val current = tx.get(userRef)
            val oldKey = current.getString("pseudoKey").orEmpty()
            val pseudoRef = pseudos().document(key)
            val owner = tx.get(pseudoRef)
            val oldRef = oldKey.takeIf { it.isNotBlank() && it != key }?.let { pseudos().document(it) }
            val oldOwner = oldRef?.let { tx.get(it) }
            check(!owner.exists() || owner.getString("uid") == uid) { "Ce pseudo est déjà utilisé." }
            val terms = current.getTimestamp("termsAcceptedAt") ?: now.takeIf { acceptsTerms }
            val rules = current.getTimestamp("moderationRulesAcceptedAt") ?: now.takeIf { acceptsTerms }
            check(terms != null && rules != null) { "Accepte les conditions et les règles de modération." }
            val payload = mapOf(
                "uid" to uid, "email" to auth.currentUser?.email, "languageCode" to com.torpille.app.util.AppLanguage.code,
                "firstName" to profile.firstName.trim(), "lastName" to profile.lastName.trim(),
                "pseudo" to pseudo, "pseudoKey" to key, "photoUrl" to profile.photoUrl,
                "profileIcon" to profile.profileIcon, "birthDate" to profile.birthDate,
                "sex" to profile.sex, "city" to profile.city.orEmpty().trim(), "bio" to profile.bio.orEmpty().trim(),
                "termsAcceptedAt" to terms, "moderationRulesAcceptedAt" to rules, "updatedAt" to now
            )
            if (!owner.exists()) tx.set(pseudoRef, mapOf("uid" to uid, "createdAt" to now))
            tx.set(userRef, payload, SetOptions.merge())
            if (oldRef != null && oldOwner?.getString("uid") == uid) tx.delete(oldRef)
            null
        }.await()
        val members = db.collectionGroup("members").whereEqualTo("uid", uid).get().await().documents
        members.chunked(400).forEach { chunk ->
            db.runBatch { batch -> chunk.forEach { member ->
                batch.set(member.reference, mapOf("pseudo" to pseudo, "photoUrl" to profile.photoUrl,
                    "profileIcon" to profile.profileIcon), SetOptions.merge())
            } }.await()
        }
        // Notification registration must not turn a successful profile save into an error.
        runCatching { ensureFcmTokenStored() }
    }

    suspend fun updateLanguage(code: String) {
        val uid = auth.currentUser?.uid ?: return
        users().document(uid).set(mapOf("languageCode" to code), SetOptions.merge()).await()
    }

    suspend fun updatePrivacy(languagePublic: Boolean, leaderboardPublic: Boolean) {
        val uid = auth.currentUser?.uid ?: error("Utilisateur non connecté")
        users().document(uid).set(mapOf("isLanguagePublic" to languagePublic,
            "isLeaderboardPublic" to leaderboardPublic, "updatedAt" to com.google.firebase.Timestamp.now()), SetOptions.merge()).await()
    }

    suspend fun deleteCurrentAccountServerSide() {
        functions
            .getHttpsCallable("deleteMyAccount")
            .call()
            .await()
    }

    suspend fun updatePinnedCommunities(communityIds: List<String>) {
        val uid = auth.currentUser?.uid ?: error("Utilisateur non connecté")
        val cleaned = communityIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)

        users().document(uid).set(
            mapOf(
                "pinnedCommunityIds" to cleaned,
                "updatedAt" to com.google.firebase.Timestamp.now()
            ),
            SetOptions.merge()
        ).await()
    }
}
