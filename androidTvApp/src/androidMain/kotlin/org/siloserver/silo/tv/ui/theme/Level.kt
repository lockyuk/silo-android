package org.siloserver.silo.tv.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.siloserver.silo.tv.R

/**
 * The Silo Vantage look: cinematic full-bleed backdrops, floating glass chrome, pill controls,
 * a white primary button with an accent halo, Outfit for words and JetBrains Mono for data.
 * The accent follows the backdrop of whatever is featured; it falls back to sky blue.
 *
 * The object keeps the name `Level` (the previous direction) so every screen that already reads
 * these tokens picks up Vantage without further changes. [Vantage] is the same object.
 */
object Level {
    val DefaultAccent = Color(0xFF7CC4FF)

    /** The accent in use. Screens with a featured backdrop set it with [followBackdrop]. */
    var liveAccent by mutableStateOf(DefaultAccent)

    val Bg = Color(0xFF07080C)
    /** Glass panel fill (rgba(16,18,26,.58)). */
    val Glass = Color(0x9410121A)
    /** Light glass for secondary buttons and chips (rgba(255,255,255,.07)). */
    val Glass2 = Color(0x12FFFFFF)
    /** Hairline edge on glass (rgba(255,255,255,.14)). */
    val Edge = Color(0x24FFFFFF)
    val Fg = Color(0xFFF4F6FB)
    val Dim = Color(0xFFC9D0DE)
    val Faint = Color(0xFF9FA8BD)
    val Good = Color(0xFF5BE3A8)
    val Warn = Color(0xFFFFC15A)
    val Bad = Color(0xFFFF6B6B)
    /** Text on the white primary button. */
    val Ink = Color(0xFF0A0C12)
    val Synopsis = Color(0xFFD0D6E3)
    val Track = Color(0x29FFFFFF)

    // Names the Level screens already use.
    val Surface get() = Glass
    val Surface2 get() = Glass2
    val Line get() = Edge
    val Muted get() = Dim
    val Accent get() = liveAccent
    val Accent2 get() = liveAccent

    /** Brand colours for the automation dashboards. */
    val Radarr = Color(0xFFFFC230)
    val Sonarr = Color(0xFF35C5F4)
    val Sabnzbd = Color(0xFF9BE564)

    /**
     * Sets the accent from a backdrop colour: keeps its hue, lifts it to a bright, saturated
     * tone so it reads on the dark glass (hsl(h, 90%, 68%) as in the mock-up).
     */
    fun followBackdrop(sample: Color?) {
        if (sample == null) {
            liveAccent = DefaultAccent
            return
        }
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.argb(255, (sample.red * 255).toInt(), (sample.green * 255).toInt(), (sample.blue * 255).toInt()),
            hsv,
        )
        liveAccent = if (hsv[1] < 0.12f) DefaultAccent else Color.hsl(hsv[0], 0.9f, 0.68f)
    }

    /** A spine colour for an artwork hue (kept for the old spine card). */
    fun spine(hue: Float): Color = Color.hsl(hue.coerceIn(0f, 359.9f), 0.22f, 0.2f)
}

/** The Vantage tokens under their own name. */
val Vantage = Level

/** The accent, animated when it changes with the backdrop. */
@Composable
fun animatedAccent(): Color =
    animateColorAsState(Level.liveAccent, animationSpec = tween(600), label = "vantageAccent").value

val VantageUiFamily = FontFamily(
    Font(R.font.vantage_ui_300, FontWeight.Light),
    Font(R.font.vantage_ui_400, FontWeight.Normal),
    Font(R.font.vantage_ui_500, FontWeight.Medium),
    Font(R.font.vantage_ui_600, FontWeight.SemiBold),
    Font(R.font.vantage_ui_700, FontWeight.Bold),
    Font(R.font.vantage_ui_700, FontWeight.ExtraBold),
    Font(R.font.vantage_ui_700, FontWeight.Black),
)

val VantageMonoFamily = FontFamily(
    Font(R.font.vantage_mono_400, FontWeight.Normal),
    Font(R.font.vantage_mono_500, FontWeight.Medium),
    Font(R.font.vantage_mono_600, FontWeight.SemiBold),
    Font(R.font.vantage_mono_600, FontWeight.Bold),
    Font(R.font.vantage_mono_600, FontWeight.Black),
)

// Previous names, now Vantage faces.
val LevelDisplayFamily = VantageUiFamily
val LevelMonoFamily = VantageMonoFamily
val LevelBodyFamily = VantageUiFamily

/** The shape style: square corners everywhere (the chosen style). The other two are kept for later. */
object ChamferMode { @Volatile var on: Boolean = false; @Volatile var rect: Boolean = true }

/** Corner radius for a halo drawn around a control: square in the rectangular style. */
fun haloRadius(r: Float): Float = if (ChamferMode.rect) 0f else r

/** A rectangle with its four corners cut off diagonally by [cut]. */
class ChamferShape(private val cut: androidx.compose.ui.unit.Dp) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(c, 0f); lineTo(size.width - c, 0f); lineTo(size.width, c)
            lineTo(size.width, size.height - c); lineTo(size.width - c, size.height)
            lineTo(c, size.height); lineTo(0f, size.height - c); lineTo(0f, c); close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

/** Pill / circle: every control, chip, tab and avatar. */
val VantagePill: Shape get() = if (ChamferMode.rect) androidx.compose.ui.graphics.RectangleShape else if (ChamferMode.on) ChamferShape(8.dp) else RoundedCornerShape(percent = 50)
/** Cards and artwork (14px in the mock-up). */
val VantageCard: Shape get() = if (ChamferMode.rect) androidx.compose.ui.graphics.RectangleShape else if (ChamferMode.on) ChamferShape(7.dp) else RoundedCornerShape(10.dp)
/** Glass panels (18px in the mock-up). */
val VantagePanel: Shape get() = if (ChamferMode.rect) androidx.compose.ui.graphics.RectangleShape else if (ChamferMode.on) ChamferShape(10.dp) else RoundedCornerShape(14.dp)

// Previous names: the chamfers became pills and rounded cards.
val LevelChamfer: Shape get() = VantagePill
val LevelChamferLarge: Shape get() = VantageCard

/** The Vantage ground. */
fun Modifier.levelBackground(): Modifier = this.background(Level.Bg)

/** A floating glass panel. */
fun Modifier.vantageGlass(shape: Shape = VantagePanel, edge: Color = Level.Edge): Modifier =
    this.background(Level.Glass, shape).border(1.dp, edge, shape)
