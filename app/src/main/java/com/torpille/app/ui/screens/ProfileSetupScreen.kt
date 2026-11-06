package com.torpille.app.ui.screens

import android.app.DatePickerDialog
import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.icu.text.BreakIterator
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import com.torpille.app.data.UserProfile
import com.torpille.app.ui.components.*
import com.torpille.app.ui.viewmodels.ProfileViewModel
import com.torpille.app.util.t
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

@Composable
fun ProfileSetupScreen(onDone: () -> Unit, embedded: Boolean = false, vm: ProfileViewModel = viewModel()) {
    if (!vm.hasLoaded || (vm.me == null && vm.error != null)) {
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } ?: CircularProgressIndicator()
        return
    }
    val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    val profile = (vm.me ?: UserProfile(uid = user?.uid.orEmpty(), profileIcon = "🎯")).let { it.copy(email = it.email ?: user?.email) }
    ProfileEditor(profile, embedded, vm.isSaving, vm.error) { draft, photo, accepted -> vm.save(draft, photo, accepted, onDone) }
}

@Composable
internal fun ProfileEditor(profile: UserProfile, embedded: Boolean, isSaving: Boolean, error: String?, onSave: (UserProfile, Uri?, Boolean) -> Unit) {
    var draft by remember(profile.uid) { mutableStateOf(profile) }
    var photoUri by remember(profile.uid) { mutableStateOf<Uri?>(null) }
    var acceptsTerms by remember(profile.uid) { mutableStateOf(profile.termsAcceptedAt != null && profile.moderationRulesAcceptedAt != null) }
    var sexMenu by remember { mutableStateOf(false) }
    var customEmoji by remember { mutableStateOf(profile.profileIcon.orEmpty()) }
    val context = LocalContext.current
    val date = draft.birthDate?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) photoUri = it }
    fun setDate(value: LocalDate?) { draft = draft.copy(birthDate = value?.let { Timestamp(Date.from(it.atStartOfDay(ZoneId.systemDefault()).toInstant())) }) }
    Column(if (embedded) Modifier.fillMaxWidth() else Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (embedded) ProfileHeroBanner(draft.pseudo, profile.effectiveTotalXP, photoUri?.toString() ?: profile.photoUrl, null)
        else Text(t("Complète ton profil"), style = MaterialTheme.typography.headlineMedium)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!profile.hasRequiredIdentity) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF1DA)).padding(14.dp)) {
                    Text(t("Profil à compléter"), style = MaterialTheme.typography.titleMedium)
                    Text(t("Obligatoire pour créer ou rejoindre une communauté :") + " " + t("Prénom") + ", " + t("Nom de famille") + ", " + t("Pseudo") + ", " + t("Photo de profil") + ".", style = MaterialTheme.typography.bodySmall)
                }
            }
            ProfileAvatar(photoUri?.toString() ?: profile.photoUrl, draft.profileIcon, 86.dp, Modifier.align(Alignment.CenterHorizontally))
            Button(onClick = { picker.launch("image/*") }, enabled = !isSaving, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.Photo, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t(if (photoUri == null) "Choisir une photo" else "Remplacer la photo"))
            }
            if (photoUri != null) TextButton(onClick = { photoUri = null }, enabled = !isSaving) { Text(t("Retirer la photo sélectionnée"), color = MaterialTheme.colorScheme.error) }
            Text(t("Formats recommandés : JPG ou PNG."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (photoUri == null && profile.photoUrl.isNullOrBlank()) {
                ProfileField(customEmoji, { value -> customEmoji = value; firstProfileEmoji(value)?.let { draft = draft.copy(profileIcon = it, photoUrl = null); customEmoji = it } }, "Emoji personnalisé", !isSaving)
                Text(t("Tu peux choisir dans la liste ou saisir n'importe quel emoji avec le clavier emoji Android."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val icons = remember { profileEmojiLibrary() }
                LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxWidth().height(320.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(icons, key = { it }) { icon ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(52.dp).clip(CircleShape).background(if (draft.profileIcon == icon) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
                                .border(2.dp, if (draft.profileIcon == icon) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                .clickable(enabled = !isSaving) { draft = draft.copy(profileIcon = icon, photoUrl = null); customEmoji = icon }, contentAlignment = Alignment.Center) { Text(icon, fontSize = 28.sp) }
                        }
                    }
                }
            }
            Text(t("Informations obligatoires"), style = MaterialTheme.typography.titleMedium)
            ProfileField(draft.firstName, { draft = draft.copy(firstName = it) }, "Prénom", !isSaving)
            ProfileField(draft.lastName, { draft = draft.copy(lastName = it) }, "Nom de famille", !isSaving)
            ProfileField(draft.pseudo, { draft = draft.copy(pseudo = it) }, "Pseudo", !isSaving)
            if (profile.termsAcceptedAt == null || profile.moderationRulesAcceptedAt == null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("J’accepte les règles EULA"), Modifier.weight(1f)); Switch(acceptsTerms, { acceptsTerms = it }, enabled = !isSaving)
            }
            Text(t("Informations facultatives"), style = MaterialTheme.typography.titleMedium)
            val sexes = listOf(null to "Non renseigné", "male" to "Homme", "female" to "Femme", "non_binary" to "Non binaire")
            Box {
                Row(Modifier.fillMaxWidth().clickable(enabled = !isSaving) { sexMenu = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Sexe"), Modifier.weight(1f)); Text(t(sexes.firstOrNull { it.first == draft.sex }?.second ?: "Non renseigné"), color = MaterialTheme.colorScheme.primary); Icon(Icons.Default.UnfoldMore, null)
                }
                DropdownMenu(sexMenu, { sexMenu = false }) { sexes.forEach { (key, label) -> DropdownMenuItem(text = { Text(t(label)) }, onClick = { draft = draft.copy(sex = key); sexMenu = false }) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Ajouter ma date de naissance"), Modifier.weight(1f)); Switch(date != null, { setDate(if (it) LocalDate.now().minusYears(18) else null) }, enabled = !isSaving)
            }
            if (date != null) Row(Modifier.fillMaxWidth().clickable(enabled = !isSaving) {
                DatePickerDialog(context, { _, year, month, day -> setDate(LocalDate.of(year, month + 1, day)) }, date.year, date.monthValue - 1, date.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
            }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Date de naissance"), Modifier.weight(1f)); Text(date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), color = MaterialTheme.colorScheme.primary); Icon(Icons.Default.CalendarMonth, null, Modifier.padding(start = 8.dp))
            }
            ProfileField(draft.city.orEmpty(), { draft = draft.copy(city = it) }, "Ville de domiciliation", !isSaving)
            ProfileField(draft.bio.orEmpty(), { draft = draft.copy(bio = it) }, "Courte présentation", !isSaving, multiline = true)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Email, null, Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(ActionAccent.Violet.tint.copy(alpha = .12f)).padding(8.dp), tint = ActionAccent.Violet.tint)
                Column { Text(t("Email"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(profile.email.orEmpty(), style = MaterialTheme.typography.titleSmall) }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Langue de l'application"), Modifier.weight(1f)); LanguagePicker()
            }
        }
        Box(Modifier.fillMaxWidth().clickable(enabled = !isSaving) { onSave(draft, photoUri, acceptsTerms) }) {
            IllustratedActionButton(t(if (isSaving) "Enregistrement…" else "Enregistrer les modifications"), "", Icons.Default.SaveAlt, Icons.Default.Verified, ActionAccent.Meadow)
        }
        error?.let { Text(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = .08f)).padding(16.dp), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ProfileField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean, multiline: Boolean = false) {
    val description = t(label)
    TextField(value, onChange, placeholder = { Text(description) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = description }, singleLine = !multiline, minLines = if (multiline) 2 else 1, maxLines = if (multiline) 4 else 1,
        enabled = enabled, shape = RoundedCornerShape(18.dp), colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent))
}

private fun firstProfileEmoji(value: String): String? {
    val iterator = BreakIterator.getCharacterInstance().apply { setText(value) }
    var start = iterator.first(); var end = iterator.next()
    while (end != BreakIterator.DONE) {
        val candidate = value.substring(start, end)
        if (candidate.codePoints().anyMatch { UCharacter.hasBinaryProperty(it, UProperty.EMOJI_PRESENTATION) || it == 0xFE0F || it == 0x20E3 }) return candidate
        start = end; end = iterator.next()
    }
    return null
}

private fun profileEmojiLibrary(): List<String> {
    val icons = linkedSetOf("🍺", "🍻", "🎯", "🚀", "⚡️", "🔥", "🌊", "⭐️", "🦈", "🐬", "🦊", "🐻", "🐼", "🐯", "🦁", "🐸", "🐵", "🦄", "🐙", "🦉", "🍀", "🌈", "🍕", "🎮")
    listOf(0x2600..0x27BF, 0x1F300..0x1F5FF, 0x1F600..0x1F64F, 0x1F680..0x1F6FF, 0x1F900..0x1F9FF, 0x1FA70..0x1FAFF).forEach { range ->
        range.filter { UCharacter.hasBinaryProperty(it, UProperty.EMOJI_PRESENTATION) }.forEach { icons.add(String(Character.toChars(it))) }
    }
    return icons.toList()
}
