package com.torpille.app.ui.screens

import com.torpille.app.util.userErrorMessage
import com.torpille.app.util.t
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.torpille.app.repo.AuthRepository
import com.torpille.app.repo.EmailVerificationActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Composable
fun VerifyEmailScreen(onVerified: () -> Unit, onSignOut: () -> Unit,
    actions: EmailVerificationActions = remember { AuthRepository() }) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(message) {
        if (message != null) {
            focusManager.clearFocus()
            scrollState.animateScrollTo(0)
        }
    }
    fun request(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try { withTimeout(25_000) { action() } }
            catch (_: TimeoutCancellationException) { message = t("La connexion prend trop de temps. Vérifie ta connexion Internet puis réessaie.") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = userErrorMessage(failure) }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        message?.let { text ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().testTag("auth.verificationNotice")
                    .semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(t(text), modifier = Modifier.padding(16.dp))
            }
        }
        Text(t("Vérifie ton adresse e-mail"), style = MaterialTheme.typography.headlineLarge)
        Text(t("Ouvre le lien envoyé à") + " " + actions.currentEmail + ", " + t("puis reviens ici."))
        Button(onClick = { request {
            if (actions.refreshVerification()) onVerified()
            else message = t("L’adresse n’est pas encore vérifiée.")
        } }, enabled = !busy) { Text(t("J’ai vérifié mon e-mail")) }
        TextButton(onClick = { request {
            actions.resendVerification()
            message = t("E-mail de vérification envoyé.")
        } }, enabled = !busy) { Text(t("Renvoyer le lien")) }
        TextButton(onClick = onSignOut, enabled = !busy) { Text(t("Changer de compte")) }
    }
}
