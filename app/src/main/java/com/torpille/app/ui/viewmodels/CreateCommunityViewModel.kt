package com.torpille.app.ui.viewmodels

import com.torpille.app.util.userErrorMessage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.torpille.app.repo.CommunityRepository
import kotlinx.coroutines.launch

data class CreateCommunityUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

class CreateCommunityViewModel(
    private val repo: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    var uiState by mutableStateOf(CreateCommunityUiState())
        private set

    fun create(
        name: String,
        isPublic: Boolean,
        responseTimeSeconds: Long,
        onCreated: (String) -> Unit
    ) {
        if (uiState.isLoading) return

        uiState = uiState.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val id = repo.createCommunity(name, isPublic, responseTimeSeconds)
                uiState = uiState.copy(isLoading = false, error = null)
                onCreated(id)
            } catch (e: Exception) {
                val msg = userErrorMessage(e)
                uiState = uiState.copy(isLoading = false, error = msg)
            }
        }
    }
}