package com.torpille.app.ui.screens

import com.torpille.app.util.userErrorMessage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.torpille.app.data.UserProfile
import com.torpille.app.data.PublishedReel
import com.torpille.app.repo.SocialRepository
import com.torpille.app.ui.components.ProfileAvatar
import com.torpille.app.ui.components.ReportDialog
import com.torpille.app.ui.viewmodels.CommunityViewModel
import com.torpille.app.util.observeDoc
import com.torpille.app.util.t
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PublicProfileScreen(uid: String, onBack: () -> Unit, onTorpille: (() -> Unit)? = null, vm: CommunityViewModel = viewModel()) {
    val db = remember { FirebaseFirestore.getInstance() }
    val social = remember { SocialRepository() }
    val scope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    val profile by remember(uid) { observeDoc(db.collection("users").document(uid), UserProfile::class.java).catch { feedback = userErrorMessage(it) } }.collectAsState(null)
    val reels by remember(uid) {
        combine(social.observe(db.collection("reels").whereEqualTo("authorUid", uid)), social.excluded()) { docs, excluded ->
            docs.mapNotNull { it.toObject(PublishedReel::class.java)?.copy(id = it.id) }
                .filter { it.authorUid !in excluded && (it.moderationStatus == null || it.moderationStatus == "visible") }
                .sortedByDescending { it.publishedAt?.seconds }
        }.catch { feedback = userErrorMessage(it) }
    }.collectAsState(emptyList())
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { TextButton(onClick = onBack) { Text(t("Retour")) }; Text(t("Profil public"), style = MaterialTheme.typography.headlineLarge) }
        item {
            ProfileAvatar(profile?.photoUrl, profile?.profileIcon, 100.dp)
            Text("@${profile?.pseudo.orEmpty()}", style = MaterialTheme.typography.headlineSmall)
            Text("${profile?.friendsCount ?: 0} ${t("Amis")}")
            if (profile?.isLanguagePublic == true) profile?.languageCode?.let { Text("${t("Langue")} : $it") }
            if (profile?.isLeaderboardPublic == true) Text("${t("Classement global")} : ${profile?.effectiveTotalXP ?: 0} XP")
            Text(t("Le pseudo, la photo de profil et le nombre d'amis sont publics. La langue et le classement ne s'affichent ici que si l'utilisateur les a rendus visibles."), style = MaterialTheme.typography.bodySmall)
        }
        feedback?.let { item { Text(it) } }
        if (uid != FirebaseAuth.getInstance().currentUser?.uid && profile != null) item {
            Button(onClick = { busy = true; scope.launch { try { social.requestFriend(profile!!.copy(uid = uid)); feedback = t("Demande envoyée.") }
                catch (e: Exception) { feedback = userErrorMessage(e) } finally { busy = false } } }, enabled = !busy) { Text(t("Envoyer une demande d'ami")) }
            TextButton(onClick = { report = true }) { Text(t("Signaler")) }
        }
        if (onTorpille != null && uid != FirebaseAuth.getInstance().currentUser?.uid) item { Button(onClick = onTorpille) { Text(t("Torpiller")) } }
        item { Text(t("Memories"), style = MaterialTheme.typography.titleLarge) }
        if (reels.isEmpty()) item { Text(t("Aucune memory publiée")) }
        items(reels, key = { it.id }) { reel -> ReelCard(reel, vm) }
    }
    if (report) ReportDialog(uid, "profile", uid, profile?.pseudo.orEmpty(), onDismiss = { report = false })
}
