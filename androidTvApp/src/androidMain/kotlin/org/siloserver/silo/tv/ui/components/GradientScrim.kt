package org.siloserver.silo.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * A full-size gradient drawn as one stretched strip image instead of a gradient shader.
 *
 * On this Fire Stick a full-screen gradient brush cost about 25 ms of GPU time a frame; a stretched
 * texture of the same colours costs next to nothing and looks the same, because the strip carries
 * 256 steps and is smoothly scaled.
 */
@Composable
fun Modifier.gradientScrim(horizontal: Boolean, vararg stops: Pair<Float, Color>): Modifier = gradientScrimAlpha(horizontal, { 1f }, *stops)

/** As [gradientScrim], with an overall opacity read at draw time (no offscreen layer). */
@Composable
fun Modifier.gradientScrimAlpha(horizontal: Boolean, alpha: () -> Float, vararg stops: Pair<Float, Color>): Modifier {
    val strip = remember(horizontal, stops.toList()) { buildStrip(horizontal, stops.toList()) }
    return this.drawBehind {
        drawImage(
            image = strip,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            alpha = alpha(),
            filterQuality = FilterQuality.Low,
        )
    }
}

private const val StripSteps = 256

private fun buildStrip(horizontal: Boolean, stops: List<Pair<Float, Color>>): ImageBitmap {
    val sorted = stops.sortedBy { it.first }
    val pixels = IntArray(StripSteps) { i ->
        val t = i / (StripSteps - 1f)
        val next = sorted.indexOfFirst { it.first >= t }
        val color = when {
            sorted.isEmpty() -> Color.Transparent
            next == -1 -> sorted.last().second
            next == 0 -> sorted.first().second
            else -> {
                val a = sorted[next - 1]; val b = sorted[next]
                val f = ((t - a.first) / (b.first - a.first).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
                androidx.compose.ui.graphics.lerp(a.second, b.second, f)
            }
        }
        // Un-premultiplied ARGB, as Bitmap.setPixels expects.
        color.toArgb()
    }
    val bitmap = if (horizontal) {
        android.graphics.Bitmap.createBitmap(StripSteps, 1, android.graphics.Bitmap.Config.ARGB_8888).also { it.setPixels(pixels, 0, StripSteps, 0, 0, StripSteps, 1) }
    } else {
        android.graphics.Bitmap.createBitmap(1, StripSteps, android.graphics.Bitmap.Config.ARGB_8888).also { it.setPixels(pixels, 0, 1, 0, 0, 1, StripSteps) }
    }
    return bitmap.asImageBitmap()
}
