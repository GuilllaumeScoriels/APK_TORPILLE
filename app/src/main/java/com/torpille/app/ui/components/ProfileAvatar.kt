package com.torpille.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun ProfileAvatar(photoUrl: String?, profileIcon: String?, size: Dp = 60.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(Color(0xFFECECEE)), contentAlignment = Alignment.Center) {
        if (!profileIcon.isNullOrBlank()) Text(profileIcon, fontSize = (size.value * 0.48f).sp)
        else Icon(Icons.Default.AccountCircle, "Avatar", Modifier.size((size.value * .36f).dp), tint = Color(0xFF77777C))
        if (!photoUrl.isNullOrBlank()) AsyncImage(photoUrl, "Avatar", Modifier.size(size), contentScale = ContentScale.Crop)
    }
}
