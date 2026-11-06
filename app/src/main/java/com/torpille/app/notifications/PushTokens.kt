package com.torpille.app.notifications

/** The same ten-device limit used by the shared Firestore rules and iOS client. */
object PushTokens {
    fun refreshed(existing: List<String>, primary: String?, current: String): List<String> {
        val token = current.trim()
        require(token.isNotEmpty()) { "Jeton de notification vide." }
        return (existing + listOfNotNull(primary)).map(String::trim)
            .filter { it.isNotEmpty() && it != token }.distinct().plus(token).takeLast(10)
    }
}
