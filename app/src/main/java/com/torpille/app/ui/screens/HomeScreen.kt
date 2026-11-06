package com.torpille.app.ui.screens

import com.torpille.app.util.t
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.ExperimentalFoundationApi
import com.torpille.app.ui.components.ProfileAvatar
import com.torpille.app.util.formatResponseTime
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.torpille.app.ui.components.ActionAccent
import com.torpille.app.ui.components.IllustratedActionButton
import com.torpille.app.ui.components.InfoCard
import com.torpille.app.ui.components.SectionTitleView
import com.torpille.app.ui.components.TopLeftQuickActionButton
import com.torpille.app.ui.components.WelcomeHeroCard
import com.torpille.app.ui.viewmodels.HomeViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onCreateCommunity: () -> Unit,
    onOpenCommunity: (String) -> Unit,
    onSignOut: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenReels: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val announcements by androidx.compose.runtime.remember { com.torpille.app.repo.AnnouncementRepository().observe() }.collectAsState(initial = emptyList())
    val me by vm.me.collectAsState(initial = null)
    val communities by vm.homeCommunities.collectAsState()
    val pinnedCommunityIds by vm.pinnedCommunityIds.collectAsState()
    val unreadCount by vm.unreadNotificationsCount.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Accueil"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenReels) { Icon(Icons.Default.Movie, "Memories") }
                IconButton(onClick = onOpenNotifications) {
                    androidx.compose.material3.BadgedBox(badge = { if (unreadCount > 0) androidx.compose.material3.Badge { Text(unreadCount.toString()) } }) {
                        Icon(Icons.Default.Notifications, "${t("Activité")} ($unreadCount)")
                    }
                }
            }
        }
        item {
            WelcomeHeroCard(
                title = "${t("Bienvenue")} ${me?.pseudo ?: t("capitaine")}",
                subtitle = t(""),
                primaryIcon = Icons.Default.Bolt,
                secondaryIcon = Icons.Default.Home,
                cornerIcon = Icons.Default.Notifications
            )
        }

        items(announcements, key = { "announcement-${it.id}" }) { announcement ->
            com.torpille.app.ui.components.HomeAnnouncementCard(announcement)
        }

        item {
            InfoCard(title = t("Profil")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileAvatar(me?.photoUrl, me?.profileIcon, modifier = Modifier.clickable(onClick = onOpenProfile))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(me?.pseudo ?: "Utilisateur", fontWeight = FontWeight.Bold)
                        Text(
                            text = "XP total : ${me?.effectiveTotalXP ?: 0}",
                            color = ActionAccent.Sunset.tint,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item {
            SectionTitleView(
                title = if (pinnedCommunityIds.isEmpty()) "Dernière communauté" else "Communautés épinglées",
                subtitle = if (communities.isEmpty()) "Aucune communauté pour le moment." else ""
            )
        }

        if (communities.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = t("Crée ta première communauté pour commencer à jouer."),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(communities) { c ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onOpenCommunity(c.id) },
                            onLongClick = { vm.togglePinnedCommunity(c, pinnedCommunityIds) }
                        )
                ) {
                    IllustratedActionButton(
                        title = c.name,
                        subtitle = "${if (c.isPublic) "Publique" else "Privée"} · Réponse en ${formatResponseTime(c.responseTimeSeconds)}",
                        leadingIcon = Icons.Default.Groups,
                        illustrationIcon = if (c.isPublic) Icons.Default.Groups else Icons.Default.Bolt,
                        accent = if (c.isPublic) ActionAccent.Meadow else ActionAccent.Violet,
                        trailingText = if (vm.isPinned(c, pinnedCommunityIds)) "Épinglée" else if (c.isPublic) "Public" else "Privé"
                    )
                }
            }
        }

        item { SectionTitleView(title = t("Actions rapides"), subtitle = t("")) }
        item {
            Box(Modifier.fillMaxWidth().clickable(onClick = onCreateCommunity)) {
                IllustratedActionButton("Créer une communauté", "", Icons.Default.Groups, Icons.Default.AddCircle, ActionAccent.Sunset)
            }
        }
        item {
            Box(Modifier.fillMaxWidth().clickable(onClick = onOpenLeaderboard)) {
                IllustratedActionButton("Classement global", "", Icons.Default.EmojiEvents, Icons.Default.EmojiEvents, ActionAccent.Gold)
            }
        }

        vm.infoMessage?.let { info ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = info,
                        color = ActionAccent.Meadow.tint,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        vm.errorMessage?.let { error ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
