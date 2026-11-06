package com.torpille.app.ui.screens

import com.torpille.app.util.userErrorMessage
import com.torpille.app.util.t
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.torpille.app.repo.CommunityRepository
import com.torpille.app.repo.SocialRepository
import com.torpille.app.ui.components.InfoCard
import com.torpille.app.ui.components.ProfileAvatar
import com.torpille.app.ui.viewmodels.CommunityInfoViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun CommunityInfoScreen(communityId: String, onBack: () -> Unit, onDeleted: () -> Unit = onBack, vm: CommunityInfoViewModel = viewModel()) {
    var publicUid by remember { mutableStateOf<String?>(null) }
    if (publicUid != null) { androidx.activity.compose.BackHandler { publicUid = null }; PublicProfileScreen(publicUid!!, onBack = { publicUid = null }); return }
    val context = LocalContext.current
    val social = remember { SocialRepository() }
    val repo = remember { CommunityRepository() }
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var invitationCode by remember(communityId) { mutableStateOf<String?>(null) }
    var invitationError by remember(communityId) { mutableStateOf<String?>(null) }
    var invitationAttempt by remember(communityId) { mutableStateOf(0) }
    LaunchedEffect(communityId, invitationAttempt) {
        invitationError = null
        try { invitationCode = repo.invitationCode(communityId) }
        catch (e: Exception) { invitationError = userErrorMessage(e) }
    }
    val community by vm.community(communityId).collectAsState()
    val members by vm.members(communityId).collectAsState()
    val isAdmin = community?.adminUid == vm.authUid && vm.authUid != null
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    val hidden by remember(communityId) { social.hidden(communityId).catch { error = userErrorMessage(it) } }.collectAsState(emptyList())
    val requests by remember(communityId, isAdmin) { if (isAdmin) social.joinRequests(communityId).catch { error = userErrorMessage(it) } else flowOf(emptyList()) }.collectAsState(emptyList())
    var rankingSchedule by remember(community?.rankingSchedule) { mutableStateOf(community?.rankingSchedule ?: com.torpille.app.data.RankingSchedule()) }
    var isPublic by remember(community?.isPublic) { mutableStateOf(community?.isPublic ?: true) }
    var eliminationMode by remember(community?.eliminationMode) { mutableStateOf(community?.eliminationMode ?: false) }
    var seconds by remember(community?.responseTimeSeconds) { mutableStateOf((community?.responseTimeSeconds ?: 3600).toString()) }
    fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch { try { block(); error = null } catch (e: Exception) { error = userErrorMessage(e) } finally { busy = false } }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text(t("Retour")) }; Text(community?.name ?: "Communauté", style = MaterialTheme.typography.headlineLarge) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item { InfoCard("Invitation") {
            Text(if (community?.isPublic == true) "Communauté publique : le lien suffit." else "L’administrateur doit accepter les demandes d’adhésion.")
            androidx.compose.foundation.text.selection.SelectionContainer { Text(vm.inviteLink(communityId)) }
            TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(vm.inviteLink(communityId))) }) { Text(t("Copier le lien")) }
            Text(t("Code d’invitation"), style = MaterialTheme.typography.labelLarge)
            invitationCode?.let { code ->
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(code, style = MaterialTheme.typography.headlineMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
                TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(code)) }) { Text(t("Copier le code")) }
            } ?: if (invitationError == null) { CircularProgressIndicator(Modifier.size(20.dp)) }
            else { Text(invitationError.orEmpty(), color = MaterialTheme.colorScheme.error); TextButton(onClick = { invitationAttempt++ }) { Text(t("Réessayer")) } }
            Button(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, vm.inviteLink(communityId))
            }, "Partager le lien")) }) { Text(t("Partager le lien")) }
        } }
        item { com.torpille.app.ui.components.RankingScheduleSummary(community?.rankingSchedule ?: com.torpille.app.data.RankingSchedule()) }
        item { Text(if (community?.eliminationMode == true) "Mode éliminatoire activé" else "Mode éliminatoire désactivé", style = MaterialTheme.typography.bodySmall) }
        item { TextButton(enabled = !busy, onClick = {
            pendingAction = "Quitter la communauté ? Si tu es administrateur, l’administration sera transférée à un autre membre." to { repo.leaveCommunity(communityId); onDeleted() }
        }) { Text("Quitter la communauté") } }
        if (isAdmin) {
            item { InfoCard("Paramètres") {
                Row(Modifier.fillMaxWidth()) { Text(if (isPublic) "Publique" else "Privée", Modifier.weight(1f)); Switch(isPublic, { isPublic = it }, enabled = !busy) }
                com.torpille.app.ui.components.RankingScheduleEditor(rankingSchedule, !busy) { rankingSchedule = it }
                Row(Modifier.fillMaxWidth()) { Text("Mode éliminatoire", Modifier.weight(1f)); Switch(eliminationMode, { eliminationMode = it }, enabled = !busy) }
                Text("Pour les prochaines torpilles : une réponse hors délai entraîne une expulsion. Un retour reste possible.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(seconds, { seconds = it.filter(Char::isDigit) }, label = { Text(t("Temps imparti (secondes)")) }, singleLine = true, enabled = !busy)
                Button(onClick = { act { val value = seconds.toLongOrNull() ?: error("Durée invalide"); require(value > 0) { "La durée doit être positive." }; repo.updateCommunitySettings(communityId, isPublic, value, eliminationMode, rankingSchedule) } }, enabled = !busy) { Text(t("Enregistrer")) }
            } }
            if (requests.isNotEmpty()) item { Text(t("Demandes d’adhésion"), style = MaterialTheme.typography.titleLarge) }
            items(requests, key = { "request_${it.id}" }) { request ->
                InfoCard(request.getString("requesterPseudo").orEmpty()) {
                    Row {
                        TextButton(onClick = { act { social.resolveJoin(request.id, true) } }, enabled = !busy) { Text(t("Accepter")) }
                        TextButton(onClick = { act { social.resolveJoin(request.id, false) } }, enabled = !busy) { Text(t("Refuser")) }
                    }
                }
            }
        }
        item { InfoCard("Notifications") {
            Text(t("Choisis les notifications de cette communauté."))
            Row {
                TextButton(onClick = { act { social.notificationMode(communityId, community?.name.orEmpty(), "all") } }, enabled = !busy) { Text(t("Tout")) }
                TextButton(onClick = { act { social.notificationMode(communityId, community?.name.orEmpty(), "mentionsOnly") } }, enabled = !busy) { Text(t("Seulement moi")) }
            }
        } }
        if (hidden.isNotEmpty()) item { InfoCard(t("Membres masqués")) {
            hidden.forEach { member -> Row(Modifier.fillMaxWidth()) {
                Text(member.getString("pseudo").orEmpty(), Modifier.weight(1f))
                TextButton(onClick = { act { social.unhide(communityId, member.id) } }, enabled = !busy) { Text(t("Réafficher")) }
            } }
        } }
        item { Text(t("Participants"), style = MaterialTheme.typography.titleLarge) }
        items(members, key = { it.uid }) { member ->
            InfoCard(member.pseudo.ifBlank { member.uid }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { ProfileAvatar(member.photoUrl, member.profileIcon, 44.dp); Text("${member.xpInCommunity} XP") }
                member.pendingDeadlineAt?.let { Text(if (it.seconds < System.currentTimeMillis() / 1000) "En retard" else "Torpillé (en attente)") }
                TextButton(onClick = { publicUid = member.uid }) { Text(t("Profil public")) }
                if (member.uid != vm.authUid) {
                    TextButton(onClick = { pendingAction = "Bloquer cet utilisateur ?" to { social.block(member) } }, enabled = !busy) { Text(t("Bloquer")) }
                    if (isAdmin && community?.isPublic == false) Row {
                        TextButton(onClick = { pendingAction = "Retirer ce membre ?" to { social.removeMember(communityId, member, false) } }, enabled = !busy) { Text(t("Retirer")) }
                        TextButton(onClick = { pendingAction = "Bannir ce membre ?" to { social.removeMember(communityId, member, true) } }, enabled = !busy) { Text(t("Bannir")) }
                    }
                }
            }
        }
        if (isAdmin) item { TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text(t("Supprimer la communauté"), color = MaterialTheme.colorScheme.error) } }
    }
    pendingAction?.let { (title, operation) -> AlertDialog(onDismissRequest = { pendingAction = null }, title = { Text(title) },
        confirmButton = { TextButton(onClick = { pendingAction = null; act(operation) }) { Text(t("Confirmer")) } },
        dismissButton = { TextButton(onClick = { pendingAction = null }) { Text(t("Annuler")) } }) }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(t("Supprimer définitivement cette communauté ?")) },
        text = { Text(t("Les messages et les vidéos de la communauté seront supprimés.")) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; act { social.deleteCommunity(communityId); onDeleted() } }) { Text(t("Supprimer")) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Annuler")) } })
}
