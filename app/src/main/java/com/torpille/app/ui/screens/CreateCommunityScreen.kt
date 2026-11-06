/**
 * Rôle :
 * - Formulaire de création d’une communauté.
 *
 * Composants :
 * - Nom de communauté.
 * - Switch “publique/privée”.
 * - Temps imparti (secondes) : utilisé pour calculer les deadlines des torpilles.
 * - Callback onCreated(id) : ouvre directement la communauté créée.
 *
 * Pourquoi comme ça :
 * - Le temps imparti est une règle de gameplay, stockée dans Community.responseTimeSeconds.
 */
package com.torpille.app.ui.screens

import com.torpille.app.util.t
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.torpille.app.ui.viewmodels.CreateCommunityViewModel

@Composable
fun CreateCommunityScreen(
    onCreated: (String) -> Unit,
    onBack: () -> Unit,
    vm: CreateCommunityViewModel = viewModel(),
) {
    var name by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var responseTime by remember { mutableStateOf("3600") }

    val state = vm.uiState
    val canSubmit = name.isNotBlank() && !state.isLoading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t("Nouvelle communauté"))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = name,
            onValueChange = { name = it },
            label = { Text(t("Nom")) },
            singleLine = true,
            enabled = !state.isLoading
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = responseTime,
            onValueChange = { responseTime = it.filter { ch -> ch.isDigit() } },
            label = { Text(t("Temps de réponse (secondes)")) },
            singleLine = true,
            enabled = !state.isLoading
        )

        Column {
            Text(if (isPublic) "Communauté publique" else "Communauté privée")
            Switch(
                checked = isPublic,
                onCheckedChange = { isPublic = it },
                enabled = !state.isLoading
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val seconds = responseTime.toLongOrNull() ?: 3600L
                vm.create(name.trim(), isPublic, seconds, onCreated)
            },
            enabled = canSubmit,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                Text(t("Créer"))
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onBack,
            enabled = !state.isLoading
        ) {
            Text(t("Retour"))
        }

        state.error?.let { Text(it) }
    }
}