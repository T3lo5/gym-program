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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val BrandPrimary = 0xFF00C853
private const val BrandPrimaryDark = 0xFF00E676
private const val BrandSecondary = 0xFF00BFA5
private const val BrandAccentEnergy = 0xFFFF6F00

private val StrengthAccent = Color(0xFF00C853)
private val RunAccent = Color(0xFFE65100)
private val MobilityAccent = Color(0xFF3D5AFE)
private val SwimAccent = Color(0xFF7C4DFF)

/** Acento por modalidade — única fonte de cor de modalidade no app (regra H6-7 #3). */
internal object ModalityAccent {
    val strength = StrengthAccent
    val run = RunAccent
    val mobility = MobilityAccent
    val swim = SwimAccent
}

internal val LocalModalityAccent = staticCompositionLocalOf<ModalityAccent> { ModalityAccent }

internal object ThemeColors {
    const val LightSurface = 0xFFFFFFFF
    const val LightOnSurface = 0xFF1C1B1F
    const val LightOnSurfaceVariant = 0xFF49494F
    const val LightPrimaryContainer = 0xFFDCFCE7
    const val LightOnPrimaryContainer = 0xFF006400
    const val LightSecondaryContainer = 0xFFCCFBF1
    const val LightOnSecondaryContainer = 0xFF004D40
    const val LightTertiaryContainer = 0xFFE8DEF8
    const val LightOnTertiaryContainer = 0xFF21005E
    const val LightError = 0xFFB3261E
    const val LightOnError = 0xFFFFFFFF
    const val LightErrorContainer = 0xFFEF9A9A
    const val LightOnErrorContainer = 0xFF4D0000
    const val DarkSurface = 0xFF0E0E10
    const val DarkOnSurface = 0xFFE6E1E5
    const val DarkOnSurfaceVariant = 0xFFCAC4D0
    const val DarkPrimaryContainer = 0xFF1B5E20
    const val DarkOnPrimaryContainer = 0xFFDCFCE7
    const val DarkSecondaryContainer = 0xFF004D40
    const val DarkOnSecondaryContainer = 0xFFCCFBF1
    const val DarkTertiaryContainer = 0xFF2D2240
    const val DarkOnTertiaryContainer = 0xFFE8DEF8
    const val DarkError = 0xFFEF5350
    const val DarkOnError = 0xFF000000
    const val DarkErrorContainer = 0xFF9A3A3A
    const val DarkOnErrorContainer = 0xFFFFFFFF
    /** Superfície elevada para cards de destaque em tema escuro. */
    const val DarkSurfaceContainerHigh = 0xFF232330
}

private val LightColors = lightColorScheme(
    primary = Color(BrandPrimary),
    onPrimary = Color.White,
    primaryContainer = Color(ThemeColors.LightPrimaryContainer),
    onPrimaryContainer = Color(ThemeColors.LightOnPrimaryContainer),
    secondary = Color(BrandSecondary),
    onSecondary = Color.White,
    secondaryContainer = Color(ThemeColors.LightSecondaryContainer),
    onSecondaryContainer = Color(ThemeColors.LightOnSecondaryContainer),
    background = Color(0xFFFAFAFA),
    surface = Color(ThemeColors.LightSurface),
    onSurface = Color(ThemeColors.LightOnSurface),
    surfaceVariant = Color(0xFFF1F8E9),
    surfaceContainer = Color(0xFFF1F8E9),
    error = Color(ThemeColors.LightError),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(BrandPrimaryDark),
    onPrimary = Color(0xFF003300),
    primaryContainer = Color(ThemeColors.DarkPrimaryContainer),
    onPrimaryContainer = Color(ThemeColors.DarkOnPrimaryContainer),
    secondary = Color(BrandSecondary),
    onSecondary = Color.White,
    secondaryContainer = Color(ThemeColors.DarkSecondaryContainer),
    onSecondaryContainer = Color(ThemeColors.DarkOnSecondaryContainer),
    background = Color(ThemeColors.DarkSurface),
    surface = Color(ThemeColors.DarkSurface),
    onSurface = Color(ThemeColors.DarkOnSurface),
    surfaceVariant = Color(0xFF1A1A24),
    surfaceContainer = Color(0xFF1A1A24),
    error = Color(ThemeColors.DarkError),
    onError = Color(ThemeColors.DarkOnError),
)

internal val TreinosTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

internal val TreinosShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
)

/** Escala de espaçamento centralizada (regra do DESIGN.md §4). */
internal object TreinosSpacing {
    val space2xs = 4.dp
    val spaceXs = 8.dp
    val spaceS = 12.dp
    val spaceM = 16.dp
    val spaceL = 20.dp
    val spaceXL = 24.dp
    val spaceXXL = 32.dp
}

@Composable
fun TreinosTheme(
    dynamicColorEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColorEnabled && Build.VERSION.SDK_INT >= 31 && isSystemInDarkTheme() ->
            dynamicDarkColorScheme(context)
        dynamicColorEnabled && Build.VERSION.SDK_INT >= 31 ->
            dynamicLightColorScheme(context)
        isSystemInDarkTheme() -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TreinosTypography,
        shapes = TreinosShapes,
        content = {
            CompositionLocalProvider(LocalModalityAccent provides ModalityAccent) {
                content()
            }
        },
    )
}

/** Retorna o acento da modalidade vigente na composição atual. */
internal fun modalityAccentFor(kind: String): Color = when (kind) {
    "run" -> ModalityAccent.run
    "mobility" -> ModalityAccent.mobility
    "swim" -> ModalityAccent.swim
    else -> ModalityAccent.strength
}
