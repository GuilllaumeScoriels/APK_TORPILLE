package com.torpille.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val TorpilleRed = Color(0xFFD31A29)
private val TorpilleSurface = Color(0xFFF6F7FB)
private val TorpilleText = Color(0xFF1B1B1F)

private val DarkColors = darkColorScheme(
    primary = TorpilleRed,
    secondary = Color(0xFF2987F5),
    tertiary = Color(0xFFF5A924),
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF2F2F7),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerLow = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerHighest = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = TorpilleText,
    onSurface = TorpilleText,
)

private val LightColors = lightColorScheme(
    primary = TorpilleRed,
    secondary = Color(0xFF2987F5),
    tertiary = Color(0xFFF5A924),
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF2F2F7),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerLow = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerHighest = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = TorpilleText,
    onSurface = TorpilleText,
)

@Composable
fun TorpilleTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography().let { base -> base.copy(
            headlineLarge = base.headlineLarge.copy(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold),
            headlineMedium = base.headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
            headlineSmall = base.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp),
            bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
            bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp)
        ) },
        content = content
    )
}
