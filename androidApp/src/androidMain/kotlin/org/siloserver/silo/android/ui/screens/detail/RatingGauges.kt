package org.siloserver.silo.android.ui.screens.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.siloserver.silo.model.catalog.DisplayRating
import org.siloserver.silo.model.catalog.ExternalRatings

private val ImdbYellow = Color(0xFFF5C518)
private val TomatoRed = Color(0xFFFA320A)
private val LeafGreen = Color(0xFF3FB22F)
private val SplatGreen = Color(0xFF6FAE3A)
private val TmdbStart = Color(0xFF90CEA1)
private val TmdbEnd = Color(0xFF01B4E4)

/** True when the rating is a Rotten Tomatoes score (critics or audience). */
private fun DisplayRating.isRottenTomatoes(): Boolean =
    source == ExternalRatings.SOURCE_RT_CRITIC || source == ExternalRatings.SOURCE_RT_AUDIENCE ||
        name.startsWith("RT", ignoreCase = true) || name.contains("Rotten", ignoreCase = true)

private fun DisplayRating.percentValue(): Double? =
    score ?: display.trim().removeSuffix("%").toDoubleOrNull()

/**
 * Ratings as source badges on one row: IMDb as the yellow badge, Rotten
 * Tomatoes as a red tomato (60% and up) or a green splat (below 60%), TMDB as
 * its teal badge, each followed by the score. Any other source shows its name
 * as text. Scrolls sideways when there are too many to fit.
 */
@Composable
internal fun RatingGauges(ratings: List<DisplayRating>, modifier: Modifier = Modifier) {
    if (ratings.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        ratings.forEach { RatingBadge(it) }
    }
}

@Composable
private fun RatingBadge(rating: DisplayRating) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "${rating.name} ${rating.display}" },
    ) {
        when {
            rating.source == ExternalRatings.SOURCE_IMDB ->
                LabelBadge("IMDb", Brush.horizontalGradient(listOf(ImdbYellow, ImdbYellow)), Color(0xFF111111))
            rating.source == ExternalRatings.SOURCE_TMDB ->
                LabelBadge("TMDB", Brush.horizontalGradient(listOf(TmdbStart, TmdbEnd)), Color(0xFF052032))
            rating.source == ExternalRatings.SOURCE_RT_AUDIENCE || rating.name.contains("Audience", ignoreCase = true) ->
                PopcornIcon(fresh = (rating.percentValue() ?: 100.0) >= 60.0)
            rating.isRottenTomatoes() -> TomatoIcon(fresh = (rating.percentValue() ?: 100.0) >= 60.0)
            else -> Text(rating.name, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1, softWrap = false)
        }
        Text(
            rating.display,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun LabelBadge(label: String, background: Brush, content: Color) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        color = content,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A red tomato with a green stem when [fresh], a green splat when not. */
@Composable
private fun TomatoIcon(fresh: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
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
private fun PopcornIcon(fresh: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stripeA = if (fresh) TomatoRed else SplatGreen
        val stripeB = if (fresh) Color.White else Color(0xFFDDE8D0)
        // Popcorn puffs
        val puff = if (fresh) Color(0xFFFFF1B8) else Color(0xFFC9D6B8)
        listOf(7f to 7f, 12f to 5.2f, 17f to 7f, 9.5f to 9f, 14.5f to 9f).forEach { (x, y) ->
            drawCircle(puff, radius = 3f * u, center = Offset(x * u, y * u))
        }
        // Bucket: a trapezoid cut into four vertical stripes
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
