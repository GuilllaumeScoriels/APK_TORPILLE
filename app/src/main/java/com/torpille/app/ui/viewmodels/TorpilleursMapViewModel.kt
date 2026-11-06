package com.torpille.app.ui.viewmodels

import com.torpille.app.util.userErrorMessage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torpille.app.data.Community
import com.torpille.app.data.Member
import com.torpille.app.repo.CommunityRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class TorpilleursMapViewModel(
    private val repo: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    var error by mutableStateOf<String?>(null)
        private set

    val communities: StateFlow<List<Community>> =
        repo.observeCommunitiesForMe()
            .catch { error = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val membersCache = mutableMapOf<String, StateFlow<List<Member>>>()
    private val multiMembersCache = mutableMapOf<String, StateFlow<List<Member>>>()

    fun members(communityId: String): StateFlow<List<Member>> {
        if (communityId.isBlank()) {
            return flowOf<List<Member>>(emptyList())
                .catch { error = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        }

        return membersCache.getOrPut(communityId) {
            repo.observeMembersForMap(communityId)
                .catch { error = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }
    }

    fun membersForCommunities(communityIds: List<String>): StateFlow<List<Member>> {
        val ids = communityIds.filter { it.isNotBlank() }.distinct().sorted()
        if (ids.isEmpty()) {
            return flowOf<List<Member>>(emptyList())
                .catch { error = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        }

        val key = ids.joinToString("|")
        return multiMembersCache.getOrPut(key) {
            repo.observeMembersForMapMulti(ids)
                .catch { error = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }
    }

    fun saveMyLocationForCommunities(
        communityIds: List<String>,
        latitude: Double,
        longitude: Double,
    ) {
        error = null
        viewModelScope.launch {
            try {
                repo.updateMyLocationInCommunities(
                    communityIds = communityIds,
                    latitude = latitude,
                    longitude = longitude
                )
            } catch (e: Exception) {
                error = userErrorMessage(e)
            }
        }
    }

    fun markMeOpenedForCommunities(communityIds: List<String>) {
        error = null
        viewModelScope.launch {
            try {
                repo.markMeOpenedInCommunities(communityIds)
            } catch (e: Exception) {
                error = userErrorMessage(e)
            }
        }
    }
}
