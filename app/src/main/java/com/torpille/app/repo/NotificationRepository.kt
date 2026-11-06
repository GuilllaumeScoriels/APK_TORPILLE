package com.torpille.app.repo

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.torpille.app.TorpilleApp
import com.torpille.app.data.AppNotification
import com.torpille.app.notifications.NotificationActivityPolicy
import com.torpille.app.util.t
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

/** Activity is persisted per account on device, as on iOS; shared events come from Firestore. */
class NotificationRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance(), private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val prefs get() = TorpilleApp.appContext.getSharedPreferences("torpille_activity", Context.MODE_PRIVATE)
    private fun load(uid: String): List<AppNotification> = runCatching {
        val values = JSONArray(prefs.getString(uid, "[]"))
        (0 until values.length()).map { index -> val item = values.getJSONObject(index)
            AppNotification(id = item.getString("id"), type = item.optString("type"), title = item.optString("title"),
                body = item.optString("body"), communityId = item.optString("communityId").takeIf(String::isNotBlank),
                communityName = item.optString("communityName"), torpilleId = item.optString("torpilleId"),
                xpDelta = item.optLong("xpDelta"), read = item.optBoolean("read"), createdAt = Timestamp(item.optLong("seconds"), 0))
        }
    }.getOrDefault(emptyList())
    private fun persist(uid: String, items: List<AppNotification>) {
        val json = JSONArray()
        items.filter { (it.createdAt?.seconds ?: 0) >= Timestamp.now().seconds - 14 * 86400 }.take(200).forEach {
            json.put(JSONObject().put("id", it.id).put("type", it.type).put("title", it.title).put("body", it.body)
                .put("communityId", it.communityId.orEmpty()).put("communityName", it.communityName.orEmpty())
                .put("torpilleId", it.torpilleId.orEmpty()).put("xpDelta", it.xpDelta).put("read", it.read).put("seconds", it.createdAt?.seconds ?: 0))
        }
        prefs.edit().putString(uid, json.toString()).apply()
    }
    private fun local(uid: String): Flow<List<AppNotification>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == uid || key == "read_$uid") trySend(load(uid)) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(load(uid))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMyNotifications(limit: Long = 200): Flow<List<AppNotification>> {
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        val social = SocialRepository()
        val events = listOf(
            db.collection("friendRequests").whereEqualTo("toUid", uid),
            db.collection("friendRequests").whereEqualTo("fromUid", uid),
            db.collection("friendRemovals").whereEqualTo("toUid", uid),
            db.collection("communityJoinRequests").whereEqualTo("adminUid", uid),
            db.collection("communityJoinRequests").whereEqualTo("requesterUid", uid)
        ).map { query -> social.observe(query).map { docs -> docs.map { doc ->
            val type = doc.reference.parent.id
            val status = doc.getString("status").orEmpty()
            AppNotification(id = "${type}_${doc.id}_$status", type = type,
                title = when(type) { "friendRequests" -> "Demande d’ami"; "friendRemovals" -> "Amitié supprimée"; else -> "Demande d’adhésion" },
                body = when(type) { "friendRequests" -> "@${doc.getString("fromPseudo").orEmpty()} · $status"; "friendRemovals" -> "@${doc.getString("actorPseudo").orEmpty()} t’a retiré de ses amis."; else -> "${doc.getString("communityName").orEmpty()} · $status" },
                communityId = doc.getString("communityId"), communityName = doc.getString("communityName"),
                createdAt = doc.getTimestamp("updatedAt") ?: doc.getTimestamp("createdAt"))
        } }.catch { emit(emptyList()) } }
        val communityRepo = CommunityRepository(notificationRepo = this)
        val discussions = communityRepo.observeCommunitiesForMe().flatMapLatest { communities ->
            if (communities.isEmpty()) flowOf(emptyList()) else combine(communities.map { community ->
                val preference = com.torpille.app.util.observeDoc(db.collection("users").document(uid).collection("notificationPreferences").document(community.id), NotificationPreference::class.java)
                combine(communityRepo.observeMessages(community.id), communityRepo.observeRecentTorpilles(community.id), preference) { messages, torpilles, setting ->
                    val messageItems = messages.filter { NotificationActivityPolicy.includes(it, uid, setting?.mode ?: "all") }.map {
                        AppNotification(id = "message_${community.id}_${it.id}", type = "message", title = community.name,
                            body = if (it.systemEvent == "settingsChanged") it.text.orEmpty() else "${it.senderPseudo} : ${it.text ?: if (it.responseRequired == false) "a répondu hors horaires. Aucune réponse ni pénalité pour toi." else if (it.torpilleId != null) "t’a torpillé !" else "Nouveau ${it.type}"}",
                            communityId = community.id, communityName = community.name, torpilleId = it.torpilleId, createdAt = it.createdAt)
                    }
                    val torpilleItems = torpilles.filter { NotificationActivityPolicy.directlyReceived(it, uid) }.map {
                        val sender = it.fromPseudo?.takeIf(String::isNotBlank)?.let { pseudo -> "@$pseudo" } ?: t("Un utilisateur")
                        val body = if (!it.responseRequired) "$sender ${t("a répondu hors horaires. Aucune réponse ni pénalité pour toi.")}"
                            else "$sender ${t("t'a torpillé.")}"
                        AppNotification(id = "torpille_${community.id}_${it.id}", type = "torpilled", title = t("Torpille reçue"),
                            body = body, communityId = community.id, communityName = community.name, torpilleId = it.id, createdAt = it.createdAt)
                    }
                    messageItems + torpilleItems
                }.catch { emit(emptyList()) }
            }) { it.flatMap { values -> values } }
        }.catch { emit(emptyList()) }
        return combine(events + discussions + local(uid)) { lists ->
            val readIds = prefs.getStringSet("read_$uid", emptySet()).orEmpty()
            lists.flatMap { it }.distinctBy { it.id }.filter { (it.createdAt?.seconds ?: 0) >= Timestamp.now().seconds - 14 * 86400 }
                .map { it.copy(read = it.read || it.id in readIds) }.sortedWith(compareByDescending<AppNotification> { it.type == "torpilled" }.thenByDescending { it.createdAt?.seconds }).take(limit.toInt())
        }
    }
    suspend fun markAsRead(id: String) {
        val uid = auth.currentUser?.uid ?: return
        val previouslyRead = prefs.getStringSet("read_$uid", emptySet()).orEmpty()
        prefs.edit().putStringSet("read_$uid", previouslyRead + id).apply()
        persist(uid, load(uid).map { if (it.id == id) it.copy(read = true) else it })
    }

    suspend fun markAllAsRead() {
        val uid = auth.currentUser?.uid ?: return
        val items = observeMyNotifications().first()
        val previouslyRead = prefs.getStringSet("read_$uid", emptySet()).orEmpty()
        prefs.edit().putStringSet("read_$uid", previouslyRead + items.map { it.id }).apply()
        persist(uid, load(uid).map { it.copy(read = true) })
    }
    suspend fun createForCurrentUser(type: String, title: String, body: String, communityId: String? = null, communityName: String? = null, torpilleId: String? = null, xpDelta: Long = 0) {
        val uid = auth.currentUser?.uid ?: return
        createForUser(uid, type, title, body, communityId, communityName, torpilleId, xpDelta)
    }
    suspend fun createForUser(uid: String, type: String, title: String, body: String, communityId: String? = null, communityName: String? = null, torpilleId: String? = null, xpDelta: Long = 0) {
        if (uid != auth.currentUser?.uid) return // Other users receive server-generated FCM events.
        persist(uid, listOf(AppNotification(id = java.util.UUID.randomUUID().toString(), type = type, title = title, body = body,
            communityId = communityId, communityName = communityName, torpilleId = torpilleId, xpDelta = xpDelta, createdAt = Timestamp.now())) + load(uid))
    }
}

data class NotificationPreference(val mode: String = "all")
