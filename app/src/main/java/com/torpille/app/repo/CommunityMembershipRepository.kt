package com.torpille.app.repo

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.torpille.app.data.Community
import com.torpille.app.data.Member
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/** Both clients share users/{uid}.communityIds and communities/{id}/members/{uid}. */
internal class CommunityMembershipRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) {
    private fun account() = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observe(): Flow<List<Pair<Community, Member>>> = account().flatMapLatest { uid ->
        if (uid == null) return@flatMapLatest flowOf(emptyList())
        val profileIds = callbackFlow<Result<List<String>>> {
            val registration = db.collection("users").document(uid).addSnapshotListener { doc, error ->
                trySend(if (error != null) Result.failure(error) else Result.success(
                    (doc?.get("communityIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
                ))
            }
            awaitClose { registration.remove() }
        }
        val membershipIds = callbackFlow<Result<List<String>>> {
            val registration = db.collectionGroup("members").whereEqualTo("uid", uid)
                .addSnapshotListener { snapshot, error ->
                    trySend(if (error != null) Result.failure(error) else Result.success(
                        snapshot?.documents.orEmpty().mapNotNull { doc ->
                            doc.reference.parent.parent?.takeIf { it.parent.id == "communities" }?.id
                        }
                    ))
                }
            awaitClose { registration.remove() }
        }

        combine(profileIds, membershipIds, ::membershipCommunityIds)
            .distinctUntilChanged().flatMapLatest { ids ->
                if (ids.isEmpty()) return@flatMapLatest flowOf(emptyList())
                combine(ids.map { id ->
                    val community = db.collection("communities").document(id)
                    val communityFlow = callbackFlow<Result<Community?>> {
                        val registration = community.addSnapshotListener { doc, error ->
                            trySend(if (error != null) Result.failure(error) else runCatching {
                                doc?.toObject(Community::class.java)?.copy(id = id)
                            })
                        }
                        awaitClose { registration.remove() }
                    }
                    val memberFlow = callbackFlow<Result<Member?>> {
                        val registration = community.collection("members").document(uid).addSnapshotListener { doc, error ->
                            trySend(if (error != null) Result.failure(error) else runCatching {
                                doc?.toObject(Member::class.java)?.copy(uid = uid)
                            })
                        }
                        awaitClose { registration.remove() }
                    }
                    combine(communityFlow, memberFlow) { c, m ->
                        runCatching { c.getOrThrow()?.let { value -> m.getOrThrow()?.let { value to it } } }
                    }
                }) { results ->
                    val values = results.mapNotNull { it.getOrNull() }
                    // A stale/deleted community must not hide the other valid memberships.
                    if (values.isEmpty()) results.firstOrNull { it.isFailure }?.getOrThrow()
                    values.sortedWith(compareByDescending<Pair<Community, Member>> {
                        it.second.lastOpenedAt?.toDate()?.time ?: Long.MIN_VALUE
                    }.thenByDescending { it.first.createdAt?.toDate()?.time ?: Long.MIN_VALUE }
                        .thenBy { it.first.name.lowercase() })
                }
            }
    }
}

internal fun membershipCommunityIds(profile: Result<List<String>>, memberships: Result<List<String>>): List<String> {
    val ids = (profile.getOrNull().orEmpty() + memberships.getOrNull().orEmpty())
        .map(String::trim).filter { it.isNotEmpty() && '/' !in it }.distinct()
    if (ids.isEmpty()) {
        profile.exceptionOrNull()?.let { throw it }
        memberships.exceptionOrNull()?.let { throw it }
    }
    return ids
}
