package com.torpille.app.ui.viewmodels

import com.torpille.app.util.userErrorMessage
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torpille.app.data.UserProfile
import com.torpille.app.repo.UserRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ProfileViewModel(private val repo: UserRepository = UserRepository()) : ViewModel() {
    var me by mutableStateOf<UserProfile?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var hasLoaded by mutableStateOf(false)
        private set
    init { viewModelScope.launch {
        repo.observeMe().catch { error = userErrorMessage(it); hasLoaded = true }.collect { me = it; hasLoaded = true }
    } }
    fun save(profile: UserProfile, selectedPhotoUri: Uri?, acceptsTerms: Boolean, onSuccess: () -> Unit) {
        if (isSaving) return
        isSaving = true
        error = null
        viewModelScope.launch {
            try {
                val photo = selectedPhotoUri?.let { repo.uploadProfilePhoto(it) }
                repo.saveIdentity(if (photo != null) profile.copy(photoUrl = photo, profileIcon = null) else profile, acceptsTerms)
                onSuccess()
            } catch (e: Exception) { error = userErrorMessage(e) }
            finally { isSaving = false }
        }
    }
}
