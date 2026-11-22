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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val BrandPrimary = 0xFFA3E635  // Verde Lima vibrante (primária)
private const val BrandPrimaryOn = 0xFF0B0D0C // Preto puro sobre primária (alto contraste)

private const val SurfaceBg = 0xFF0B0D0C      // Background principal da tela
private const val SurfaceCard = 0xFF151816    // Superfície de cards
private const val SurfaceBorder = 0xFF242926  // Borda sutil 1dp
private const val NavigationBarBg = 0xFF111412 // Barra de navegação inferior
private const val NavigationBarActive = 0xFFA3E635 // Indicador/texto ativo
private const val TextPrimary = 0xFFF9FAFB    // Texto primário
private const val TextSecondary = 0xFF9CA3AF  // Texto secundário/legendas
private const val OutlinedBorder = 0xFF323834 // Borda de OutlinedButton
private const val OutlinedText = 0xFFF3F4F6   // Texto de OutlinedButton

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
    const val DarkSurface = SurfaceBg
    const val DarkNavigationBar = NavigationBarBg
    const val DarkSurfaceCard = SurfaceCard
    const val DarkSurfaceBorder = SurfaceBorder
    const val DarkOnSurface = TextPrimary
    const val DarkOnSurfaceVariant = TextSecondary
    const val DarkPrimary = BrandPrimary
    const val DarkOnPrimary = BrandPrimaryOn
    const val DarkPrimaryContainer = BrandPrimary
    const val DarkOnPrimaryContainer = BrandPrimaryOn
    const val DarkSurfaceContainerHigh = SurfaceCard
    const val DarkError = 0xFFEF5350
    const val DarkOnError = 0xFF000000
    const val DarkErrorContainer = 0xFF9A3A3A
    const val DarkOnErrorContainer = 0xFFFFFFFF
}

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

/**
 * Tema do Treinos — Dark Mode profundo com Verde Lima/Neon como identidade.
 *
 * `dynamicColorEnabled` é ignorado por padrão (desligado): a identidade da
 * marca (escuro, energético) se mantém independentemente do sistema.
 */
@Composable
fun TreinosTheme(
    dynamicColorEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = Color(BrandPrimary),
        onPrimary = Color(BrandPrimaryOn),
        primaryContainer = Color(BrandPrimary),
        onPrimaryContainer = Color(BrandPrimaryOn),
        background = Color(SurfaceBg),
        surface = Color(SurfaceCard),
        surfaceVariant = Color(SurfaceCard),
        surfaceContainer = Color(SurfaceCard),
        surfaceContainerHigh = Color(SurfaceCard),
        onSurface = Color(TextPrimary),
        onSurfaceVariant = Color(TextSecondary),
        error = Color(ThemeColors.DarkError),
        onError = Color(ThemeColors.DarkOnError),
    )
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
