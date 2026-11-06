package com.torpille.app.data
import org.junit.Assert.*
import org.junit.Test
class MessageReactionTest {
    @Test fun usesTheSameSixEmojisAsApple() {
        assertEquals(listOf("👍", "❤️", "😂", "😮", "😢", "🔥"), MessageReaction.allowed)
    }
    @Test fun legacyMessagesHaveNoReactions() { assertTrue(MessageReaction.summary(Message().reactions).isEmpty()) }
    @Test fun countsMembersInStableOrderAndIgnoresUnsupportedValues() {
        assertEquals(listOf("👍" to 2, "❤️" to 1), MessageReaction.summary(mapOf("apple" to "❤️", "android" to "👍", "other" to "👍", "invalid" to "unknown")))
    }
}
