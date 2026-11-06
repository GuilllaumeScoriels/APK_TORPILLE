package com.torpille.app.util

import com.torpille.app.data.Message

fun isMessageVisible(message: Message, nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
    if (message.moderationStatus != null && message.moderationStatus != "visible") return false
    val expired = message.createdAt?.seconds?.let { nowSeconds - it >= 14 * 86400L } ?: false
    return if (!message.torpilleId.isNullOrBlank()) !expired else message.savedAt != null || !expired
}

fun validateCommunityText(text: String) {
    var normalized = normalizePseudoKey(text)
    mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't').forEach { (digit, letter) -> normalized = normalized.replace(digit, letter) }
    val compact = normalized.filter(Char::isLetter)
    val blocked = listOf("porn", "porno", "xxx", "nude", "nudes", "sexe", "suicide", "kill yourself", "va te tuer", "pute", "connard", "salope")
    require(blocked.none { normalized.contains(it) || compact.contains(it.filter(Char::isLetter)) }) {
        "Ce contenu ne respecte pas les règles de la communauté."
    }
}
