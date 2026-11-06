package com.torpille.app.ui.screens

import com.torpille.app.util.userErrorMessage
import com.torpille.app.util.t
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.ui.platform.LocalUriHandler
import com.torpille.app.util.AppLanguage
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import com.torpille.app.repo.AuthRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.torpille.app.ui.components.ActionAccent
import com.torpille.app.ui.components.IllustratedActionButton
import com.torpille.app.ui.components.SectionTitleView
import com.torpille.app.ui.components.WelcomeHeroCard
import com.torpille.app.ui.viewmodels.AuthViewModel

@Composable
fun AuthScreen(
    onAuthed: () -> Unit,
    vm: AuthViewModel = viewModel(),
) {
    var signupAccepted by remember { mutableStateOf(false) }
    var showSignupRules by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(feedback, vm.error) {
        if (feedback != null || vm.error != null) {
            focusManager.clearFocus()
            scrollState.animateScrollTo(0)
        }
    }
    val uriHandler = LocalUriHandler.current
    var preparingSignUp by remember { mutableStateOf(false) }
    var passwordConfirmation by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    if (showSignupRules) {
        LegalWelcomeScreen(onContinue = { signupAccepted = true; showSignupRules = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        vm.error?.let { text ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("auth.errorMessage")
                    .semantics { liveRegion = LiveRegionMode.Assertive }) {
                Text(t(text), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        feedback?.let { text ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("auth.infoMessage")
                    .semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(text, modifier = Modifier.padding(16.dp))
            }
        }
        WelcomeHeroCard(
            title = t("Bienvenue dans tes communautés privées"),
            subtitle = t("Torpille relie des membres identifiés dans des communautés choisies, avec modération, blocage et signalement."),
            primaryIcon = Icons.Default.Bolt,
            secondaryIcon = Icons.Default.Star,
            cornerIcon = Icons.Default.Lock
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LanguagePicker()
            SectionTitleView(
                title = t("Connexion"),
                subtitle = t("Entre tes identifiants pour rejoindre tes communautés.")
            )

            Text(t("Email à confirmer"), style = MaterialTheme.typography.titleSmall)
            Text(t("Après inscription, tu devras confirmer ton adresse email avant d'accéder à l'app."), style = MaterialTheme.typography.bodySmall)

            TextField(
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                value = email,
                onValueChange = { email = it },
                label = { Text(t("Email")) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                shape = RoundedCornerShape(18.dp)
            )

            TextField(
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                value = password,
                onValueChange = { password = it },
                label = { Text(t("Mot de passe")) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                shape = RoundedCornerShape(18.dp)
            )
            if (preparingSignUp) {
                TextField(
                    value = passwordConfirmation,
                    onValueChange = { passwordConfirmation = it },
                    label = { Text(t("Confirmer le mot de passe")) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        TextButton(onClick = { showSignupRules = true }) { Text(t("Revoir mes acceptations")) }
        TextButton(onClick = { uriHandler.openUri("https://torpille-38783.web.app/privacy.html?lang=${AppLanguage.code}") }) { Text(t("Politique de confidentialité")) }
        TextButton(onClick = { uriHandler.openUri("https://torpille-38783.web.app/support.html") }) { Text(t("Support et contact")) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !vm.isLoading) {
                    vm.signIn(email.trim(), password) { onAuthed() }
                }
        ) {
            IllustratedActionButton(
                title = t(if (vm.isLoading) "Chargement…" else "Se connecter"),
                subtitle = "",
                leadingIcon = Icons.Default.Login,
                illustrationIcon = Icons.Default.Lock,
                accent = ActionAccent.Meadow
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !vm.isLoading) {
                    if (!preparingSignUp) { preparingSignUp = true; showSignupRules = true }
                    else if (!signupAccepted) showSignupRules = true
                    else if (password != passwordConfirmation) feedback = t("Les deux mots de passe ne correspondent pas.")
                    else { feedback = null; vm.signUp(email.trim(), password, signupAccepted) { onAuthed() } }
                }
        ) {
            IllustratedActionButton(
                title = t("Créer un compte"),
                subtitle = "",
                leadingIcon = Icons.Default.PersonAdd,
                illustrationIcon = Icons.Default.Star,
                accent = ActionAccent.Sunset
            )
        }

        Box(Modifier.fillMaxWidth().clickable(enabled = !vm.isLoading) {
            feedback = null
            focusManager.clearFocus()
            vm.resetPassword(email) { feedback = AppLanguage.resetPasswordInfo(email.trim()) }
        }) {
            IllustratedActionButton(
                title = t("Mot de passe oublié"),
                subtitle = "",
                leadingIcon = Icons.Default.Key,
                illustrationIcon = Icons.Default.Email,
                accent = ActionAccent.Gold
            )
        }
    }
}
