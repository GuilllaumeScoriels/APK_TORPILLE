package com.torpille.app.ui.viewmodels

import com.torpille.app.util.userErrorMessage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torpille.app.repo.CommunityRepository
import com.torpille.app.repo.UserRepository
import kotlinx.coroutines.launch

class JoinCommunityViewModel(
    private val communityRepo: CommunityRepository = CommunityRepository(),
    private val userRepo: UserRepository = UserRepository(),
) : ViewModel() {

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var requestPending by mutableStateOf(false)
        private set

    fun join(communityId: String, onJoined: () -> Unit) {
        if (loading) return
        error = null
        loading = true

        viewModelScope.launch {
            try {
                val me = userRepo.getMeOrThrow()
                check(me.hasRequiredIdentity) { "Complète ton profil avant de rejoindre une communauté." }
                val joined = communityRepo.joinCommunity(
                    communityId = communityId,
                    pseudo = me.pseudo,
                    photoUrl = me.photoUrl
                )

                loading = false
                requestPending = !joined
                if (joined) onJoined()
            } catch (e: Exception) {
                error = userErrorMessage(e)
                loading = false
            }
        }
    }
}
