package com.torpille.app.ui.viewmodels

import com.torpille.app.util.userErrorMessage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torpille.app.data.Community
import com.torpille.app.data.UserProfile
import com.torpille.app.repo.CommunityRepository
import com.torpille.app.repo.NotificationRepository
import com.torpille.app.repo.UserRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch

class HomeViewModel(
    private val userRepo: UserRepository = UserRepository(),
    private val communityRepo: CommunityRepository = CommunityRepository(),
    private val notificationRepo: NotificationRepository = NotificationRepository(),
) : ViewModel() {
    var infoMessage by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    var errorMessage by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            runCatching { userRepo.ensureFcmTokenStored() }
        }
    }

    val me: StateFlow<UserProfile?> = userRepo.observeMe()
        .catch { errorMessage = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val communities: StateFlow<List<Community>> = communityRepo.observeCommunitiesForMe()
        .catch { errorMessage = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pinnedCommunityIds: StateFlow<List<String>> = me
        .map { profile -> profile?.pinnedCommunityIds?.take(3) ?: emptyList() }
        .catch { errorMessage = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadNotificationsCount: StateFlow<Int> = notificationRepo.observeMyNotifications()
        .map { list -> list.count { !it.read } }
        .catch { errorMessage = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val orderedCommunities: StateFlow<List<Community>> = combine(communities, pinnedCommunityIds) { values, pinnedIds ->
            HomeCommunityPresentation.orderedCommunities(
                communities = values,
                pinnedCommunityIds = pinnedIds
            )
        }
        .catch { errorMessage = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val homeCommunities: StateFlow<List<Community>> = combine(communities, pinnedCommunityIds) { values, pinnedIds ->
            HomeCommunityPresentation.homeCommunities(
                communities = values,
                pinnedCommunityIds = pinnedIds
            )
        }
        .catch { errorMessage = userErrorMessage(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun isPinned(community: Community, currentPinnedIds: List<String>): Boolean {
        return currentPinnedIds.contains(community.id)
    }

    fun togglePinnedCommunity(community: Community, currentPinnedIds: List<String>) {
        errorMessage = null
        infoMessage = null

        if (currentPinnedIds.contains(community.id)) {
            viewModelScope.launch {
                runCatching {
                    userRepo.updatePinnedCommunities(currentPinnedIds.filter { it != community.id })
                }.onSuccess {
                    infoMessage = "Communauté retirée de l’accueil."
                }.onFailure { error ->
                    errorMessage = userErrorMessage(error)
                }
            }
            return
        }

        if (currentPinnedIds.size >= 3) {
            errorMessage = "Tu peux épingler jusqu’à 3 communautés."
            return
        }

        viewModelScope.launch {
            runCatching {
                userRepo.updatePinnedCommunities(currentPinnedIds + community.id)
            }.onSuccess {
                infoMessage = "Communauté épinglée dans l’accueil."
            }.onFailure { error ->
                errorMessage = userErrorMessage(error)
            }
        }
    }
}
