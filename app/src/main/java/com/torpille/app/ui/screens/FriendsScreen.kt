package com.torpille.app.ui.screens

import com.torpille.app.util.userErrorMessage
import com.torpille.app.util.t
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.torpille.app.data.UserProfile
import com.torpille.app.repo.SocialRepository
import com.torpille.app.ui.components.ProfileAvatar
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun FriendsScreen(onBack: () -> Unit, initialTab: Int = 0) {
    var showPhoneContacts by remember { mutableStateOf(false) }
    if (showPhoneContacts) {
        androidx.activity.compose.BackHandler { showPhoneContacts = false }
        PhoneContactsScreen(onBack = { showPhoneContacts = false })
        return
    }
    var publicUid by remember { mutableStateOf<String?>(null) }
    if (publicUid != null) { androidx.activity.compose.BackHandler { publicUid = null }; PublicProfileScreen(publicUid!!, onBack = { publicUid = null }); return }
    val repo = remember { SocialRepository() }
    val scope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val friends by remember { repo.friends().catch { feedback = userErrorMessage(it) } }.collectAsState(emptyList())
    val requests by remember { repo.requests().catch { feedback = userErrorMessage(it) } }.collectAsState(emptyList())
    val blocked by remember { repo.blocked().catch { feedback = userErrorMessage(it) } }.collectAsState(emptyList())
    var tab by remember { mutableIntStateOf(initialTab) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var removeId by remember { mutableStateOf<String?>(null) }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch { try { block(); feedback = null } catch (e: Exception) { feedback = userErrorMessage(e) } finally { busy = false } }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text(t("Retour")) }; Text(t("Amis"), style = MaterialTheme.typography.headlineLarge) }
        item { TabRow(tab) { listOf("Amis", "Demandes", "Bloqués").forEachIndexed { index, title -> Tab(tab == index, { tab = index }, text = { Text(title) }) } } }
        feedback?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (tab == 0) {
            item { Button(onClick = { showPhoneContacts = true }) { Text("Retrouver mes contacts par téléphone") } }
            item {
                OutlinedTextField(query, { query = it }, label = { Text(t("Rechercher un pseudo")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TextButton(onClick = { action { results = repo.search(query) } }, enabled = !busy && query.trim().length >= 2) { Text(t("Rechercher")) }
            }
            items(results, key = { "search_${it.uid}" }) { profile ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileAvatar(profile.photoUrl, profile.profileIcon, 44.dp, Modifier.clickable { publicUid = profile.uid })
                    Column(Modifier.weight(1f)) { Text("@${profile.pseudo}"); Text("${profile.friendsCount} ${t("Amis")}") }
                    TextButton(onClick = { action { repo.requestFriend(profile); results = results.filter { it.uid != profile.uid } } }, enabled = !busy) { Text(t("Ajouter")) }
                }
            }
            if (friends.isEmpty()) item { Text(t("Aucun ami pour le moment.")) }
            items(friends, key = { "friend_${it.id}" }) { friend ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileAvatar(friend.getString("photoUrl"), friend.getString("profileIcon"), 44.dp, Modifier.clickable { publicUid = friend.id })
                    Text("@${friend.getString("pseudo").orEmpty()}", Modifier.weight(1f))
                    TextButton(onClick = { removeId = friend.id }, enabled = !busy) { Text(t("Retirer")) }
                }
            }
        } else if (tab == 1) {
            if (requests.isEmpty()) item { Text(t("Aucune demande en attente.")) }
            items(requests, key = { it.id }) { request ->
                Column {
                    Text("@${request.getString("fromPseudo").orEmpty()}")
                    Row { TextButton(onClick = { action { repo.resolveFriend(request, true) } }, enabled = !busy) { Text(t("Accepter")) }
                        TextButton(onClick = { action { repo.resolveFriend(request, false) } }, enabled = !busy) { Text(t("Refuser")) } }
                }
            }
        } else {
            if (blocked.isEmpty()) item { Text(t("Aucun utilisateur bloqué.")) }
            items(blocked, key = { it.id }) { member -> Row(Modifier.fillMaxWidth()) {
                Text("@${member.getString("pseudo").orEmpty()}", Modifier.weight(1f))
                TextButton(onClick = { action { repo.unblock(member.id) } }, enabled = !busy) { Text(t("Débloquer")) }
            } }
        }
    }
    removeId?.let { id -> AlertDialog(onDismissRequest = { removeId = null }, title = { Text(t("Retirer cet ami ?")) },
        confirmButton = { TextButton(onClick = { removeId = null; action { repo.removeFriend(id) } }) { Text(t("Retirer")) } },
        dismissButton = { TextButton(onClick = { removeId = null }) { Text(t("Annuler")) } }) }
}
