package com.torpille.app.ui.screens

import com.torpille.app.util.userErrorMessage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.torpille.app.repo.AuthRepository
import com.torpille.app.repo.UserRepository
import com.torpille.app.repo.SocialRepository
import com.torpille.app.ui.components.*
import com.torpille.app.util.AppLanguage
import com.torpille.app.util.t
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun UserProfileScreen(onEditProfile: () -> Unit, onSignOut: () -> Unit, onOpenLeaderboard: () -> Unit) {
    val repo = remember { UserRepository() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var feedback by remember { mutableStateOf<String?>(null) }
    val me by remember { repo.observeMe().catch { feedback = userErrorMessage(it) } }.collectAsState(null)
    var showFriends by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val socialRepo = remember { SocialRepository() }
    val blocked by remember { socialRepo.blocked().catch { feedback = userErrorMessage(it) } }.collectAsState(emptyList())
    var unblockId by remember { mutableStateOf<String?>(null) }
    var passwordFeedback by remember { mutableStateOf<String?>(null) }
    if (showFriends) { androidx.activity.compose.BackHandler { showFriends = false }; FriendsScreen(onBack = { showFriends = false }); return }
    if (showPrivacy) {
        androidx.activity.compose.BackHandler { showPrivacy = false }
        var languageVisible by remember { mutableStateOf(me?.isLanguagePublic ?: true) }
        var leaderboardVisible by remember { mutableStateOf(me?.isLeaderboardPublic ?: true) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = { showPrivacy = false }) { Text(t("Retour")) }
            Text(t("Informations publiques"), style = MaterialTheme.typography.headlineLarge)
            Text(t("Toujours public"), style = MaterialTheme.typography.titleMedium)
            listOf("Pseudo", "Photo de profil", "Nombre d'amis").forEach { label -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Visibility, null, tint = ActionAccent.Ocean.tint); Text(t(label)) } }
            Text(t("Ces informations permettent aux membres de communautés partagées de t'identifier et de demander à devenir ami."), style = MaterialTheme.typography.bodySmall)
            Text(t("Visibilité optionnelle"), style = MaterialTheme.typography.titleMedium)
            Row {
                Text(t("Afficher ma langue"), Modifier.weight(1f))
                Switch(languageVisible, { languageVisible = it }, enabled = !busy)
            }
            Row {
                Text(t("Afficher mon classement global"), Modifier.weight(1f))
                Switch(leaderboardVisible, { leaderboardVisible = it }, enabled = !busy)
            }
            Text(t("Tu peux masquer ta langue et ton classement global à tout moment."), style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = !busy, onClick = { scope.launch { busy = true; try { repo.updatePrivacy(languageVisible, leaderboardVisible); repo.updateLanguage(AppLanguage.code); feedback = t("Profil enregistré.") } catch (e: Exception) { feedback = userErrorMessage(e) } finally { busy = false } } }) { Icon(Icons.Default.Verified, null); Text(t("Enregistrer la visibilité")) }
            feedback?.let { Text(it) }
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(t("Profil"), style = MaterialTheme.typography.headlineLarge)
        ProfileSetupScreen(onDone = { feedback = t("Profil enregistré.") }, embedded = true)
        feedback?.let { Text(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ActionAccent.Meadow.tint.copy(alpha = .08f)).padding(16.dp), color = ActionAccent.Meadow.tint) }
        Box(Modifier.clickable { showPrivacy = true }) {
            IllustratedActionButton(t("Informations publiques"), "", Icons.Default.Visibility, Icons.Default.Person, ActionAccent.Ocean)
        }
        Box(Modifier.clickable { showFriends = true }) {
            IllustratedActionButton(t("Amis"), "${me?.friendsCount ?: 0} · ${t("rechercher par pseudo")}", Icons.Default.People, Icons.Default.Search, ActionAccent.Violet)
        }
        Box(Modifier.clickable(enabled = !busy) { scope.launch {
            busy = true
            try { AuthRepository().sendPasswordResetEmail(FirebaseAuth.getInstance().currentUser?.email.orEmpty()); passwordFeedback = AppLanguage.resetPasswordInfo(FirebaseAuth.getInstance().currentUser?.email.orEmpty()) }
            catch (e: Exception) { passwordFeedback = userErrorMessage(e) } finally { busy = false }
        } }) {
            IllustratedActionButton(t("Réinitialiser le mot de passe"), "", Icons.Default.Key, Icons.Default.Email, ActionAccent.Gold)
        }
        passwordFeedback?.let { Text(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ActionAccent.Meadow.tint.copy(alpha = .08f)).padding(16.dp), color = ActionAccent.Meadow.tint) }
        Box(Modifier.clickable(onClick = onOpenLeaderboard)) {
            IllustratedActionButton(t("Voir le classement global"), "", Icons.Default.EmojiEvents, Icons.Default.BarChart, ActionAccent.Ocean)
        }
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitleView(t("Informations légales et assistance"), t("Consulte les informations sur tes données ou contacte directement Torpille."))
            TextButton(onClick = { uriHandler.openUri("https://torpille-38783.web.app/privacy.html?lang=${AppLanguage.code}") }) { Text(t("Politique de confidentialité")) }
            TextButton(onClick = { uriHandler.openUri("https://torpille-38783.web.app/support.html") }) { Text(t("Support et contact")) }
        }
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitleView(t("Utilisateurs bloqués"), t("Les utilisateurs bloqués disparaissent des communautés partagées, des fils et de la carte."))
            if (blocked.isEmpty()) Text(t("Aucun utilisateur bloqué."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            blocked.forEach { member -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileAvatar(member.getString("photoUrl"), member.getString("profileIcon"), 38.dp)
                Text("@${member.getString("pseudo").orEmpty().ifBlank { member.id }}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { unblockId = member.id }, enabled = !busy) { Text(t("Débloquer")) }
            } }
        }
        Box(Modifier.clickable { confirm = "logout" }) {
            IllustratedActionButton(t("Déconnexion"), "", Icons.Default.Logout, Icons.Default.MeetingRoom, ActionAccent.Coral)
        }
        Box(Modifier.fillMaxWidth().clickable(enabled = !busy) { confirm = "delete" }) {
            IllustratedActionButton(t(if (busy && confirm == "delete") "Suppression…" else "Supprimer mon compte"), "", Icons.Default.Delete, Icons.Default.PersonOff, ActionAccent.Coral)
        }
    }
    unblockId?.let { id -> AlertDialog(onDismissRequest = { if (!busy) unblockId = null }, title = { Text(t("Débloquer cet utilisateur ?")) },
        text = { Text(t("Cet utilisateur réapparaîtra dans les communautés partagées, les fils et la carte.")) },
        confirmButton = { TextButton(enabled = !busy, onClick = { scope.launch { busy = true; try { socialRepo.unblock(id); unblockId = null } catch (e: Exception) { feedback = userErrorMessage(e) } finally { busy = false } } }) { Text(t("Débloquer")) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { unblockId = null }) { Text(t("Annuler")) } }) }
    confirm?.let { action -> AlertDialog(onDismissRequest = { if (!busy) confirm = null },
        title = { Text(t(if (action == "delete") "Supprimer ce compte ?" else "Se déconnecter ?")) },
        text = { Text(t(if (action == "delete") "Cette action est définitive. Ton profil et tes données seront supprimés." else "Tu pourras te reconnecter avec ton adresse e-mail.")) },
        confirmButton = { TextButton(onClick = {
            if (action == "logout") { confirm = null; onSignOut() }
            else { busy = true; scope.launch { try { repo.deleteCurrentAccountServerSide(); onSignOut() }
                catch (e: Exception) { feedback = userErrorMessage(e) } finally { busy = false; confirm = null } } }
        }, enabled = !busy) { Text(t(if (action == "delete") "Supprimer" else "Déconnexion")) } },
        dismissButton = { TextButton(onClick = { confirm = null }, enabled = !busy) { Text(t("Annuler")) } }) }
}
