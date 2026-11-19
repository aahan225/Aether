package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.ThemeMode
import com.example.domain.model.WeatherScene

/**
 * Dynamic atmospheric sky gradient palette driven by live WMO weather conditions
 * and local solar elevation (daylight, golden hour, twilight, night, storms, snow).
 */
@Immutable
data class AtmosphericPalette(
    val skyTop: Color,
    val skyMid: Color,
    val skyBottom: Color,
    val ambientGlow: Color
) {
    fun verticalSkyBrush(): Brush = Brush.verticalGradient(
        colors = listOf(skyTop, skyMid, skyBottom)
    )
}

fun resolveAtmosphericPalette(
    scene: WeatherScene,
    themeMode: ThemeMode
): AtmosphericPalette {
    if (themeMode == ThemeMode.LIGHT) {
        return AtmosphericPalette(
            skyTop = Color(0xFFD6E8FA),
            skyMid = Color(0xFFEAF3FC),
            skyBottom = Color(0xFFF5F9FD),
            ambientGlow = Color(0x55F6B756)
        )
    }
    if (themeMode == ThemeMode.DARK) {
        return AtmosphericPalette(
            skyTop = Color(0xFF07111F),
            skyMid = Color(0xFF0D1B30),
            skyBottom = Color(0xFF142642),
            ambientGlow = Color(0x3367D4F5)
        )
    }
    return when (scene) {
        WeatherScene.CLEAR_DAY -> AtmosphericPalette(
            skyTop = Color(0xFF0F4C8A),
            skyMid = Color(0xFF1E6FB8),
            skyBottom = Color(0xFF2D8AC7),
            ambientGlow = Color(0x66FFD27D)
        )
        WeatherScene.GOLDEN_HOUR -> AtmosphericPalette(
            skyTop = Color(0xFF1E2B58),
            skyMid = Color(0xFF8C494E),
            skyBottom = Color(0xFFD67B43),
            ambientGlow = Color(0x77FFB65C)
        )
        WeatherScene.PARTLY_CLOUDY -> AtmosphericPalette(
            skyTop = Color(0xFF1A3A63),
            skyMid = Color(0xFF2E5984),
            skyBottom = Color(0xFF47729B),
            ambientGlow = Color(0x44FFE0A3)
        )
        WeatherScene.CLOUDY -> AtmosphericPalette(
            skyTop = Color(0xFF1E2B3B),
            skyMid = Color(0xFF334457),
            skyBottom = Color(0xFF495C70),
            ambientGlow = Color(0x28CBD5E1)
        )
        WeatherScene.FOG -> AtmosphericPalette(
            skyTop = Color(0xFF2A3642),
            skyMid = Color(0xFF455563),
            skyBottom = Color(0xFF60707E),
            ambientGlow = Color(0x33E2E8F0)
        )
        WeatherScene.RAIN -> AtmosphericPalette(
            skyTop = Color(0xFF0E1B2B),
            skyMid = Color(0xFF1B3047),
            skyBottom = Color(0xFF2B4661),
            ambientGlow = Color(0x3367D4F5)
        )
        WeatherScene.THUNDERSTORM -> AtmosphericPalette(
            skyTop = Color(0xFF090F1D),
            skyMid = Color(0xFF151E33),
            skyBottom = Color(0xFF242D47),
            ambientGlow = Color(0x44A5B4FC)
        )
        WeatherScene.SNOW -> AtmosphericPalette(
            skyTop = Color(0xFF1F3854),
            skyMid = Color(0xFF3B5C7E),
            skyBottom = Color(0xFF6A8CAB),
            ambientGlow = Color(0x55E0F2FE)
        )
        WeatherScene.CLEAR_NIGHT -> AtmosphericPalette(
            skyTop = Color(0xFF050B18),
            skyMid = Color(0xFF0E1A35),
            skyBottom = Color(0xFF192B52),
            ambientGlow = Color(0x33A5B4FC)
        )
    }
}

/**
 * Centralized glassmorphism styling tokens for Aether's translucent surfaces,
 * directional specular rim borders, frost opacity, and WCAG-safe contrast overlays.
 */
@Immutable
data class AetherGlassTokens(
    val isDarkGlass: Boolean,
    val surfaceTopAlpha: Float,
    val surfaceBottomColor: Color,
    val surfaceBottomAlpha: Float,
    val borderHighlightTop: Color,
    val borderHighlightBottom: Color,
    val subtleChipBackground: Color,
    val dividerColor: Color,
    val navScrimColor: Color,
    val blurRadius: Dp = 18.dp,
    val defaultCornerRadius: Dp = 24.dp,
    val compactCornerRadius: Dp = 18.dp,
    val borderWidth: Dp = 1.dp
) {
    fun cardFillBrush(highlightTint: Color? = null): Brush = if (isDarkGlass) {
        Brush.verticalGradient(
            colors = listOf(
                (highlightTint ?: Color.White).copy(alpha = surfaceTopAlpha),
                surfaceBottomColor.copy(alpha = surfaceBottomAlpha)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                (highlightTint ?: Color.White).copy(alpha = if (highlightTint != null) 0.22f else surfaceTopAlpha),
                surfaceBottomColor.copy(alpha = surfaceBottomAlpha)
            )
        )
    }

    fun cardBorderBrush(): Brush = Brush.linearGradient(
        colors = listOf(borderHighlightTop, borderHighlightBottom)
    )
}

private val DarkGlassTokens = AetherGlassTokens(
    isDarkGlass = true,
    surfaceTopAlpha = 0.14f,
    surfaceBottomColor = Color(0xFF0A1628),
    surfaceBottomAlpha = 0.44f,
    borderHighlightTop = Color.White.copy(alpha = 0.30f),
    borderHighlightBottom = Color.White.copy(alpha = 0.06f),
    subtleChipBackground = Color.White.copy(alpha = 0.08f),
    dividerColor = Color.White.copy(alpha = 0.09f),
    navScrimColor = Color(0xD90A1424)
)

private val LightGlassTokens = AetherGlassTokens(
    isDarkGlass = false,
    surfaceTopAlpha = 0.88f,
    surfaceBottomColor = Color(0xFFF0F6FC),
    surfaceBottomAlpha = 0.78f,
    borderHighlightTop = Color.White.copy(alpha = 0.96f),
    borderHighlightBottom = Color(0xFF9BB4D1).copy(alpha = 0.38f),
    subtleChipBackground = Color(0xFF0E1D34).copy(alpha = 0.06f),
    dividerColor = Color(0xFF0E1D34).copy(alpha = 0.10f),
    navScrimColor = Color(0xEBF1F6FB)
)

val LocalAetherGlass = staticCompositionLocalOf { DarkGlassTokens }
val LocalAtmosphericPalette = staticCompositionLocalOf {
    resolveAtmosphericPalette(WeatherScene.CLEAR_NIGHT, ThemeMode.DYNAMIC_WEATHER)
}

val AetherDarkColorScheme = darkColorScheme(
    primary = AuroraCyan,
    onPrimary = DeepAtmosphereNavy,
    primaryContainer = Color(0xFF143C5E),
    onPrimaryContainer = Color(0xFFD4F3FF),
    secondary = SolarAmber,
    onSecondary = Color(0xFF281A00),
    secondaryContainer = Color(0xFF42300E),
    onSecondaryContainer = Color(0xFFFFE5B8),
    tertiary = LavenderMist,
    onTertiary = Color(0xFF151C48),
    background = DeepAtmosphereNavy,
    onBackground = Color(0xFFF4F8FC),
    surface = MidnightSlate,
    onSurface = Color(0xFFF4F8FC),
    surfaceVariant = ElevatedGlassDark,
    onSurfaceVariant = Color(0xFFB5C7DD),
    outline = Color(0xFF3E5575),
    error = CoralAlert,
    onError = Color(0xFF3B080A)
)

val AetherLightColorScheme = lightColorScheme(
    primary = CobaltAccent,
    onPrimary = Color.White,
    primaryContainer = SkyBlueContainer,
    onPrimaryContainer = Color(0xFF0A2A5E),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = Color(0xFF4F46E5),
    onTertiary = Color.White,
    background = DaylightSkySurface,
    onBackground = DaylightNavyInk,
    surface = DaylightCardSurface,
    onSurface = DaylightNavyInk,
    surfaceVariant = Color(0xFFE5EEF8),
    onSurfaceVariant = DaylightSecondaryInk,
    outline = Color(0xFFBACAE0),
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun AetherTheme(
    themeMode: ThemeMode = ThemeMode.DYNAMIC_WEATHER,
    weatherScene: WeatherScene = WeatherScene.CLEAR_NIGHT,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DYNAMIC_WEATHER -> true
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTOMATIC -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) AetherDarkColorScheme else AetherLightColorScheme
    val glassTokens = if (useDark) DarkGlassTokens else LightGlassTokens
    val atmosphericPalette = resolveAtmosphericPalette(weatherScene, themeMode)

    CompositionLocalProvider(
        LocalAetherGlass provides glassTokens,
        LocalAtmosphericPalette provides atmosphericPalette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
