/**
 * Rôle :
 * - Utilitaires “Flow” pour écouter Firestore de façon réactive (temps réel).
 *
 * Composants :
 * - callbackFlow + awaitClose :
 *   - transforme un listener Firestore (callback) en Kotlin Flow.
 *
 * Pourquoi comme ça :
 * - Compose s’intègre naturellement avec Flow (collectAsState).
 * - Le listener est correctement désinscrit à la fin (awaitClose) => évite les fuites mémoire.
 */
package com.torpille.app.util

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun <T> observeDoc(ref: DocumentReference, clazz: Class<T>): Flow<T?> = callbackFlow {
    val reg = ref.addSnapshotListener { snap, err ->
        if (err != null) { close(err); return@addSnapshotListener }
        trySend(snap?.toObject(clazz))
    }
    awaitClose { reg.remove() }
}
