package org.siloserver.silo.tv.ui.screens.requests

import androidx.compose.foundation.gestures.animateScrollBy
import org.siloserver.silo.common.requests.rememberRequestRouter
import org.siloserver.silo.model.request.RequestMediaResult
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.siloserver.silo.common.requests.RequestRowCopy
import org.siloserver.silo.common.requests.RequestStageTrack
import org.siloserver.silo.model.catalog.ExternalRatings
import org.siloserver.silo.tv.ui.screens.detail.TvCastCrewSection
import org.siloserver.silo.model.request.RequestDisplayState
import org.siloserver.silo.model.request.RequestMediaDetail
import org.siloserver.silo.model.request.RequestMediaType
import org.siloserver.silo.model.request.RequestProgress
import org.siloserver.silo.model.request.RequestStep
import org.siloserver.silo.model.request.RequestTargetSummary
import org.siloserver.silo.model.request.requestBackdropUrl
import org.siloserver.silo.model.request.requestPosterUrl
import org.siloserver.silo.repository.RequestsRepository
import org.siloserver.silo.tv.ui.components.PillKind
import org.siloserver.silo.tv.ui.components.SquaredPillSurface
import org.siloserver.silo.tv.ui.components.TvErrorScreen
import org.siloserver.silo.tv.ui.components.TvHeroFactToken
import org.siloserver.silo.tv.ui.components.TvLoadingScreen
import org.siloserver.silo.tv.ui.components.rememberAmbientBackdropTintState
import org.siloserver.silo.tv.ui.focus.TvContentInitialFocusMaxAttempts
import org.siloserver.silo.tv.ui.focus.TvObservedFocusResult
import org.siloserver.silo.tv.ui.focus.requestFocusUntilObserved
import org.siloserver.silo.tv.ui.screens.detail.TvDetailHero
import org.siloserver.silo.tv.ui.screens.detail.TvDetailHorizontalInset
import org.siloserver.silo.tv.ui.screens.detail.TvDetailSectionHeader
import org.siloserver.silo.tv.ui.screens.detail.tvDetailPageSurfaceColor
import org.siloserver.silo.tv.ui.theme.SiloOnSurface
import org.siloserver.silo.viewmodel.RequestDetailUiState
import org.siloserver.silo.viewmodel.RequestDetailViewModel
import org.siloserver.silo.viewmodel.RequestPrimaryAction

/**
 * TV request detail on the same page surface and hero as a library title
 * (`TvDetailHero`), like tvOS `RequestDetailView`: a STATUS / REQUESTED /
 * QUALITY summary with a labeled stage track, one primary action that morphs
 * in place, Cancel, and a "More like this" row.
 *
 * Focus: the primary action is one stable node whose label and look follow
 * [RequestPrimaryAction], so focus stays put across request → requesting →
 * pending. Notify me is likewise one node whose label flips. A secondary pill
 * that leaves the row while focused hands focus to the primary action
 * ([RequestActionFocusFallback]).
 */
@Composable
fun TvRequestDetailScreen(
    mediaType: String,
    tmdbId: Int,
    onBack: () -> Unit,
    onOpenLibraryItem: (contentId: String) -> Unit = {},
    onOpenRequestDetail: (mediaType: String, tmdbId: Int) -> Unit = { _, _ -> },
    /** Opens a person page by TMDB id, for cast who are not in the library. */
    onOpenPersonByTmdb: (tmdbId: String) -> Unit = {},
    /**
     * False while the shell has an overlay (a cascade panel or the profile
     * menu) that Back should close first. This handler registers after the
     * shell's, so on Android 16 it would otherwise take that press and pop
     * the page from under the open overlay.
     */
    backEnabled: Boolean = true,
    onInitialContentFocus: () -> Unit = {},
    viewModel: RequestDetailViewModel = koinViewModel { parametersOf(mediaType, tmdbId) },
    repository: RequestsRepository = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val router = rememberRequestRouter(repository, onOpenLibraryItem, onOpenRequestDetail)
    val loadingFocusRequester = remember { FocusRequester() }
    val primaryActionFocusRequester = remember { FocusRequester() }
    var pageHasFocus by remember { mutableStateOf(false) }
    var pageHadFocus by remember { mutableStateOf(false) }
    var primaryHasFocus by remember { mutableStateOf(false) }
    val showsLoading = state.detail == null && state.error == null
    // The title, not the loaded detail: refreshes after an action replace the
    // detail and must not re-run the claim below.
    val detailKey = state.detail?.let { "${it.mediaType}:${it.tmdbId}" }
    val detailArrived by rememberUpdatedState(detailKey != null)

    // Nothing here is focusable until the detail loads, and the row that opened
    // the page is about to be disposed. Hold focus on the loading indicator so
    // it stays in the page.
    LaunchedEffect(showsLoading) {
        if (!showsLoading) return@LaunchedEffect
        requestFocusUntilObserved(
            maxAttempts = TvContentInitialFocusMaxAttempts,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = loadingFocusRequester::requestFocus,
            isFocused = { pageHasFocus || detailArrived },
        )
    }

    // Hand focus to the primary action when the detail arrives, but only if
    // focus is still in the page (or never got here). Observed on the action
    // itself: the synopsis above it is focusable too, and focus entering the
    // page first lands there.
    val synopsisPillFocusRequester = remember { FocusRequester() }
    var synopsisPillPresent by remember { mutableStateOf(false) }
    val claimOnArrival = remember(detailKey) { pageHasFocus || !pageHadFocus }
    LaunchedEffect(detailKey) {
        if (detailKey == null || !claimOnArrival) return@LaunchedEffect
        val result = requestFocusUntilObserved(
            maxAttempts = TvContentInitialFocusMaxAttempts,
            awaitAttempt = { withFrameNanos { } },
            // Start at the top of the page: on the synopsis "Read more" pill when there is one.
            requestFocus = {
                if (synopsisPillPresent) synopsisPillFocusRequester.requestFocus() else primaryActionFocusRequester.requestFocus()
            },
            isFocused = { primaryHasFocus || (synopsisPillPresent && pageHasFocus) },
        )
        if (result == TvObservedFocusResult.Focused) onInitialContentFocus()
    }

    // A focused pill that leaves the row (Cancel once cancelled, Notify me once
    // the title is requestable or in the library) hands its focus to the
    // synopsis; put it back on the primary action, which never leaves.
    val actionFocusFallback = remember { RequestActionFocusFallback() }
    val actionFocusChecks = actionFocusFallback.checks
    LaunchedEffect(actionFocusChecks) {
        if (actionFocusChecks == 0) return@LaunchedEffect
        withFrameNanos { }
        if (!actionFocusFallback.takeShouldReclaim()) return@LaunchedEffect
        requestFocusUntilObserved(
            maxAttempts = TvContentInitialFocusMaxAttempts,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = primaryActionFocusRequester::requestFocus,
            isFocused = { primaryHasFocus },
        )
    }

    BackHandler(enabled = backEnabled) { onBack() }

    val backdropUrl = state.detail?.let { requestBackdropUrl(it.backdropPath) ?: requestPosterUrl(it.posterPath) }
    val pageTint = rememberAmbientBackdropTintState()
    LaunchedEffect(backdropUrl) { pageTint.set(item = null, url = backdropUrl) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged {
                pageHasFocus = it.hasFocus
                if (it.hasFocus) pageHadFocus = true
            }
            .background(org.siloserver.silo.tv.ui.theme.Level.Bg),
    ) {
        val detail = state.detail
        when {
            detail != null -> RequestDetailContent(
                detail = detail,
                state = state,
                backdropUrl = backdropUrl,
                primaryActionFocusRequester = primaryActionFocusRequester,
                synopsisPillFocusRequester = synopsisPillFocusRequester,
                onSynopsisPillPresent = { synopsisPillPresent = it },
                onPrimaryFocusChanged = { primaryHasFocus = it },
                onPrimary = {
                    when (val action = state.primaryAction) {
                        RequestPrimaryAction.Request -> viewModel.submitRequest()
                        is RequestPrimaryAction.OpenInLibrary -> onOpenLibraryItem(action.contentId)
                        else -> Unit
                    }
                },
                onCancel = viewModel::cancel,
                onToggleFollow = viewModel::toggleFollow,
                actionFocusFallback = actionFocusFallback,
                onOpenPersonByTmdb = onOpenPersonByTmdb,
            )
            state.error != null -> TvErrorScreen(message = state.error.orEmpty(), onRetry = viewModel::load)
            else -> RequestDetailLoading(focusRequester = loadingFocusRequester)
        }
    }
}

/**
 * The loading indicator plus a spinner-sized focus target over it, so focus
 * can wait inside the page.
 */
@Composable
private fun RequestDetailLoading(focusRequester: FocusRequester) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TvLoadingScreen()
        Box(
            modifier = Modifier
                .size(64.dp)
                .focusRequester(focusRequester)
                .focusable(),
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RequestDetailContent(
    detail: RequestMediaDetail,
    state: RequestDetailUiState,
    backdropUrl: String?,
    primaryActionFocusRequester: FocusRequester,
    synopsisPillFocusRequester: FocusRequester,
    onSynopsisPillPresent: (Boolean) -> Unit,
    onPrimaryFocusChanged: (Boolean) -> Unit,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
    onToggleFollow: () -> Unit,
    actionFocusFallback: RequestActionFocusFallback,
    onOpenPersonByTmdb: (tmdbId: String) -> Unit,
) {
    val castScope = androidx.compose.runtime.rememberCoroutineScope()
    // IMDb / Rotten Tomatoes / TMDB scores and the cast come from the Filmography plugin when it is installed.
    val pluginApi: org.siloserver.silo.network.apiv2.FilmographyPluginApi = org.koin.compose.koinInject()
    val tokenManager: org.siloserver.silo.network.TokenManager = org.koin.compose.koinInject()
    val extra by androidx.compose.runtime.produceState<org.siloserver.silo.model.catalog.PluginTitle?>(null, detail.tmdbId, detail.mediaType) {
        val owner = tokenManager.snapshotCurrentScope() ?: return@produceState
        value = (pluginApi.title(detail.mediaType == RequestMediaType.Series, detail.tmdbId, owner)
            as? org.siloserver.silo.network.ApiResult.Success)?.data
    }
    val ratings = extra?.ratings?.takeIf { it.isNotEmpty() } ?: listOfNotNull(ExternalRatings.tmdb(detail.voteAverage))
    val castMembers = remember(detail, extra) {
        extra?.cast?.takeIf { it.isNotEmpty() }?.mapIndexed { i, c ->
            org.siloserver.silo.model.catalog.CastMember(name = c.name, character = c.character, order = i, tmdbId = c.tmdbId, photoUrl = c.photoUrl)
        } ?: detail.cast.mapIndexed { i, c ->
            org.siloserver.silo.model.catalog.CastMember(
                name = c.name,
                character = c.character.takeIf { it.isNotBlank() },
                order = i,
                photoUrl = c.profilePath?.let { "https://image.tmdb.org/t/p/w185$it" },
            )
        }
    }
    val progress = state.progress?.takeIf { state.showsStatus }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Like the library title page: focus inside the hero never scrolls the page on its own (the
    // default pushed the hero's top out of view); the body rails keep the smooth spec.
    val heroHasFocus = remember { mutableStateOf(false) }
    // Opening a title (from a filmography, say) always starts at the top of the page.
    LaunchedEffect(detail.tmdbId, detail.mediaType) { listState.scrollToItem(0) }
    val bringIntoViewSpec = remember {
        object : androidx.compose.foundation.gestures.BringIntoViewSpec {
            override val scrollAnimationSpec = org.siloserver.silo.tv.ui.theme.TvSmoothBringIntoViewSpec.scrollAnimationSpec
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (heroHasFocus.value) 0f
                else org.siloserver.silo.tv.ui.theme.TvSmoothBringIntoViewSpec.calculateScrollDistance(offset, size, containerSize)
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides bringIntoViewSpec,
        org.siloserver.silo.tv.ui.screens.detail.LocalTvPageScrollBy provides { dy: Float -> castScope.launch { listState.animateScrollBy(dy) } },
    ) {
    Box(Modifier.fillMaxSize()) {
    // Vantage: the backdrop covers the whole page and stays put while the page scrolls over it,
    // softening once past the first screen (the same layers as the library title page).
    run {
        val scrolledPast by androidx.compose.runtime.remember {
            androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 }
        }
        val softAlpha by androidx.compose.animation.core.animateFloatAsState(
            if (scrolledPast) 1f else 0f, androidx.compose.animation.core.tween(450), label = "requestSoftBackdrop",
        )
        val bg = org.siloserver.silo.tv.ui.theme.Level.Bg
        org.siloserver.silo.common.ui.components.ThumbhashImage(
            url = backdropUrl,
            thumbhash = null,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0.00f to bg.copy(alpha = 0.88f),
                    0.45f to bg.copy(alpha = 0.45f),
                    0.75f to bg.copy(alpha = 0.08f),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f + 0.35f * softAlpha }.background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0.35f to androidx.compose.ui.graphics.Color.Transparent,
                    0.75f to bg.copy(alpha = 0.55f),
                    1.00f to bg.copy(alpha = 0.85f),
                ),
            ),
        )
    }
    val isSeriesTitle = detail.mediaType == RequestMediaType.Series
    // A film that is not out yet: days until its release date (null once released).
    val releaseInDays: Int? = if (!isSeriesTitle && org.siloserver.silo.tv.ui.screens.detail.isFutureAirDate(detail.releaseDate)) {
        detail.releaseDate?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?.let { java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), it).toInt().coerceAtLeast(0) }
    } else null
        val stripCells = buildList {
            if (isSeriesTitle && detail.creators.isNotEmpty()) add("Created by" to detail.creators.take(2).joinToString(", "))
            (detail.director.takeIf { it.isNotBlank() } ?: extra?.directors?.takeIf { it.isNotEmpty() }?.take(2)?.joinToString(", "))
                ?.let { add("Director" to it) }
            (detail.genres.ifEmpty { extra?.genres.orEmpty() }).filter { it.isNotBlank() }.take(2)
                .takeIf { it.isNotEmpty() }?.let { add("Genres" to it.joinToString(" · ")) }
            val budget = extra?.budget?.takeIf { it > 0 }
            val revenue = extra?.revenue?.takeIf { it > 0 }
            if (budget != null || revenue != null) {
                add("Budget / Revenue" to "${org.siloserver.silo.tv.ui.screens.detail.money(budget)} / ${org.siloserver.silo.tv.ui.screens.detail.money(revenue)}")
            }
            if (isSeriesTitle) {
                detail.numberOfSeasons?.takeIf { it > 0 }?.let { s ->
                    add("Seasons" to listOfNotNull("$s", detail.numberOfEpisodes?.takeIf { it > 0 }?.let { "$it episodes" }).joinToString(" · "))
                }
            } else {
                detail.runtime?.takeIf { it > 0 }?.let { add("Runtime" to if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m") }
            }
            (detail.networks.firstOrNull() ?: detail.productionCompanies.firstOrNull() ?: extra?.companies?.firstOrNull())
                ?.let { add((if (isSeriesTitle && detail.networks.isNotEmpty()) "Network" else "Studio") to it) }
        }.take(6)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 160.dp),
    ) {
        item(key = "hero", contentType = "detail-hero") {
            TvDetailHero(
                // Focus anywhere in the hero brings the whole hero (logo included) back into view.
                modifier = Modifier.onFocusChanged {
                    heroHasFocus.value = it.hasFocus
                    if (it.hasFocus) castScope.launch { listState.animateScrollToItem(0) }
                },
                title = detail.title,
                seriesTitle = null,
                logoUrl = extra?.logoUrl,
                // The page draws the backdrop behind everything, as the library title page does.
                backdropUrl = null,
                backdropThumbhash = null,
                sourceTokens = listOf(
                    "Not in library",
                    if (detail.mediaType == RequestMediaType.Series) "Series"
                    else if (releaseInDays != null) "Upcoming movie" else "Movie",
                ),
                ratingChip = detail.contentRating.takeIf { it.isNotBlank() },
                overview = detail.overview.takeIf { it.isNotBlank() },
                tagline = detail.tagline.takeIf { it.isNotBlank() },
                factsLine = detail.factTokens(ratings),
                directorText = detail.creditText(),
                synopsisPillFocusRequester = synopsisPillFocusRequester,
                onSynopsisPillPresent = onSynopsisPillPresent,
                sidePanel = {
                    val grid = org.siloserver.silo.tv.ui.theme.TvGrid
                    androidx.compose.foundation.layout.Column(
                        Modifier.width(grid.rightW),
                        verticalArrangement = Arrangement.spacedBy(12.dp * 0.75f),
                    ) {
                        org.siloserver.silo.tv.ui.components.vantage.VPanel(
                            Modifier.fillMaxWidth().height(grid.detailsH),
                            title = "Details", sub = "TMDB", gap = 6.dp,
                            padding = PaddingValues(horizontal = 13.5.dp, vertical = 10.5.dp),
                        ) {
                            stripCells.take(5).forEach { (k, v) ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(k, style = org.siloserver.silo.tv.ui.components.vantage.mono(12, color = org.siloserver.silo.tv.ui.theme.Level.Faint), maxLines = 1)
                                    androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                                    Text(v, style = org.siloserver.silo.tv.ui.components.vantage.mono(12), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        org.siloserver.silo.tv.ui.components.vantage.VPanel(
                            Modifier.fillMaxWidth().height(grid.progressH),
                            gap = 8.dp,
                            padding = PaddingValues(horizontal = 13.5.dp, vertical = 10.5.dp),
                        ) {
                            org.siloserver.silo.tv.ui.components.vantage.VLabel(if (releaseInDays != null) "In cinemas in" else "Request")
                            Text(
                                when {
                                    releaseInDays != null -> if (releaseInDays == 0) "Today" else "$releaseInDays ${if (releaseInDays == 1) "day" else "days"}"
                                    state.canCancel -> "Requested"
                                    else -> "Not requested yet"
                                },
                                style = org.siloserver.silo.tv.ui.components.vantage.ui(if (releaseInDays != null) 28 else 22, FontWeight.SemiBold),
                            )
                            Text(
                                if (state.canCancel) "It will be added to your library when it is available."
                                else "Press OK on Request and it is searched for as soon as it can be found.",
                                style = org.siloserver.silo.tv.ui.components.vantage.mono(12, color = org.siloserver.silo.tv.ui.theme.Level.Dim),
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.onFocusChanged { actionFocusFallback.onRowFocusChanged(it.hasFocus) },
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RequestPrimaryActionPill(
                            state = state,
                            focusRequester = primaryActionFocusRequester,
                            onClick = onPrimary,
                            modifier = Modifier.onFocusChanged { onPrimaryFocusChanged(it.hasFocus) },
                        )
                        if (state.canCancel) {
                            RequestActionPill(icon = Icons.Filled.Close, title = "Cancel Request", onClick = onCancel, focusFallback = actionFocusFallback)
                        }
                        if (state.canFollow) {
                            // One node across toggles: only the label and icon change, so focus stays.
                            key("follow") {
                                RequestActionPill(
                                    icon = if (state.isFollowing) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                                    title = state.followActionTitle,
                                    pending = state.isFollowPending,
                                    onClick = onToggleFollow,
                                    focusFallback = actionFocusFallback,
                                )
                            }
                        }
                        (state.actionErrorMessage ?: state.shownFollowNotice)?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                                color = SiloOnSurface.copy(alpha = 0.72f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
            )
        }
        if (castMembers.isNotEmpty()) {
            item(key = "cast", contentType = "detail-cast") {
                TvCastCrewSection(
                    cast = castMembers,
                    horizontalContentPadding = TvDetailHorizontalInset,
                    modifier = Modifier.padding(top = 12.dp),
                    onCastMemberClick = { _, member ->
                        val known = member.tmdbId?.takeIf { it.isNotBlank() }
                        if (known != null) {
                            onOpenPersonByTmdb(known)
                        } else {
                            // Only a name: find the person on TMDB first.
                            castScope.launch {
                                val owner = tokenManager.snapshotCurrentScope() ?: return@launch
                                pluginApi.findPersonTmdbId(member.name, owner)?.let(onOpenPersonByTmdb)
                            }
                        }
                    },
                )
            }
        }
        val videoEntries = extra?.videos.orEmpty().map { org.siloserver.silo.model.catalog.TrailerRailEntry.Remote(it) }
        if (videoEntries.isNotEmpty()) {
            item(key = "videos", contentType = "detail-trailers") {
                val context = androidx.compose.ui.platform.LocalContext.current
                org.siloserver.silo.tv.ui.screens.detail.TvDetailTrailersSection(
                    entries = videoEntries,
                    onSelectRemote = { video -> org.siloserver.silo.common.ui.openYoutubeTrailer(context, video) },
                    onSelectLocal = {},
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
    }
    }
}

/**
 * The page's one primary action: a white Request / Open in Library pill, or
 * the request's status with its dot. Never disabled and never swapped for
 * another node, so a submit can't drop focus mid-morph; presses on a status
 * do nothing.
 */
@Composable
private fun RequestPrimaryActionPill(
    state: RequestDetailUiState,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = state.primaryAction
    SquaredPillSurface(
        kind = PillKind.Primary,
        onClick = { if (action.isInteractive) onClick() },
        modifier = modifier.height(38.dp),
        focusRequester = focusRequester,
        capsule = true,
        stableHero = true,
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) { foreground ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            when (action) {
                RequestPrimaryAction.Request -> Icon(Icons.Filled.Add, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
                RequestPrimaryAction.Submitting -> CircularProgressIndicator(color = foreground, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                is RequestPrimaryAction.OpenInLibrary ->
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = foreground, modifier = Modifier.size(16.dp))
                is RequestPrimaryAction.Status ->
                    Box(modifier = Modifier.size(9.dp).background(action.state.tint.tvColor(), org.siloserver.silo.tv.ui.theme.LevelChamfer))
                RequestPrimaryAction.Loading -> Unit
            }
            Text(
                text = state.primaryActionTitle,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp),
                color = foreground,
                maxLines = 1,
            )
        }
    }
}

/**
 * A secondary action in the hero row (Cancel, Notify me), in the row's 38dp
 * capsule so every control in it shares one height.
 */
@Composable
private fun RequestActionPill(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    pending: Boolean = false,
    /** Told when this pill leaves the row, so focus it held can return to the primary action. */
    focusFallback: RequestActionFocusFallback? = null,
) {
    DisposableEffect(focusFallback) {
        onDispose { focusFallback?.onPillRemoved() }
    }
    SquaredPillSurface(
        kind = PillKind.Secondary,
        onClick = onClick,
        modifier = Modifier.height(38.dp),
        focusRequester = null,
        capsule = true,
        stableHero = true,
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) { foreground ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pending) {
                CircularProgressIndicator(color = foreground, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            } else {
                Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(16.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp),
                color = foreground,
                maxLines = 1,
            )
        }
    }
}

/**
 * Notices a focused secondary pill leaving the action row. Compose hands the
 * focus of a removed node to the nearest focusable, which here is the synopsis
 * above the row, so Select would open the overview instead of acting. A TV has
 * no touch fallback, so focus must return to the primary action.
 *
 * Whether the pill held focus is judged from the row: either the row still has
 * focus when the pill is disposed, or it loses focus in the same frame (the
 * order of the two callbacks is not guaranteed). Each signal bumps [checks];
 * the screen waits a frame, then reclaims for the primary action. A pill that
 * leaves while focus is outside the row (the top bar, a rail, another page)
 * changes nothing; one that leaves while the primary action is focused makes
 * the reclaim a no-op.
 */
@Stable
internal class RequestActionFocusFallback {
    private var rowHasFocus = false
    private var rowLostFocus = false
    private var pillRemoved = false
    private var rowFocusedAtRemoval = false

    var checks by mutableIntStateOf(0)
        private set

    fun onRowFocusChanged(hasFocus: Boolean) {
        if (rowHasFocus && !hasFocus) {
            rowLostFocus = true
            checks++
        }
        rowHasFocus = hasFocus
    }

    fun onPillRemoved() {
        pillRemoved = true
        if (rowHasFocus) rowFocusedAtRemoval = true
        checks++
    }

    /** Whether to reclaim focus now; clears the signals either way. */
    fun takeShouldReclaim(): Boolean {
        val reclaim = shouldReclaimRequestActionFocus(
            pillRemoved = pillRemoved,
            rowFocusedAtRemoval = rowFocusedAtRemoval,
            rowLostFocus = rowLostFocus,
        )
        rowLostFocus = false
        pillRemoved = false
        rowFocusedAtRemoval = false
        return reclaim
    }
}

internal fun shouldReclaimRequestActionFocus(pillRemoved: Boolean, rowFocusedAtRemoval: Boolean, rowLostFocus: Boolean): Boolean =
    pillRemoved && (rowFocusedAtRemoval || rowLostFocus)

/** Extra hero height for the status strip and labeled track. */
private val StatusStripHeight = 64.dp

/**
 * The labeled summary under the synopsis, in the playback summary's grammar,
 * plus the stage track with its step names.
 */
@Composable
private fun RequestStatusStrip(progress: RequestProgress, state: RequestDetailUiState) {
    val record = state.record
    Column(modifier = Modifier.padding(top = 7.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SummaryField(label = "STATUS", value = progress.longLabel, dot = progress.tint.tvColor())
            if (record != null) {
                RequestRowCopy.day(record.createdAt)?.let { SummaryField(label = "REQUESTED", value = it) }
                (RequestTargetSummary.text(record.targets) ?: RequestTargetSummary.qualities(record.targets))?.let {
                    SummaryField(label = "QUALITY", value = it)
                }
            }
        }
        Column(modifier = Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            RequestStageTrack(progress = progress, height = 3.dp)
            Row {
                RequestStep.entries.forEach { step ->
                    val color = when {
                        step == progress.currentStep -> progress.tint.tvColor()
                        step.ordinal < progress.completedSteps -> SiloOnSurface
                        else -> SiloOnSurface.copy(alpha = 0.45f)
                    }
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = color,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryField(label: String, value: String, dot: Color? = null) {
    Column(modifier = Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.55.sp),
            color = Color.White.copy(alpha = 0.48f),
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (dot != null) Box(modifier = Modifier.size(6.dp).background(dot, org.siloserver.silo.tv.ui.theme.LevelChamfer))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** `2026 · 2h 25m · TMDB 7.9`, with genres after it as source tokens. */
private fun RequestMediaDetail.factTokens(ratings: List<org.siloserver.silo.model.catalog.DisplayRating>): List<TvHeroFactToken> = buildList {
    year?.takeIf { it > 0 }?.let { add(TvHeroFactToken.TextToken(it.toString())) }
    if (mediaType == RequestMediaType.Series) {
        numberOfSeasons?.takeIf { it > 0 }?.let { add(TvHeroFactToken.TextToken(if (it == 1) "1 season" else "$it seasons")) }
    } else {
        runtime?.takeIf { it > 0 }?.let { add(TvHeroFactToken.TextToken(if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m")) }
    }
    ratings.forEach { add(TvHeroFactToken.ExternalRating(it)) }
}

private fun RequestMediaDetail.creditText(): String? = when {
    director.isNotBlank() -> "Directed by $director"
    creators.isNotEmpty() -> "Created by ${creators.take(2).joinToString(", ")}"
    else -> null
}

