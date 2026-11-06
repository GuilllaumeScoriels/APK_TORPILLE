package com.torpille.app.util

import com.torpille.app.TorpilleApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DeepLinkStore {
    var pendingTorpilleTargetUid: String? = null
    private val prefs get() = TorpilleApp.appContext.getSharedPreferences("pending_invitation", android.content.Context.MODE_PRIVATE)
    private val mutablePending = MutableStateFlow(load())
    val pendingLink = mutablePending.asStateFlow()
    private val mutableActivity = MutableStateFlow(prefs.getBoolean("activity", false))
    val pendingActivity = mutableActivity.asStateFlow()

    fun requestActivity() {
        consumePendingLink()
        prefs.edit().putBoolean("activity", true).apply()
        mutableActivity.value = true
    }
    fun consumeActivity(): Boolean {
        val pending = mutableActivity.value
        prefs.edit().remove("activity").apply()
        mutableActivity.value = false
        return pending
    }

    private fun load(): PendingCommunityLink? {
        val id = prefs.getString("communityId", null)
        return if (CommunityLink.validId(id)) PendingCommunityLink(id!!, prefs.getBoolean("discussion", false)) else null
    }

    fun setPendingCommunityId(id: String, opensDiscussion: Boolean = false) {
        if (!CommunityLink.validId(id)) return
        consumeActivity()
        prefs.edit().putString("communityId", id).putBoolean("discussion", opensDiscussion).apply()
        mutablePending.value = PendingCommunityLink(id, opensDiscussion)
    }

    fun consumePendingLink(expected: PendingCommunityLink? = null): PendingCommunityLink? {
        val current = mutablePending.value ?: return null
        if (expected != null && current != expected) return null
        prefs.edit().clear().apply()
        mutablePending.value = null
        return current
    }
}
