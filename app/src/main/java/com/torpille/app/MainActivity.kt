package com.torpille.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.torpille.app.ui.AppRoot
import com.torpille.app.ui.theme.TorpilleTheme
import com.torpille.app.util.DeepLinkStore
import com.torpille.app.util.LocationSyncManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            syncPresenceAtStartup()
            lifecycleScope.launch { com.torpille.app.notifications.PushRegistration.registerCurrentDevice() }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) handleDeepLink(intent)
        requestNotificationsPermissionIfNeeded()

        setContent {
            TorpilleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
            lifecycleScope.launch {
                runCatching { com.torpille.app.repo.UserRepository().ensureFcmTokenStored() }
            }
        }
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.isEmailVerified == true) syncPresenceAtStartup()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent?.getStringExtra("destination") == "notificationCenter") {
            DeepLinkStore.requestActivity()
            return
        }
        val notificationCommunity = intent?.getStringExtra("notificationCommunityId") ?: intent?.getStringExtra("communityId")
        if (com.torpille.app.util.CommunityLink.validId(notificationCommunity)) {
            DeepLinkStore.setPendingCommunityId(notificationCommunity!!, opensDiscussion = true)
            return
        }
        intent?.data?.toString()?.let(com.torpille.app.util.CommunityLink::parse)?.let {
            DeepLinkStore.setPendingCommunityId(it.id)
        }
    }

    private fun syncPresenceAtStartup() {
        lifecycleScope.launch {
            // Match iOS: location is requested only from the map, never at launch.
            runCatching { LocationSyncManager.syncPresenceIfPossible() }
        }
    }

    private fun requestNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) {
            syncPresenceAtStartup()
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else syncPresenceAtStartup()
    }
}
