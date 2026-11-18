package es.dan15d.grimorio.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.resources.Res
import es.dan15d.grimorio.resources.dumbledor_regular
import es.dan15d.grimorio.resources.dumbledor_wide
import es.dan15d.grimorio.resources.oswald
import es.dan15d.grimorio.resources.sorts_mill_goudy
import es.dan15d.grimorio.resources.sorts_mill_goudy_italic
import org.jetbrains.compose.resources.Font

// Estética de Ravenswood Bluff (la misma de app.css): noche morada, oro viejo y la sangre solo
// para lo peligroso.

@Immutable
object Grim {
    val bg = Color(0xFF140E1B)
    val bg2 = Color(0xFF1B1424)
    val surface = Color(0xFF241A2F)
    val surface2 = Color(0xFF2C2138)
    val surface3 = Color(0xFF382A47)
    val line = Color(0xFF3E3150)
    val lineStrong = Color(0xFF5B4A72)
    val text = Color(0xFFEFE6D6)
    val textStrong = Color(0xFFFFF8EA)
    val muted = Color(0xFFB2A5BF)
    val gold = Color(0xFFCFA04A)
    val goldBright = Color(0xFFECC878)
    val blood = Color(0xFF8E1120)
    val bloodBright = Color(0xFFC8252F)
    val accent = Color(0xFF6D3FAE)
    val accentDeep = Color(0xFF4A2479)
    val accentBright = Color(0xFFA77CE6)
    val parchment = Color(0xFFECE0C2)
    val ink = Color(0xFF2A1D14)

    val townsfolk = Color(0xFF4F9BFF)
    val outsider = Color(0xFF7FD0F0)
    val minion = Color(0xFFEF6A4F)
    val demon = Color(0xFFE0242F)
    val traveller = Color(0xFFB48BE8)
    val fabled = Color(0xFFE8B84A)
    val loric = Color(0xFF7CCF7F)
    val good = Color(0xFF4F9BFF)
    val evil = Color(0xFFE0242F)

    /** Fondo de la página: el mismo degradado morado que body::before. */
    val pageBrush = Brush.verticalGradient(0f to Color(0xFF1D1230), 0.55f to bg, 1f to Color(0xFF0F0A15))
    val glowBrush = Brush.radialGradient(listOf(Color(0x557C48C4), Color.Transparent))

    /** Botón principal: morado, nunca rojo. */
    val primaryBrush = Brush.verticalGradient(listOf(accent, accentDeep))
}

@Immutable
data class GrimFonts(val display: FontFamily, val body: FontFamily, val ui: FontFamily)

val LocalGrimFonts = staticCompositionLocalOf { GrimFonts(FontFamily.Serif, FontFamily.Serif, FontFamily.SansSerif) }

@Composable
private fun grimFonts(): GrimFonts {
    val display = FontFamily(
        Font(Res.font.dumbledor_regular, FontWeight.Normal),
        Font(Res.font.dumbledor_wide, FontWeight.Bold),
    )
    val body = FontFamily(
        Font(Res.font.sorts_mill_goudy, FontWeight.Normal),
        Font(Res.font.sorts_mill_goudy_italic, FontWeight.Normal, FontStyle.Italic),
    )
    val ui = FontFamily(
        Font(Res.font.oswald, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(Res.font.oswald, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(Res.font.oswald, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(Res.font.oswald, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
    return GrimFonts(display, body, ui)
}

/** Estilos de texto de la app (equivalen a las clases de app.css). */
object GrimType {
    val fonts: GrimFonts @Composable get() = LocalGrimFonts.current
    val title: TextStyle @Composable get() = TextStyle(fontFamily = fonts.display, fontSize = 30.sp, lineHeight = 34.sp, color = Grim.textStrong)
    val heading: TextStyle @Composable get() = TextStyle(fontFamily = fonts.display, fontSize = 22.sp, lineHeight = 26.sp, color = Grim.goldBright)
    val body: TextStyle @Composable get() = TextStyle(fontFamily = fonts.body, fontSize = 17.sp, lineHeight = 25.sp, color = Grim.text)
    val small: TextStyle @Composable get() = TextStyle(fontFamily = fonts.body, fontSize = 15.sp, lineHeight = 21.sp, color = Grim.muted)
    val label: TextStyle @Composable get() = TextStyle(fontFamily = fonts.ui, fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp, color = Grim.text)
    val caps: TextStyle @Composable get() = TextStyle(fontFamily = fonts.ui, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = Grim.muted)
}

@Composable
fun GrimorioTheme(content: @Composable () -> Unit) {
    val fonts = grimFonts()
    val scheme = darkColorScheme(
        primary = Grim.accent,
        onPrimary = Grim.textStrong,
        primaryContainer = Grim.accentDeep,
        onPrimaryContainer = Grim.textStrong,
        secondary = Grim.gold,
        onSecondary = Grim.ink,
        background = Grim.bg,
        onBackground = Grim.text,
        surface = Grim.surface,
        onSurface = Grim.text,
        surfaceVariant = Grim.surface2,
        onSurfaceVariant = Grim.muted,
        surfaceContainerLowest = Grim.bg,
        surfaceContainerLow = Grim.bg2,
        surfaceContainer = Grim.surface,
        surfaceContainerHigh = Grim.surface2,
        surfaceContainerHighest = Grim.surface3,
        outline = Grim.lineStrong,
        outlineVariant = Grim.line,
        error = Grim.bloodBright,
    )
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = fonts.display),
        displayMedium = base.displayMedium.copy(fontFamily = fonts.display),
        displaySmall = base.displaySmall.copy(fontFamily = fonts.display),
        headlineLarge = base.headlineLarge.copy(fontFamily = fonts.display),
        headlineMedium = base.headlineMedium.copy(fontFamily = fonts.display),
        headlineSmall = base.headlineSmall.copy(fontFamily = fonts.display),
        titleLarge = base.titleLarge.copy(fontFamily = fonts.display),
        titleMedium = base.titleMedium.copy(fontFamily = fonts.ui),
        titleSmall = base.titleSmall.copy(fontFamily = fonts.ui),
        bodyLarge = base.bodyLarge.copy(fontFamily = fonts.body, fontSize = 17.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = fonts.body, fontSize = 16.sp),
        bodySmall = base.bodySmall.copy(fontFamily = fonts.body),
        labelLarge = base.labelLarge.copy(fontFamily = fonts.ui),
        labelMedium = base.labelMedium.copy(fontFamily = fonts.ui),
        labelSmall = base.labelSmall.copy(fontFamily = fonts.ui),
    )
    CompositionLocalProvider(LocalGrimFonts provides fonts) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** Fondo de todas las pantallas: degradado morado con un resplandor arriba. */
@Composable
fun GrimBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Grim.pageBrush)) {
        Box(Modifier.fillMaxSize().background(Grim.glowBrush))
        content()
    }
}
