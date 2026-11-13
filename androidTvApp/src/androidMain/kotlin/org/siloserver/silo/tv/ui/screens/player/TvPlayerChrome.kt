package org.siloserver.silo.tv.ui.screens.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.siloserver.silo.tv.ui.theme.InterFamily

/**
 * The TV player's visual language, shared with the phone player: smoked
 * panels over the picture, Paper for whatever has focus, Inter with tabular
 * figures for anything that counts time. Focus is an inversion to Paper with
 * Ink content, never a scale, as on tvOS.
 */
internal object TvPlayerChrome {
    val Paper = Color(0xFFF4F6FB)
    val Ink = Color(0xFF0A0C12)
    val Graphite = Paper.copy(alpha = 0.60f)
    val Faint = Paper.copy(alpha = 0.38f)
    val InkMuted = Ink.copy(alpha = 0.58f)

    /** Panels and menus over the picture. Opaque enough that nothing reads through. */
    val Smoke = Color(0xFF10121A).copy(alpha = 0.92f)

    /** Dialog and anchored-menu cards: fully opaque. */
    val Card = Color(0xFF12141C)
    val PanelStroke = Color.White.copy(alpha = 0.14f)

    /** Transport buttons at rest: a dark disc and a ring, so they read on letterbox bars. */
    val ButtonRest = Color(0xFF10121A).copy(alpha = 0.58f)
    val ButtonRing = Color.White.copy(alpha = 0.14f)

    /** A selected row that does not have focus. */
    val Selected = Color.White.copy(alpha = 0.075f)
    val Chip = Color.White.copy(alpha = 0.10f)

    /** Destructive: bright red text at rest, a red fill on focus. */
    val Stop = Color(0xFFFF453A)
    val StopText = Color(0xFFFF6961)
    val StopRest = Stop.copy(alpha = 0.12f)

    val SideInset = 33.dp
    val BottomInset = 22.dp
}

/**
 * Text that is read meets the TV app's 14sp floor. The two exceptions are
 * chrome at tvOS half scale, approved in TvTypographyReadabilityTest: the
 * uppercase eyebrow above titles and sections, and the SDH/FORCED chips.
 */
internal object TvPlayerType {
    private val base = TextStyle(
        fontFamily = InterFamily,
        color = TvPlayerChrome.Paper,
        textDirection = TextDirection.Content,
    )

    val Eyebrow = base.copy(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.14.em,
        color = TvPlayerChrome.Graphite,
    )
    val Title = base.copy(fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.018).em)
    val Time = base.copy(fontFamily = org.siloserver.silo.tv.ui.theme.VantageMonoFamily, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
    val ButtonLabel = base.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    val ButtonValue = ButtonLabel.copy(fontWeight = FontWeight.Medium)
    val Tab = base.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    val Row = base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val RowDetail = base.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = TvPlayerChrome.Graphite)
    /** A row's value. Not tabular: Inter's tnum also widens hyphens ("X - Large"). */
    val Value = RowDetail

    /** Columns of times and counts that must line up, such as chapter starts. */
    val Figures = RowDetail.copy(fontFamily = org.siloserver.silo.tv.ui.theme.VantageMonoFamily)
    val Chip = base.copy(
        fontFamily = org.siloserver.silo.tv.ui.theme.VantageMonoFamily,
        fontSize = 9.5.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.07.em,
        color = TvPlayerChrome.Paper.copy(alpha = 0.78f),
    )
    val DialogTitle = base.copy(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em)
    val ConfirmTitle = base.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em)
    val Body = base.copy(fontSize = 15.sp, lineHeight = 22.sp, color = TvPlayerChrome.Graphite)
    val Button = base.copy(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
}
