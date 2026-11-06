/**
 * Rôle :
 * - Backend “communautés” (groupes) : création, adhésion, membres, messages,
 *   envoi de torpille vidéo, réponse et chaînage.
 *
 * Composants :
 * - observeCommunitiesForMe() :
 *   - fusionne les identifiants du profil et la recherche des adhésions, puis observe
 *     les documents Community et Member directement, comme le client iOS.
 * - createCommunity(...) :
 *   - crée un doc "communities/{id}" et ajoute l’admin dans "members".
 * - joinCommunity(...) :
 *   - écrit le Member dans "communities/{id}/members/{uid}".
 * - observeMembers(...) :
 *   - observe et trie par xpInCommunity décroissant (classement).
 * - observeMessages(...) :
 *   - observe les 100 derniers messages, triés par createdAt.
 * - updateCommunitySettings(...) :
 *   - autorisé uniquement pour adminUid.
 * - sendText(...) :
 *   - ajoute un message textuel dans "messages".
 * - sendVideoTorpille(...) :
 *   - upload vidéo (Storage),
 *   - crée Torpille + Message (batch),
 *   - met à jour le membre ciblé : pendingTorpilleId, deadline, etc.,
 *   - ajoute de l’XP à l’expéditeur,
 *   - appelle la Cloud Function "sendTorpilleNotification" pour notifier le taggé.
 * - respondWithVideo(...) :
 *   - vérifie que la torpille en attente appartient bien à l’utilisateur,
 *   - upload la vidéo réponse,
 *   - marque la Torpille comme répondue + nettoie le pending du membre,
 *   - ajoute XP,
 *   - relance une nouvelle torpille via sendVideoTorpille (chaînage).
 *
 * Pourquoi comme ça :
 * - Batch Firestore => cohérence (message + torpille + état du membre) écrits ensemble.
 * - Notification via Cloud Function => utilise Admin SDK (plus fiable/sécurisé qu’un client).
 * - Les règles de sécurité peuvent évoluer pour verrouiller davantage les écritures sensibles.
 */
package com.torpille.app.repo

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.torpille.app.data.Community
import com.torpille.app.data.Member
import com.torpille.app.data.UserCommunityXp
import com.torpille.app.data.Message
import com.torpille.app.data.Torpille
import com.torpille.app.data.UnseenTorpilleStory
import com.torpille.app.data.PublishedReel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FieldPath
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.torpille.app.TorpilleApp
import android.content.ContentResolver
import android.util.Log
import com.torpille.app.util.FirebaseVideoStorageClient
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import com.torpille.app.util.MediaDebug
import com.torpille.app.util.observeDoc

class CommunityRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val contentResolver: ContentResolver = TorpilleApp.appContext.contentResolver,
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("europe-west1"),
    private val notificationRepo: NotificationRepository = NotificationRepository(),
) {

    private companion object {
        const val XP_TORPILLE_SENT = 20L
        const val XP_MISSED_TORPILLE_PENALTY = -10L
        const val XP_LATE_RESPONSE_RECOVERY = 5L
    }

    private fun communities() = db.collection("communities")
    private fun users() = db.collection("users")
    suspend fun invitationCode(communityId: String): String {
        val data = functions.getHttpsCallable("getCommunityInvitationCode")
            .call(mapOf("communityId" to communityId)).await().data as? Map<*, *>
        return data?.get("code") as? String ?: error("Code d’invitation indisponible.")
    }

    suspend fun resolveInvitationCode(code: String): String {
        val data = functions.getHttpsCallable("resolveCommunityInvitationCode")
            .call(mapOf("code" to code)).await().data as? Map<*, *>
        return data?.get("communityId") as? String ?: error("Code d’invitation introuvable.")
    }
    private fun reels() = db.collection("reels")

    private val firebaseVideoStorage by lazy {
        FirebaseVideoStorageClient(
            storage = com.google.firebase.storage.FirebaseStorage.getInstance(),
            contentResolver = contentResolver
        )
    }

    fun inviteLinkFor(communityId: String): String =
        "https://torpille-38783.web.app/join?cid=${java.net.URLEncoder.encode(communityId, "UTF-8")}&lang=${com.torpille.app.util.AppLanguage.code}"

    suspend fun getCommunityOrThrow(communityId: String): Community {
        return communities().document(communityId).get().await()
            .toObject(Community::class.java) ?: error("Communauté introuvable")
    }

    suspend fun isPseudoAllowed(communityId: String, pseudoKey: String): Boolean {
        val doc = communities().document(communityId)
            .collection("allowlist")
            .document(pseudoKey)
            .get()
            .await()
        return doc.exists()
    }


    fun observePublishedReels(limit: Long = 40): Flow<List<PublishedReel>> =
        combine(observePublishedReelsRaw(limit), SocialRepository().excluded()) { values, excluded ->
            values.filter { it.authorUid !in excluded && (it.moderationStatus == null || it.moderationStatus == "visible") }
        }

    fun observeMessages(communityId: String): Flow<List<Message>> =
        combine(observeMessagesRaw(communityId), SocialRepository().excluded(communityId)) { values, excluded ->
            values.filter { it.senderUid !in excluded && com.torpille.app.util.isMessageVisible(it) }
        }

    fun observeRecentTorpilles(communityId: String): Flow<List<Torpille>> =
        combine(observeRecentTorpillesRaw(communityId), SocialRepository().excluded(communityId)) { values, excluded ->
            values.filter { it.fromUid !in excluded && it.toUid !in excluded &&
                (it.moderationStatus == null || it.moderationStatus == "visible") &&
                (it.createdAt?.seconds ?: Long.MAX_VALUE) > Timestamp.now().seconds - 14 * 86400 }
        }

    fun observeSavedMessages(communityId: String): Flow<List<Message>> =
        combine(SocialRepository().observe(communities().document(communityId).collection("savedMessages").orderBy("savedAt", Query.Direction.DESCENDING)),
            SocialRepository().excluded(communityId)) { docs, excluded ->
            docs.mapNotNull { it.toObject(Message::class.java)?.copy(id = it.id) }.filter {
                it.senderUid !in excluded && (it.moderationStatus == null || it.moderationStatus == "visible")
            }
        }

    suspend fun setReaction(communityId: String, messageId: String, emoji: String?) {
        val uid = auth.currentUser?.uid ?: error("Utilisateur non connecté")
        require(emoji == null || emoji in com.torpille.app.data.MessageReaction.allowed) { "Réaction indisponible." }
        communities().document(communityId).collection("messages").document(messageId)
            .update(FieldPath.of("reactions", uid), emoji ?: FieldValue.delete()).await()
    }

    suspend fun saveMessage(communityId: String, message: Message, savedByPseudo: String) {
        val uid = auth.currentUser?.uid ?: error("Utilisateur non connecté")
        val ref = communities().document(communityId)
        val messageRef = ref.collection("messages").document(message.id)
        val original = messageRef.get().await()
        check(original.exists()) { "Contenu introuvable" }
        val metadata = mapOf("savedAt" to Timestamp.now(), "savedByUid" to uid, "savedByPseudo" to savedByPseudo)
        val payload = (original.data.orEmpty() - "reactions") + metadata + mapOf("id" to message.id)
        db.runBatch { batch ->
            batch.set(messageRef, metadata, SetOptions.merge())
            batch.set(ref.collection("savedMessages").document(message.id), payload)
        }.await()
    }

    suspend fun publishSavedMessage(communityId: String, message: Message) {
        val uid = auth.currentUser?.uid ?: error("Utilisateur non connecté")
        val id = message.torpilleId?.takeIf { it.isNotBlank() } ?: error("Seules les torpilles enregistrées peuvent être publiées.")
        val community = getCommunityOrThrow(communityId)
        val ref = communities().document(communityId)
        check(ref.collection("savedMessages").document(message.id).get().await().exists()) { "Enregistre cette torpille avant de la publier." }
        val torpRef = ref.collection("torpilles").document(id)
        val torpille = torpRef.get().await()
        check(torpille.getString("fromUid") == uid) { "Tu peux publier uniquement tes propres torpilles." }
        check(torpille.getBoolean("isPublished") != true) { "Cette torpille est déjà publiée." }
        val now = Timestamp.now()
        db.runBatch { batch ->
            batch.set(torpRef, mapOf("isPublished" to true, "publishedAt" to now, "publishedByUid" to uid,
                "publishedByPseudo" to message.senderPseudo, "communityName" to community.name), SetOptions.merge())
            batch.set(reels().document(id), PublishedReel(id = id, torpilleId = id, sourceMessageId = message.id,
                communityId = communityId, communityName = community.name, authorUid = uid,
                authorPseudo = message.senderPseudo, videoUrl = message.videoUrl, videoBucket = message.videoBucket,
                videoPath = message.videoPath, taggedPseudo = message.taggedPseudo, createdAt = message.createdAt,
                publishedAt = now, moderationStatus = "pending", reportCount = 0))
        }.await()
    }

    suspend fun unpublish(communityId: String, torpilleId: String) {
        val uid = auth.currentUser?.uid ?: error("Utilisateur non connecté")
        val ref = communities().document(communityId).collection("torpilles").document(torpilleId)
        check(ref.get().await().getString("fromUid") == uid) { "Seul l’auteur peut retirer cette torpille." }
        db.runBatch { batch ->
            batch.set(ref, mapOf("isPublished" to false, "publishedAt" to FieldValue.delete(),
                "publishedByUid" to FieldValue.delete(), "publishedByPseudo" to FieldValue.delete(),
                "communityName" to FieldValue.delete()), SetOptions.merge())
            batch.delete(reels().document(torpilleId))
        }.await()
    }

    suspend fun markMessagesSeen(communityId: String, timestamp: Timestamp) {
        val uid = auth.currentUser?.uid ?: return
        communities().document(communityId).collection("members").document(uid)
            .set(mapOf("lastSeenMessageAt" to timestamp, "lastActiveAt" to Timestamp.now(), "lastOpenedAt" to Timestamp.now()), SetOptions.merge()).await()
    }

    private fun observePublishedReelsRaw(limit: Long): Flow<List<PublishedReel>> = callbackFlow {
        val reg = reels()
            .orderBy("publishedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val values = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(PublishedReel::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(values)
            }
        awaitClose { reg.remove() }
    }

    fun observeAllowlist(communityId: String): Flow<List<String>> = callbackFlow {
        val reg = communities().document(communityId)
            .collection("allowlist")
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val keys = snap?.documents?.map { it.id } ?: emptyList()
                trySend(keys.sorted())
            }
        awaitClose { reg.remove() }
    }

    suspend fun replaceAllowlist(communityId: String, pseudoKeys: List<String>) {
        val uid = auth.currentUser?.uid ?: error("Not logged in")
        val community = getCommunityOrThrow(communityId)
        if (community.adminUid != uid) error("Only admin can edit allowlist")

        val allowRef = communities().document(communityId).collection("allowlist")
        val existing = allowRef.get().await().documents.map { it.id }.toSet()
        val target = pseudoKeys.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()

        db.runBatch { batch ->
            for (id in existing) {
                if (!target.contains(id)) {
                    batch.delete(allowRef.document(id))
                }
            }
            for (id in target) {
                if (!existing.contains(id)) {
                    batch.set(
                        allowRef.document(id),
                        mapOf(
                            "addedAt" to Timestamp.now(),
                            "addedByUid" to uid
                        )
                    )
                }
            }
        }.await()
    }

    fun observeMyCommunityXp(): Flow<List<UserCommunityXp>> =
        CommunityMembershipRepository(auth, db).observe().map { entries ->
            entries.map { (community, member) ->
                UserCommunityXp(
                    communityId = community.id,
                    communityName = community.name,
                    xpInCommunity = member.xpInCommunity,
                    lastTorpilledAt = member.lastTorpilledAt,
                )
            }.sortedBy { it.communityName.lowercase() }
        }

    fun observeCommunitiesForMe(): Flow<List<Community>> =
        CommunityMembershipRepository(auth, db).observe().map { entries -> entries.map { it.first } }

    suspend fun updateMyLocationInCommunities(
        communityIds: List<String>,
        latitude: Double,
        longitude: Double,
    ) {
        val uid = auth.currentUser?.uid ?: error("Not logged in")
        if (users().document(uid).get().await().getBoolean("isGhostMode") == true) return
        val ids = communityIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val now = Timestamp.now()
        users().document(uid).set(mapOf("lastLatitude" to latitude, "lastLongitude" to longitude,
            "lastLocationUpdatedAt" to now), SetOptions.merge()).await()

        db.runBatch { batch ->
            ids.forEach { communityId ->
                val memberRef = communities()
                    .document(communityId)
                    .collection("members")
                    .document(uid)

                batch.set(
                    memberRef,
                    mapOf(
                        "lastLatitude" to latitude,
                        "lastLongitude" to longitude,
                        "lastLocationUpdatedAt" to now
                    ),
                    SetOptions.merge()
                )
            }
        }.await()
    }

    suspend fun markMeOpenedInCommunities(communityIds: List<String>) {
        val uid = auth.currentUser?.uid ?: error("Not logged in")
        val ids = communityIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return

        val now = Timestamp.now()
        db.runBatch { batch ->
            ids.forEach { communityId ->
                val memberRef = communities()
                    .document(communityId)
                    .collection("members")
                    .document(uid)

                batch.set(
                    memberRef,
                    mapOf("lastActiveAt" to now),
                    SetOptions.merge()
                )
            }
        }.await()
    }

    fun observeMembersForMap(communityId: String): Flow<List<Member>> =
        combine(observeMembersEnriched(communityId), SocialRepository().excluded(communityId)) { members, excluded ->
            members.filter { it.uid !in excluded }
        }.map { members ->
            members.mapNotNull { member ->
                val profile = users().document(member.uid).get().await().toObject(com.torpille.app.data.UserProfile::class.java)
                if (profile?.isGhostMode == true) null else member.copy(
                    photoUrl = profile?.photoUrl ?: member.photoUrl, profileIcon = profile?.profileIcon ?: member.profileIcon,
                    lastLatitude = profile?.lastLatitude ?: member.lastLatitude,
                    lastLongitude = profile?.lastLongitude ?: member.lastLongitude,
                    lastLocationUpdatedAt = profile?.lastLocationUpdatedAt ?: member.lastLocationUpdatedAt)
            }
        }

    fun observeMembersForMapMulti(communityIds: List<String>) = when {
        communityIds.isEmpty() -> flowOf(emptyList())
        communityIds.size == 1 -> observeMembersForMap(communityIds.first())
        else -> combine(communityIds.distinct().map { observeMembersForMap(it) }) { arrays ->
            arrays.flatMap { it.asIterable() }
        }
    }

    suspend fun createCommunity(name: String, isPublic: Boolean, responseTimeSeconds: Long): String {
        require(name.trim().isNotEmpty()) { "Donne un nom à ta communauté." }
        require(responseTimeSeconds > 0) { "La durée doit être positive." }
        val uid = auth.currentUser?.uid ?: error("Not logged in")

        val me = users().document(uid).get().await()
        val pseudo = me.getString("pseudo") ?: ""
        val photoUrl = me.getString("photoUrl")

        val doc = communities().document()
        val community = Community(
            id = doc.id,
            name = name,
            isPublic = isPublic,
            adminUid = uid,
            responseTimeSeconds = responseTimeSeconds,
            createdAt = Timestamp.now(),
        )
        doc.set(community).await()

        doc.collection("members").document(uid).set(
            mapOf("uid" to uid, "pseudo" to pseudo, "photoUrl" to photoUrl, "profileIcon" to me.getString("profileIcon"), "xpInCommunity" to 0L)
        ).await()

        users().document(uid).set(mapOf("communityIds" to FieldValue.arrayUnion(doc.id)), SetOptions.merge()).await()
        runCatching { markMeOpenedInCommunities(listOf(doc.id)) }
        return doc.id
    }

    suspend fun joinCommunity(communityId: String, pseudo: String, photoUrl: String?): Boolean {
        auth.currentUser?.getIdToken(true)?.await() ?: error("Utilisateur non connecté")
        val result = functions.getHttpsCallable("requestCommunityMembership")
            .call(mapOf("communityId" to communityId)).await().data as? Map<*, *>
        return when (result?.get("status")) {
            "pending", "already-pending" -> false
            "joined", "already-member" -> true
            else -> error("Réponse invalide du serveur")
        }
    }

    fun observeMembers(communityId: String): Flow<List<Member>> = callbackFlow {
        val reg = communities().document(communityId)
            .collection("members")
            .orderBy("xpInCommunity", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val list = snap?.documents?.mapNotNull { it.toObject(Member::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    private fun observeMessagesRaw(communityId: String): Flow<List<Message>> = callbackFlow {
        val reg = communities().document(communityId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(
                        MediaDebug.TAG,
                        MediaDebug.firestoreErrorMessage(
                            action = "Lecture du fil de discussion ($communityId)",
                            codeName = err.code.name,
                            rawMessage = err.message,
                        ),
                        err,
                    )
                    return@addSnapshotListener
                }
                val list = snap?.documents?.map { d ->
                    d.toObject(Message::class.java)?.copy(id = d.id)
                }?.filterNotNull() ?: emptyList()
                Log.d(MediaDebug.TAG, "observeMessages success communityId=$communityId count=${list.size} audioCount=${list.count { it.type == "audio" }}")
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeCommunity(communityId: String): Flow<Community?> = callbackFlow {
        val reg = communities().document(communityId).addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener
            trySend(snap?.toObject(Community::class.java))
        }
        awaitClose { reg.remove() }
    }

    suspend fun updateCommunitySettings(communityId: String, isPublic: Boolean, responseTimeSeconds: Long, eliminationMode: Boolean,
        rankingSchedule: com.torpille.app.data.RankingSchedule = com.torpille.app.data.RankingSchedule()) {
        functions.getHttpsCallable("updateCommunitySettings").call(mapOf("communityId" to communityId,
            "isPublic" to isPublic, "responseTimeSeconds" to responseTimeSeconds, "eliminationMode" to eliminationMode,
            "rankingSchedule" to rankingSchedule.payload())).await()
    }

    private fun observeRecentTorpillesRaw(communityId: String): Flow<List<Torpille>> = callbackFlow {
        val reg = communities().document(communityId)
            .collection("torpilles")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(40)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val list = snap?.documents?.mapNotNull { it.toObject(Torpille::class.java)?.copy(id = it.id) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAccountUnseenTorpilles(): Flow<List<UnseenTorpilleStory>> {
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())

        return observeCommunitiesForMe().flatMapLatest { communitiesForMe ->
            val scopedCommunities = communitiesForMe
                .filter { it.id.isNotBlank() }
                .distinctBy { it.id }

            if (scopedCommunities.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    scopedCommunities.map { community ->
                        combine(
                            observeDoc(
                                communities()
                                    .document(community.id)
                                    .collection("members")
                                    .document(uid),
                                Member::class.java,
                            ),
                            observeRecentTorpilles(community.id),
                        ) { myMember, torpilles ->
                            val lastSeenSeconds = myMember?.lastSeenTorpilleAt?.seconds ?: Long.MIN_VALUE
                            torpilles
                                .filter { it.fromUid != uid }
                                .filter { it.toUid.isNotBlank() }
                                .filter { (it.createdAt?.seconds ?: Long.MIN_VALUE) > lastSeenSeconds }
                                .map { torpille ->
                                    UnseenTorpilleStory(
                                        communityId = community.id,
                                        communityName = community.name,
                                        torpille = torpille,
                                    )
                                }
                        }
                    }
                ) { arrays ->
                    arrays
                        .flatMap { it.asIterable() }
                        .sortedByDescending { it.torpille.createdAt?.seconds ?: Long.MIN_VALUE }
                }
            }
        }
    }


    suspend fun getTorpilleById(
        communityId: String,
        torpilleId: String,
    ): Torpille? {
        return communities()
            .document(communityId)
            .collection("torpilles")
            .document(torpilleId)
            .get()
            .await()
            .toObject(Torpille::class.java)
            ?.copy(id = torpilleId)
    }

    suspend fun markTorpillesSeen(communityId: String) {
        val uid = auth.currentUser?.uid ?: error("Not logged in")
        communities().document(communityId)
            .collection("members")
            .document(uid)
            .set(mapOf("lastSeenTorpilleAt" to Timestamp.now()), SetOptions.merge())
            .await()
    }

    suspend fun markTorpillesSeen(communityIds: List<String>) {
        val uid = auth.currentUser?.uid ?: error("Not logged in")
        val ids = communityIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return

        val now = Timestamp.now()
        db.runBatch { batch ->
            ids.forEach { communityId ->
                batch.set(
                    communities()
                        .document(communityId)
                        .collection("members")
                        .document(uid),
                    mapOf("lastSeenTorpilleAt" to now),
                    SetOptions.merge(),
                )
            }
        }.await()
    }

    suspend fun recalculateMyXP() {
        val uid = auth.currentUser?.uid ?: return
        val memberships = db.collectionGroup("members").whereEqualTo("uid", uid).get().await().documents
        val values = memberships.mapNotNull { doc -> doc.reference.parent.parent?.id?.let { it to (doc.getLong("xpInCommunity") ?: 0L) } }.toMap()
        val total = values.values.sum()
        users().document(uid).set(mapOf("communityXP" to values, "xpTotal" to total, "xptotal" to total), SetOptions.merge()).await()
    }

    suspend fun syncXPState(communityId: String) {
        applyExpiredPenaltyIfNeeded(communityId)
        recalculateMyXP()
    }

    suspend fun applyExpiredPenaltyIfNeeded(communityId: String): Boolean {
        val uid = auth.currentUser?.uid ?: error("Not logged in")
        val response = functions.getHttpsCallable("applyExpiredTorpillePenalty").call(mapOf("communityId" to communityId)).await().data as? Map<*, *>
        val applied = response?.get("applied") == true

        if (applied) {
            runCatching { recalculateMyXP() }
            notificationRepo.createForCurrentUser(
                type = "xp_lost",
                title = "-10 XP",
                body = "Tu as laissé expirer une torpille.",
                communityId = communityId,
                xpDelta = XP_MISSED_TORPILLE_PENALTY,
            )
        }
        return applied
    }

    suspend fun sendImageMessage(communityId: String, senderPseudo: String, imageUri: Uri) {
        val uid = auth.currentUser?.uid ?: error("User not logged in")
        val filename = "${System.currentTimeMillis()}_${uid}.jpg"
        val uploaded = firebaseVideoStorage.uploadCommunityImage(
            communityId = communityId,
            localImageUri = imageUri,
            filename = filename,
        )

        val msgRef = communities().document(communityId).collection("messages").document()
        val msg = mapOf(
            "id" to msgRef.id,
            "type" to "image",
            "senderUid" to uid,
            "senderPseudo" to senderPseudo,
            "imageUrl" to uploaded.downloadUrl,
            "imageBucket" to uploaded.bucket,
            "imagePath" to uploaded.storagePath,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        msgRef.set(msg).await()
    }

    suspend fun sendText(communityId: String, senderPseudo: String, text: String) {
        val uid = auth.currentUser?.uid ?: error("Not logged in")
        com.torpille.app.util.validateCommunityText(text)
        val msgRef = communities().document(communityId).collection("messages").document()
        val msg = mapOf(
            "id" to msgRef.id,
            "type" to "text",
            "senderUid" to uid,
            "senderPseudo" to senderPseudo,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        msgRef.set(msg).await()
    }


    suspend fun sendAudioMessage(communityId: String, senderPseudo: String, audioUri: Uri) {
        val uid = auth.currentUser?.uid ?: error("Not logged in")
        val filename = "${System.currentTimeMillis()}_${uid}.m4a"

        Log.d(MediaDebug.TAG, "sendAudioMessage start communityId=$communityId uid=$uid senderPseudo=$senderPseudo uri=$audioUri")

        val uploaded = try {
            firebaseVideoStorage.uploadCommunityAudio(
                communityId = communityId,
                localAudioUri = audioUri,
                filename = filename,
            )
        } catch (e: StorageException) {
            val readable = MediaDebug.storageErrorMessage(
                action = "Upload du vocal",
                errorCode = e.errorCode,
                rawMessage = e.message,
            )
            Log.e(MediaDebug.TAG, readable, e)
            throw IllegalStateException(readable, e)
        } catch (e: Exception) {
            Log.e(MediaDebug.TAG, "Upload du vocal a échoué de manière inattendue", e)
            throw e
        }

        val durationSeconds = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(TorpilleApp.appContext, audioUri)
                (retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull() ?: 0.0) / 1000.0
            } finally { retriever.release() }
        }
        val msgRef = communities().document(communityId).collection("messages").document()
        val msg = hashMapOf<String, Any>(
            "id" to msgRef.id,
            "type" to "audio",
            "senderUid" to uid,
            "senderPseudo" to senderPseudo,
            "audioBucket" to uploaded.bucket,
            "audioPath" to uploaded.storagePath,
            "audioDurationSeconds" to durationSeconds,
            "createdAt" to FieldValue.serverTimestamp(),
        )

        uploaded.downloadUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { msg["audioUrl"] = it }

        try {
            Log.d(MediaDebug.TAG, "sendAudioMessage firestore write start docId=${msgRef.id} path=${uploaded.storagePath}")
            msgRef.set(msg).await()
            Log.d(MediaDebug.TAG, "sendAudioMessage firestore write success docId=${msgRef.id}")
        } catch (e: FirebaseFirestoreException) {
            val readable = MediaDebug.firestoreErrorMessage(
                action = "Création du message audio dans Firestore",
                codeName = e.code.name,
                rawMessage = e.message,
            )
            Log.e(MediaDebug.TAG, readable, e)
            throw IllegalStateException(readable, e)
        }
    }

    suspend fun sendVideoMessage(communityId: String, senderPseudo: String, videoUri: Uri) {
        val uid = auth.currentUser?.uid ?: error("User not logged in")
        val filename = "${System.currentTimeMillis()}_${uid}.mp4"
        val uploaded = firebaseVideoStorage.uploadCommunityVideo(
            communityId = communityId,
            localVideoUri = videoUri,
            filename = filename,
        )

        val msgRef = communities().document(communityId).collection("messages").document()
        val msg = mapOf(
            "id" to msgRef.id,
            "type" to "video",
            "senderUid" to uid,
            "senderPseudo" to senderPseudo,
            "videoUrl" to uploaded.downloadUrl,
            "videoBucket" to uploaded.bucket,
            "videoPath" to uploaded.storagePath,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        msgRef.set(msg).await()
    }

    suspend fun getAudioPlaybackUrl(
        audioPath: String,
        audioBucket: String?,
    ): String {
        Log.d(MediaDebug.TAG, "getAudioPlaybackUrl start path=$audioPath bucket=$audioBucket")

        return try {
            firebaseVideoStorage.resolveStorageDownloadUrl(
                storagePath = audioPath,
                bucket = audioBucket,
            )
        } catch (e: Exception) {
            Log.w(MediaDebug.TAG, "getAudioPlaybackUrl direct storage failed, fallback to signed url", e)
            getSignedPlaybackUrl(
                videoPath = audioPath,
                videoBucket = audioBucket,
            )
        }
    }

    suspend fun getSignedPlaybackUrl(
        videoPath: String,
        videoBucket: String?,
    ): String {
        val user = auth.currentUser ?: error("Utilisateur non connecté. Reconnecte-toi puis réessaie.")

        val idTokenResult = user.getIdToken(true).await()
        val idToken = idTokenResult.token
        if (idToken.isNullOrBlank()) {
            error("Token Firebase Auth introuvable. Reconnecte-toi puis réessaie.")
        }
        Log.d(
            "VIDEO_DEBUG",
            "idToken refreshed uid=${user.uid} tokenPresent=${!idToken.isNullOrBlank()}"
        )

        val payload = hashMapOf<String, Any>(
            "path" to videoPath,
            "expiresIn" to 3600
        )

        if (!videoBucket.isNullOrBlank()) {
            payload["bucket"] = videoBucket
        }

        Log.d(
            "VIDEO_DEBUG",
            "getSignedPlaybackUrl uid=${user.uid} path=$videoPath bucket=$videoBucket"
        )

        try {
            val result = functions
                .getHttpsCallable("videoSignedUrl")
                .call(payload)
                .await()

            

            val data = result.data as? Map<*, *>
                ?: error("Réponse invalide de videoSignedUrl")

            val signedUrl = data["signedUrl"] as? String
                ?: error("signedUrl absente dans la réponse Firebase Functions")

            
            return signedUrl
        } catch (e: FirebaseFunctionsException) {
            Log.e(
                "VIDEO_DEBUG",
                "videoSignedUrl failed code=${e.code} message=${e.message}",
                e
            )

            val readableMessage = MediaDebug.functionsErrorMessage(
                action = "Récupération de l'URL signée ($videoPath)",
                codeName = e.code.name,
                rawMessage = e.message,
            )

            throw IllegalStateException(readableMessage, e)
        }
    }

    suspend fun respondWithVideo(
        communityId: String,
        senderPseudo: String,
        responseVideoUri: Uri,
        pendingTorpilleId: String,
        nextTaggedUid: String,
        nextTaggedPseudo: String,
        tagX: Double,
        tagY: Double,
        publishToReels: Boolean = false,
    ) {
        applyExpiredPenaltyIfNeeded(communityId)
        sendVideoTorpille(communityId, senderPseudo, responseVideoUri, nextTaggedUid, nextTaggedPseudo,
            tagX, tagY, publishToReels, respondingTo = pendingTorpilleId)
    }

    suspend fun leaveCommunity(communityId: String) {
        functions.getHttpsCallable("leaveCommunity").call(mapOf("communityId" to communityId)).await()
    }

    private suspend fun fetchPseudosFor(uids: List<String>): Map<String, String> {
        val clean = uids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (clean.isEmpty()) return emptyMap()

        val out = mutableMapOf<String, String>()
        val chunks = clean.chunked(10) // whereIn() limité à 10

        for (chunk in chunks) {
            val qs = users()
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .await()

            for (doc in qs.documents) {
                val pseudo = doc.getString("pseudo")?.trim().orEmpty()
                if (pseudo.isNotBlank()) out[doc.id] = pseudo
            }
        }
        return out
    }

    fun observeMembersEnriched(communityId: String): Flow<List<Member>> =
        combine(observeMembersEnrichedRaw(communityId), SocialRepository().excluded()) { members, excluded ->
            members.filter { it.uid !in excluded }
        }

    private fun observeMembersEnrichedRaw(communityId: String): Flow<List<Member>> = channelFlow {
        val pseudoCache = mutableMapOf<String, String>()

        val reg = communities().document(communityId)
            .collection("members")
            .orderBy("xpInCommunity", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener

                val raw = snap?.documents?.mapNotNull { it.toObject(Member::class.java) } ?: emptyList()

                val missingUids = raw
                    .filter { it.pseudo.isBlank() }
                    .map { it.uid }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .filterNot { pseudoCache.containsKey(it) }

                launch {
                    if (missingUids.isNotEmpty()) {
                        val fetched = fetchPseudosFor(missingUids)
                        pseudoCache.putAll(fetched)
                    }

                    val enriched = raw.map { m ->
                        if (m.pseudo.isNotBlank()) return@map m
                        val p = pseudoCache[m.uid].orEmpty()
                        if (p.isNotBlank()) m.copy(pseudo = p) else m
                    }

                    send(enriched)
                }
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendVideoTorpille(
        communityId: String,
        senderPseudo: String,
        videoUri: Uri,
        taggedUid: String,
        taggedPseudo: String,
        tagX: Double,
        tagY: Double,
        publishToReels: Boolean = false,
        respondingTo: String = "",
    ) {

        val uid = auth.currentUser?.uid ?: error("Utilisateur non connecté")
        auth.currentUser!!.getIdToken(true).await()
        val uploaded = firebaseVideoStorage.uploadCommunityVideo(
            communityId = communityId, localVideoUri = videoUri,
            filename = "${System.currentTimeMillis()}_${uid}.mp4"
        )
        functions.getHttpsCallable("createVideoTorpille").call(mapOf(
            "communityId" to communityId, "senderPseudo" to senderPseudo,
            "taggedUid" to taggedUid, "taggedPseudo" to taggedPseudo,
            "videoBucket" to uploaded.bucket, "videoPath" to uploaded.storagePath,
            "videoUrl" to uploaded.downloadUrl, "tagX" to tagX, "tagY" to tagY, "respondingTo" to respondingTo
        )).await()
        runCatching { recalculateMyXP() }
    }
}
