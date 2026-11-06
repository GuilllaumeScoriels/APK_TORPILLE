package com.torpille.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.torpille.app.R
import com.torpille.app.util.AppLanguage
import com.torpille.app.util.t
import org.json.JSONObject

@Composable
fun LanguagePicker() {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(AppLanguage.choices.firstOrNull { it.first == AppLanguage.code }?.second ?: "Français") }
        DropdownMenu(expanded, { expanded = false }) {
            AppLanguage.choices.forEach { (code, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { AppLanguage.select(code); expanded = false }) }
        }
    }
}

@Composable
fun LegalWelcomeScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var acceptsEula by remember { mutableStateOf(false) }
    var acceptsPrivacy by remember { mutableStateOf(false) }
    var readEula by remember { mutableStateOf(false) }
    var showingEula by remember { mutableStateOf(false) }
    if (showingEula) {
        androidx.activity.compose.BackHandler { showingEula = false }
        val paragraphs = remember(AppLanguage.code) {
            val json = JSONObject(context.assets.open("ios_eula.json").bufferedReader().use { it.readText() })
            val array = json.getJSONArray(AppLanguage.code)
            (0 until array.length()).map { array.getString(it) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(t("Règles EULA"), style = MaterialTheme.typography.headlineLarge)
            paragraphs.forEach { Text(it) }
            Button(onClick = { readEula = true; acceptsEula = true; showingEula = false }, modifier = Modifier.fillMaxWidth()) { Text(t("Lu et accepté")) }
            TextButton(onClick = { showingEula = false }) { Text(t("Retour")) }
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Image(painterResource(R.drawable.torpille_logo), "Torpille", Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)))
        Text(t("Avant de rejoindre Torpille"), style = MaterialTheme.typography.headlineLarge)
        Text(t("Lis et accepte les règles EULA et la politique de confidentialité pour accéder à la connexion ou à la création de compte."))
        LanguagePicker()
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TextButton(onClick = { showingEula = true }) { Text(t("Règles EULA")) }
            Row { Text(t("J’accepte les règles EULA"), Modifier.weight(1f)); Switch(acceptsEula, { acceptsEula = it }, enabled = readEula) }
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TextButton(onClick = { uriHandler.openUri("https://torpille-38783.web.app/privacy.html?lang=${AppLanguage.code}") }) { Text(t("Lire la politique de confidentialité")) }
            Row { Text(t("J’accepte la politique de confidentialité"), Modifier.weight(1f)); Switch(acceptsPrivacy, { acceptsPrivacy = it }) }
        } }
        Text(t("Les deux acceptations sont obligatoires pour continuer."), style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            context.getSharedPreferences("legal", android.content.Context.MODE_PRIVATE).edit().putLong("acceptedAt", System.currentTimeMillis()).apply()
            onContinue()
        }, enabled = acceptsEula && acceptsPrivacy, modifier = Modifier.fillMaxWidth()) { Text(t("Continuer")) }
    }
}
