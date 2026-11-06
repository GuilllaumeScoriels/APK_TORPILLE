package com.torpille.app.ui.screens

import com.torpille.app.util.userErrorMessage
import com.torpille.app.util.t
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.torpille.app.repo.SocialRepository
import com.torpille.app.data.Member
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import com.torpille.app.ui.components.ReportDialog
import com.torpille.app.repo.CommunityRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.torpille.app.data.PublishedReel
import com.torpille.app.ui.components.VideoPlayerWithOverlay
import com.torpille.app.ui.components.WelcomeHeroCard
import com.torpille.app.ui.viewmodels.CommunityViewModel

@Composable
fun ReelsScreen(
    onBack: () -> Unit,
    vm: CommunityViewModel = viewModel(),
) {
    val reels by vm.publishedReels().collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TextButton(onClick = onBack) { Text(t("Retour")) } }
        item {
            WelcomeHeroCard(
                title = t("Memories"),
                subtitle = t("Les torpilles que leurs auteurs ont choisi de partager avec toute l'app."),
                primaryIcon = Icons.Default.Movie,
                secondaryIcon = Icons.Default.Movie,
                cornerIcon = Icons.Default.Movie
            )
        }

        if (reels.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = t("Aucune memory publiée pour le moment."),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(reels, key = { it.id }) { reel ->
                ReelCard(reel = reel, vm = vm)
            }
        }
    }
}

@Composable
internal fun ReelCard(
    reel: PublishedReel,
    vm: CommunityViewModel,
) {
    var showReport by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repo = remember { CommunityRepository() }
    var signedUrl by remember(reel.id) { mutableStateOf<String?>(null) }
    var error by remember(reel.id) { mutableStateOf<String?>(null) }
    var loading by remember(reel.id) { mutableStateOf(false) }
    fun open() {
        if (loading) return
        loading = true
        scope.launch {
            try {
                signedUrl = reel.videoPath?.takeIf { it.isNotBlank() }?.let { vm.getSignedPlaybackUrl(it, reel.videoBucket) }
                    ?: reel.videoUrl?.takeIf { it.isNotBlank() } ?: error("Vidéo introuvable")
                showPlayer = true
            } catch (e: Exception) { error = userErrorMessage(e) }
            finally { loading = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFC9154), Color(0xFFE33B57), Color(0xFF2E1F40))))
            .clickable(enabled = !loading) { open() }.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("Publié"), color = Color.White, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .20f)).padding(horizontal = 10.dp, vertical = 6.dp))
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("@${reel.authorPseudo}", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("${t("Communauté")} · ${reel.communityName}", color = Color.White)
                    reel.taggedPseudo?.takeIf { it.isNotBlank() }?.let { Text("→ @$it", color = Color.White, fontWeight = FontWeight.Bold) }
                }
                if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                else Icon(Icons.Default.PlayArrow, t("Lire"), tint = Color.White, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .18f)).padding(12.dp))
            }
        }
        reel.publishedAt?.let { Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(it.toDate()), style = MaterialTheme.typography.bodySmall) }
        if (reel.authorUid == FirebaseAuth.getInstance().currentUser?.uid) {
            TextButton(onClick = { scope.launch { try { repo.unpublish(reel.communityId, reel.torpilleId) } catch (e: Exception) { error = userErrorMessage(e) } } }) { Text(t("Retirer des Memories"), color = MaterialTheme.colorScheme.error) }
        } else {
            TextButton(onClick = { showReport = true }) { Text(t("Signaler"), color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { confirmBlock = true }) { Text(t("Bloquer cet utilisateur"), color = MaterialTheme.colorScheme.error) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (showReport) ReportDialog(reel.id, "reel", reel.authorUid, reel.authorPseudo, reel.communityId, onDismiss = { showReport = false })
    if (confirmBlock) AlertDialog(onDismissRequest = { confirmBlock = false }, title = { Text(t("Bloquer cet utilisateur ?")) },
        confirmButton = { TextButton(onClick = { confirmBlock = false; scope.launch { try { SocialRepository().block(Member(uid = reel.authorUid, pseudo = reel.authorPseudo)) } catch (e: Exception) { error = userErrorMessage(e) } } }) { Text(t("Bloquer")) } },
        dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text(t("Annuler")) } })
    if (showPlayer && signedUrl != null) Dialog(onDismissRequest = { showPlayer = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = { showPlayer = false }) { Text(t("Fermer"), color = Color.White) }
                TextButton(onClick = { showPlayer = false; showProfile = true }) { Text("@${reel.authorPseudo}", color = Color.White) }
            }
            VideoPlayerWithOverlay(Uri.parse(signedUrl), reel.taggedPseudo, null, null, Modifier.weight(1f))
        }
    }
    if (showProfile) Dialog(onDismissRequest = { showProfile = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.White).systemBarsPadding()) { PublicProfileScreen(reel.authorUid, onBack = { showProfile = false }) }
    }
}
