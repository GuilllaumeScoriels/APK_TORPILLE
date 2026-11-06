package com.torpille.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.torpille.app.repo.CommunityRepository
import com.torpille.app.repo.SocialRepository
import com.torpille.app.repo.NotificationPreference
import com.torpille.app.util.observeDoc
import com.torpille.app.util.t
import com.torpille.app.util.UserFacingError
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityNotificationPreferences(onDismiss: () -> Unit) {
    val repository = remember { CommunityRepository() }
    val communities by remember { repository.observeCommunitiesForMe() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(t("Préférences par communauté"), style = MaterialTheme.typography.titleLarge) }
            item { Text(t("Choisir quand chaque communauté peut te notifier.")) }
            item { Text(t("Les torpilles qui te sont adressées restent toujours notifiées."), style = MaterialTheme.typography.bodySmall) }
            if (communities.isEmpty()) item { Text(t("Rejoins ou crée une communauté pour configurer tes notifications.")) }
            error?.let { message -> item { Text(t(message), color = MaterialTheme.colorScheme.error) } }
            items(communities, key = { it.id }) { community ->
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                val preference by remember(uid, community.id) {
                    observeDoc(FirebaseFirestore.getInstance().collection("users").document(uid).collection("notificationPreferences").document(community.id), NotificationPreference::class.java)
                }.collectAsState(initial = null)
                Column {
                    Text(community.name, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("all" to "Tout", "mentionsOnly" to "Seulement moi").forEach { (mode, label) ->
                            FilterChip(selected = (preference?.mode ?: "all") == mode, enabled = saving == null,
                                onClick = { scope.launch {
                                    saving = community.id
                                    try { SocialRepository().notificationMode(community.id, community.name, mode); error = null }
                                    catch (e: Exception) { error = UserFacingError.message(e) }
                                    finally { saving = null }
                                } }, label = { Text(t(label)) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
