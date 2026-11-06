package com.torpille.app.notifications
import com.torpille.app.data.Message
import com.torpille.app.data.Torpille
import org.junit.Assert.*
import org.junit.Test

class NotificationActivityPolicyTest {
    @Test fun targetedTorpilleIsIndependentOfDiscussionPreference() {
        assertTrue(NotificationActivityPolicy.directlyReceived(Torpille(fromUid="sender", toUid="me"), "me"))
        assertFalse(NotificationActivityPolicy.directlyReceived(Torpille(fromUid="me", toUid="me"), "me"))
        assertFalse(NotificationActivityPolicy.directlyReceived(Torpille(fromUid="sender", toUid="other"), "me"))
    }
    @Test fun mirroredMessageDoesNotDuplicateDirectTorpille() {
        val message=Message(senderUid="sender", taggedUid="me", torpilleId="t1", type="video")
        for (mode in listOf("all", "mentionsOnly", "none")) assertFalse(NotificationActivityPolicy.includes(message,"me",mode))
        assertTrue(NotificationActivityPolicy.includes(message,"observer","all"))
    }
    @Test fun normalMessagesAndSettingsRetainIOSPolicy() {
        val message=Message(senderUid="sender")
        assertTrue(NotificationActivityPolicy.includes(message,"me","all"))
        assertFalse(NotificationActivityPolicy.includes(message,"me","mentionsOnly"))
        assertTrue(NotificationActivityPolicy.includes(message.copy(taggedUid="me"),"me","mentionsOnly"))
        assertFalse(NotificationActivityPolicy.includes(message.copy(taggedUid="me"),"me","none"))
        assertTrue(NotificationActivityPolicy.includes(message.copy(type="system",systemEvent="settingsChanged"),"me","none"))
    }
}
