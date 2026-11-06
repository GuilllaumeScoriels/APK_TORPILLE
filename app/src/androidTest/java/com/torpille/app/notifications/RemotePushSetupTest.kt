package com.torpille.app.notifications

import android.Manifest
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Isolated emulator setup only. Never writes an account or sends a message. */
class RemotePushSetupTest {
    @Test fun prepareEmulatorTransport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk")) { "Emulator only" }
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels.create(context)
        val token = Tasks.await(FirebaseMessaging.getInstance().token, 90, TimeUnit.SECONDS)
        assertTrue(token.isNotBlank())
        // Private app storage, extracted only by the local emulator runner and removed afterwards.
        java.io.File(context.filesDir, "emulator-fcm-token.txt").writeText(token)
    }
}
