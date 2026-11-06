package com.torpille.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.torpille.app.data.AppNotification
import com.torpille.app.repo.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val repo: NotificationRepository,
) : ViewModel() {

    constructor() : this(NotificationRepository())

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                repo.observeMyNotifications().collect { list ->
                    _notifications.value = list
                    _unreadCount.value = list.count { !it.read }
                }
            }.onFailure {
                _notifications.value = emptyList()
                _unreadCount.value = 0
            }
        }
    }

    fun markAsRead(id: String) {
        viewModelScope.launch { repo.markAsRead(id) }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            runCatching { repo.markAllAsRead() }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NotificationViewModel() as T
            }
        }
    }
}
