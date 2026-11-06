package com.torpille.app.ui.components

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.torpille.app.notifications.NotificationChannels
import com.torpille.app.notifications.PushRegistration
import com.torpille.app.util.t
import kotlinx.coroutines.launch

@Composable
fun DeviceNotificationSettingsCard() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val enabled = remember(revision) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val channel = remember(revision) { context.getSystemService(NotificationManager::class.java).getNotificationChannel(NotificationChannels.ALERTS) }
    val registration by PushRegistration.state.collectAsState()
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Notifications sur cet appareil"), style = MaterialTheme.typography.titleMedium)
            Text(t(when {
                !enabled || channel?.importance == NotificationManager.IMPORTANCE_NONE -> "Les notifications sont désactivées. Autorise-les dans les réglages Android."
                channel?.sound == null || (channel?.importance ?: 0) < NotificationManager.IMPORTANCE_HIGH -> "Active le son et les alertes dans les réglages de notifications de Torpille."
                channel?.lockscreenVisibility == android.app.Notification.VISIBILITY_SECRET -> "Active l’affichage sur l’écran verrouillé dans les réglages Android."
                else -> "Le canal Torpille est configuré pour les alertes sonores."
            }))
            Text(t(if (registration.registered) "Cet appareil est enregistré pour recevoir les notifications."
                else "L’enregistrement de cet appareil pour les notifications n’est pas encore confirmé."), style = MaterialTheme.typography.bodySmall)
            registration.error?.let { Text(t(it), color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text(t("Ouvrir les réglages de notifications")) }
            TextButton(enabled = !registration.registering, onClick = { scope.launch { PushRegistration.registerCurrentDevice() } }) {
                Text(t(if (registration.registering) "Activation…" else "Réessayer l’activation"))
            }
            Text(t("Le mode silencieux et Ne pas déranger peuvent couper le son des notifications."), style = MaterialTheme.typography.bodySmall)
        }
    }
}
