package com.torpille.app.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TorpilleMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { PushRegistration.onTokenRefreshed(token) }
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"] ?: message.notification?.title ?: "Torpille"
        val body = message.data["body"] ?: message.notification?.body ?: "Tu as été torpillé !"
        PushNotificationPresenter.show(this, title, body, message.data["communityId"], message.data["destination"])
    }
}
