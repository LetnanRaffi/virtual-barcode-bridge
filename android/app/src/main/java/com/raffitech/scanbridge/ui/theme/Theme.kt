package com.raffitech.scanbridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Background = Color(0xFF07111F)
val Surface = Color(0xFF0D1A2B)
val SurfaceRaised = Color(0xFF122238)
val Accent = Color(0xFF5BA3FF)
val ActionBlue = Color(0xFF256DCE)
val AccentLight = Color(0xFF7DB8FF)
val TextPrimary = Color(0xFFF5F7FB)
val TextSecondary = Color(0xFF9BAAC0)
val Outline = Color(0xFF31445E)
val Success = Color(0xFF37D6A0)
val Error = Color(0xFFFF6B6B)

private val palette = darkColorScheme(
    primary = Accent,
    onPrimary = TextPrimary,
    secondary = AccentLight,
    onSecondary = TextPrimary,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    outline = Outline,
    error = Error,
)

private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 39.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 29.sp, lineHeight = 35.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp),
)

@Composable
fun ScanBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = palette, typography = typography, content = content)
}
