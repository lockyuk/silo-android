package org.siloserver.silo.tv.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one grid every title page is laid out on, copied from the mock-up
 * (1280 x 720 px, 0.75 dp per px). Nothing on a title page is positioned
 * per page: logos, text, panels and the bottom row sit at these coordinates.
 */
object TvGrid {
    private fun px(v: Number): Dp = (v.toFloat() * 0.75f).dp

    val edge = px(44)

    // Left column
    val leftW = px(560)
    val kickerTop = px(100)
    val kickerH = px(26)
    val logoTop = px(134)
    val logoW = px(580)
    val logoH = px(116)
    val factsTop = px(262)
    val factsH = px(28)
    val synopsisTop = px(300)
    val synopsisW = px(600)
    /** Five lines of 16 px / 1.5, always reserved. */
    val synopsisH = px(120)
    val actionTop = px(432)
    val actionH = px(46)

    // Right column (no longer used on title pages; kept for pages that still place a card there)
    val rightTop = px(88)
    val rightW = px(248)
    val detailsH = px(182)
    val progressTop = px(282)
    val progressH = px(160)

    // Bottom
    /** Where the section header starts; the hero ends here. */
    val bodyTop = px(548)
    val rowTop = px(590)
    val cardW = px(176)
    val cardH = px(99)
    val cardGap = px(16)
}
