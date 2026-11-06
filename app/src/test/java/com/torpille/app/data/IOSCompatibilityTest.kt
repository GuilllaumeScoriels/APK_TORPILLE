package com.torpille.app.data

import com.google.firebase.Timestamp
import com.torpille.app.util.isMessageVisible
import com.torpille.app.util.normalizePseudoKey
import org.junit.Assert.*
import org.junit.Test

class IOSCompatibilityTest {
    private val now = 2_000_000_000L
    private val complete = UserProfile(firstName = "Jean", lastName = "Dupont", pseudo = "jean", profileIcon = "🚀",
        termsAcceptedAt = Timestamp(now, 0), moderationRulesAcceptedAt = Timestamp(now, 0))

    @Test fun legacyPseudoOnlyProfileRequiresCompletion() { assertFalse(UserProfile(pseudo = "jean", photoUrl = "https://example.com/avatar").hasRequiredIdentity) }
    @Test fun emojiIsAValidAvatarWithoutPhoto() { assertTrue(complete.hasRequiredIdentity) }
    @Test fun acceptanceAndBothNamesAreMandatory() {
        assertFalse(complete.copy(termsAcceptedAt = null).hasRequiredIdentity)
        assertFalse(complete.copy(moderationRulesAcceptedAt = null).hasRequiredIdentity)
        assertFalse(complete.copy(lastName = " ").hasRequiredIdentity)
        assertFalse(complete.copy(profileIcon = null).hasRequiredIdentity)
    }
    @Test fun xpUsesCommunityTotalsWhenAvailable() {
        assertEquals(30L, complete.copy(xpTotal = 999, communityXP = mapOf("a" to 40L, "b" to -10L)).effectiveTotalXP)
        assertEquals(20L, complete.copy(xpTotal = 20).effectiveTotalXP)
    }
    @Test fun accentedPseudoHasSameReservationAsIOS() {
        assertEquals("elodie", normalizePseudoKey(" ÉLODIE "))
        assertEquals(normalizePseudoKey("E\u0301lodie"), normalizePseudoKey("Élodie"))
        assertEquals("i", normalizePseudoKey("I"))
    }
    @Test fun unsavedDiscussionExpiresAtFourteenDays() {
        assertFalse(isMessageVisible(Message(createdAt = Timestamp(now - 14 * 86400, 0)), now))
        assertTrue(isMessageVisible(Message(createdAt = Timestamp(now - 14 * 86400 + 1, 0)), now))
    }
    @Test fun savedTextRemainsInDiscussionButOldTorpilleDoesNot() {
        val saved = Message(createdAt = Timestamp(now - 15 * 86400, 0), savedAt = Timestamp(now, 0))
        assertTrue(isMessageVisible(saved, now))
        assertFalse(isMessageVisible(saved.copy(torpilleId = "torpille"), now))
    }
    @Test fun quarantinedContentIsNeverVisibleEvenWhenSaved() {
        listOf("pending", "hidden", "rejected").forEach { status ->
            assertFalse(isMessageVisible(Message(moderationStatus = status, savedAt = Timestamp(now, 0)), now))
        }
        assertTrue(isMessageVisible(Message(moderationStatus = "visible"), now))
    }
}
