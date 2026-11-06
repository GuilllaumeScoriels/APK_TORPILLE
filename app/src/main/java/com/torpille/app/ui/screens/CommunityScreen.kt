package com.torpille.app.ui.screens

// ------------------------------------------------------------
// Imports : nettoyés (pas de doublons) + ajouts nécessaires
// - Permissions runtime (CAMERA / RECORD_AUDIO)
// - Compat (ContextCompat) + FileProvider (pour Android 8/9)
// - MediaStore RELATIVE_PATH uniquement Android 10+
// ------------------------------------------------------------
import com.torpille.app.util.userErrorMessage
import com.torpille.app.util.t
import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.torpille.app.data.MessageReaction
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.FilterChip
import com.torpille.app.repo.SocialRepository
import com.torpille.app.repo.CommunityRepository
import com.torpille.app.ui.components.ReportDialog
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.torpille.app.data.Member
import com.torpille.app.repo.UserRepository
import com.torpille.app.ui.viewmodels.CommunityViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.torpille.app.data.Message
import com.torpille.app.data.Torpille
import com.torpille.app.data.UnseenTorpilleStory
import com.torpille.app.ui.components.AudioMessageItem
import com.torpille.app.ui.components.VideoMessageItem
import com.torpille.app.ui.components.VideoPlayerWithOverlay
import com.torpille.app.ui.components.ActionAccent
// ------------------------------------------------------------
// Type interne : on garde ton flux actuel (TORPILLE vs RESPONSE)
// La vidéo est envoyée sous forme de "message video" dans le fil
// via vm.sendVideoTorpille / vm.respond (qui créent un message type "video").
// ------------------------------------------------------------
private enum class PendingVideoKind { TORPILLE, RESPONSE, SIMPLE_VIDEO }

@Composable
fun CommunityScreen(
    communityId: String,
    onOpenInfo: () -> Unit,
    onBack: () -> Unit,
    vm: CommunityViewModel = viewModel(),
) {
    // ------------------------------------------------------------
    // Contexte + sources de données (moi, messages, membres)
    // ------------------------------------------------------------
    val context = LocalContext.current

    val me by remember { UserRepository().observeMe() }.collectAsState(initial = null)
    val discussionMessages by vm.messages(communityId).collectAsState()
    val contentRepo = remember { CommunityRepository() }
    val socialRepo = remember { SocialRepository() }
    var savedError by remember { mutableStateOf<String?>(null) }
    val savedMessages by remember(communityId) { contentRepo.observeSavedMessages(communityId).catch { savedError = userErrorMessage(it) } }.collectAsState(emptyList())
    var showSaved by remember { mutableStateOf(false) }
    val messages = if (showSaved) savedMessages else discussionMessages
    var actionMessage by remember { mutableStateOf<Message?>(null) }
    var reportMessage by remember { mutableStateOf<Message?>(null) }
    val actionScope = rememberCoroutineScope()
    var actionBusy by remember { mutableStateOf(false) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }
    fun contentAction(action: suspend () -> Unit) {
        if (actionBusy) return
        actionBusy = true
        actionScope.launch { try { action(); actionMessage = null; actionFeedback = "Action effectuée." }
            catch (e: Exception) { actionFeedback = userErrorMessage(e) } finally { actionBusy = false } }
    }
    var exportPath by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val saveExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destination ->
        val path = exportPath
        exportPath = null
        if (destination != null && path != null) contentAction {
            com.torpille.app.util.SavedContentExport.save(context, File(path), destination)
        }
    }
    fun exportContent(message: Message, save: Boolean) {
        contentAction {
            val file = com.torpille.app.util.SavedContentExport.prepare(context, message, contentRepo)
            if (save) { exportPath = file.absolutePath; saveExport.launch(file.name) }
            else com.torpille.app.util.SavedContentExport.share(context, file)
        }
    }
    val communitySettings by remember(communityId) { contentRepo.observeCommunity(communityId) }.collectAsState(null)
    var scheduleNow by remember { mutableStateOf(java.time.Instant.now()) }
    LaunchedEffect(communityId) { while (true) { scheduleNow = java.time.Instant.now(); kotlinx.coroutines.delay(30000) } }
    val rankingActive = communitySettings?.rankingSchedule?.isActive(scheduleNow) ?: true
    LaunchedEffect(communityId, discussionMessages.firstOrNull()?.id) {
        discussionMessages.firstOrNull()?.createdAt?.let { stamp -> runCatching { contentRepo.markMessagesSeen(communityId, stamp) } }
    }
    val members by vm.members(communityId).collectAsState()
    val myMember by vm.myMember(communityId).collectAsState()
    var hadMembership by remember(communityId) { mutableStateOf(false) }
    LaunchedEffect(myMember?.uid) {
        if (myMember != null) hadMembership = true
        else if (hadMembership) onBack()
    }
    val accountUnseenTorpilles by vm.accountUnseenTorpilles().collectAsState()

    val myUid = FirebaseAuth.getInstance().currentUser?.uid
    val senderPseudo = me?.pseudo ?: ""

    // ------------------------------------------------------------
    // Etats UI
    // - uiError : erreurs affichées sans passer par vm.error (setter privé)
    // ------------------------------------------------------------
    var text by remember { mutableStateOf("") }
    var uiError by remember { mutableStateOf<String?>(null) }
    // Vidéo à lire (clic sur le message vidéo)
    var videoToPlay by remember { mutableStateOf<Message?>(null) }
    var imageToView by remember { mutableStateOf<Message?>(null) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var showUnseenTorpilles by remember { mutableStateOf(false) }
    var unseenTorpilleIndex by remember { mutableStateOf(0) }
    var seenStoryCommunityIds by remember { mutableStateOf(setOf<String>()) }

    var showPickTarget by remember { mutableStateOf(false) }
    var pendingTagUid by remember { mutableStateOf<String?>(null) }
    var pendingTagPseudo by remember { mutableStateOf<String?>(null) }

    var showPickNext by remember { mutableStateOf(false) }
    var pendingNextUid by remember { mutableStateOf<String?>(null) }
    var pendingNextPseudo by remember { mutableStateOf<String?>(null) }

    var showPositionDialog by remember { mutableStateOf(false) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingKind by remember { mutableStateOf<PendingVideoKind?>(null) }
    var pendingRespondTorpilleId by remember { mutableStateOf<String?>(null) }
    var publishToReels by remember { mutableStateOf(false) }

    // ------------------------------------------------------------
    // pendingOutputUri : URI où CaptureVideo va écrire la vidéo.
    // C’est ce point qui crashait avant sur Android 8/9 quand RELATIVE_PATH était utilisé.
    // ------------------------------------------------------------
    var mediaToConfirm by remember { mutableStateOf<Pair<String, Uri>?>(null) }
    var pendingOutputUri by remember { mutableStateOf<Uri?>(null) }

    var isRecordingAudio by remember { mutableStateOf(false) }
    var audioRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioRecordFile by remember { mutableStateOf<File?>(null) }
    var audioToPlay by remember { mutableStateOf<Message?>(null) }

    // ------------------------------------------------------------
    // 1) Launcher de capture vidéo
    // - Si annulé/échec : reset états
    // - Si OK : on ouvre le dialogue de positionnement du tag, puis envoi (message vidéo)
    // ------------------------------------------------------------
    val captureVideoLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success: Boolean ->
            val out = pendingOutputUri
            if (!success || out == null) {
                pendingOutputUri = null
                pendingVideoUri = null
                pendingTagUid = null
                pendingTagPseudo = null
                pendingNextUid = null
                pendingNextPseudo = null
                pendingKind = null
                pendingRespondTorpilleId = null
                return@rememberLauncherForActivityResult
            }

            pendingVideoUri = out

            when (pendingKind) {
                PendingVideoKind.SIMPLE_VIDEO -> {
                    mediaToConfirm = "video" to out
                    pendingVideoUri = null
                    pendingOutputUri = null
                    pendingKind = null
                }
                PendingVideoKind.TORPILLE -> {
                    if (pendingTagUid != null) showPositionDialog = true else showPickTarget = true
                }
                PendingVideoKind.RESPONSE -> {
                    showPositionDialog = true
                }
                null -> {
                }
            }
        }

// ------------------------------------------------------------
// Lance réellement la capture vidéo (sans label return@...)
// On utilise une fonction anonyme pour pouvoir faire `return` normalement.
// ------------------------------------------------------------
    val launchVideoCaptureInternal: () -> Unit = fun() {
        uiError = null

        val uri = createVideoOutputUri(context)
        if (uri == null) {
            uiError = "Impossible de créer le fichier vidéo."
            return
        }

        pendingOutputUri = uri
        captureVideoLauncher.launch(uri)
    }.let { it }

    // ------------------------------------------------------------
    // 3) Launcher des permissions caméra + micro
    // - Si refus : message UI
    // - Si OK : on lance réellement la capture
    // ------------------------------------------------------------
    val pickMediaLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { selectedUri: Uri? ->
            if (selectedUri == null) {
                return@rememberLauncherForActivityResult
            }

            val mimeType = context.contentResolver.getType(selectedUri).orEmpty()
            val isVideo = mimeType.startsWith("video/") || selectedUri.toString().substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("mp4", "mov", "m4v", "3gp", "webm", "mkv")

            mediaToConfirm = (if (isVideo) "video" else "image") to selectedUri
        }

    val requestAudioPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                uiError = "Permission micro refusée : impossible d'envoyer un vocal."
                return@rememberLauncherForActivityResult
            }
            startAudioRecordingInternal(
                context = context,
                onPermissionMissing = {
                    uiError = "Permission micro refusée : impossible d'envoyer un vocal."
                },
                onStarted = { recorder, file ->
                    audioRecordFile = file
                    audioRecorder = recorder
                    isRecordingAudio = true
                },
                onError = { message ->
                    uiError = message
                    audioRecorder?.release()
                    audioRecorder = null
                    audioRecordFile = null
                    isRecordingAudio = false
                },
            )
        }

    val requestVideoPermissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val cameraOk = result[Manifest.permission.CAMERA] == true
            val audioOk = result[Manifest.permission.RECORD_AUDIO] == true

            if (!cameraOk || !audioOk) {
                uiError = "Permissions caméra/micro refusées : impossible d’enregistrer une vidéo."
                return@rememberLauncherForActivityResult
            }

            launchVideoCaptureInternal()
        }

    // ------------------------------------------------------------
    // 4) Fonction appelée par ton flux UI (après choix du membre et "Continuer")
    // - Vérifie permissions runtime
    // - Sinon déclenche la demande
    // ------------------------------------------------------------

    mediaToConfirm?.let { (type, uri) ->
        AlertDialog(onDismissRequest = { if (!vm.isSending) mediaToConfirm = null },
            title = { Text(t("Envoyer ce média ?")) }, text = { Text(t("Le média ne sera envoyé qu’après ta confirmation.")) },
            confirmButton = { TextButton(onClick = {
                when (type) {
                    "video" -> vm.sendVideoMessage(communityId, senderPseudo, uri)
                    "image" -> vm.sendImageMessage(communityId, senderPseudo, uri)
                    "audio" -> vm.sendAudioMessage(communityId, senderPseudo, uri)
                }
                mediaToConfirm = null
            }, enabled = !vm.isSending) { Text(t("Envoyer")) } },
            dismissButton = { TextButton(onClick = { mediaToConfirm = null }, enabled = !vm.isSending) { Text(t("Annuler")) } })
    }

    actionMessage?.let { message ->
        AlertDialog(onDismissRequest = { if (!actionBusy) actionMessage = null }, title = { Text(t("Actions du message")) }, text = {
            Column {
                if (!showSaved && message.type != "system") {
                    MessageReaction.allowed.chunked(3).forEach { emojis ->
                        Row {
                            emojis.forEach { emoji ->
                                TextButton(enabled = !actionBusy, onClick = { contentAction {
                                    contentRepo.setReaction(communityId, message.id,
                                        if (message.reactions.orEmpty()[myUid] == emoji) null else emoji)
                                } }) { Text(emoji) }
                            }
                        }
                    }
                }
                if (!showSaved && message.savedAt == null) TextButton(onClick = { contentAction { contentRepo.saveMessage(communityId, message, senderPseudo) } }, enabled = !actionBusy) { Text(t("Enregistrer")) }
                if (showSaved) {
                    TextButton(enabled = !actionBusy, onClick = { exportContent(message, false) }) { Text("Partager") }
                    TextButton(enabled = !actionBusy, onClick = { exportContent(message, true) }) { Text("Enregistrer sur l’appareil") }
                }
                if (showSaved && message.senderUid == myUid && !message.torpilleId.isNullOrBlank()) {
                    TextButton(onClick = { contentAction { contentRepo.publishSavedMessage(communityId, message) } }, enabled = !actionBusy) { Text(t("Publier dans Memories")) }
                    TextButton(onClick = { contentAction { contentRepo.unpublish(communityId, message.torpilleId!!) } }, enabled = !actionBusy) { Text(t("Retirer des Memories")) }
                }
                if (message.senderUid != myUid) {
                    TextButton(onClick = { reportMessage = message; actionMessage = null }) { Text(t("Signaler")) }
                    TextButton(onClick = { contentAction { socialRepo.hide(communityId, Member(uid = message.senderUid, pseudo = message.senderPseudo)) } }, enabled = !actionBusy) { Text(t("Masquer ce membre dans cette communauté")) }
                    TextButton(onClick = { contentAction { socialRepo.block(Member(uid = message.senderUid, pseudo = message.senderPseudo)) } }, enabled = !actionBusy) { Text(t("Bloquer cet utilisateur")) }
                }
                actionFeedback?.let { Text(it) }
            }
        }, confirmButton = { TextButton(onClick = { actionMessage = null }, enabled = !actionBusy) { Text(t("Fermer")) } })
    }
    reportMessage?.let { message -> ReportDialog(message.id, "message", message.senderUid, message.senderPseudo, communityId, onDismiss = { reportMessage = null }) }

    fun startAudioRecording() {
        startAudioRecordingInternal(
            context = context,
            onPermissionMissing = {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onStarted = { recorder, file ->
                audioRecordFile = file
                audioRecorder = recorder
                isRecordingAudio = true
            },
            onError = { message ->
                uiError = message
                audioRecorder?.release()
                audioRecorder = null
                audioRecordFile = null
                isRecordingAudio = false
            },
        )
    }

    fun launchVideoCapture() {
        uiError = null

        val cameraGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val audioGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (cameraGranted && audioGranted) {
            launchVideoCaptureInternal()
        } else {
            requestVideoPermissionsLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    fun stopAudioRecordingAndSend() {
        val recorder = audioRecorder
        val file = audioRecordFile

        try {
            recorder?.stop()
        } catch (_: Exception) {
        } finally {
            recorder?.release()
            audioRecorder = null
            isRecordingAudio = false
        }

        if (file == null || !file.exists()) {
            uiError = "Enregistrement audio introuvable."
            audioRecordFile = null
            return
        }

        mediaToConfirm = "audio" to Uri.fromFile(file)
        audioRecordFile = null
    }

    val unseenTorpilles = accountUnseenTorpilles

    fun finishUnseenStories() {
        showUnseenTorpilles = false
        if (seenStoryCommunityIds.isNotEmpty()) {
            vm.markTorpillesSeen(seenStoryCommunityIds.toList())
        }
        seenStoryCommunityIds = emptySet()
    }

    fun advanceToNextUnseenStory() {
        if (unseenTorpilleIndex < accountUnseenTorpilles.lastIndex) {
            unseenTorpilleIndex += 1
        } else {
            finishUnseenStories()
        }
    }

    LaunchedEffect(communityId, members) {
        val target = com.torpille.app.util.DeepLinkStore.pendingTorpilleTargetUid
        val member = members.firstOrNull { it.uid == target }
        if (member != null) {
            com.torpille.app.util.DeepLinkStore.pendingTorpilleTargetUid = null
            pendingTagUid = member.uid
            pendingTagPseudo = member.pseudo
            pendingKind = PendingVideoKind.TORPILLE
            launchVideoCapture()
        }
    }

    LaunchedEffect(communityId) {
        vm.syncXPState(communityId)
    }

    LaunchedEffect(showUnseenTorpilles, unseenTorpilleIndex, accountUnseenTorpilles) {
        if (!showUnseenTorpilles) return@LaunchedEffect
        val currentStory = accountUnseenTorpilles.getOrNull(unseenTorpilleIndex) ?: run {
            finishUnseenStories()
            return@LaunchedEffect
        }
        seenStoryCommunityIds = seenStoryCommunityIds + currentStory.communityId
    }

    // ------------------------------------------------------------
    // UI principale : header + carte réponse + carte composition message + liste messages
    // ------------------------------------------------------------
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            try {
                audioRecorder?.release()
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text(t("Retour")) }
            Text(t("Communauté"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onOpenInfo) { Text(t("Infos")) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.weight(1f).clickable {
                    if (accountUnseenTorpilles.isNotEmpty()) {
                        uiError = null
                        seenStoryCommunityIds = emptySet()
                        unseenTorpilleIndex = 0
                        showUnseenTorpilles = true
                    }
                },
                color = Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(ActionAccent.Violet.brush)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = Color.White,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t("Torpilles non vues"), color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                if (unseenTorpilles.isEmpty()) "Aucune nouvelle torpille" else "${unseenTorpilles.size} à regarder",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                            )
                        }
                        if (unseenTorpilles.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("${unseenTorpilles.size}", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.size(82.dp).clickable {
                    uiError = null
                    if (myMember?.pendingTorpilleId != null) {
                        showPickNext = true
                    } else if (communitySettings?.rankingSchedule?.isActive() == false) {
                        uiError = "Hors horaires : tu peux seulement répondre à une torpille reçue."
                    } else {
                        pendingKind = PendingVideoKind.TORPILLE
                        pendingTagUid = null
                        pendingTagPseudo = null
                        pendingNextUid = null
                        pendingNextPseudo = null
                        pendingRespondTorpilleId = null
                        launchVideoCapture()
                    }
                },
                color = Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ActionAccent.Coral.brush),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Text(t("Torpille"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        myMember?.let { mm ->
            if (mm.pendingTorpilleId != null) {
                val remaining = (mm.pendingDeadlineAt?.seconds ?: 0L) - Timestamp.now().seconds
                val isOverdue = remaining <= 0
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF4D6))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(t("Torpille en attente"), fontWeight = FontWeight.Bold)
                        Text("Tu dois répondre à la torpille de @${mm.pendingFromPseudo ?: "quelqu'un"}.")
                        Text(if (isOverdue) "Temps dépassé" else "Temps restant : ${formatSeconds(remaining)}", fontSize = 12.sp, color = Color.DarkGray)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                    .background(ActionAccent.Coral.brush)
                                    .clickable { showPickNext = true }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(t("Filmer la réponse"), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                    .background(ActionAccent.Violet.brush)
                                    .clickable { showPickNext = true }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(t("Relancer"), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (!rankingActive) Text("Hors horaires · réponses uniquement, sans points ni relance", style = MaterialTheme.typography.bodySmall)
        if (vm.isSending) Text(t("Envoi en cours…"))
        if (!uiError.isNullOrBlank()) {
            Text(uiError!!, color = Color.Red, fontSize = 12.sp)
        } else if (!vm.error.isNullOrBlank()) {
            Text(vm.error.orEmpty(), color = Color.Red, fontSize = 12.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!showSaved, { showSaved = false }, label = { Text(t("Discussion")) })
            FilterChip(showSaved, { showSaved = true }, label = { Text(t("Enregistrés")) })
        }
        (savedError ?: actionFeedback)?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            itemsIndexed(
                items = messages,
                key = { _, message -> message.id }
            ) { index, message ->
                Column {
                    if (shouldShowDayHeader(messages, index)) {
                        CommunityDayHeader(message.createdAt?.toDate())
                    }

                    if (message.type == "system") {
                        Text(message.text.orEmpty(), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            color = Color.Gray, style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    } else {
                    CommunityMessageCard(
                        message = message,
                        members = members,
                        me = Member(uid = me?.uid.orEmpty(), pseudo = me?.pseudo.orEmpty(), photoUrl = me?.photoUrl),
                        onOpenVideo = { videoToPlay = it },
                        onOpenAudio = { audioToPlay = it },
                        onOpenImage = { imageToView = it },
                    )
                    if (showSaved) Row {
                        TextButton(enabled = !actionBusy, onClick = { exportContent(message, false) }) { Text("Partager") }
                        TextButton(enabled = !actionBusy, onClick = { exportContent(message, true) }) { Text("Télécharger") }
                    }
                    if (!showSaved && !message.reactions.isNullOrEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            MessageReaction.summary(message.reactions).forEach { (emoji, count) ->
                                TextButton(enabled = !actionBusy, onClick = { contentAction {
                                    contentRepo.setReaction(communityId, message.id,
                                        if (message.reactions.orEmpty()[myUid] == emoji) null else emoji)
                                } }) { Text("$emoji $count") }
                            }
                        }
                    }
                    TextButton(onClick = { actionFeedback = null; actionMessage = message }) { Text(if (message.savedAt != null) "Enregistré · Actions" else "Actions") }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF4F5F7))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ActionAccent.Ocean.brush)
                        .clickable { showMediaSheet = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Photos / vidéos", tint = Color.White)
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                        .background(Color.White)
                        .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(t("Écrire un message…")) },
                        singleLine = false,
                        maxLines = 4,
                    )
                    IconButton(onClick = {
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty()) {
                            vm.sendText(communityId, senderPseudo, trimmed)
                            text = ""
                        }
                    }) {
                        Icon(Icons.Filled.Send, contentDescription = "Envoyer", tint = ActionAccent.Sunset.tint)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isRecordingAudio) ActionAccent.Coral.brush else ActionAccent.Meadow.brush)
                        .clickable {
                            uiError = null
                            if (isRecordingAudio) stopAudioRecordingAndSend() else startAudioRecording()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isRecordingAudio) Icons.Filled.Close else Icons.Filled.Mic,
                        contentDescription = "Vocal",
                        tint = Color.White,
                    )
                }
            }
        }
    }

    if (showMediaSheet) {
        AlertDialog(
            onDismissRequest = { showMediaSheet = false },
            title = { Text(t("Photos / vidéos")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showMediaSheet = false
                        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }) { Text(t("Choisir dans la galerie")) }
                    TextButton(onClick = {
                        showMediaSheet = false
                        pendingKind = PendingVideoKind.SIMPLE_VIDEO
                        launchVideoCapture()
                    }) { Text(t("Filmer une vidéo")) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMediaSheet = false }) { Text(t("Fermer")) } },
        )
    }

    if (showPickTarget) {
        PseudoAutoCompleteDialog(
            title = t("Qui torpiller ?"),
            meUid = myUid,
            members = members,
            onPick = { uid, pseudo ->
                pendingTagUid = uid
                pendingTagPseudo = pseudo
                pendingNextUid = null
                pendingNextPseudo = null
                pendingRespondTorpilleId = null
                pendingKind = PendingVideoKind.TORPILLE
                showPickTarget = false

                if (pendingVideoUri != null) {
                    showPositionDialog = true
                } else {
                    launchVideoCapture()
                }
            },
            onDismiss = {
                showPickTarget = false
                pendingVideoUri = null
                pendingOutputUri = null
                pendingTagUid = null
                pendingTagPseudo = null
                pendingKind = null
            },
        )
    }

    // ------------------------------------------------------------
    // Dialog "Qui identifier ensuite ?" :
    // - on récupère l’ID de torpille en attente
    // - on fixe pending* pour RESPONSE, puis capture vidéo
    // ------------------------------------------------------------
    if (showPickNext) {
        PseudoAutoCompleteDialog(
            title = t("Qui identifier ensuite ?"),
            meUid = myUid,
            members = members,
            onPick = { uid, pseudo ->
                val pendingId = myMember?.pendingTorpilleId
                if (pendingId == null) {
                    uiError = "Aucune torpille en attente."
                    showPickNext = false
                    return@PseudoAutoCompleteDialog
                }

                pendingNextUid = uid
                pendingNextPseudo = pseudo
                pendingTagUid = null
                pendingTagPseudo = null
                pendingKind = PendingVideoKind.RESPONSE
                pendingRespondTorpilleId = pendingId
                showPickNext = false
                launchVideoCapture()
            },
            onDismiss = { showPickNext = false },
        )
    }

    // ------------------------------------------------------------
    // Dialog de positionnement du tag :
    // - Après validation, on envoie la vidéo.
    // - Ton VM doit créer un message "video" dans le fil (c’est ce que tu veux).
    // ------------------------------------------------------------
    audioToPlay?.let { msg ->
        var resolvedUrl by remember(msg.id) { mutableStateOf<String?>(null) }
        var loading by remember(msg.id) { mutableStateOf(false) }
        var localError by remember(msg.id) { mutableStateOf<String?>(null) }

        LaunchedEffect(msg.id) {
            resolvedUrl = null
            localError = null

            if (!msg.audioUrl.isNullOrBlank()) {
                resolvedUrl = msg.audioUrl
                return@LaunchedEffect
            }

            val path = msg.audioPath
            if (!path.isNullOrBlank()) {
                loading = true
                try {
                    resolvedUrl = vm.getAudioPlaybackUrl(
                        audioPath = path,
                        audioBucket = msg.audioBucket,
                    )
                    return@LaunchedEffect
                } catch (e: Exception) {
                    localError = userErrorMessage(e)
                } finally {
                    loading = false
                }
            }

            localError = "Vocal introuvable."
        }

        if (localError != null) {
            uiError = localError
            audioToPlay = null
        } else if (!loading && !resolvedUrl.isNullOrBlank()) {
            Dialog(onDismissRequest = { audioToPlay = null }) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Vocal de ${msg.senderPseudo}")
                        SimpleAudioPlayer(uri = Uri.parse(resolvedUrl))
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { audioToPlay = null },
                        ) {
                            Text(t("Fermer"))
                        }
                    }
                }
            }
        }
    }

    if (showPositionDialog) {
        val uri = pendingVideoUri
        val kind = pendingKind

        val taggedUid = if (kind == PendingVideoKind.TORPILLE) pendingTagUid else pendingNextUid
        val taggedPseudo = if (kind == PendingVideoKind.TORPILLE) pendingTagPseudo else pendingNextPseudo
        val torpId = pendingRespondTorpilleId

        if (uri != null && kind != null && taggedUid != null && taggedPseudo != null) {
            TagPositionDialog(
                videoUri = uri,
                displayText = "@$taggedPseudo",
                showPublishToReels = false,
                publishToReels = publishToReels,
                onPublishToReelsChange = { publishToReels = it },
                onConfirm = { normX, normY ->
                    uiError = null

                    // ------------------------------------------------------------
                    // Envoi vidéo :
                    // - TORPILLE : envoie la vidéo + metadata tag + crée un message "video"
                    // - RESPONSE : idem mais lié à la torpille en attente
                    // ------------------------------------------------------------
                    when (kind) {
                        PendingVideoKind.TORPILLE -> {
                            vm.sendVideoTorpille(
                                communityId = communityId,
                                senderPseudo = senderPseudo,
                                videoUri = uri,
                                taggedUid = taggedUid,
                                taggedPseudo = taggedPseudo,
                                tagX = normX,
                                tagY = normY,
                                publishToReels = publishToReels,
                            )
                        }
                        PendingVideoKind.RESPONSE -> {
                            val pendingId = torpId
                            if (pendingId != null) {
                                vm.respond(
                                    communityId = communityId,
                                    senderPseudo = senderPseudo,
                                    videoUri = uri,
                                    pendingTorpilleId = pendingId,
                                    nextTaggedUid = taggedUid,
                                    nextTaggedPseudo = taggedPseudo,
                                    tagX = normX,
                                    tagY = normY,
                                    publishToReels = publishToReels,
                                )
                            } else {
                                uiError = "Aucune torpille en attente."
                            }
                        }
                        PendingVideoKind.SIMPLE_VIDEO -> {
                            vm.sendVideoMessage(
                                communityId = communityId,
                                senderPseudo = senderPseudo,
                                videoUri = uri,
                            )
                        }
                    }

                    // ------------------------------------------------------------
                    // Reset états (évite des incohérences si l'utilisateur recommence)
                    // ------------------------------------------------------------
                    showPositionDialog = false
                    pendingVideoUri = null
                    pendingKind = null
                    pendingOutputUri = null
                    pendingTagUid = null
                    pendingTagPseudo = null
                    pendingNextUid = null
                    pendingNextPseudo = null
                    pendingRespondTorpilleId = null
                    publishToReels = false
                },
                onDismiss = {
                    showPositionDialog = false
                    pendingVideoUri = null
                    pendingKind = null
                    pendingOutputUri = null
                    pendingTagUid = null
                    pendingTagPseudo = null
                    pendingNextUid = null
                    pendingNextPseudo = null
                    pendingRespondTorpilleId = null
                    publishToReels = false
                },
            )
        } else {
            showPositionDialog = false
        }
    }

    // ------------------------------------------------------------
// Video player: opens when the user taps the video message item
// ------------------------------------------------------------
    imageToView?.let { msg ->
        Dialog(onDismissRequest = { imageToView = null }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Image de ${msg.senderPseudo}")
                    AsyncImage(
                        model = msg.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                    )
                    Button(modifier = Modifier.fillMaxWidth(), onClick = { imageToView = null }) {
                        Text(t("Fermer"))
                    }
                }
            }
        }
    }
    if (showUnseenTorpilles) {
        unseenTorpilles.getOrNull(unseenTorpilleIndex)?.let { story ->
            UnseenTorpilleStoryViewer(
                story = story,
                index = unseenTorpilleIndex,
                total = unseenTorpilles.size,
                onPrevious = {
                    unseenTorpilleIndex = (unseenTorpilleIndex - 1).coerceAtLeast(0)
                },
                onNext = { advanceToNextUnseenStory() },
                onDismiss = { finishUnseenStories() },
                onStoryUnavailable = { message ->
                    uiError = message
                    advanceToNextUnseenStory()
                },
                resolveVideoUrl = { torpille ->
                    when {
                        !torpille.videoUrl.isNullOrBlank() -> torpille.videoUrl
                        !torpille.videoPath.isNullOrBlank() -> vm.getSignedPlaybackUrl(
                            videoPath = torpille.videoPath!!,
                            videoBucket = torpille.videoBucket,
                        )
                        else -> null
                    }
                },
            )
        }
    }

    videoToPlay?.let { msg ->
        var resolvedUrl by remember(msg.id) { mutableStateOf<String?>(null) }
        var loading by remember(msg.id) { mutableStateOf(false) }
        var localError by remember(msg.id) { mutableStateOf<String?>(null) }

        LaunchedEffect(msg.id) {
            resolvedUrl = null
            localError = null
            loading = true

            try {
                var candidateUrl: String? = msg.videoUrl
                var candidatePath: String? = msg.videoPath
                var candidateBucket: String? = msg.videoBucket

                if (candidateUrl.isNullOrBlank() && candidatePath.isNullOrBlank() && !msg.torpilleId.isNullOrBlank()) {
                    val torpille = vm.getTorpilleById(
                        communityId = communityId,
                        torpilleId = msg.torpilleId!!,
                    )
                    candidateUrl = torpille?.videoUrl
                    candidatePath = torpille?.videoPath
                    candidateBucket = torpille?.videoBucket
                }

                Log.d(
                    "VIDEO_DEBUG",
                    "openVideo id=${msg.id} path=$candidatePath bucket=$candidateBucket legacyUrl=$candidateUrl torpilleId=${msg.torpilleId}"
                )

                if (!candidateUrl.isNullOrBlank()) {
                    resolvedUrl = candidateUrl
                    Log.d("VIDEO_DEBUG", "direct videoUrl=$resolvedUrl")
                    return@LaunchedEffect
                }

                if (!candidatePath.isNullOrBlank()) {
                    resolvedUrl = vm.getSignedPlaybackUrl(
                        videoPath = candidatePath,
                        videoBucket = candidateBucket,
                    )
                    Log.d("VIDEO_DEBUG", "resolvedUrl=$resolvedUrl")
                    return@LaunchedEffect
                }

                localError = "Vidéo introuvable."
            } catch (e: Exception) {
                Log.e("VIDEO_DEBUG", "failed to open video", e)
                localError = userErrorMessage(e)
            } finally {
                loading = false
            }
        }

        if (localError != null) {
            // ferme le player et affiche l’erreur via l’UI existante
            uiError = localError
            videoToPlay = null
        } else if (loading || resolvedUrl.isNullOrBlank()) {
            // Option simple : on ne montre pas le dialog tant que l’URL n’est pas prête
            // (tu peux aussi afficher un dialog "Chargement..." si tu veux)
        } else {
            Dialog(onDismissRequest = { videoToPlay = null }) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Vidéo de ${msg.senderPseudo}")

                        val overlay = msg.taggedPseudo?.takeIf { it.isNotBlank() }?.let { "@${it}" }
                        VideoPlayerWithOverlay(
                            videoUri = Uri.parse(resolvedUrl),
                            overlayText = overlay,
                            tagX = msg.tagX,
                            tagY = msg.tagY,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { videoToPlay = null },
                        ) {
                            Text(t("Fermer"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnseenTorpilleStoryViewer(
    story: UnseenTorpilleStory,
    index: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    onStoryUnavailable: (String) -> Unit,
    resolveVideoUrl: suspend (Torpille) -> String?,
) {
    var resolvedUrl by remember(story.communityId, story.torpille.id) { mutableStateOf<String?>(null) }
    var loading by remember(story.communityId, story.torpille.id) { mutableStateOf(true) }

    LaunchedEffect(story.communityId, story.torpille.id) {
        loading = true
        resolvedUrl = null
        try {
            val url = resolveVideoUrl(story.torpille)
            if (url.isNullOrBlank()) {
                onStoryUnavailable("Une torpille n'a pas pu être chargée.")
            } else {
                resolvedUrl = url
            }
        } catch (_: Exception) {
            onStoryUnavailable("Une torpille n'a pas pu être chargée.")
        } finally {
            loading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(total.coerceAtLeast(1)) { step ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (step <= index) Color.White else Color.White.copy(alpha = 0.28f)
                                )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = story.communityName.ifBlank { "Communauté" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = story.torpille.fromPseudo?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Torpille",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 12.sp,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (loading || resolvedUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(t("Chargement..."), color = Color.White)
                        }
                    } else {
                        VideoPlayerWithOverlay(
                            videoUri = Uri.parse(resolvedUrl),
                            overlayText = story.torpille.taggedPseudo?.takeIf { it.isNotBlank() }?.let { "@$it" },
                            tagX = story.torpille.tagX,
                            tagY = story.torpille.tagY,
                            modifier = Modifier.fillMaxSize(),
                            useController = false,
                            onPlaybackEnded = onNext,
                        )
                    }

                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable(enabled = index > 0, onClick = onPrevious)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable(onClick = onNext)
                        )
                    }
                }

                Text(
                    text = t("Touche à gauche pour revenir, à droite pour passer."),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// ------------------------------------------------------------
// Dialog d'autocomplete : inchangé (logique OK)
// ------------------------------------------------------------
@Composable
private fun PseudoAutoCompleteDialog(
    title: String,
    meUid: String?,
    members: List<Member>,
    onPick: (uid: String, pseudo: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val candidates = remember(members, meUid) {
        members
            .filter { it.uid.isNotBlank() && it.uid != meUid }
            .map { m -> m.uid to (m.pseudo.ifBlank { m.uid }) }
            .distinctBy { it.first }
    }

    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showSuggestions by remember { mutableStateOf(true) }

    val filtered = remember(query, candidates) {
        val q = query.trim()
        if (q.isEmpty()) candidates
        else candidates.filter { (_, pseudo) -> pseudo.contains(q, ignoreCase = true) }
    }

    val exactMatch = remember(query, candidates) {
        val q = query.trim()
        if (q.isEmpty()) null
        else candidates.firstOrNull { (_, pseudo) -> pseudo.equals(q, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selected = null
                        showSuggestions = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("Pseudo")) },
                    singleLine = true,
                )

                if (showSuggestions) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(t("Suggestions"))
                            Spacer(Modifier.height(6.dp))
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(filtered.take(12)) { (uid, pseudo) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                query = pseudo
                                                selected = uid to pseudo
                                                showSuggestions = false
                                            }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(pseudo)
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                val errorText = when {
                    query.isBlank() -> null
                    selected != null -> null
                    exactMatch != null -> null
                    else -> "Pseudo non trouvé dans la communauté."
                }
                if (errorText != null) {
                    Text(errorText)
                }
            }
        },
        confirmButton = {
            val picked = selected ?: exactMatch
            Button(
                onClick = {
                    val p = picked ?: return@Button
                    onPick(p.first, p.second)
                },
                enabled = (selected != null || exactMatch != null),
            ) { Text(t("Continuer")) }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text(t("Annuler")) }
        },
    )
}

// ------------------------------------------------------------
// Dialog de positionnement du tag : inchangé (logique OK)
// ------------------------------------------------------------
@Composable
private fun TagPositionDialog(
    videoUri: Uri,
    displayText: String,
    showPublishToReels: Boolean,
    publishToReels: Boolean,
    onPublishToReelsChange: (Boolean) -> Unit,
    onConfirm: (tagX: Double, tagY: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    var thumbnail by remember(videoUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(videoUri) {
        thumbnail = withContext(Dispatchers.IO) { loadVideoFrame(context, videoUri) }
    }

    var boxSizePx by remember { mutableStateOf(IntOffset(1, 1)) }
    var tagOffsetPx by remember { mutableStateOf(Offset(120f, 120f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Positionner l'identifiant")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Glisse \"$displayText\" sur l'image, puis valide.")

                if (showPublishToReels) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t("Publier aussi dans Memories"))
                        Switch(
                            checked = publishToReels,
                            onCheckedChange = onPublishToReelsChange,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .onSizeChanged{size ->
                            boxSizePx = IntOffset(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = thumbnail
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Aperçu vidéo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(t("Aperçu…"))
                        }
                    }

                    Text(
                        text = displayText,
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()

                                    val newX = tagOffsetPx.x + dragAmount.x
                                    val newY = tagOffsetPx.y + dragAmount.y

                                    val clampedX = newX.coerceIn(0f, (boxSizePx.x - 1).toFloat())
                                    val clampedY = newY.coerceIn(0f, (boxSizePx.y - 1).toFloat())

                                    tagOffsetPx = Offset(clampedX, clampedY)
                                }
                            }
                            .padding(6.dp)
                            .offset { IntOffset(tagOffsetPx.x.roundToInt(), tagOffsetPx.y.roundToInt()) },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = boxSizePx.x.toFloat().coerceAtLeast(1f)
                    val h = boxSizePx.y.toFloat().coerceAtLeast(1f)
                    val nx = (tagOffsetPx.x / w).toDouble().coerceIn(0.0, 1.0)
                    val ny = (tagOffsetPx.y / h).toDouble().coerceIn(0.0, 1.0)
                    onConfirm(nx, ny)
                }
            ) { Text(t("Valider")) }
        },
        dismissButton = { Button(onClick = onDismiss) { Text(t("Annuler")) } },
    )
}

@Composable
private fun SimpleAudioPlayer(uri: Uri) {
    val context = LocalContext.current
    var isPlaying by remember(uri) { mutableStateOf(false) }
    val mediaPlayer = remember(uri) {
        MediaPlayer().apply {
            setDataSource(context, uri)
            prepare()
        }
    }

    androidx.compose.runtime.DisposableEffect(mediaPlayer) {
        val listener = MediaPlayer.OnCompletionListener { isPlaying = false }
        mediaPlayer.setOnCompletionListener(listener)

        runCatching {
            mediaPlayer.start()
            isPlaying = true
        }

        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
            } catch (_: Exception) {
            }
            try {
                mediaPlayer.release()
            } catch (_: Exception) {
            }
        }
    }

    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (isPlaying) {
                mediaPlayer.pause()
                isPlaying = false
            } else {
                mediaPlayer.start()
                isPlaying = true
            }
        },
    ) {
        Text(if (isPlaying) "Pause" else "Reprendre le vocal")
    }
}


private fun startAudioRecordingInternal(
    context: Context,
    onPermissionMissing: () -> Unit,
    onStarted: (MediaRecorder, File) -> Unit,
    onError: (String) -> Unit,
) {
    val audioGranted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    if (!audioGranted) {
        onPermissionMissing()
        return
    }

    try {
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        onStarted(recorder, file)
    } catch (e: Exception) {
        onError(userErrorMessage(e))
    }
}
// ------------------------------------------------------------
// createVideoOutputUri : cœur du fix crash
// - Android 10+ (API 29+) : RELATIVE_PATH OK (MediaStore scoped storage)
// - Android 8/9 (API 26-28) : FileProvider sur app-specific external files
//   => pas besoin de WRITE_EXTERNAL_STORAGE, et pas de RELATIVE_PATH (inexistant)
// ------------------------------------------------------------
@Composable
private fun CommunityMessageCard(
    message: Message,
    members: List<Member>,
    me: Member,
    onOpenVideo: (Message) -> Unit,
    onOpenAudio: (Message) -> Unit,
    onOpenImage: (Message) -> Unit,
) {
    val member = if (message.senderUid == me.uid) me else members.firstOrNull { it.uid == message.senderUid }
    CompactCommunityMessageRow(
        message = message,
        member = member,
        onPlayAudio = { onOpenAudio(message) },
        onPlayVideo = {
            if (message.type == "image") onOpenImage(message) else onOpenVideo(message)
        },
    )
}

@Composable
private fun CommunityDayHeader(date: Date?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatCommunityDayHeader(date),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = Color(0xFF7A7A7A),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun CompactCommunityMessageRow(
    message: Message,
    member: Member?,
    onPlayAudio: () -> Unit,
    onPlayVideo: () -> Unit,
) {
    val contentIsPlayable = message.type != "text"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommunitySenderAvatar(member = member)

        Spacer(Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFF4F4F6))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommunityContentTypeIcon(message = message)

            Spacer(Modifier.width(8.dp))

            Text(
                text = "@${message.senderPseudo}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.width(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                when {
                    !message.taggedPseudo.isNullOrBlank() -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.ArrowRightAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF8B8B8B),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "@${message.taggedPseudo}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color(0xFF1E1E1E),
                            )
                        }
                    }
                    message.type == "text" -> {
                        Text(
                            text = message.text.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4A4A4A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    else -> {
                        Text(
                            text = contentLabel(message),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4A4A4A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (contentIsPlayable) {
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        when (message.type) {
                            "audio" -> onPlayAudio()
                            "video", "image" -> onPlayVideo()
                        }
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Lire le contenu",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF1E1E1E),
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = formatMessageTime(message.createdAt?.toDate()),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = Color(0xFF8B8B8B),
            maxLines = 1,
        )
    }
}

@Composable
private fun CommunitySenderAvatar(member: Member?) {
    val photoUrl = member?.photoUrl

    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = "Photo de profil",
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFF0F0F0)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF8B8B8B),
            )
        }
    }
}

@Composable
private fun CommunityContentTypeIcon(message: Message) {
    val icon = when {
        !message.taggedPseudo.isNullOrBlank() -> Icons.Filled.Bolt
        message.type == "video" -> Icons.Filled.Videocam
        message.type == "audio" -> Icons.Filled.GraphicEq
        message.type == "image" -> Icons.Filled.Photo
        else -> Icons.Filled.ChatBubble
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = Color(0xFF1E1E1E),
    )
}

private fun contentLabel(message: Message): String = when {
    !message.taggedPseudo.isNullOrBlank() -> "Torpille"
    message.type == "video" -> "Vidéo"
    message.type == "audio" -> "Vocal"
    message.type == "image" -> "Photo"
    else -> "Contenu"
}

private fun shouldShowDayHeader(messages: List<Message>, index: Int): Boolean {
    if (index !in messages.indices) return false
    val currentDate = messages[index].createdAt?.toDate() ?: return index == 0
    if (index == 0) return true
    val previousDate = messages[index - 1].createdAt?.toDate() ?: return true

    val calendar = Calendar.getInstance()
    calendar.time = currentDate
    val currentYear = calendar.get(Calendar.YEAR)
    val currentDay = calendar.get(Calendar.DAY_OF_YEAR)

    calendar.time = previousDate
    val previousYear = calendar.get(Calendar.YEAR)
    val previousDay = calendar.get(Calendar.DAY_OF_YEAR)

    return currentYear != previousYear || currentDay != previousDay
}

private fun formatCommunityDayHeader(date: Date?): String {
    if (date == null) return ""
    return SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(date)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
}

private fun formatMessageTime(date: Date?): String {
    if (date == null) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
}

private fun createVideoOutputUri(context: Context): Uri? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "torpille_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Torpille")
            }
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        } else {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return null
            val dir = File(baseDir, "Torpille").apply { mkdirs() }
            val file = File(dir, "torpille_${System.currentTimeMillis()}.mp4")

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
    } catch (_: Exception) {
        null
    }
}

// ------------------------------------------------------------
// Extraction d'une frame vidéo pour l'aperçu (thumbnail)
// ------------------------------------------------------------
private fun loadVideoFrame(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(0)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

// ------------------------------------------------------------
// Format d'un compte à rebours
// ------------------------------------------------------------
private fun formatSeconds(sec: Long): String {
    val s = kotlin.math.max(0, sec)
    val h = TimeUnit.SECONDS.toHours(s)
    val m = TimeUnit.SECONDS.toMinutes(s) % 60
    val ss = s % 60
    return if (h > 0) "%dh %02dm %02ds".format(h, m, ss) else "%02dm %02ds".format(m, ss)
}
