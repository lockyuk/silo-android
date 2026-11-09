package org.siloserver.silo.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.siloserver.silo.common.ui.PopcornMark
import org.siloserver.silo.common.ui.TomatoMark
import org.siloserver.silo.model.catalog.DisplayRating
import org.siloserver.silo.model.catalog.ExternalRatings

private val ImdbYellow = Color(0xFFF5C518)
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
                PopcornMark(fresh = (rating.percentValue() ?: 100.0) >= 60.0)
            rating.isRottenTomatoes() -> TomatoMark(fresh = (rating.percentValue() ?: 100.0) >= 60.0)
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
