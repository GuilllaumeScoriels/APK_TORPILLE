package com.torpille.app.ui.screens

import com.torpille.app.util.t
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.torpille.app.data.Community
import com.torpille.app.data.LeaderboardEntry
import com.torpille.app.ui.viewmodels.LeaderboardViewModel

@Composable
fun LeaderboardScreen(
    vm: LeaderboardViewModel = viewModel(),
) {
    var publicUid by remember { mutableStateOf<String?>(null) }
    if (publicUid != null) { androidx.activity.compose.BackHandler { publicUid = null }; PublicProfileScreen(publicUid!!, onBack = { publicUid = null }); return }
    val myUid = FirebaseAuth.getInstance().currentUser?.uid
    val communities by vm.communities.collectAsState(initial = emptyList())

    var selectedCommunityIds by rememberSaveable { mutableStateOf(setOf<String>()) }

    val validSelection = remember(communities, selectedCommunityIds) {
        selectedCommunityIds.intersect(communities.map { it.id }.toSet())
    }

    LaunchedEffect(validSelection) {
        if (validSelection != selectedCommunityIds) {
            selectedCommunityIds = validSelection
        }
    }

    val leaderboard by vm.leaderboard(validSelection).collectAsState(initial = null)

    val myRank = leaderboard
        ?.indexOfFirst { it.uid == myUid }
        ?.takeIf { it >= 0 }
        ?.plus(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(t("Classement"), style = MaterialTheme.typography.titleLarge)
        vm.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        RankingSummaryCard(
            myRank = myRank,
            totalUsers = leaderboard?.size,
            selectedCommunities = communities.filter { it.id in validSelection }
        )

        Text(t("Filtrer par communauté"), style = MaterialTheme.typography.titleMedium)

        if (communities.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = t("Aucune communauté. Le filtre « Tout » affiche l'ensemble des Torpilleurs inscrits."),
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            CommunityFilters(
                communities = communities,
                selectedCommunityIds = validSelection,
                onSelectAll = { selectedCommunityIds = emptySet() },
                onToggleCommunity = { communityId ->
                    selectedCommunityIds = if (communityId in validSelection) {
                        validSelection - communityId
                    } else {
                        validSelection + communityId
                    }
                }
            )
        }

        when (val entries = leaderboard) {
            null -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }

            emptyList<LeaderboardEntry>() -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = t("Aucun torpilleur trouvé pour ce filtre."),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = entries,
                        key = { _, item -> item.uid }
                    ) { index, entry ->
                        Box(Modifier.clickable { publicUid = entry.uid }) {
                            LeaderboardRow(rank = index + 1, entry = entry, isCurrentUser = entry.uid == myUid)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingSummaryCard(
    myRank: Int?,
    totalUsers: Int?,
    selectedCommunities: List<Community>,
) {
    val scopeLabel = if (selectedCommunities.isEmpty()) {
        "Classement global"
    } else {
        "Classement filtré : ${selectedCommunities.joinToString { it.name }}"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(scopeLabel, style = MaterialTheme.typography.titleMedium)
            Text(
                text = when {
                    myRank == null || totalUsers == null -> "Chargement du rang…"
                    totalUsers == 0 -> "Aucun utilisateur dans ce classement."
                    else -> "Ton rang : $myRank sur $totalUsers"
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = t("Le classement est basé sur l'XP totale publique de chaque utilisateur."),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun CommunityFilters(
    communities: List<Community>,
    selectedCommunityIds: Set<String>,
    onSelectAll: () -> Unit,
    onToggleCommunity: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedCommunityIds.isEmpty(),
                onClick = onSelectAll,
                label = { Text(t("Tout")) }
            )
        }
        items(communities, key = { it.id }) { community ->
            FilterChip(
                selected = community.id in selectedCommunityIds,
                onClick = { onToggleCommunity(community.id) },
                label = { Text(community.name) }
            )
        }
    }
}

@Composable
private fun LeaderboardRow(
    rank: Int,
    entry: LeaderboardEntry,
    isCurrentUser: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            AsyncImage(
                model = entry.photoUrl,
                contentDescription = "Avatar de ${entry.pseudo}",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCurrentUser) "${entry.pseudo} (toi)" else entry.pseudo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrentUser) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = "XP totale : ${entry.xpTotal}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
