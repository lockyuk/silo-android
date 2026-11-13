package org.siloserver.silo.tv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.siloserver.silo.tv.R

/**
 * The Silo Level look: a silo is read by how full it is, so watch progress is a level gauge, the
 * library is a shelf of spines, and the chrome is flat fills with chamfered corners, condensed
 * display type and monospaced data. No glows, no gradients, no pills.
 */
class LevelScheme(
    val name: String,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val fg: Color,
    val muted: Color,
    val accent: Color,
    val accent2: Color,
    val ink: Color,
    /** Saturation / lightness of the spine fill behind each item's hue. */
    val spineSat: Float,
    val spineLight: Float,
)

object Level {
    val Signal = LevelScheme("Signal", Color(0xFF0E0E0D), Color(0xFF181816), Color(0xFF24241F), Color(0xFF3A3A34), Color(0xFFF2EFE6), Color(0xFFA9A698), Color(0xFFFF5A1F), Color(0xFFFFD23F), Color(0xFF140A04), 0.16f, 0.23f)
    val Grain = LevelScheme("Grain", Color(0xFF100D08), Color(0xFF1A160E), Color(0xFF272015), Color(0xFF403726), Color(0xFFF6ECD8), Color(0xFFB5A88C), Color(0xFFE8B04A), Color(0xFFF6E2B0), Color(0xFF1A1205), 0.22f, 0.22f)
    val Coolant = LevelScheme("Coolant", Color(0xFF070F19), Color(0xFF0D1A28), Color(0xFF14283C), Color(0xFF27415C), Color(0xFFEAF5FF), Color(0xFF9BB4CB), Color(0xFF3DDCFF), Color(0xFFA9F1FF), Color(0xFF031018), 0.30f, 0.21f)
    val Verdigris = LevelScheme("Verdigris", Color(0xFF08110E), Color(0xFF0F1B17), Color(0xFF18291F), Color(0xFF2C4A3D), Color(0xFFEAF8F1), Color(0xFF9BBDAD), Color(0xFF4FE0A8), Color(0xFFC3F7E0), Color(0xFF03150E), 0.28f, 0.21f)
    val Rose = LevelScheme("Rose", Color(0xFF140B10), Color(0xFF1E1118), Color(0xFF2E1A25), Color(0xFF4D2C3D), Color(0xFFFBEAF1), Color(0xFFC3A0B1), Color(0xFFFF7AA8), Color(0xFFFFD0E0), Color(0xFF1C0610), 0.26f, 0.23f)
    val Paper = LevelScheme("Paper", Color(0xFFECE8DC), Color(0xFFF8F5EA), Color(0xFFDDD8C8), Color(0xFFB5AF9B), Color(0xFF1A1914), Color(0xFF5E5A4C), Color(0xFFD9400B), Color(0xFF1A1914), Color(0xFFFFFFFF), 0.34f, 0.74f)
    val Schemes = listOf(Signal, Grain, Coolant, Verdigris, Rose, Paper)

    /** The scheme in use; reading it from composition recomposes when it changes. */
    var current by mutableStateOf(Signal)

    val Bg get() = current.bg
    val Surface get() = current.surface
    val Surface2 get() = current.surface2
    val Line get() = current.line
    val Fg get() = current.fg
    val Muted get() = current.muted
    val Accent get() = current.accent
    val Accent2 get() = current.accent2
    val Ink get() = current.ink

    /** A spine's colour for an item whose artwork hue is [hue] degrees. */
    fun spine(hue: Float): Color =
        Color.hsl(hue.coerceIn(0f, 359.9f), current.spineSat, current.spineLight)
}

val LevelDisplayFamily = FontFamily(
    Font(R.font.level_display_semibold, FontWeight.Normal),
    Font(R.font.level_display_semibold, FontWeight.Medium),
    Font(R.font.level_display_semibold, FontWeight.SemiBold),
    Font(R.font.level_display_extrabold, FontWeight.Bold),
    Font(R.font.level_display_extrabold, FontWeight.ExtraBold),
    Font(R.font.level_display_extrabold, FontWeight.Black),
)

val LevelMonoFamily = FontFamily(
    Font(R.font.level_mono_regular, FontWeight.Normal),
    Font(R.font.level_mono_medium, FontWeight.Medium),
    Font(R.font.level_mono_semibold, FontWeight.SemiBold),
    Font(R.font.level_mono_semibold, FontWeight.Bold),
    Font(R.font.level_mono_semibold, FontWeight.Black),
)

val LevelBodyFamily = FontFamily(
    Font(R.font.level_body_regular, FontWeight.Normal),
    Font(R.font.level_body_medium, FontWeight.Medium),
    Font(R.font.level_body_semibold, FontWeight.SemiBold),
    Font(R.font.level_body_semibold, FontWeight.Bold),
    Font(R.font.level_body_semibold, FontWeight.Black),
)

/** A rectangle with the top-left and bottom-right corners cut at 45 degrees. */
class ChamferShape(private val cut: Dp) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(c, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(0f, size.height)
            lineTo(0f, c)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

val LevelChamfer = ChamferShape(7.dp)
val LevelChamferLarge = ChamferShape(14.dp)

/** The flat Silo Level ground. */
fun Modifier.levelBackground(): Modifier = this.background(Level.Bg)
