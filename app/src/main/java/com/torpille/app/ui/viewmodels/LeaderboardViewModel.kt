package com.torpille.app.ui.viewmodels

import com.torpille.app.util.userErrorMessage
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.catch
import com.torpille.app.repo.LeaderboardRepository

class LeaderboardViewModel(
    private val repo: LeaderboardRepository = LeaderboardRepository(),
) : ViewModel() {
    var errorMessage by mutableStateOf<String?>(null)
        private set
    val communities = repo.observeMyCommunities().catch { errorMessage = userErrorMessage(it) }

    fun leaderboard(selectedCommunityIds: Set<String>) = repo.observeLeaderboard(selectedCommunityIds)
}
