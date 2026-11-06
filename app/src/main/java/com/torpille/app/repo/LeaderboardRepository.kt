package com.torpille.app.repo

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.torpille.app.data.Community
import com.torpille.app.data.LeaderboardEntry
import com.torpille.app.data.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LeaderboardRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private fun users() = db.collection("users")
    private fun communities() = db.collection("communities")

    fun observeMyCommunities(): Flow<List<Community>> =
        CommunityMembershipRepository(auth, db).observe().map { entries ->
            entries.map { it.first }.sortedBy { it.name.lowercase() }
        }

    fun observeLeaderboard(selectedCommunityIds: Set<String>): Flow<List<LeaderboardEntry>> {
        val cleanIds = selectedCommunityIds.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        return if (cleanIds.isEmpty()) {
            observeGlobalLeaderboard()
        } else {
            observeCommunityFilteredLeaderboard(cleanIds)
        }
    }

    private fun observeGlobalLeaderboard(): Flow<List<LeaderboardEntry>> = callbackFlow {
        val registration = users()
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val entries = snapshot?.documents
                    ?.mapNotNull { doc ->
                        doc.toObject(UserProfile::class.java)?.takeIf { it.isLeaderboardPublic }?.toLeaderboardEntry(doc.id)
                    }
                    .orEmpty()
                    .sortedWith(leaderboardComparator)

                trySend(entries)
            }

        awaitClose { registration.remove() }
    }

    private fun observeCommunityFilteredLeaderboard(
        selectedCommunityIds: Set<String>,
    ): Flow<List<LeaderboardEntry>> {
        val memberFlows = selectedCommunityIds.map { communityId ->
            observeMemberUids(communityId)
        }

        return combine(memberFlows) { arrays ->
            arrays.flatMap { it.asIterable() }.toSet()
        }.let { uidsFlow ->
            channelFlow {
                val collector = launch {
                    uidsFlow.collect { userIds ->
                        if (userIds.isEmpty()) {
                            send(emptyList())
                        } else {
                            send(fetchUsersByIds(userIds.toList()).sortedWith(leaderboardComparator))
                        }
                    }
                }
                awaitClose { collector.cancel() }
            }
        }
    }

    private fun observeMemberUids(communityId: String): Flow<List<String>> = callbackFlow {
        val registration = communities()
            .document(communityId)
            .collection("members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val userIds = snapshot?.documents?.map { it.id }.orEmpty()
                trySend(userIds)
            }

        awaitClose { registration.remove() }
    }

    private suspend fun fetchUsersByIds(userIds: List<String>): List<LeaderboardEntry> {
        val cleanIds = userIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) return emptyList()

        val results = mutableListOf<LeaderboardEntry>()
        cleanIds.chunked(10).forEach { chunk ->
            val snapshot = users()
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .await()

            snapshot.documents.forEach { doc ->
                doc.toObject(UserProfile::class.java)
                    ?.takeIf { it.isLeaderboardPublic }
                    ?.toLeaderboardEntry(doc.id)
                    ?.let(results::add)
            }
        }

        return results
    }

    private fun UserProfile.toLeaderboardEntry(documentId: String): LeaderboardEntry {
        return LeaderboardEntry(
            uid = uid.ifBlank { documentId },
            pseudo = pseudo,
            photoUrl = photoUrl,
            xpTotal = effectiveTotalXP,
        )
    }

    private companion object {
        val leaderboardComparator = compareByDescending<LeaderboardEntry> { it.xpTotal }
            .thenBy { it.pseudo.lowercase() }
            .thenBy { it.uid }
    }
}
