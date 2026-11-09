package org.siloserver.silo.common.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.siloserver.silo.model.catalog.DisplayRating
import org.siloserver.silo.model.catalog.ExternalRatings

private val TomatoRed = Color(0xFFFA320A)
private val LeafGreen = Color(0xFF3FB22F)
private val SplatGreen = Color(0xFF6FAE3A)
private val ImdbYellow = Color(0xFFF5C518)

/** Which graphic mark a rating draws, or null when it is shown as plain text. */
internal enum class RatingMarkKind { Imdb, Tomato, Popcorn }

internal fun DisplayRating.markKind(): RatingMarkKind? = when {
    source == ExternalRatings.SOURCE_IMDB -> RatingMarkKind.Imdb
    source == ExternalRatings.SOURCE_RT_AUDIENCE || name.contains("Audience", ignoreCase = true) -> RatingMarkKind.Popcorn
    source == ExternalRatings.SOURCE_RT_CRITIC || name.startsWith("RT", ignoreCase = true) ||
        name.contains("Rotten", ignoreCase = true) -> RatingMarkKind.Tomato
    else -> null
}

/** True when the score is 60 or above (fresh tomato, full popcorn bucket). */
internal fun DisplayRating.isFresh(): Boolean {
    val value = score ?: display.trim().removeSuffix("%").toDoubleOrNull() ?: return true
    return value >= 60.0
}

/** The yellow IMDb badge. */
@Composable
fun ImdbMark(fontSize: androidx.compose.ui.unit.TextUnit = 12.sp) {
    BasicText(
        text = "IMDb",
        style = TextStyle(color = Color(0xFF111111), fontSize = fontSize, fontWeight = FontWeight.Black),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ImdbYellow)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A red tomato with a green stem when [fresh], a green splat when not. */
@Composable
fun TomatoMark(fresh: Boolean, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        if (fresh) {
            drawCircle(TomatoRed, radius = 9f * u, center = Offset(12f * u, 14f * u))
            val left = Path().apply {
                moveTo(12f * u, 6f * u); cubicTo(11f * u, 3f * u, 8f * u, 3f * u, 7f * u, 4f * u)
                cubicTo(9f * u, 4f * u, 10f * u, 5f * u, 10f * u, 6f * u)
                cubicTo(8f * u, 5f * u, 6f * u, 6f * u, 6f * u, 7f * u)
                cubicTo(9f * u, 7f * u, 11f * u, 7f * u, 12f * u, 6f * u); close()
            }
            val right = Path().apply {
                moveTo(12f * u, 6f * u); cubicTo(13f * u, 3f * u, 16f * u, 3f * u, 17f * u, 4f * u)
                cubicTo(15f * u, 4f * u, 14f * u, 5f * u, 14f * u, 6f * u)
                cubicTo(16f * u, 5f * u, 18f * u, 6f * u, 18f * u, 7f * u)
                cubicTo(15f * u, 7f * u, 13f * u, 7f * u, 12f * u, 6f * u); close()
            }
            drawPath(left, LeafGreen)
            drawPath(right, LeafGreen)
        } else {
            val pts = listOf(
                12f to 3f, 14.5f to 7f, 19f to 6.5f, 18f to 11f, 22f to 13.5f, 18f to 16f,
                19f to 20.5f, 14.5f to 20f, 12f to 24f, 9.5f to 20.5f, 5f to 21f, 6f to 16.5f,
                2f to 13.5f, 6f to 11f, 5f to 6.5f, 9.5f to 7f,
            )
            val splat = Path().apply {
                pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u) }
                close()
            }
            drawPath(splat, SplatGreen)
            drawCircle(Color(0xFF243D10), radius = 1.2f * u, center = Offset(9f * u, 12f * u))
            drawCircle(Color(0xFF243D10), radius = 1.2f * u, center = Offset(15f * u, 12f * u))
        }
    }
}

/** Rotten Tomatoes audience score: a striped popcorn bucket (red and white) when [fresh], greyed green when not. */
@Composable
fun PopcornMark(fresh: Boolean, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val stripeA = if (fresh) TomatoRed else SplatGreen
        val stripeB = if (fresh) Color.White else Color(0xFFDDE8D0)
        val puff = if (fresh) Color(0xFFFFF1B8) else Color(0xFFC9D6B8)
        listOf(7f to 7f, 12f to 5.2f, 17f to 7f, 9.5f to 9f, 14.5f to 9f).forEach { (x, y) ->
            drawCircle(puff, radius = 3f * u, center = Offset(x * u, y * u))
        }
        val top = 10f; val bottom = 22f
        val topL = 4f; val topR = 20f; val botL = 6.5f; val botR = 17.5f
        for (i in 0 until 4) {
            val t0 = i / 4f; val t1 = (i + 1) / 4f
            val path = Path().apply {
                moveTo((topL + (topR - topL) * t0) * u, top * u)
                lineTo((topL + (topR - topL) * t1) * u, top * u)
                lineTo((botL + (botR - botL) * t1) * u, bottom * u)
                lineTo((botL + (botR - botL) * t0) * u, bottom * u)
                close()
            }
            drawPath(path, if (i % 2 == 0) stripeA else stripeB)
        }
    }
}
