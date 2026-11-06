package com.torpille.app.ui.screens

import com.torpille.app.util.userErrorMessage
import com.torpille.app.util.t
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.*
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.torpille.app.ui.components.ActionAccent
import com.torpille.app.ui.components.IllustratedActionButton
import com.torpille.app.ui.components.SectionTitleView
import com.torpille.app.ui.components.TopLeftQuickActionButton
import com.torpille.app.ui.components.WelcomeHeroCard
import com.torpille.app.ui.viewmodels.HomeViewModel

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CommunitiesTabScreen(
    onCreateCommunity: () -> Unit,
    onOpenCommunity: (String) -> Unit,
    onJoinCommunity: (String) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val communities by vm.orderedCommunities.collectAsState()
    val pinnedCommunityIds by vm.pinnedCommunityIds.collectAsState()
    var code by remember { mutableStateOf("") }
    var joining by remember { mutableStateOf(false) }
    var codeError by remember { mutableStateOf<String?>(null) }
    val repo = remember { com.torpille.app.repo.CommunityRepository() }
    val scope = rememberCoroutineScope()
    fun joinByCode() {
        if (joining) return
        joining = true
        codeError = null
        scope.launch {
            try { onJoinCommunity(repo.resolveInvitationCode(code)) }
            catch (e: Exception) { codeError = userErrorMessage(e) }
            finally { joining = false }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Text(t("Communautés"), style = MaterialTheme.typography.headlineLarge) }
        item {
            OutlinedTextField(code, { code = it; codeError = null }, Modifier.fillMaxWidth(),
                label = { Text(t("Code d’invitation")) }, placeholder = { Text("ABCD2345") },
                singleLine = true, enabled = !joining,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false, imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { joinByCode() }))
            Button(onClick = { joinByCode() }, enabled = code.isNotBlank() && !joining) {
                Text(t(if (joining) "Chargement…" else "Rejoindre la communauté"))
            }
            codeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        item {
            WelcomeHeroCard(
                title = t("Tes communautés"),
                subtitle = t(""),
                primaryIcon = Icons.Default.Groups,
                secondaryIcon = Icons.Default.Flag,
                cornerIcon = Icons.Default.AddCircle
            )
        }

        if (communities.isEmpty()) item {
            Text(t("Aucune communauté"), style = MaterialTheme.typography.titleLarge)
            Text(t("Crée ta première communauté pour commencer à jouer."))
        }

        items(communities) { community ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpenCommunity(community.id) },
                        onLongClick = { vm.togglePinnedCommunity(community, pinnedCommunityIds) }
                    )
            ) {
                IllustratedActionButton(
                    title = community.name,
                    subtitle = if (vm.isPinned(community, pinnedCommunityIds)) {
                        "${if (community.isPublic) "Communauté publique" else "Communauté privée"} · Épinglée"
                    } else {
                        if (community.isPublic) "Communauté publique" else "Communauté privée"
                    },
                    leadingIcon = if (community.isPublic) Icons.Default.Groups else Icons.Default.Lock,
                    illustrationIcon = Icons.Default.Flag,
                    accent = if (community.isPublic) ActionAccent.Meadow else ActionAccent.Berry,
                    trailingText = if (vm.isPinned(community, pinnedCommunityIds)) "Épinglée" else null
                )
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
