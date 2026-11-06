/**
 * Rôle :
 * - Écran Communauté : messages + membres + actions d’envoi (texte / vidéo / torpille).
 *
 * Composants :
 * - messages(communityId) : Flow<List<Message>> temps réel.
 * - members(communityId) : Flow<List<Member>> temps réel.
 * - sendText(...) / sendVideoTorpille(...) / respondWithVideo(...) :
 *   - délègue au CommunityRepository.
 *
 * Pourquoi comme ça :
 * - Encapsule la logique d’envoi et limite la quantité de code Firebase dans l’UI Compose.
 */
package com.torpille.app.ui.viewmodels

import com.torpille.app.util.userErrorMessage
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.torpille.app.data.Member
import com.torpille.app.data.Message
import com.torpille.app.data.Torpille
import com.torpille.app.data.UnseenTorpilleStory
import com.torpille.app.data.PublishedReel
import com.torpille.app.repo.CommunityRepository
import com.torpille.app.repo.UserRepository
import com.torpille.app.util.observeDoc
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class CommunityViewModel(
    private val communityRepo: CommunityRepository = CommunityRepository(),
    private val userRepo: UserRepository = UserRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ViewModel() {

    var isSending by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    private val messagesCache = mutableMapOf<String, StateFlow<List<Message>>>()
    private val membersCache = mutableMapOf<String, StateFlow<List<Member>>>()
    private val myMemberCache = mutableMapOf<String, StateFlow<Member?>>()
    private val torpillesCache = mutableMapOf<String, StateFlow<List<Torpille>>>()
    private val reelsFlow by lazy {
        communityRepo
            .observePublishedReels()
            .catch { error = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }
    private val accountUnseenTorpilles by lazy {
        communityRepo
            .observeAccountUnseenTorpilles()
            .catch { error = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    fun messages(communityId: String): StateFlow<List<Message>> =
        messagesCache.getOrPut(communityId) {
            communityRepo
                .observeMessages(communityId)
                .catch { error = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    fun members(communityId: String): StateFlow<List<Member>> =
        membersCache.getOrPut(communityId) {
            communityRepo
                .observeMembersEnriched(communityId)
                .catch { error = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    fun torpilles(communityId: String): StateFlow<List<Torpille>> =
        torpillesCache.getOrPut(communityId) {
            communityRepo
                .observeRecentTorpilles(communityId)
                .catch { error = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    fun myMember(communityId: String): StateFlow<Member?> =
        myMemberCache.getOrPut(communityId) {
            val uid = auth.currentUser?.uid
            if (uid == null) {
                flowOf(null).catch { error = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
            } else {
                val doc = FirebaseFirestore.getInstance()
                    .collection("communities").document(communityId)
                    .collection("members").document(uid)

                observeDoc(doc, Member::class.java)
                    .catch { error = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
            }
        }

    fun accountUnseenTorpilles(): StateFlow<List<UnseenTorpilleStory>> = accountUnseenTorpilles

    fun publishedReels(): StateFlow<List<PublishedReel>> = reelsFlow

    fun sendText(communityId: String, senderPseudo: String, text: String) {
        if (isSending) return
        isSending = true
        error = null
        viewModelScope.launch {
            try {
                communityRepo.sendText(communityId, senderPseudo, text)
            } catch (e: Exception) {
                error = userErrorMessage(e)
            } finally { isSending = false }
        }
    }


    fun sendAudioMessage(
        communityId: String,
        senderPseudo: String,
        audioUri: Uri,
    ) {
        if (isSending) return
        isSending = true
        error = null
        viewModelScope.launch {
            try {
                communityRepo.sendAudioMessage(communityId, senderPseudo, audioUri)
            } catch (e: Exception) {
                error = userErrorMessage(e)
            } finally { isSending = false }
        }
    }

    fun sendImageMessage(
        communityId: String,
        senderPseudo: String,
        imageUri: Uri,
    ) {
        if (isSending) return
        isSending = true
        error = null
        viewModelScope.launch {
            try {
                communityRepo.sendImageMessage(communityId, senderPseudo, imageUri)
            } catch (e: Exception) {
                error = userErrorMessage(e)
            } finally { isSending = false }
        }
    }

    fun sendVideoMessage(
        communityId: String,
        senderPseudo: String,
        videoUri: Uri,
    ) {
        if (isSending) return
        isSending = true
        error = null
        viewModelScope.launch {
            try {
                communityRepo.sendVideoMessage(communityId, senderPseudo, videoUri)
            } catch (e: Exception) {
                error = userErrorMessage(e)
            } finally { isSending = false }
        }
    }

    fun sendVideoTorpille(
        communityId: String,
        senderPseudo: String,
        videoUri: Uri,
        taggedUid: String,
        taggedPseudo: String,
        tagX: Double,
        tagY: Double,
        publishToReels: Boolean = false,
    ) {
        if (isSending) return
        isSending = true
        error = null
        viewModelScope.launch {
            try {
                communityRepo.sendVideoTorpille(
                    communityId = communityId,
                    senderPseudo = senderPseudo,
                    videoUri = videoUri,
                    taggedUid = taggedUid,
                    taggedPseudo = taggedPseudo,
                    tagX = tagX,
                    tagY = tagY,
                    publishToReels = publishToReels,
                )
            } catch (e: Exception) {
                Log.e("TORPILLE", "sendVideoTorpille failed", e)
                error = userErrorMessage(e)
            } finally { isSending = false }
        }
    }

    fun respond(
        communityId: String,
        senderPseudo: String,
        videoUri: Uri,
        pendingTorpilleId: String,
        nextTaggedUid: String,
        nextTaggedPseudo: String,
        tagX: Double,
        tagY: Double,
        publishToReels: Boolean = false,
    ) {
        if (isSending) return
        isSending = true
        error = null
        viewModelScope.launch {
            try {
                Log.d("TORPILLE", "respond start: torpilleId=$pendingTorpilleId, videoUri=$videoUri")
                communityRepo.respondWithVideo(
                    communityId = communityId,
                    senderPseudo = senderPseudo,
                    responseVideoUri = videoUri,
                    pendingTorpilleId = pendingTorpilleId,
                    nextTaggedUid = nextTaggedUid,
                    nextTaggedPseudo = nextTaggedPseudo,
                    tagX = tagX,
                    tagY = tagY,
                    publishToReels = publishToReels,
                )
                Log.d("TORPILLE", "respond success")
            } catch (e: Exception) {
                Log.e("TORPILLE", "respond failed", e)
                error = userErrorMessage(e)
            } finally { isSending = false }
        }
    }

    fun markTorpillesSeen(communityId: String) {
        viewModelScope.launch {
            try {
                communityRepo.markTorpillesSeen(communityId)
            } catch (e: Exception) {
                com.torpille.app.util.logDiagnostic(e)
            }
        }
    }

    fun markTorpillesSeen(communityIds: List<String>) {
        viewModelScope.launch {
            try {
                communityRepo.markTorpillesSeen(communityIds)
            } catch (e: Exception) {
                com.torpille.app.util.logDiagnostic(e)
            }
        }
    }

    fun syncXPState(communityId: String) {
        viewModelScope.launch {
            try {
                communityRepo.syncXPState(communityId)
            } catch (e: Exception) {
                com.torpille.app.util.logDiagnostic(e)
            }
        }
    }

    suspend fun getAudioPlaybackUrl(
        audioPath: String,
        audioBucket: String?,
    ): String {
        return communityRepo.getAudioPlaybackUrl(audioPath, audioBucket)
    }

    suspend fun getSignedPlaybackUrl(
        videoPath: String,
        videoBucket: String?,
    ): String {
        return communityRepo.getSignedPlaybackUrl(videoPath, videoBucket)
    }

    suspend fun getTorpilleById(
        communityId: String,
        torpilleId: String,
    ): Torpille? {
        return communityRepo.getTorpilleById(communityId, torpilleId)
    }
}
