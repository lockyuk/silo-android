package org.siloserver.silo.tv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The Aurora look: a deep violet-black ground with soft cyan, violet and pink glows, pill-shaped
 * controls and a cyan to violet to pink gradient wherever something is selected or filled.
 */
object Aurora {
    val Cyan = Color(0xFF5CE1FF)
    val Violet = Color(0xFF8F7BFF)
    val VioletLight = Color(0xFFB9A8FF)
    val Pink = Color(0xFFFF7AD9)
    val Base = Color(0xFF07060D)

    /** The signature gradient for fills, progress and the selected tab. */
    val Gradient: Brush = Brush.horizontalGradient(listOf(Cyan, Violet, Pink))
}

/**
 * Paints the Aurora ground: the base colour plus three large soft glows (violet top left,
 * cyan top right, pink along the bottom). Used in place of the flat page background.
 */
fun Modifier.auroraBackground(): Modifier = this
    .background(Aurora.Base)
    .drawBehind {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.radialGradient(
                colors = listOf(Aurora.Violet.copy(alpha = 0.34f), Color.Transparent),
                center = Offset(w * 0.05f, h * 0.02f),
                radius = w * 0.55f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(Aurora.Cyan.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(w * 0.98f, h * 0.18f),
                radius = w * 0.45f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(Aurora.Pink.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(w * 0.55f, h * 1.05f),
                radius = w * 0.55f,
            ),
        )
    }
