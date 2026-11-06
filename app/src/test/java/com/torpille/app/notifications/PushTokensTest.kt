package com.torpille.app.notifications
import org.junit.Assert.*
import org.junit.Test

class PushTokensTest {
    @Test fun registeringEleventhDeviceKeepsNewTokenAndTenSlots() {
        val result = PushTokens.refreshed((1..10).map { "device-$it" }, "device-10", "new")
        assertEquals(10, result.size); assertEquals("new", result.last()); assertFalse("device-1" in result)
    }
    @Test fun refreshingSameDeviceDoesNotEvictAnotherDevice() {
        assertEquals(listOf("other", "current"), PushTokens.refreshed(listOf("current", "other"), "current", "current"))
    }
    @Test fun legacyPrimaryTokenAndWhitespaceArePreservedOnce() {
        assertEquals(listOf("other", "legacy", "current"), PushTokens.refreshed(listOf(" other ", "other", ""), "legacy", " current "))
    }
    @Test(expected = IllegalArgumentException::class) fun emptyTokenCannotOverwriteRegistration() { PushTokens.refreshed(emptyList(), null, " ") }
}
