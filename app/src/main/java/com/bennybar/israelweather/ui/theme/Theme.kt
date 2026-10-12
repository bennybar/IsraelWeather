package com.bennybar.israelweather.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.bennybar.israelweather.R
import com.bennybar.israelweather.data.ThemeMode

/** The "Night Data" look: deep ink surfaces, one bright accent, blue for rain, orange for heat. */
val Accents = listOf(Color(0xFFC8F169), Color(0xFF7FE0D0), Color(0xFFFFB37A), Color(0xFFB9A6FF))

/** Colors the M3 scheme has no slot for. */
data class WeatherColors(
    val rain: Color,
    val heat: Color,
    val track: Color,
    val yellow: Color,
    val orange: Color,
    val red: Color,
    val warningBg: Color,
    val onWarningBg: Color,
    val dark: Boolean,
    /** The accent for text and small icons on the page background (darkened in light mode). */
    val accentInk: Color = Color.Unspecified,
)

val LocalWeatherColors = staticCompositionLocalOf { darkWeather }

private val darkWeather = WeatherColors(
    rain = Color(0xFF8AB4FF), heat = Color(0xFFFFB37A), track = Color(0xFF252A36),
    yellow = Color(0xFFFFD84D), orange = Color(0xFFFF8C00), red = Color(0xFFFF4D4F),
    warningBg = Color(0xFF2B2610), onWarningBg = Color(0xFFFFE58A), dark = true,
)
private val lightWeather = WeatherColors(
    rain = Color(0xFF1F5FD1), heat = Color(0xFFB85A00), track = Color(0xFFE2E5DA),
    yellow = Color(0xFFE0A800), orange = Color(0xFFE07000), red = Color(0xFFD32F2F),
    warningBg = Color(0xFFFFF1B8), onWarningBg = Color(0xFF3D2E00), dark = false,
)

private fun darkScheme(accent: Color): ColorScheme = darkColorScheme(
    primary = accent, onPrimary = Color(0xFF0F1117),
    primaryContainer = accent, onPrimaryContainer = Color(0xFF0F1117),
    secondary = Color(0xFF8AB4FF), onSecondary = Color(0xFF0F1117),
    secondaryContainer = Color(0xFF343A2A), onSecondaryContainer = Color(0xFFECEEF4),
    background = Color(0xFF0F1117), onBackground = Color(0xFFECEEF4),
    surface = Color(0xFF0F1117), onSurface = Color(0xFFECEEF4),
    surfaceVariant = Color(0xFF252A36), onSurfaceVariant = Color(0xFFA7AEC0),
    surfaceContainerLowest = Color(0xFF0B0D12), surfaceContainerLow = Color(0xFF161922),
    surfaceContainer = Color(0xFF1A1D26), surfaceContainerHigh = Color(0xFF252A36),
    surfaceContainerHighest = Color(0xFF2E3340),
    outline = Color(0xFF5A6173), outlineVariant = Color(0xFF2E3340),
)

private fun lightScheme(accent: Color): ColorScheme = lightColorScheme(
    primary = accent, onPrimary = Color(0xFF0F1117),
    primaryContainer = accent, onPrimaryContainer = Color(0xFF0F1117),
    secondary = Color(0xFF1F5FD1), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE3CF), onSecondaryContainer = Color(0xFF14161C),
    background = Color(0xFFF4F5EF), onBackground = Color(0xFF14161C),
    surface = Color(0xFFF4F5EF), onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0xFFE2E5DA), onSurfaceVariant = Color(0xFF4D5466),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFBF6),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFE9ECE2),
    surfaceContainerHighest = Color(0xFFDFE3D6),
    outline = Color(0xFF8A90A0), outlineVariant = Color(0xFFD5D9CC),
)

// Google Sans (OFL), trimmed to Latin + Hebrew; its weight axis stops at 700, so ExtraBold uses 700.
private fun googleSans(weight: Int) =
    Font(R.font.google_sans, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight.coerceAtMost(700))))

val GoogleSans = FontFamily(googleSans(400), googleSans(500), googleSans(600), googleSans(700), googleSans(800))

private fun Typography.withFont(f: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = f)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

private val typography = Typography().withFont(GoogleSans)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WeatherTheme(mode: ThemeMode, accent: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val a = Accents.getOrElse(accent) { Accents[0] }
    // The accents are all pale: on the light background they need darkening to stay readable.
    val wc = if (dark) darkWeather.copy(accentInk = a) else lightWeather.copy(accentInk = lerp(a, Color.Black, 0.55f))
    CompositionLocalProvider(LocalWeatherColors provides wc) {
        MaterialExpressiveTheme(
            colorScheme = if (dark) darkScheme(a) else lightScheme(a),
            typography = typography,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
