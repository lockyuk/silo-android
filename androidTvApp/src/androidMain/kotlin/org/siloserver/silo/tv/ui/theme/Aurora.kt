package org.siloserver.silo.tv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/**
 * The Aurora look: a deep violet-black ground with soft cyan, violet and pink glows, pill-shaped
 * controls and a cyan to violet to pink gradient wherever something is selected or filled.
 */
object Aurora {
    // Retargeted to the Silo Level scheme: every old Aurora colour is now the scheme's accent.
    val Cyan get() = Level.Accent
    val Violet get() = Level.Accent
    val VioletLight get() = Level.Accent
    val Pink get() = Level.Accent
    val Base get() = Level.Bg

    /** Fills are flat now: the accent colour. */
    val Gradient: Brush get() = SolidColor(Level.Accent)
}

/**
 * Paints the Aurora ground: the base colour plus three large soft glows (violet top left,
 * cyan top right, pink along the bottom). Used in place of the flat page background.
 */
fun Modifier.auroraBackground(): Modifier = this.background(Level.Bg)
