package com.torpille.app.ui.screens

import com.torpille.app.util.t
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.torpille.app.ui.viewmodels.JoinCommunityViewModel

@Composable
fun JoinCommunityScreen(
    communityId: String,
    onJoined: () -> Unit,
    onCancel: () -> Unit,
    vm: JoinCommunityViewModel = viewModel(),
) {
    LaunchedEffect(communityId) {
        vm.join(communityId, onJoined)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t("Invitation communauté"))

        if (vm.loading) {
            CircularProgressIndicator()
            Text(t("Vérification et adhésion…"))
        }

        if (vm.requestPending) Text(t("Demande envoyée. L’administrateur doit accepter ton adhésion."))

        vm.error?.let { Text(it) }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onCancel
        ) {
            Text(t("Retour"))
        }
    }
}