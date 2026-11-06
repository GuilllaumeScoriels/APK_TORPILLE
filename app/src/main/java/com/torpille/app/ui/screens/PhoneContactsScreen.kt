package com.torpille.app.ui.screens

import com.torpille.app.util.userErrorMessage
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.functions.FirebaseFunctions
import com.torpille.app.data.UserProfile
import com.torpille.app.repo.SocialRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
private data class PhoneVerification(val id: String? = null, val credential: PhoneAuthCredential? = null)
private suspend fun requestPhoneCode(activity: Activity, number: String): PhoneVerification = suspendCancellableCoroutine { continuation ->
    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            if (continuation.isActive) continuation.resume(PhoneVerification(credential = credential))
        }
        override fun onVerificationFailed(error: FirebaseException) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
            if (continuation.isActive) continuation.resume(PhoneVerification(id = id))
        }
    }
    PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
        .setPhoneNumber(number).setTimeout(60L, TimeUnit.SECONDS).setActivity(activity).setCallbacks(callbacks).build())
}

@Composable
fun PhoneContactsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val functions = remember { FirebaseFunctions.getInstance("europe-west1") }
    val social = remember { SocialRepository() }
    var phone by rememberSaveable { mutableStateOf("") }
    var verifiedPhone by remember { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var code by rememberSaveable { mutableStateOf("") }
    var discoverable by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf(Locale.getDefault().country.ifBlank { "BE" }) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var pending by remember { mutableStateOf<UserProfile?>(null) }
    var sent by remember { mutableStateOf(setOf<String>()) }
    suspend fun load() {
        val data = functions.getHttpsCallable("getContactDiscoverySettings").call().await().data as? Map<*, *> ?: error("Réponse invalide")
        verifiedPhone = data["phoneNumber"] as? String ?: ""
        discoverable = data["discoverable"] as? Boolean ?: false
    }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true; message = null
        scope.launch {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = userErrorMessage(e) }
            finally { busy = false }
        }
    }
    suspend fun verify(credential: PhoneAuthCredential) {
        val user = FirebaseAuth.getInstance().currentUser ?: error("Connecte-toi à nouveau.")
        if (user.phoneNumber == null) user.linkWithCredential(credential).await()
        else user.updatePhoneNumber(credential).await()
        user.getIdToken(true).await()
        verificationId = null; code = ""
        load()
        message = "Numéro vérifié. Tu peux maintenant retrouver tes contacts."
    }
    suspend fun search(numbers: List<String>) {
        results = emptyList()
        val unique = numbers.filter { it.isNotBlank() }.distinct()
        if (unique.isEmpty()) { message = "Aucun numéro à rechercher."; return }
        check(unique.size <= 2000) { "Ton carnet contient plus de 2 000 numéros. Utilise la recherche par numéro." }
        val found = mutableMapOf<String, UserProfile>()
        for (batch in unique.chunked(100)) {
            val response = functions.getHttpsCallable("findPhoneContacts").call(mapOf("numbers" to batch, "country" to country.trim().uppercase(Locale.ROOT))).await().data as? Map<*, *>
            (response?.get("profiles") as? List<*>)?.forEach { value ->
                val p = value as? Map<*, *> ?: return@forEach
                val uid = p["uid"] as? String ?: return@forEach
                found[uid] = UserProfile(uid = uid, pseudo = p["pseudo"] as? String ?: "",
                    photoUrl = p["photoUrl"] as? String, profileIcon = p["profileIcon"] as? String,
                    friendsCount = (p["friendsCount"] as? Number)?.toLong() ?: 0,
                    isLanguagePublic = false, isLeaderboardPublic = false)
            }
        }
        results = found.values.sortedBy { it.pseudo.lowercase() }
        message = if (results.isEmpty()) "Aucun contact visible sur Torpille. Seuls les numéros vérifiés avec la visibilité activée peuvent être trouvés." else "${results.size} contact(s) trouvé(s)."
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (!allowed) { message = "Accès aux contacts refusé. Autorise-le dans les paramètres ou recherche un numéro manuellement." }
        else action {
            val numbers = withContext(Dispatchers.IO) {
                val values = mutableListOf<String>()
                context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) cursor.getString(0)?.let(values::add)
                }
                values
            }
            search(numbers)
        }
    }
    LaunchedEffect(Unit) { action { load() } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("Retour") }; Text("Contacts téléphoniques", style = MaterialTheme.typography.headlineMedium) }
        item {
            Text("Vérifie ton numéro pour retrouver tes contacts. Ton numéro n’est jamais affiché sur ton profil public.")
            if (verifiedPhone.isNotBlank()) Text("Numéro vérifié : $verifiedPhone")
            OutlinedTextField(phone, { phone = it; verificationId = null; code = "" }, enabled = !busy, label = { Text("Numéro avec indicatif (+32, +33…)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            TextButton(enabled = !busy && phone.isNotBlank(), onClick = { action {
                require(phone.trim().startsWith("+")) { "Indique ton numéro avec son indicatif international." }
                val activity = context.activity() ?: error("Impossible d’ouvrir la vérification.")
                verificationId = null
                val response = requestPhoneCode(activity, phone.trim())
                if (response.credential != null) verify(response.credential)
                else { verificationId = response.id; message = "Code SMS envoyé." }
            } }) { Text("Recevoir un code SMS") }
            Text("En continuant, tu acceptes que Firebase traite ton numéro pour la vérification et la prévention des abus. Des frais SMS peuvent s’appliquer.", style = MaterialTheme.typography.bodySmall)
            if (verificationId != null) {
                OutlinedTextField(code, { code = it }, enabled = !busy, label = { Text("Code SMS") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                TextButton(enabled = !busy && code.length >= 6, onClick = { action { verify(PhoneAuthProvider.getCredential(verificationId!!, code.trim())) } }) { Text("Vérifier mon numéro") }
            }
        }
        if (verifiedPhone.isNotBlank()) {
            item {
                Text("Être retrouvé", style = MaterialTheme.typography.titleMedium)
                Text(if (discoverable) "Tes contacts peuvent te retrouver avec ton numéro." else "La recherche de ton compte par numéro est désactivée.")
                TextButton(enabled = !busy, onClick = { action {
                    functions.getHttpsCallable("setContactDiscoverySettings").call(mapOf("discoverable" to !discoverable)).await(); load()
                } }) { Text(if (discoverable) "Désactiver ma visibilité" else "Autoriser la recherche de mon numéro") }
            }
            item {
                OutlinedTextField(country, { country = it.take(2).uppercase(Locale.ROOT) }, enabled = !busy, label = { Text("Pays des numéros locaux : BE, FR, SE…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(query, { query = it }, enabled = !busy, label = { Text("Numéro à rechercher") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                TextButton(enabled = !busy && query.isNotBlank(), onClick = { action { search(listOf(query)) } }) { Text("Rechercher ce numéro") }
                Text("En choisissant Retrouver mes contacts, tu autorises l’envoi de leurs numéros à Torpille pour cette recherche. Ils ne sont pas conservés. Les noms ne sont pas envoyés.", style = MaterialTheme.typography.bodySmall)
                Button(enabled = !busy, onClick = { permission.launch(Manifest.permission.READ_CONTACTS) }) { Text("Retrouver mes contacts") }
            }
        }
        if (busy) item { CircularProgressIndicator() }
        message?.let { item { Text(it) } }
        items(results, key = { it.uid }) { profile ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("@${profile.pseudo}")
                if (profile.uid in sent) Text("Demande envoyée")
                else TextButton(enabled = !busy, onClick = { pending = profile }) { Text("Ajouter") }
            }
        }
    }
    pending?.let { profile -> AlertDialog(onDismissRequest = { pending = null },
        title = { Text("Envoyer une demande d’ami à @${profile.pseudo} ?") },
        confirmButton = { TextButton(onClick = { pending = null; action { social.requestFriend(profile); sent = sent + profile.uid; message = "Demande envoyée." } }) { Text("Envoyer") } },
        dismissButton = { TextButton(onClick = { pending = null }) { Text("Annuler") } }) }
}
