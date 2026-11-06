package com.torpille.app.ui.screens

import androidx.test.platform.app.InstrumentationRegistry
import com.torpille.app.util.DeviceLegalAcceptance
import org.junit.Assert.*
import org.junit.Test

class DeviceLegalAcceptanceTest {
    @Test fun consentPersistsForOneAccountWithoutAuthorizingAnother() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uid = "test-legal-" + java.util.UUID.randomUUID()
        val prefs = context.getSharedPreferences("device_legal_acceptance", 0)
        try {
            assertFalse(DeviceLegalAcceptance.hasAccepted(uid))
            DeviceLegalAcceptance.record(uid)
            assertTrue(DeviceLegalAcceptance.hasAccepted(uid))
            assertFalse(DeviceLegalAcceptance.hasAccepted("$uid-other-account"))
            assertFalse(DeviceLegalAcceptance.hasAccepted(null))
            // Reopen the on-disk preference file, as on a later launch/sign-in.
            assertEquals("2026-09-27", context.createPackageContext(context.packageName, 0)
                .getSharedPreferences("device_legal_acceptance", 0).getString(uid, null))
        } finally { prefs.edit().remove(uid).commit() }
    }
}
