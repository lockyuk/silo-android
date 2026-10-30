package org.siloserver.silo.tv.ui.screens.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.siloserver.silo.common.cards.LocalEpisodeSpoilerPrefs
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.catalog.BrowseItem
import org.siloserver.silo.model.settings.EpisodeSpoilers
import org.siloserver.silo.overlays.OverlayDataExtractor
import org.siloserver.silo.tv.ui.components.TvMediaCard
import org.siloserver.silo.tv.ui.components.rememberTvBrowseItemCardActions
import org.siloserver.silo.tv.ui.theme.TvSkyline

/**
 * A library collection laid out like a title page rather than a grid: the
 * collection's own backdrop across the whole screen, its name, film count,
 * year span and synopsis at the top left, the focused film's details above a
 * single row of its films in collection order.
 */
@Composable
internal fun TvCollectionShowcase(
    title: String,
    state: TvLibraryCollectionDetailViewModel.UiState,
    onItemClick: (contentId: String) -> Unit,
    onLoadMore: () -> Unit,
    firstItemFocusRequester: FocusRequester,
    firstItemCardModifier: Modifier,
    controls: @Composable () -> Unit,
) {
    val items = remember(state.items) { state.items.distinctBy { it.contentId } }
    var focused by remember { mutableStateOf<BrowseItem?>(null) }
    val shownFilm = focused?.takeIf { f -> items.any { it.contentId == f.contentId } } ?: items.firstOrNull()

    val info = state.info
    val backdropUrl = info?.backdropUrl?.takeIf { it.isNotBlank() }
        ?: items.firstNotNullOfOrNull { it.backdropUrl?.takeIf { url -> url.isNotBlank() } }
    val backdropThumbhash = if (info?.backdropUrl.isNullOrBlank()) {
        items.firstOrNull { !it.backdropUrl.isNullOrBlank() }?.backdropThumbhash
    } else {
        info?.backdropThumbhash
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (backdropUrl != null) {
            ThumbhashImage(
                url = backdropUrl,
                thumbhash = backdropThumbhash,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                crossfadeMillis = 400,
                modifier = Modifier.fillMaxSize(),
            )
        }
        ShowcaseScrim()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 36.dp, bottom = 22.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = TvSkyline.safeAreaX)
                    .widthIn(max = 560.dp),
            ) {
                Text(
                    text = "COLLECTION",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    style = TextStyle(shadow = ShowcaseShadow),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = (info?.name ?: title).removeSuffix(" Collection").ifBlank { title },
                    color = Color.White,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = ShowcaseShadow),
                )
                collectionFacts(items, complete = !state.isLoading && !state.hasMore)?.let { facts ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = facts,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        style = TextStyle(shadow = ShowcaseShadow),
                    )
                }
                info?.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = description,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = ShowcaseShadow),
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
            Box(Modifier.padding(horizontal = TvSkyline.safeAreaX)) { controls() }

            Spacer(Modifier.weight(1f))

            AnimatedContent(
                targetState = shownFilm,
                contentKey = { it?.contentId },
                transitionSpec = { fadeIn(tween(260, delayMillis = 80)) togetherWith fadeOut(tween(160)) },
                label = "tvCollectionShowcaseFilm",
                modifier = Modifier
                    .padding(horizontal = TvSkyline.safeAreaX)
                    .fillMaxWidth(),
            ) { film ->
                if (film != null) FilmDetails(film, position = items.indexOfFirst { it.contentId == film.contentId } + 1)
            }
            Spacer(Modifier.height(10.dp))

            FilmRow(
                items = items,
                hasMore = state.hasMore,
                onItemClick = onItemClick,
                onLoadMore = onLoadMore,
                onFocused = { focused = it },
                firstItemFocusRequester = firstItemFocusRequester,
                firstItemCardModifier = firstItemCardModifier,
            )
        }
    }
}

/** "4 films · 1999 – 2021 · 2 watched", once every film is known. */
private fun collectionFacts(items: List<BrowseItem>, complete: Boolean): String? {
    if (items.isEmpty()) return null
    val parts = mutableListOf<String>()
    val movies = items.all { it.type.equals("movie", ignoreCase = true) }
    val count = items.size
    parts += if (complete) {
        if (movies) "$count ${if (count == 1) "film" else "films"}" else "$count ${if (count == 1) "title" else "titles"}"
    } else {
        "$count+ titles"
    }
    val years = items.map { it.year }.filter { it > 0 }
    if (years.isNotEmpty()) {
        val first = years.min()
        val last = years.max()
        parts += if (first == last) "$first" else "$first – $last"
    }
    val runtime = items.sumOf { it.runtime ?: 0 }
    if (complete && movies && runtime > 0) parts += formatRuntime(runtime) + " in all"
    val watched = items.count { it.userState?.played == true }
    if (watched > 0) parts += if (watched == count && complete) "All watched" else "$watched watched"
    return parts.joinToString("  ·  ")
}

private fun formatRuntime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

/** The focused film: its place in the collection, title, facts and synopsis. */
@Composable
private fun FilmDetails(film: BrowseItem, position: Int) {
    Column(Modifier.widthIn(max = 640.dp)) {
        Text(
            text = if (position > 0) "$position  ·  ${film.title}" else film.title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = ShowcaseShadow),
        )
        val facts = buildList {
            film.year.takeIf { it > 0 }?.let { add(it.toString()) }
            film.runtime?.takeIf { it > 0 }?.let { add(formatRuntime(it)) }
            (film.ratingImdb ?: film.ratingTmdb)?.takeIf { it > 0 }?.let { add("★ " + "%.1f".format(it)) }
            film.genres.take(2).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
            if (film.userState?.played == true) add("Watched")
        }
        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = facts.joinToString("  ·  "),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                maxLines = 1,
                style = TextStyle(shadow = ShowcaseShadow),
            )
        }
        film.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Spacer(Modifier.height(5.dp))
            Text(
                text = overview,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = ShowcaseShadow),
            )
        }
    }
}

@Composable
private fun FilmRow(
    items: List<BrowseItem>,
    hasMore: Boolean,
    onItemClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onFocused: (BrowseItem) -> Unit,
    firstItemFocusRequester: FocusRequester,
    firstItemCardModifier: Modifier,
) {
    val listState = rememberLazyListState()
    var focusedIndex by remember { mutableStateOf(0) }
    LaunchedEffect(focusedIndex, items.size, hasMore) {
        if (hasMore && focusedIndex >= items.size - 6) onLoadMore()
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = TvSkyline.safeAreaX, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(items, key = { _, item -> item.contentId }, contentType = { _, item -> item.type }) { index, item ->
            val (actions, userState, positionSeconds) = rememberTvBrowseItemCardActions(item)
            val spoilerPrefs = LocalEpisodeSpoilerPrefs.current
            TvMediaCard(
                hideArtwork = EpisodeSpoilers.hidesBrowseArtwork(item, spoilerPrefs, userState.played, positionSeconds = positionSeconds),
                title = item.title,
                posterUrl = item.posterUrl,
                posterThumbhash = item.posterThumbhash,
                year = item.year.takeIf { it > 0 },
                userState = userState,
                mediaType = item.type,
                onClick = { onItemClick(item.contentId) },
                width = ShowcasePosterWidth,
                focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                cardModifier = if (index == 0) firstItemCardModifier else Modifier,
                modifier = Modifier.onFocusChanged {
                    if (it.hasFocus) {
                        focusedIndex = index
                        onFocused(item)
                    }
                },
                overlay = OverlayDataExtractor.fromBrowseItem(item),
                actions = actions,
            )
        }
    }
}

/** Plain black over the backdrop: heavier on the left for the copy and along the bottom for the row. */
@Composable
private fun ShowcaseScrim() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.horizontalGradient(
                0.00f to Color.Black.copy(alpha = 0.78f),
                0.40f to Color.Black.copy(alpha = 0.45f),
                0.75f to Color.Black.copy(alpha = 0.10f),
                1.00f to Color.Transparent,
            ),
        )
        drawRect(
            Brush.verticalGradient(
                0.00f to Color.Transparent,
                0.45f to Color.Transparent,
                1.00f to Color.Black.copy(alpha = 0.80f),
            ),
        )
    }
}

private val ShowcasePosterWidth = 104.dp

private val ShowcaseShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 10f)
