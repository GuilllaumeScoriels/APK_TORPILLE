package com.torpille.app.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.torpille.app.MainActivity
import com.torpille.app.util.DeepLinkStore
import com.torpille.app.util.PendingCommunityLink
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

/** Emulator only: fictitious invitations and locally injected alerts; no user account. */
class NotificationCompatibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    @Before fun permissions() {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }
    @After fun cleanup() {
        context.getSystemService(NotificationManager::class.java).cancel(7001)
        DeepLinkStore.consumePendingLink()
        DeepLinkStore.consumeActivity()
    }
    @Test fun localAlertUsesAudibleHighPriorityChannelAndExpectedDestination() {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val permissionDeadline = android.os.SystemClock.elapsedRealtime() + 5000
        while (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() && android.os.SystemClock.elapsedRealtime() < permissionDeadline) android.os.SystemClock.sleep(50)
        assertTrue("Notification permission must be enabled before posting", androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
        NotificationChannels.create(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(NotificationChannels.ALERTS)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertNotNull(channel.sound)
        PushNotificationPresenter.show(context,"Torpille de test","Alerte locale sur émulateur","fixture_community","communityDiscussion",7001)
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        var active = manager.activeNotifications.firstOrNull { it.id == 7001 }
        while (active == null && android.os.SystemClock.elapsedRealtime() < deadline) {
            android.os.SystemClock.sleep(50)
            active = manager.activeNotifications.firstOrNull { it.id == 7001 }
        }
        assertNotNull("The local notification must be posted within five seconds", active)
        val posted = requireNotNull(active).notification
        assertEquals(Notification.CATEGORY_MESSAGE, posted.category)
        assertEquals(Notification.VISIBILITY_PRIVATE, posted.visibility)
        assertEquals(NotificationChannels.ALERTS, posted.channelId)
        assertNotNull(posted.contentIntent)
    }
    @Test fun invitationRemainsPendingThroughActivityRecreationBeforeLogin() {
        val intent=Intent(Intent.ACTION_VIEW,Uri.parse("https://torpille-38783.web.app/join?cid=fixture_community&lang=fr"),context,MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { assertEquals(PendingCommunityLink("fixture_community"),DeepLinkStore.pendingLink.value) }
            scenario.recreate()
            scenario.onActivity {
                assertEquals("fixture_community",context.getSharedPreferences("pending_invitation",Context.MODE_PRIVATE).getString("communityId",null))
                assertEquals(PendingCommunityLink("fixture_community"),DeepLinkStore.pendingLink.value)
            }
        }
    }
    @Test fun backgroundFcmTapOpensTheTargetCommunityDiscussion() {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("communityId", "fixture_community")
            putExtra("destination", "communityDiscussion")
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity {
                assertEquals("fixture_community", DeepLinkStore.pendingLink.value?.id)
                assertTrue(DeepLinkStore.pendingLink.value?.opensDiscussion == true)
            }
        }
    }
    @Test fun activityNotificationTapOpensActivityInsteadOfJoiningACommunity() {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("destination", "notificationCenter")
            putExtra("communityId", "fixture_community")
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { assertTrue(DeepLinkStore.pendingActivity.value) }
        }
    }

}
