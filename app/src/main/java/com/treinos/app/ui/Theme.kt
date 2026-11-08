package com.treinos.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Green = Color(0xFF2E7D32)
private val GreenDark = Color(0xFF81C784)
private val Teal = Color(0xFF00695C)

internal object ThemeColors {
    const val LightSurface = 0xFFFFFFFF
    const val LightOnSurface = 0xFF1C1B1F
    const val LightPrimaryContainer = 0xFFE8F5E9
    const val LightOnPrimaryContainer = 0xFF1B5E20
    const val DarkSurface = 0xFF1E1E1E
    const val DarkOnSurface = 0xFFE6E1E5
    const val DarkPrimaryContainer = 0xFF387C3C
    const val DarkOnPrimaryContainer = 0xFFE8F5E9
}

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(ThemeColors.LightPrimaryContainer),
    onPrimaryContainer = Color(ThemeColors.LightOnPrimaryContainer),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2F1),
    onSecondaryContainer = Color(0xFF004D40),
    background = Color(0xFFFAFAFA),
    surface = Color(ThemeColors.LightSurface),
    onSurface = Color(ThemeColors.LightOnSurface),
    surfaceVariant = Color(0xFFF1F8E9),
    surfaceContainer = Color(0xFFF1F8E9),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = GreenDark,
    onPrimary = Color(0xFF1B5E20),
    primaryContainer = Color(ThemeColors.DarkPrimaryContainer),
    onPrimaryContainer = Color(ThemeColors.DarkOnPrimaryContainer),
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color(0xFF00332E),
    secondaryContainer = Color(0xFF00695C),
    onSecondaryContainer = Color(0xFFE0F2F1),
    background = Color(0xFF121212),
    surface = Color(ThemeColors.DarkSurface),
    onSurface = Color(ThemeColors.DarkOnSurface),
    surfaceVariant = Color(0xFF2C2C2C),
    surfaceContainer = Color(0xFF2C2C2C),
    error = Color(0xFFEF5350),
    onError = Color.Black,
)

internal val TreinosTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

internal val TreinosShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
)

@Composable
fun TreinosTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= 31 && isSystemInDarkTheme() -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        isSystemInDarkTheme() -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TreinosTypography,
        shapes = TreinosShapes,
        content = content,
    )
}
