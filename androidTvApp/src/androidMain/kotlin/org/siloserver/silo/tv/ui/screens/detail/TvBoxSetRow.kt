package org.siloserver.silo.tv.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import org.siloserver.silo.model.catalog.yearsLabel
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import org.siloserver.silo.model.catalog.CatalogBoxSet
import org.siloserver.silo.model.catalog.boxSetRowWindow
import org.siloserver.silo.model.section.SectionItem
import org.siloserver.silo.tv.ui.components.TvMediaRow
import org.siloserver.silo.tv.ui.components.TvRowStyle
import org.siloserver.silo.tv.ui.components.tvCardWidth

/**
 * The box set row on a movie's page: the box set's title over as many of its
 * movies, in release order, as fit across the screen at the usual poster
 * size, ended by an Explore all card that opens the box set page
 * ([onExploreAll]). The row does not scroll; [boxSetRowWindow] picks which
 * movies show. The movie on screen is outlined and tagged.
 *
 * The row sits between the trailers and More Like This. Focus: entering the
 * row lands on the movie on screen the first time ([restoreFocusRequester]
 * at [restoreFocusIndex], an index into [items], where `items.size` names
 * Explore all); Explore all is its last stop. Up moves to the rail above, or
 * calls [onReturnToHero] when it is set.
 */
@Composable
internal fun TvBoxSetRowSection(
    boxSet: CatalogBoxSet,
    items: List<SectionItem>,
    currentContentId: String,
    onExploreAll: () -> Unit,
    onReturnToHero: (() -> Boolean)?,
    onMovieClick: (contentId: String) -> Unit,
    restoreFocusIndex: Int,
    restoreFocusRequester: FocusRequester?,
    restoreFocusRequest: Int,
    onItemFocused: ((SectionItem) -> Unit)?,
    onExploreAllFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One link to the box set page, not the films themselves: the page lists them all.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TvDetailSectionHeader(
            title = "Box set",
            modifier = Modifier.padding(horizontal = TvDetailHorizontalInset),
        )
        val linkFocus = restoreFocusRequester ?: remember { FocusRequester() }
        Column(
            modifier = Modifier.padding(horizontal = TvDetailHorizontalInset),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            org.siloserver.silo.tv.ui.components.TvEpisodeCard(
                title = boxSet.title,
                stillUrl = boxSet.backdropUrl,
                stillThumbhash = boxSet.backdropThumbhash,
                mediaType = "movie",
                onClick = onExploreAll,
                focusRequester = linkFocus,
                titleOverlay = true,
                cardModifier = Modifier
                    .onFocusChanged { if (it.isFocused) onExploreAllFocused() }
                    .onPreviewKeyEvent { event ->
                        if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                            event.key == androidx.compose.ui.input.key.Key.DirectionUp
                        ) {
                            onReturnToHero?.invoke() ?: false
                        } else {
                            false
                        }
                    },
            )
            androidx.tv.material3.Text(
                text = boxSet.title,
                style = org.siloserver.silo.tv.ui.components.vantage.ui(13, androidx.compose.ui.text.font.FontWeight.SemiBold),
                maxLines = 1,
                modifier = Modifier.width(org.siloserver.silo.tv.ui.theme.TvGrid.cardW),
            )
            androidx.tv.material3.Text(
                text = listOfNotNull(
                    org.siloserver.silo.model.catalog.boxSetFilmCount(boxSet.movieCount),
                    boxSet.yearsLabel(),
                ).joinToString(" · "),
                style = org.siloserver.silo.tv.ui.components.vantage.mono(11, color = org.siloserver.silo.tv.ui.theme.Level.Faint),
                maxLines = 1,
            )
        }
    }
}

private val TvBoxSetRowSpacing = org.siloserver.silo.tv.ui.theme.TvGrid.cardGap
