package com.torpille.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.torpille.app.data.HomeAnnouncement

@Composable
fun HomeAnnouncementCard(announcement: HomeAnnouncement) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(Color(0xFFD31A29).copy(alpha = .06f)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (announcement.title.isNotEmpty()) Text(announcement.title,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (announcement.body.isNotEmpty()) Text(announcement.body, style = MaterialTheme.typography.bodyLarge)
        announcement.imageUrls.forEach { url ->
            AsyncImage(url, announcement.title.ifEmpty { null },
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))
                    .background(Color.Gray.copy(alpha = .08f)), contentScale = ContentScale.Fit)
        }
    }
}
