package com.clipvault.smartclipboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF1A1814)
private val Paper = Color(0xFFF3EFE6)
private val PaperBright = Color(0xFFFFFBF5)
private val Copper = Color(0xFFB85C38)
private val CopperSoft = Color(0xFFE08A62)
private val Moss = Color(0xFF3D5C4A)
private val Night = Color(0xFF141311)
private val NightSurface = Color(0xFF1E1C19)

private val LightColors = lightColorScheme(
    primary = Copper,
    onPrimary = Color(0xFFFFF8F3),
    secondary = Moss,
    onSecondary = Color(0xFFF3EFE6),
    background = Paper,
    onBackground = Ink,
    surface = PaperBright,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E1D4),
    onSurfaceVariant = Color(0xFF5C564C),
    error = Color(0xFF8F3D3D),
    outline = Color(0xFFD5CEC0),
)

private val DarkColors = darkColorScheme(
    primary = CopperSoft,
    onPrimary = Color(0xFF2A140C),
    secondary = Color(0xFF8FB39A),
    onSecondary = Color(0xFF102018),
    background = Night,
    onBackground = Paper,
    surface = NightSurface,
    onSurface = Paper,
    surfaceVariant = Color(0xFF2A2723),
    onSurfaceVariant = Color(0xFFB7B0A4),
    error = Color(0xFFE7A3A3),
    outline = Color(0xFF3A362F),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

@Composable
fun ClipVaultTheme(darkMode: String, content: @Composable () -> Unit) {
    val dark = when (darkMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
