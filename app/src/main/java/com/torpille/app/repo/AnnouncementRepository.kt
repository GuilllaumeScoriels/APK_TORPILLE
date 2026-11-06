package com.torpille.app.repo

import com.google.firebase.firestore.FirebaseFirestore
import com.torpille.app.data.HomeAnnouncement
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

class AnnouncementRepository {
    fun observe() = callbackFlow {
        val listener = FirebaseFirestore.getInstance().collection("appAnnouncements")
            .whereEqualTo("published", true).addSnapshotListener { snapshot, error ->
                // An unavailable announcement must never prevent access to the home screen.
                trySend(if (error != null) emptyList() else snapshot?.documents.orEmpty()
                    .mapNotNull { HomeAnnouncement.from(it.id, it.data.orEmpty()) }
                    .sortedWith(compareByDescending<HomeAnnouncement> { it.priority }.thenBy { it.id }))
            }
        awaitClose { listener.remove() }
    }
}
