package com.torpille.app.ui.screens

import com.torpille.app.util.userErrorMessage
import com.torpille.app.util.t
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Switch
import com.torpille.app.repo.UserRepository
import com.torpille.app.repo.SocialRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.torpille.app.data.Member
import com.torpille.app.ui.viewmodels.TorpilleursMapViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val ALL_COMMUNITIES_KEY = "__all__"
private const val RECENT_CONNECTION_WINDOW_MS = 30 * 60 * 1000L

@Composable
fun TorpilleursMapScreen(
    onTorpille: (String, String) -> Unit,
    vm: TorpilleursMapViewModel = viewModel(),
) {
    var publicUid by remember { mutableStateOf<String?>(null) }
    var targetUid by remember { mutableStateOf<String?>(null) }
    if (publicUid != null) {
        androidx.activity.compose.BackHandler { publicUid = null }
        PublicProfileScreen(publicUid!!, onBack = { publicUid = null }, onTorpille = { targetUid = publicUid; publicUid = null })
        return
    }
    val me by remember { UserRepository().observeMe() }.collectAsState(initial = null)
    val ghostMode = me?.isGhostMode == true
    val scope = rememberCoroutineScope()
    var privacyError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val communities by vm.communities.collectAsState()

    var selectedCommunityId by remember { mutableStateOf(ALL_COMMUNITIES_KEY) }
    var onlyRecentTorpilleurs by remember { mutableStateOf(true) }
    var mapLoaded by remember { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(context.hasFineLocationPermission())
    }

    val mapsApiKey = remember(context) { context.readMapsApiKey() }
    val isMapsConfigured = mapsApiKey.isNotBlank()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
    }

    LaunchedEffect(communities) {
        if (selectedCommunityId != ALL_COMMUNITIES_KEY && communities.none { it.id == selectedCommunityId }) {
            selectedCommunityId = ALL_COMMUNITIES_KEY
        }
        if (communities.isNotEmpty()) {
            vm.markMeOpenedForCommunities(communities.map { it.id })
        }
    }

    LaunchedEffect(selectedCommunityId, onlyRecentTorpilleurs) {
        mapLoaded = false
    }

    val baseMembers by when (selectedCommunityId) {
        ALL_COMMUNITIES_KEY -> vm.membersForCommunities(communities.map { it.id }).collectAsState()
        else -> vm.members(selectedCommunityId).collectAsState()
    }

    var sharedCommunities by remember { mutableStateOf<List<com.torpille.app.data.Community>>(emptyList()) }
    var loadingShared by remember { mutableStateOf(false) }
    LaunchedEffect(targetUid, communities) {
        val target = targetUid ?: return@LaunchedEffect
        loadingShared = true
        try {
            sharedCommunities = communities.filter { community ->
                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("communities")
                    .document(community.id).collection("members").document(target).get().await().exists()
            }
        } catch (e: Exception) { privacyError = userErrorMessage(e) }
        finally { loadingShared = false }
    }
    targetUid?.let { target ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { targetUid = null }, title = { Text(t("Choisir une communauté")) }, text = {
            Column {
                if (loadingShared) Text(t("Chargement…"))
                else if (sharedCommunities.isEmpty()) Text(t("Aucune communauté en commun."))
                sharedCommunities.forEach { community -> androidx.compose.material3.TextButton(onClick = {
                    targetUid = null; onTorpille(community.id, target)
                }) { Text(community.name) } }
                privacyError?.let { Text(it) }
            }
        }, confirmButton = {}, dismissButton = { androidx.compose.material3.TextButton(onClick = { targetUid = null }) { Text(t("Annuler")) } })
    }
    val nowMs = remember(baseMembers, onlyRecentTorpilleurs) { System.currentTimeMillis() }

    val positionedMembers = remember(baseMembers, onlyRecentTorpilleurs, nowMs, ghostMode) {
        (if (ghostMode) emptyList() else baseMembers)
            .filter { it.lastLatitude != null && it.lastLongitude != null }
            .filter { member ->
                if (!onlyRecentTorpilleurs) return@filter true
                val lastOpenedMs = (member.lastActiveAt ?: member.lastOpenedAt)?.toDate()?.time ?: return@filter false
                nowMs - lastOpenedMs <= RECENT_CONNECTION_WINDOW_MS
            }
            .distinctBy { it.uid }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(50.8503, 4.3517), 10f)
    }

    LaunchedEffect(positionedMembers, mapLoaded) {
        if (!mapLoaded) return@LaunchedEffect

        val positions = positionedMembers.mapNotNull { member ->
            val lat = member.lastLatitude
            val lng = member.lastLongitude
            if (lat != null && lng != null) LatLng(lat, lng) else null
        }

        if (positions.isEmpty()) return@LaunchedEffect

        if (positions.size == 1) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(positions.first(), 12f)
            return@LaunchedEffect
        }

        val bounds = LatLngBounds.builder().apply {
            positions.forEach { include(it) }
        }.build()

        try {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngBounds(bounds, 140),
                durationMs = 800
            )
        } catch (_: Exception) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(positions.first(), 10f)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(t("Carte des torpilleurs"), style = MaterialTheme.typography.titleLarge)

        if (communities.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = t("Tu n'appartiens encore à aucune communauté."),
                    modifier = Modifier.padding(16.dp)
                )
            }
            return@Column
        }

        if (!isMapsConfigured) {
            MissingMapsConfigurationCard()
            return@Column
        }

        if (!hasLocationPermission) {
            LocationPermissionCard(
                onRequestPermission = {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            )
        }

        Row(Modifier.fillMaxWidth()) {
            Text(t("Mode fantôme"), Modifier.weight(1f))
            Switch(ghostMode, { enabled -> scope.launch {
                try { SocialRepository().ghostMode(enabled) } catch (e: Exception) { privacyError = userErrorMessage(e) }
            } })
        }
        if (ghostMode) Text(t("Le mode fantôme est actif : ta position n'est pas partagée et aucun autre utilisateur n'apparaît sur ta carte."))
        privacyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(t("Filtre des communautés"), style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedCommunityId == ALL_COMMUNITIES_KEY,
                    onClick = { selectedCommunityId = ALL_COMMUNITIES_KEY },
                    label = { Text(t("Toutes")) }
                )
            }
            items(communities) { community ->
                FilterChip(
                    selected = selectedCommunityId == community.id,
                    onClick = { selectedCommunityId = community.id },
                    label = { Text(community.name) }
                )
            }
        }

        Text(t("Activité récente"), style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = onlyRecentTorpilleurs,
                    onClick = { onlyRecentTorpilleurs = true },
                    label = { Text(t("Dernière demi-heure")) }
                )
            }
            item {
                FilterChip(
                    selected = !onlyRecentTorpilleurs,
                    onClick = { onlyRecentTorpilleurs = false },
                    label = { Text(t("Tout l'historique")) }
                )
            }
        }

        if (positionedMembers.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (onlyRecentTorpilleurs) {
                        "Aucun utilisateur n’a ouvert l’app dans les 30 dernières minutes pour ce filtre."
                    } else {
                        "Aucun torpilleur n'a encore partagé sa position pour ce filtre."
                    },
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = hasLocationPermission && !ghostMode),
                    uiSettings = MapUiSettings(
                        compassEnabled = true,
                        zoomControlsEnabled = true,
                        myLocationButtonEnabled = hasLocationPermission,
                        mapToolbarEnabled = true
                    ),
                    onMapLoaded = { mapLoaded = true }
                ) {
                    positionedMembers.forEach { member ->
                        val lat = member.lastLatitude ?: return@forEach
                        val lng = member.lastLongitude ?: return@forEach

                        Marker(
                            state = MarkerState(position = LatLng(lat, lng)),
                            title = member.pseudo.ifBlank { "Utilisateur" },
                            snippet = buildSnippet(member),
                            icon = rememberInitialsMarkerIcon(member),
                            onInfoWindowClick = { publicUid = member.uid }
                        )
                    }
                }

                if (!mapLoaded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(t("Chargement de Google Maps…"))
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(t("Interprétation"), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (selectedCommunityId == ALL_COMMUNITIES_KEY) {
                        "La carte regroupe les torpilleurs de toutes tes communautés."
                    } else {
                        "La carte montre uniquement les torpilleurs de la communauté sélectionnée."
                    }
                )
                Text(
                    if (onlyRecentTorpilleurs) {
                        "Seuls les utilisateurs ayant ouvert l’application dans la dernière demi-heure sont visibles."
                    } else {
                        "Tous les torpilleurs disposant d'une dernière position connue sont visibles."
                    }
                )
            }
        }

        vm.error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun MissingMapsConfigurationCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(t("Google Maps n'est pas encore configuré."), style = MaterialTheme.typography.titleMedium)
            Text(t("Ajoute MAPS_API_KEY dans ton fichier gradle.properties, puis relance l'application."))
            Text(t("Exemple : MAPS_API_KEY=ta_cle_google_maps"))
        }
    }
}

@Composable
private fun LocationPermissionCard(
    onRequestPermission: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(t("Autorisation de localisation requise"), style = MaterialTheme.typography.titleMedium)
            Text(t("Accorde la localisation précise pour afficher ta position sur la carte et activer le bouton Ma position."))
            Button(onClick = onRequestPermission) {
                Text(t("Autoriser la localisation"))
            }
        }
    }
}

@Composable
private fun rememberInitialsMarkerIcon(member: Member): BitmapDescriptor {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary.toArgb()

    var avatar by remember(member.photoUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(member.photoUrl) {
        if (!member.photoUrl.isNullOrBlank()) {
            avatar = runCatching { context.imageLoader.execute(ImageRequest.Builder(context).data(member.photoUrl).allowHardware(false).size(160).build()).drawable?.toBitmap() }.getOrNull()
        }
    }
    return remember(member.uid, member.pseudo, member.profileIcon, avatar, primaryColor, onPrimaryColor) {
        createInitialsMarkerBitmapDescriptor(
            context = context,
            pseudo = member.pseudo,
            backgroundColor = primaryColor,
            textColor = onPrimaryColor,
            profileIcon = member.profileIcon,
            avatar = avatar
        )
    }
}

private fun createInitialsMarkerBitmapDescriptor(
    context: Context,
    pseudo: String,
    backgroundColor: Int,
    textColor: Int,
    profileIcon: String?,
    avatar: Bitmap?,
): BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val circleDiameter = (56f * density).roundToInt()
    val pointerHeight = (14f * density).roundToInt()
    val totalWidth = circleDiameter
    val totalHeight = circleDiameter + pointerHeight

    val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val circleRadius = circleDiameter / 2f
    val centerX = totalWidth / 2f
    val centerY = circleRadius

    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
        style = Paint.Style.FILL
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        textSize = 20f * density
        isFakeBoldText = true
    }

    val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
        style = Paint.Style.FILL
    }

    canvas.drawCircle(centerX, centerY, circleRadius, circlePaint)

    val initials = pseudo
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "U" }

    val textBaseline = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
    if (avatar != null) {
        canvas.save()
        canvas.clipPath(android.graphics.Path().apply { addCircle(centerX, centerY, circleRadius - 2f * density, android.graphics.Path.Direction.CW) })
        canvas.drawBitmap(avatar, null, android.graphics.RectF(0f, 0f, circleDiameter.toFloat(), circleDiameter.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
    } else canvas.drawText(profileIcon?.takeIf { it.isNotBlank() } ?: initials, centerX, textBaseline, textPaint)

    val pointerTopY = circleDiameter.toFloat()
    val pointerHalfWidth = 10f * density

    val path = android.graphics.Path().apply {
        moveTo(centerX - pointerHalfWidth, pointerTopY - 2f * density)
        lineTo(centerX + pointerHalfWidth, pointerTopY - 2f * density)
        lineTo(centerX, totalHeight.toFloat())
        close()
    }

    canvas.drawPath(path, pointerPaint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun buildSnippet(member: Member): String {
    val torpilleText = member.lastTorpilledAt?.toDate()?.let {
        "Dernière torpille : ${formatDateTime(it)}"
    } ?: "Dernière torpille : jamais"

    val locationText = member.lastLocationUpdatedAt?.toDate()?.let {
        "Position mise à jour : ${formatDateTime(it)}"
    } ?: "Position jamais mise à jour"

    return "$torpilleText\n$locationText"
}

private fun formatDateTime(date: Date): String {
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
}

private fun Context.hasFineLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun Context.readMapsApiKey(): String {
    return try {
        val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        }

        applicationInfo.metaData
            ?.getString("com.google.android.geo.API_KEY")
            .orEmpty()
            .trim()
    } catch (_: Exception) {
        ""
    }
}
