package com.torpille.app.ui.screens

import com.torpille.app.util.t
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.torpille.app.data.AppNotification
import com.torpille.app.ui.viewmodels.NotificationViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val interestingNotificationTypes = setOf(
    "torpilled",
    "xp_gained",
    "xp_lost",
    "profile_updated",
    "community_joined",
)

private data class NotificationFilter(
    val key: String,
    val label: String,
)

private val notificationFilters = listOf(
    NotificationFilter(key = "all", label = "Toutes"),
    NotificationFilter(key = "torpilled", label = "Torpillé"),
    NotificationFilter(key = "xp", label = "XP"),
    NotificationFilter(key = "profile", label = "Profil"),
    NotificationFilter(key = "community", label = "Communautés"),
)

@Composable
fun NotificationScreen(
    onBack: () -> Unit,
    onOpenCommunity: (String) -> Unit,
    vm: NotificationViewModel = viewModel(factory = NotificationViewModel.Factory),
) {
    var showFriends by remember { mutableStateOf(false) }
    var showPreferences by remember { mutableStateOf(false) }
    if (showFriends) {
        androidx.activity.compose.BackHandler { showFriends = false }
        FriendsScreen(onBack = { showFriends = false }, initialTab = 1)
        return
    }
    if (showPreferences) com.torpille.app.ui.components.CommunityNotificationPreferences { showPreferences = false }
    val notifications by vm.notifications.collectAsState()
    var selectedFilter by remember { mutableStateOf("all") }
    LaunchedEffect(Unit) { com.torpille.app.notifications.PushRegistration.registerCurrentDevice() }
    val filtered = remember(notifications, selectedFilter) {
        notifications.filter { when (selectedFilter) {
            "all" -> true
            "xp" -> it.type == "xp_gained" || it.type == "xp_lost"
            "profile" -> it.type == "profile_updated"
            "community" -> it.type == "community_joined" || it.type == "communityJoinRequests"
            else -> it.type == selectedFilter
        } }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = t("Retour")) }
                    Text(t("Notifications"), style = MaterialTheme.typography.titleLarge)
                }
                TextButton(onClick = { vm.markAllAsRead() }) { Text(t("Tout lu")) }
            }
        }
        item { com.torpille.app.ui.components.DeviceNotificationSettingsCard() }
        item { TextButton(onClick = { showPreferences = true }) { Text(t("Préférences par communauté")) } }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notificationFilters, key = { it.key }) { filter ->
                    FilterChip(selected = selectedFilter == filter.key, onClick = { selectedFilter = filter.key }, label = { Text(t(filter.label)) })
                }
            }
        }
        if (filtered.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) { Text(t("Les nouvelles notifications reçues apparaîtront ici."), Modifier.padding(16.dp)) }
        }
        items(filtered, key = { it.id }) { notification ->
            Column {
                NotificationItem(notification)
                if (notification.type == "friendRequests") TextButton(onClick = { vm.markAsRead(notification.id); showFriends = true }) { Text(t("Demandes d’amis")) }
                notification.communityId?.let { id ->
                    TextButton(onClick = { vm.markAsRead(notification.id); onOpenCommunity(id) }) { Text(t("Ouvrir la communauté")) }
                }
            }
        }
        item { Text(t("Les notifications sont automatiquement supprimées après deux semaines."), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun NotificationItem(notification: AppNotification) {
    val badge = notification.badge()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium
                        )
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badge.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(notification.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = badge.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(notification.body, style = MaterialTheme.typography.bodyMedium)

            val details = buildString {
                if (!notification.communityName.isNullOrBlank()) {
                    append(notification.communityName)
                }
                if (notification.xpDelta != 0L) {
                    if (isNotBlank()) append(" • ")
                    append(if (notification.xpDelta > 0) "+" else "")
                    append(notification.xpDelta)
                    append(" XP")
                }
            }

            if (details.isNotBlank()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val date = notification.createdAt?.toDate()?.let {
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it)
            }.orEmpty()

            if (date.isNotBlank()) {
                Text(
                    date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class NotificationBadge(
    val label: String,
    val icon: ImageVector,
)

private fun AppNotification.badge(): NotificationBadge = when (type) {
    "torpilled" -> NotificationBadge("Torpille reçue", Icons.Default.Notifications)
    "xp_gained" -> NotificationBadge("XP gagné", Icons.Default.Campaign)
    "xp_lost" -> NotificationBadge("XP perdu", Icons.Default.Warning)
    "profile_updated" -> NotificationBadge("Profil", Icons.Default.Person)
    "community_joined" -> NotificationBadge("Communauté", Icons.Default.Groups)
    else -> NotificationBadge("Info", Icons.Default.Notifications)
}

private fun notificationPermissionGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        true
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
