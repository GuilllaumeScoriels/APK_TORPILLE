package com.torpille.app.repo

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.functions.FirebaseFunctions
import com.torpille.app.data.*
import com.torpille.app.util.normalizePseudoKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

/** Uses the same collections and document identities as the iOS repository. */
class SocialRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val functions = FirebaseFunctions.getInstance("europe-west1")
    private val uid get() = auth.currentUser?.uid ?: error("Utilisateur non connecté")
    private fun user(id: String = uid) = db.collection("users").document(id)

    fun observe(query: Query): Flow<List<DocumentSnapshot>> = callbackFlow {
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.documents.orEmpty())
        }
        awaitClose { listener.remove() }
    }
    fun friends() = observe(user().collection("friends").orderBy("pseudo"))
    fun requests() = observe(db.collection("friendRequests").whereEqualTo("toUid", uid).whereEqualTo("status", "pending"))
    fun hidden(communityId: String) = observe(user().collection("contentFilters").document(communityId).collection("hiddenMembers"))
    suspend fun unhide(communityId: String, memberUid: String) { user().collection("contentFilters").document(communityId).collection("hiddenMembers").document(memberUid).delete().await() }
    fun blocked() = observe(user().collection("blockedUsers"))
    fun joinRequests(communityId: String) = observe(db.collection("communityJoinRequests").whereEqualTo("communityId", communityId).whereEqualTo("status", "pending"))
    fun excluded(communityId: String? = null): Flow<Set<String>> {
        val streams = mutableListOf(observe(user().collection("blockedUsers")), observe(user().collection("blockedBy")))
        if (communityId != null) streams += observe(user().collection("contentFilters").document(communityId).collection("hiddenMembers"))
        return combine(streams) { lists -> lists.flatMap { list -> list.map { it.id } }.toSet() }
    }
    suspend fun search(query: String): List<UserProfile> {
        val key = normalizePseudoKey(query)
        if (key.length < 2) return emptyList()
        return user().parent.orderBy("pseudoKey").startAt(key).endAt(key + "\uf8ff").limit(20).get().await().documents
            .mapNotNull { it.toObject(UserProfile::class.java)?.copy(uid = it.id) }.filter { it.uid != uid }
    }
    suspend fun requestFriend(profile: UserProfile) {
        require(profile.uid != uid)
        check(!user().collection("friends").document(profile.uid).get().await().exists()) { "Ce membre fait déjà partie de tes amis." }
        val me = UserRepository().getMeOrThrow()
        val ref = db.collection("friendRequests").document(listOf(uid, profile.uid).sorted().joinToString("_"))
        // Rules authorize reads by participant fields; a direct read of a missing
        // request cannot prove participation and is denied on a first invitation.
        val outgoing = db.collection("friendRequests").whereEqualTo("fromUid", uid).get().await()
        val incoming = db.collection("friendRequests").whereEqualTo("toUid", uid).get().await()
        val existing = (outgoing.documents + incoming.documents).firstOrNull { it.id == ref.id }
        if (existing != null) {
            check(existing.getString("status") != "pending") { "Une demande d’ami est déjà en attente. Consulte l’onglet Demandes." }
            check(existing.getString("fromUid") == uid) { "L’ancienne demande doit être supprimée par son expéditeur." }
            ref.delete().await()
        }
        ref.set(mapOf("fromUid" to uid, "fromPseudo" to me.pseudo, "fromPhotoUrl" to me.photoUrl.orEmpty(),
            "fromProfileIcon" to me.profileIcon.orEmpty(), "toUid" to profile.uid, "toPseudo" to profile.pseudo,
            "status" to "pending", "createdAt" to Timestamp.now(), "updatedAt" to Timestamp.now())).await()
    }
    suspend fun resolveFriend(request: DocumentSnapshot, accept: Boolean) {
        check(request.getString("toUid") == uid)
        val me = UserRepository().getMeOrThrow()
        val from = request.getString("fromUid") ?: error("Demande invalide")
        db.runBatch { batch ->
            batch.update(request.reference, mapOf("status" to if (accept) "accepted" else "rejected", "updatedAt" to Timestamp.now()))
            if (accept) {
                batch.set(user().collection("friends").document(from), mapOf("uid" to from, "pseudo" to request.getString("fromPseudo").orEmpty(),
                    "photoUrl" to request.getString("fromPhotoUrl").orEmpty(), "profileIcon" to request.getString("fromProfileIcon").orEmpty(), "createdAt" to Timestamp.now()), SetOptions.merge())
                batch.set(user(from).collection("friends").document(uid), mapOf("uid" to uid, "pseudo" to me.pseudo,
                    "photoUrl" to me.photoUrl.orEmpty(), "profileIcon" to me.profileIcon.orEmpty(), "createdAt" to Timestamp.now()), SetOptions.merge())
            }
        }.await()
    }
    suspend fun removeFriend(id: String) {
        val me = UserRepository().getMeOrThrow()
        db.runBatch { batch ->
            batch.delete(user().collection("friends").document(id))
            batch.delete(user(id).collection("friends").document(uid))
            batch.set(db.collection("friendRemovals").document(), mapOf("actorUid" to uid, "actorPseudo" to me.pseudo,
                "toUid" to id, "type" to "friendRemoved", "createdAt" to Timestamp.now()))
        }.await()
    }
    suspend fun block(member: Member) {
        require(member.uid != uid)
        db.runBatch { batch ->
            batch.set(user().collection("blockedUsers").document(member.uid), mapOf("blockedUid" to member.uid,
                "pseudo" to member.pseudo, "photoUrl" to member.photoUrl.orEmpty(), "profileIcon" to member.profileIcon.orEmpty(), "createdAt" to Timestamp.now()), SetOptions.merge())
            batch.delete(user().collection("friends").document(member.uid))
            batch.delete(user(member.uid).collection("friends").document(uid))
        }.await()
    }
    suspend fun unblock(id: String) { user().collection("blockedUsers").document(id).delete().await() }
    suspend fun hide(communityId: String, member: Member) {
        require(member.uid != uid)
        user().collection("contentFilters").document(communityId).collection("hiddenMembers").document(member.uid)
            .set(mapOf("uid" to member.uid, "pseudo" to member.pseudo, "createdAt" to Timestamp.now())).await()
    }
    suspend fun report(contentId: String, contentType: String, authorUid: String, authorPseudo: String,
                       reason: String, details: String, communityId: String? = null) {
        require(contentId.isNotBlank() && details.trim().length <= 1000)
        val payload = mutableMapOf<String, Any>("reporterUid" to uid, "contentId" to contentId, "contentType" to contentType,
            "authorUid" to authorUid, "authorPseudo" to authorPseudo, "reason" to reason, "details" to details.trim(),
            "status" to "open", "createdAt" to Timestamp.now(), "updatedAt" to Timestamp.now())
        if (!communityId.isNullOrBlank()) payload["communityId"] = communityId
        db.collection("reports").add(payload).await()
    }
    suspend fun resolveJoin(requestId: String, accept: Boolean) {
        functions.getHttpsCallable("resolveCommunityJoinRequest").call(mapOf("requestId" to requestId,
            "decision" to if (accept) "accepted" else "rejected")).await()
    }
    suspend fun deleteCommunity(communityId: String) {
        functions.getHttpsCallable("deleteCommunity").call(mapOf("communityId" to communityId)).await()
    }
    suspend fun removeMember(communityId: String, member: Member, ban: Boolean) {
        val ref = db.collection("communities").document(communityId)
        val community = ref.get().await()
        check(community.getString("adminUid") == uid && community.getBoolean("isPublic") == false && member.uid != uid)
        db.runBatch { batch ->
            if (ban) batch.set(ref.collection("banned").document(member.uid), mapOf("uid" to member.uid,
                "pseudo" to member.pseudo, "bannedAt" to Timestamp.now(), "bannedByUid" to uid), SetOptions.merge())
            batch.delete(ref.collection("members").document(member.uid))
        }.await()
    }
    suspend fun ghostMode(enabled: Boolean) {
        user().set(mapOf("isGhostMode" to enabled, "updatedAt" to Timestamp.now()), SetOptions.merge()).await()
        if (enabled) {
            val clear = mapOf("lastLatitude" to FieldValue.delete(), "lastLongitude" to FieldValue.delete(), "lastLocationUpdatedAt" to FieldValue.delete())
            user().update(clear).await()
            val members = db.collectionGroup("members").whereEqualTo("uid", uid).get().await().documents
            members.chunked(400).forEach { chunk -> db.runBatch { batch -> chunk.forEach { batch.update(it.reference, clear) } }.await() }
        }
    }
    suspend fun notificationMode(communityId: String, communityName: String, mode: String) {
        require(mode in listOf("all", "mentionsOnly"))
        user().collection("notificationPreferences").document(communityId).set(mapOf("communityId" to communityId,
            "communityName" to communityName, "mode" to mode, "updatedAt" to Timestamp.now()), SetOptions.merge()).await()
    }
}
