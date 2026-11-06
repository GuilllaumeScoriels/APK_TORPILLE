package com.torpille.app.ui.components

import com.torpille.app.util.t
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.torpille.app.data.Message

@Composable
fun VideoMessageItem(
    message: Message,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = message.taggedPseudo?.takeIf { it.isNotBlank() }?.let { " → @$it" }.orEmpty()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPlay() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Vidéo",
                    modifier = Modifier.size(22.dp),
                )

                Text(
                    text = "Vidéo$extra",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = t("Lire"),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Lire la vidéo",
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}