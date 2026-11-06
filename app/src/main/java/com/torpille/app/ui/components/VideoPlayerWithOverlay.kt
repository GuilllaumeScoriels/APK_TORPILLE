package com.torpille.app.ui.components

import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.rememberUpdatedState
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.media3.common.Player
import kotlin.math.roundToInt

@Composable
fun VideoPlayerWithOverlay(
    videoUri: Uri,
    overlayText: String?,
    tagX: Double?,
    tagY: Double?,
    modifier: Modifier = Modifier,
    useController: Boolean = true,
    onPlaybackEnded: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestOnEnded by rememberUpdatedState(onPlaybackEnded)
    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("VIDEO_DEBUG", "ExoPlayer playback error", error)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        latestOnEnded?.invoke()
                    }
                }
            })
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player, lifecycle) {
        var resumePlayback = true
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> { resumePlayback = player.playWhenReady; player.pause() }
                Lifecycle.Event.ON_RESUME -> if (resumePlayback) player.play()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.release() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    this.useController = useController
                }
            },
            update = { view -> view.player = player; view.useController = useController },
        )

        if (!overlayText.isNullOrBlank() && tagX != null && tagY != null) {
            var boxSize by remember { mutableStateOf(IntSize.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { boxSize = it }
            ) {
                val w = boxSize.width.toFloat().coerceAtLeast(1f)
                val h = boxSize.height.toFloat().coerceAtLeast(1f)

                val px = tagX.coerceIn(0.0, 1.0) * w
                val py = tagY.coerceIn(0.0, 1.0) * h

                Text(
                    text = overlayText,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.offset {
                        IntOffset(px.roundToInt(), py.roundToInt())
                    },
                )
            }
        }
    }
}
