package com.torpille.app.notifications

import com.torpille.app.data.Message
import com.torpille.app.data.Torpille

object NotificationActivityPolicy {
    fun includes(message: Message, uid: String, mode: String): Boolean {
        if (message.systemEvent == "settingsChanged") return true
        if (message.type == "system" || message.senderUid == uid) return false
        // The dedicated torpille observer supplies this item once, like iOS.
        if (!message.torpilleId.isNullOrBlank() && message.taggedUid == uid) return false
        return mode == "all" || (mode == "mentionsOnly" && message.taggedUid == uid)
    }
    fun directlyReceived(torpille: Torpille, uid: String): Boolean =
        uid.isNotBlank() && torpille.toUid == uid && torpille.fromUid != uid
}
