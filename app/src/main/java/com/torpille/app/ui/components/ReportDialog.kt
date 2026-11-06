package com.torpille.app.ui.components

import com.torpille.app.util.userErrorMessage
import com.torpille.app.util.t
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.torpille.app.repo.SocialRepository
import kotlinx.coroutines.launch

@Composable
fun ReportDialog(contentId: String, contentType: String, authorUid: String, authorPseudo: String,
                 communityId: String? = null, onDismiss: () -> Unit) {
    val repo = remember { SocialRepository() }
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("unwantedContent") }
    var details by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(t("Signaler un contenu")) }, text = {
        Column {
            listOf("harassment" to "Harcèlement", "sexualContent" to "Contenu sexuel", "violenceThreat" to "Violence ou menace",
                "hateSpeech" to "Discours haineux", "spam" to "Spam", "unwantedContent" to "Contenu non désiré", "other" to "Autre").forEach { (key, title) ->
                Row { RadioButton(reason == key, { reason = key }, enabled = !busy); TextButton({ reason = key }, enabled = !busy) { Text(title) } }
            }
            OutlinedTextField(details, { if (it.length <= 1000) details = it }, label = { Text(t("Contexte (facultatif)")) }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        busy = true
        scope.launch { try { repo.report(contentId, contentType, authorUid, authorPseudo, reason, details, communityId); onDismiss() }
            catch (e: Exception) { error = userErrorMessage(e) } finally { busy = false } }
    }, enabled = !busy) { Text(t("Envoyer")) } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(t("Annuler")) } })
}
