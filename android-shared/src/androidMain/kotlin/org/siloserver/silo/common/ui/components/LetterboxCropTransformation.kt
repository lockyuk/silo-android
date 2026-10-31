package org.siloserver.silo.common.ui.components

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Crops black bars baked into the top and bottom of a picture (a 2.39:1 frame
 * saved in a 16:9 still), so a full-screen backdrop is all picture rather than
 * picture with a black band through the middle of the screen.
 *
 * Only crops when both edges carry a bar and at least half the picture is
 * left, so a dark scene is not mistaken for a letterbox.
 */
object LetterboxCropTransformation : Transformation() {
    override val cacheKey: String = "letterbox-crop-1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val (top, bottom) = letterboxRows(input) ?: return input
        return Bitmap.createBitmap(input, 0, top, input.width, input.height - top - bottom)
    }

    /** Rows of black bar at the top and bottom, or null for no letterbox. */
    internal fun letterboxRows(bitmap: Bitmap): Pair<Int, Int>? {
        val h = bitmap.height
        val w = bitmap.width
        if (h < 32 || w < 32) return null
        val row = IntArray(w)
        val step = (w / SAMPLES).coerceAtLeast(1)
        fun isBar(y: Int): Boolean {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            var x = 0
            while (x < w) {
                val c = row[x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r > MAX_LEVEL || g > MAX_LEVEL || b > MAX_LEVEL) return false
                x += step
            }
            return true
        }
        val limit = h / 4
        var top = 0
        while (top < limit && isBar(top)) top++
        var bottom = 0
        while (bottom < limit && isBar(h - 1 - bottom)) bottom++
        val minBar = (h * MIN_BAR_FRACTION).toInt().coerceAtLeast(2)
        if (top < minBar || bottom < minBar) return null
        // A few rows of margin past the detected edge hide the soft
        // transition line some encodes leave.
        val pad = (h * 0.004f).toInt()
        return (top + pad) to (bottom + pad)
    }

    private const val SAMPLES = 48
    private const val MAX_LEVEL = 24
    private const val MIN_BAR_FRACTION = 0.03f
}
