/**
 * Rôle :
 * - Écran “Infos communauté” : paramètres admin + liste membres (tri XP) + statut couleurs.
 *
 * Composants :
 * - community(communityId) : observe le doc Community.
 * - members(communityId) : observe les membres triés par xpInCommunity.
 * - saveSettings(...) : met à jour isPublic et responseTimeSeconds (admin only).
 *
 * Pourquoi comme ça :
 * - Centralise le contrôle “admin” et les mises à jour Firestore.
 */
package com.torpille.app.ui.viewmodels

import com.torpille.app.util.userErrorMessage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.torpille.app.data.Community
import com.torpille.app.data.Member
import com.torpille.app.repo.CommunityRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class CommunityInfoViewModel(
    private val repo: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    val authUid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    private val communityCache = mutableMapOf<String, StateFlow<Community?>>() // Map = ensemble de clés/valeurs
    private val membersCache = mutableMapOf<String, StateFlow<List<Member>>>()
    private val allowlistCache = mutableMapOf<String, StateFlow<List<String>>>()

    fun community(communityId: String): StateFlow<Community?> =
        communityCache.getOrPut(communityId) {
            repo.observeCommunity(communityId)
                .catch { error = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        }

    fun members(communityId: String): StateFlow<List<Member>> =
        membersCache.getOrPut(communityId) {
            repo.observeMembersEnriched(communityId)
                .catch { error = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    fun allowlist(communityId: String): StateFlow<List<String>> =
        allowlistCache.getOrPut(communityId) {
            repo.observeAllowlist(communityId)
                .catch { error = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    var error by mutableStateOf<String?>(null)
        private set

    fun inviteLink(communityId: String): String = repo.inviteLinkFor(communityId)

    fun saveSettings(communityId: String, isPublic: Boolean, responseTimeSeconds: Long, eliminationMode: Boolean) {
        error = null
        viewModelScope.launch {
            try {
                repo.updateCommunitySettings(communityId, isPublic, responseTimeSeconds, eliminationMode)
            } catch (e: Exception) {
                error = userErrorMessage(e)
            }
        }
    }

    fun saveAllowlist(communityId: String, pseudosRaw: String) {
        error = null
        val keys = pseudosRaw
            .split(",", "\n", ";", " ")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()

        viewModelScope.launch {
            try {
                repo.replaceAllowlist(communityId, keys)
            } catch (e: Exception) {
                error = userErrorMessage(e)
            }
        }
    }
}