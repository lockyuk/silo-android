package org.siloserver.silo.tv.ui.screens.boxset

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.siloserver.silo.model.catalog.yearsLabel
import org.siloserver.silo.tv.ui.components.vantage.ChipTone
import org.siloserver.silo.tv.ui.components.vantage.VBar
import org.siloserver.silo.tv.ui.components.vantage.VButton
import org.siloserver.silo.tv.ui.components.vantage.VChip
import org.siloserver.silo.tv.ui.components.vantage.VDot
import org.siloserver.silo.tv.ui.components.vantage.VArt
import org.siloserver.silo.tv.ui.components.vantage.VLabel
import org.siloserver.silo.tv.ui.components.vantage.VPanel
import org.siloserver.silo.tv.ui.components.vantage.VRing
import org.siloserver.silo.tv.ui.components.vantage.VSection
import org.siloserver.silo.tv.ui.components.vantage.VTag
import org.siloserver.silo.tv.ui.theme.animatedAccent
import org.siloserver.silo.tv.ui.components.vantage.mono
import org.siloserver.silo.tv.ui.components.vantage.mx
import org.siloserver.silo.tv.ui.components.vantage.ui
import org.siloserver.silo.tv.ui.theme.Level
import org.siloserver.silo.tv.ui.theme.VantageCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.siloserver.silo.common.ui.OnViewerAccessChanged
import org.siloserver.silo.domain.MediaActionsCoordinator
import org.siloserver.silo.model.catalog.BrowseItem
import org.siloserver.silo.model.catalog.CatalogBoxSetDetail
import org.siloserver.silo.model.catalog.boxSetNextMovie
import org.siloserver.silo.model.catalog.boxSetResumePosition
import org.siloserver.silo.model.catalog.boxSetFilmCount
import org.siloserver.silo.model.catalog.watchedFraction
import org.siloserver.silo.model.catalog.heroFacts
import org.siloserver.silo.model.catalog.playLabel
import org.siloserver.silo.model.section.SectionItem
import org.siloserver.silo.network.AccessChangeSignals
import org.siloserver.silo.tv.ui.components.TvCatalogEmptyState
import org.siloserver.silo.tv.ui.components.TvErrorScreen
import org.siloserver.silo.tv.ui.components.TvHeroFactToken
import org.siloserver.silo.tv.ui.components.TvLoadingScreen
import org.siloserver.silo.tv.ui.components.TvMediaRow
import org.siloserver.silo.tv.ui.components.TvPrimaryPillButton
import org.siloserver.silo.tv.ui.components.rememberAmbientBackdropTintState
import org.siloserver.silo.tv.ui.components.tvCardActions
import org.siloserver.silo.tv.ui.components.withMembershipChanges
import org.siloserver.silo.tv.ui.focus.TvContentInitialFocusMaxAttempts
import org.siloserver.silo.tv.ui.focus.requestFocusUntilObserved
import org.siloserver.silo.tv.ui.screens.detail.TvDetailHero
import org.siloserver.silo.tv.ui.screens.watchparty.TvWatchPartyPlayGuardDialog
import org.siloserver.silo.tv.ui.screens.watchparty.rememberTvWatchPartyPlayGuard
import org.siloserver.silo.tv.ui.screens.detail.TvDetailHorizontalInset
import org.siloserver.silo.tv.ui.screens.detail.TvDetailPageBottomPadding
import org.siloserver.silo.tv.ui.screens.detail.TvDetailSectionHeader
import org.siloserver.silo.tv.ui.screens.detail.tvDetailPageSurfaceColor
import org.siloserver.silo.viewmodel.BoxSetViewModel

/**
 * One movie box set on TV: the movie detail hero (backdrop, title, facts,
 * synopsis) with Play on the next movie to watch, then the box set's movies
 * in release order on one row. Focus starts on Play; Down reaches the row
 * and Up returns to Play. Back from a movie lands on the card it opened.
 */
@Composable
fun TvBoxSetScreen(
    boxSetId: String,
    libraryId: String,
    onBack: () -> Unit,
    onPlayMovie: (contentId: String, resumePositionSeconds: Double?) -> Unit,
    onOpenMovie: (contentId: String) -> Unit,
    viewModel: BoxSetViewModel = koinViewModel(
        key = "box-set-$libraryId-$boxSetId",
        parameters = { parametersOf(boxSetId, libraryId) },
    ),
) {
    BackHandler(enabled = true) { onBack() }
    // D5: while this TV is in a Watch Party, Play first asks to leave the
    // party; leaving is never hidden behind a solo start.
    val watchPartyPlayGuard = rememberTvWatchPartyPlayGuard()
    val guardedPlayMovie: (String, Double?) -> Unit = { contentId, resumePositionSeconds ->
        watchPartyPlayGuard.requestPlay { onPlayMovie(contentId, resumePositionSeconds) }
    }
    TvWatchPartyPlayGuardDialog(watchPartyPlayGuard)
    // A box set the viewer may no longer see shows as unavailable, and one
    // that comes back loads again, so this listens whatever the page shows.
    OnViewerAccessChanged(koinInject<AccessChangeSignals>(), viewModel.accessChanges) { viewModel.refreshAfterAccessChange() }
    val state by viewModel.uiState.collectAsState()
    val detail = state.detail
    when {
        detail != null -> BoxSetContent(detail, viewModel, guardedPlayMovie, onOpenMovie)
        state.unavailable -> TvCatalogEmptyState(
            message = "This box set isn't available. Box sets may be turned off for this library, " +
                "or too few of its movies are here.",
            modifier = Modifier.fillMaxSize(),
        )
        state.error != null -> TvErrorScreen(message = state.error.orEmpty(), onRetry = viewModel::retry)
        else -> TvLoadingScreen()
    }
}

@Composable
private fun BoxSetContent(
    detail: CatalogBoxSetDetail,
    viewModel: BoxSetViewModel,
    onPlayMovie: (contentId: String, resumePositionSeconds: Double?) -> Unit,
    onOpenMovie: (contentId: String) -> Unit,
) {
    val boxSet = detail.boxSet
    // Story order is offered only for box sets with a hand-kept list (see BoxSetStoryOrder).
    val storyItems = remember(detail.items, boxSet.id) { BoxSetStoryOrder.apply(boxSet.id, detail.items) }
    var storyOrder by rememberSaveable(boxSet.id) { mutableStateOf(false) }
    val showStory = storyOrder && storyItems != null
    val items = if (showStory) storyItems!! else detail.items
    val next = boxSetNextMovie(items)
    // Each movie's card menu marks it watched or adds it to a list, as on the
    // library grid; the view model moves the hero's count and play target.
    val coordinator: MediaActionsCoordinator = koinInject()
    val context = LocalContext.current.applicationContext
    val membershipActions by coordinator.memberships.actions.collectAsState()
    val movies = remember(items, membershipActions) {
        items.map { movie ->
            movie.toSectionItem().copy(
                userState = coordinator.withMembershipChanges(movie.contentId, movie.userState, membershipActions),
            )
        }
    }
    val cardActions = remember(coordinator, context, viewModel) {
        { movie: SectionItem ->
            coordinator.tvCardActions(
                contentId = movie.contentId,
                context = context,
                // The view model follows the write, so it runs as long as the view model does.
                scope = viewModel.viewModelScope,
                onWatchedChanged = { watched -> viewModel.onMovieWatchedChanged(movie.contentId, watched) },
                onWatchedWriteFailed = { viewModel.onMovieWatchedWriteFailed(movie.contentId) },
                onWatchedWriteSucceeded = { viewModel.onMovieWatchedWriteSucceeded(movie.contentId) },
            )
        }
    }
    val playFocus = remember { FocusRequester() }
    val movieReturnFocus = remember { FocusRequester() }
    val firstMovieFocus = remember { FocusRequester() }
    // The card a movie page was opened from, so Back lands on it again.
    var returnIndex by rememberSaveable { mutableIntStateOf(-1) }
    var restoreRequest by remember { mutableIntStateOf(0) }
    var playFocusRequest by remember { mutableIntStateOf(0) }
    var screenHasFocus by remember { mutableStateOf(false) }
    var playFocused by remember { mutableStateOf(false) }
    var focusedMovieIndex by remember { mutableIntStateOf(-1) }

    LifecycleResumeEffect(viewModel) {
        // Coming back from a movie re-reads watched state and the play target.
        viewModel.onScreenResumed()
        // Back from a movie's page lands on its card.
        if (returnIndex >= 0) restoreRequest += 1 else playFocusRequest += 1
        onPauseOrDispose {}
    }
    // Opening the box set, or coming back to it from the player, lands on
    // Play. Back from a movie's page lands on that movie's card instead.
    LaunchedEffect(Unit) {
        if (returnIndex >= 0 || next == null) return@LaunchedEffect
        requestFocusUntilObserved(
            maxAttempts = TvContentInitialFocusMaxAttempts,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = playFocus::requestFocus,
            isFocused = { playFocused },
        )
    }
    LaunchedEffect(playFocusRequest) {
        // A later resume puts focus on Play only if the page lost it; one that
        // finds focus on the page (back from the background, say) leaves it
        // where the viewer put it.
        if (playFocusRequest <= 1 || returnIndex >= 0 || next == null || screenHasFocus) return@LaunchedEffect
        requestFocusUntilObserved(
            maxAttempts = TvContentInitialFocusMaxAttempts,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = playFocus::requestFocus,
            isFocused = { playFocused },
        )
    }
    LaunchedEffect(restoreRequest) {
        if (restoreRequest == 0 || returnIndex < 0) return@LaunchedEffect
        val target = returnIndex
        // Retries while the row composes the target card.
        requestFocusUntilObserved(
            maxAttempts = TvContentInitialFocusMaxAttempts,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = movieReturnFocus::requestFocus,
            isFocused = { focusedMovieIndex == target },
        )
        returnIndex = -1
    }

    val tint = rememberAmbientBackdropTintState()
    LaunchedEffect(boxSet.backdropUrl) { tint.set(item = null, url = boxSet.backdropUrl) }
    val listState = rememberLazyListState()

    // The clear logo belongs to the movies, not the collection: use the one for the movie Play starts.
    val catalogApi: org.siloserver.silo.network.api.CatalogApi = koinInject()
    val logoSource = (next ?: items.firstOrNull())?.contentId
    var logoUrl by remember(logoSource) { mutableStateOf<String?>(null) }
    LaunchedEffect(logoSource) {
        val id = logoSource ?: return@LaunchedEffect
        val r = runCatching { catalogApi.getItemDetail(id) }.getOrNull()
        if (r is org.siloserver.silo.network.ApiResult.Success) logoUrl = r.data.logoUrl
    }
    val facts = remember(detail.items) { BoxSetFacts.of(detail.items) }
    val firstCardFocus = remember(movies.size) { List(movies.size) { FocusRequester() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { screenHasFocus = it.hasFocus }
            .background(Level.Bg),
    ) {
        org.siloserver.silo.tv.ui.screens.detail.TvVantagePageBackdrop(boxSet.backdropUrl, boxSet.backdropThumbhash, listState)

        val grid = org.siloserver.silo.tv.ui.theme.TvGrid
        // Kicker, logo, facts, synopsis and Play sit on the shared title-page grid.
        Row(
            Modifier.offset(x = grid.edge, y = grid.kickerTop).width(grid.leftW).height(grid.kickerH),
            horizontalArrangement = Arrangement.spacedBy(8.mx), verticalAlignment = Alignment.CenterVertically,
        ) {
            VChip("COLLECTION")
            VChip(boxSetFilmCount(boxSet.movieCount).uppercase().replace("FILMS", "MOVIES").replace("FILM", "MOVIE"))
            boxSet.yearsLabel()?.let { Spacer(Modifier.width(6.mx)); VTag(it) }
        }
        Box(Modifier.offset(x = grid.edge, y = grid.logoTop).size(grid.logoW, grid.logoH), contentAlignment = Alignment.CenterStart) {
            if (!logoUrl.isNullOrBlank()) {
                coil3.compose.AsyncImage(
                    model = logoUrl,
                    contentDescription = boxSet.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(boxSet.title.uppercase(), style = ui(58, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            Modifier.offset(x = grid.edge, y = grid.factsTop).width(grid.leftW).height(grid.factsH),
            horizontalArrangement = Arrangement.spacedBy(8.mx), verticalAlignment = Alignment.CenterVertically,
        ) {
            facts.contentRating?.let { VChip(it, tone = ChipTone.Solid) }
            facts.genres.forEach { VChip(it.uppercase()) }
            facts.totalRuntime?.let { VChip(it) }
            facts.avgImdb?.let { Text("IMDb %.1f".format(it), style = mono(13), modifier = Modifier.padding(start = 6.mx)) }
        }
        Box(Modifier.offset(x = grid.edge, y = grid.synopsisTop).width(grid.synopsisW).height(grid.synopsisH).clipToBounds()) {
            boxSet.overview?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = ui(16, FontWeight.Normal, Level.Dim), maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            Modifier.offset(x = grid.edge, y = grid.actionTop).height(grid.actionH),
            horizontalArrangement = Arrangement.spacedBy(12.mx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val downToRow = Modifier.focusProperties { down = firstCardFocus.getOrElse(if (returnIndex >= 0) returnIndex else 0) { firstMovieFocus } }
            if (next != null) {
                VButton(
                    text = boxSet.playLabel(next),
                    primary = true,
                    height = 40.mx,
                    textPx = 15,
                    small = next.boxSetResumePosition()?.let { clockLabel(it) },
                    leading = { c -> androidx.compose.material3.Icon(Icons.Filled.PlayArrow, null, tint = c, modifier = Modifier.size(18.dp)) },
                    onClick = { onPlayMovie(next.contentId, next.boxSetResumePosition()) },
                    focusRequester = playFocus,
                    modifier = Modifier
                        .onFocusChanged { playFocused = it.hasFocus }
                        .then(downToRow),
                )
            }
            if (storyItems != null) {
                org.siloserver.silo.tv.ui.components.vantage.VTabChip("Release order", selected = !storyOrder, onFocus = { storyOrder = false }, modifier = downToRow)
                org.siloserver.silo.tv.ui.components.vantage.VTabChip("Story order", selected = storyOrder, onFocus = { storyOrder = true }, modifier = downToRow)
            }
        }

        // Right: progress card.
        VPanel(
            Modifier.align(Alignment.TopEnd).padding(top = grid.rightTop, end = grid.edge).width(grid.rightW).height(grid.progressH),
            gap = 10.mx,
        ) {
            VLabel("Your progress")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mx)) {
                org.siloserver.silo.tv.ui.screens.detail.TvGridRing((facts.fraction(detail.items) * 100).toInt(), "${boxSet.playedCount}/${boxSet.movieCount}")
                Text(
                    "${facts.watched} watched\n${facts.inProgress} in progress\n${facts.toGo} to go",
                    style = mono(12, color = Level.Dim), lineHeight = androidx.compose.ui.unit.TextUnit(19f, androidx.compose.ui.unit.TextUnitType.Sp),
                )
            }
            VBar(facts.fraction(detail.items))
            facts.timeLeft?.let { Text("About $it left", style = mono(11, color = Level.Faint)) }
        }

        // Bottom: the movies, in release order.
        Column(
            modifier = Modifier.offset(x = grid.edge - 12.mx, y = grid.bodyTop - 8.mx).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(grid.rowTop - grid.bodyTop - 16.dp),
        ) {
            org.siloserver.silo.tv.ui.screens.detail.TvDetailSectionHeader(
                title = if (showStory) "In story order" else "In release order",
                note = if (showStory) "the order the story happens" else "the order they came out",
                modifier = Modifier.padding(start = 12.mx),
            )
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(start = 12.mx, end = 44.mx, top = 8.mx, bottom = 40.mx),
                horizontalArrangement = Arrangement.spacedBy(grid.cardGap),
                modifier = Modifier.focusProperties { up = playFocus },
            ) {
                itemsIndexed(items, key = { _, m -> m.contentId }) { index, movie ->
                    BoxSetCard(
                        movie = movie,
                        isNext = movie.contentId == next?.contentId,
                        ordinal = if (showStory) index + 1 else null,
                        focusRequester = firstCardFocus[index],
                        onFocused = { focusedMovieIndex = index },
                        onClick = {
                            returnIndex = index
                            onOpenMovie(movie.contentId)
                        },
                        onToggleWatched = {
                            val watched = movie.userState?.played != true
                            movies.getOrNull(index)?.let { cardActions(it).onSetWatched?.invoke(!(movie.userState?.played == true)) }
                            Unit
                        },
                        modifier = if (index == 0) Modifier.focusRequester(firstMovieFocus) else Modifier,
                    )
                }
            }
        }
    }
}

/** Totals the page shows, from the box set's movies. */
private class BoxSetFacts(
    val contentRating: String?,
    val genres: List<String>,
    val totalRuntime: String?,
    val avgImdb: Double?,
    val avgRt: Int?,
    val watched: Int,
    val inProgress: Int,
    val toGo: Int,
    val timeLeft: String?,
) {
    fun fraction(items: List<BrowseItem>): Float {
        if (items.isEmpty()) return 0f
        val sum = items.sumOf { m ->
            when {
                m.userState?.played == true -> 1.0
                (m.positionSeconds ?: 0.0) > 0 && (m.durationSeconds ?: 0.0) > 0 -> (m.positionSeconds!! / m.durationSeconds!!).coerceIn(0.0, 1.0)
                else -> 0.0
            }
        }
        return (sum / items.size).toFloat()
    }

    companion object {
        fun of(items: List<BrowseItem>): BoxSetFacts {
            fun hm(min: Int) = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"
            val played = { m: BrowseItem -> m.userState?.played == true }
            val inProg = { m: BrowseItem -> !played(m) && (m.positionSeconds ?: 0.0) > 0 }
            val total = items.sumOf { it.runtime ?: 0 }
            var leftMin = 0.0
            items.forEach { m ->
                val rt = (m.runtime ?: 0).toDouble()
                leftMin += when {
                    played(m) -> 0.0
                    inProg(m) -> (rt - (m.positionSeconds ?: 0.0) / 60.0).coerceAtLeast(0.0)
                    else -> rt
                }
            }
            val imdb = items.mapNotNull { it.ratingImdb }.takeIf { it.isNotEmpty() }?.average()
            val rt = items.mapNotNull { it.ratingRtCritic }.takeIf { it.isNotEmpty() }?.average()?.toInt()
            val genres = items.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(2).map { it.key }
            return BoxSetFacts(
                contentRating = items.mapNotNull { it.contentRating?.takeIf { r -> r.isNotBlank() } }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
                genres = genres,
                totalRuntime = total.takeIf { it > 0 }?.let(::hm),
                avgImdb = imdb,
                avgRt = rt,
                watched = items.count(played),
                inProgress = items.count(inProg),
                toGo = items.count { !played(it) && !inProg(it) },
                timeLeft = leftMin.takeIf { it >= 1 }?.let { hm(it.toInt()) },
            )
        }
    }
}

private fun clockLabel(sec: Double): String {
    val t = sec.toLong(); val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** One movie: backdrop art with a watched tick or progress bar, then year, length and title beneath. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BoxSetCard(
    movie: BrowseItem,
    isNext: Boolean,
    ordinal: Int? = null,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onToggleWatched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    var menu by remember { mutableStateOf(false) }
    val played = movie.userState?.played == true
    val pos = movie.positionSeconds ?: 0.0
    val dur = movie.durationSeconds ?: ((movie.runtime ?: 0) * 60.0)
    val progress = if (!played && pos > 0 && dur > 0) (pos / dur).toFloat() else null
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "boxCard")
    val accent = animatedAccent()
    Column(modifier.width(176.mx), verticalArrangement = Arrangement.spacedBy(10.mx)) {
        Box(
            Modifier
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .scale(scale)
                .then(if (focused) Modifier.border(3.dp, Level.Fg, VantageCard) else Modifier)
                .combinedClickable(interactionSource = source, indication = null, onClick = onClick, onLongClick = { menu = true }),
        ) {
            VArt(movie.backdropUrl ?: movie.posterUrl, 176.mx, 99.mx, progress = progress)
            if (played) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(9.mx).size(22.mx).background(Level.Good, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("\u2713", style = ui(13, FontWeight.Bold, Color(0xFF06130D))) }
            }
            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(if (played) "Mark unwatched" else "Mark watched") },
                    onClick = { menu = false; onToggleWatched() },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.mx)) {
            val dotColor = when { played -> Level.Good; progress != null -> accent; else -> Color.White.copy(alpha = 0.3f) }
            VDot(dotColor, 7.mx)
            Text(ordinal?.toString() ?: if (movie.year > 0) "${movie.year}" else "", style = mono(13, FontWeight.SemiBold), maxLines = 1)
            movie.runtime?.takeIf { it > 0 }?.let { Text(if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m", style = mono(11, color = Level.Faint), maxLines = 1) }
        }
        Text(
            movie.title, style = ui(13, FontWeight.SemiBold, if (isNext || focused) Level.Fg else Level.Dim),
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.defaultMinSize(minHeight = 34.mx),
        )
    }
}

private fun BrowseItem.toSectionItem(): SectionItem = SectionItem(
    contentId = contentId,
    type = type,
    title = title,
    year = year,
    genres = genres,
    status = status,
    ratingImdb = ratingImdb,
    ratingTmdb = ratingTmdb,
    contentRating = contentRating,
    overlaySummary = overlaySummary,
    overview = overview,
    posterUrl = posterUrl,
    posterThumbhash = posterThumbhash,
    backdropUrl = backdropUrl,
    backdropThumbhash = backdropThumbhash,
    userState = userState,
    positionSeconds = positionSeconds,
    durationSeconds = durationSeconds,
)
