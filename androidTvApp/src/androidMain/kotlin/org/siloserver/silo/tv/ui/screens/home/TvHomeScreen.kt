package org.siloserver.silo.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.siloserver.silo.common.ui.components.PrefetchSectionArtwork
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.section.ResolvedSection
import org.siloserver.silo.model.section.SectionItem
import org.siloserver.silo.tv.data.preferences.TvHomeSectionPreferences
import org.siloserver.silo.tv.ui.components.TvErrorScreen
import org.siloserver.silo.tv.ui.components.TvLoadingScreen
import org.siloserver.silo.tv.ui.components.TvMediaCardActions
import org.siloserver.silo.tv.ui.components.TvSkylineDetailPrefetchTarget
import org.siloserver.silo.tv.ui.components.TvSkylineSectionFeed
import org.siloserver.silo.tv.ui.components.isTvContinueWatchingRow
import org.siloserver.silo.tv.ui.components.isTvProgressRow
import org.siloserver.silo.tv.ui.focus.TvContentInitialFocusMaxAttempts
import org.siloserver.silo.tv.ui.focus.TvObservedFocusResult
import org.siloserver.silo.tv.ui.focus.claimFocusOrReport
import org.siloserver.silo.tv.ui.focus.requestFocusUntilObserved
import org.siloserver.silo.tv.ui.theme.RowDimens
import org.siloserver.silo.viewmodel.HomeDismissal
import org.siloserver.silo.viewmodel.HomeViewModel

internal fun shouldShowHomeEmptyState(
    isLoading: Boolean,
    error: String?,
    visibleSectionCount: Int,
): Boolean = !isLoading && error == null && visibleSectionCount == 0

/**
 * Android TV Home screen: the Skyline landing, matching tvOS Home by rendering
 * all visible non-empty server sections through the shared `TvSkylineSectionFeed`.
 */
@Composable
fun TvHomeScreen(
    onItemClick: (contentId: String) -> Unit,
    onItemDetailSelection: (
        contentId: String,
        seasonNumber: Int?,
        episodeContentId: String?,
    ) -> Unit = { contentId, _, _ -> onItemClick(contentId) },
    onPlayItem: (contentId: String, type: String?, resumePositionSeconds: Double?) -> Unit = { _, _, _ -> },
    onSeeAll: () -> Unit = {},
    onOpenForYou: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onInitialContentFocus: () -> Unit = {},
    focusRequest: Int = 0,
    detailReturnFocusRequest: Int = 0,
    detailReturnCardFocusRequester: FocusRequester? = null,
    firstRowFocusRequester: FocusRequester? = null,
    firstRowContainerFocusRequester: FocusRequester? = null,
    shouldRefreshOnResume: () -> Boolean = { true },
    onContentUpFallbackChanged: ((((Boolean) -> Boolean)?) -> Unit)? = null,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val homeSectionPreferences: TvHomeSectionPreferences = koinInject()
    val homeSectionPreferenceState by homeSectionPreferences.state.collectAsState()
    val normalizedSections = remember(state.sections) { state.sections.normalizeTvHomeSections() }
    val visibleSections = remember(normalizedSections, homeSectionPreferenceState.layout) {
        TvHomeSectionPreferences.arrange(
            sections = normalizedSections,
            layout = homeSectionPreferenceState.layout,
        )
    }
    PrefetchSectionArtwork(remember(visibleSections) { visibleSections.map { it.items } })
    val sectionsHiddenByPreference =
        normalizedSections.isNotEmpty() && visibleSections.isEmpty()

    LaunchedEffect(homeSectionPreferences) {
        homeSectionPreferences.refresh()
    }

    // TV has no pull-to-refresh, so ON_RESUME is the only manual freshness
    // path — refresh quietly whenever the user returns to Home (e.g. after
    // finishing playback), complementing the realtime socket. While Home is
    // paused (the player, a detail page or another tab covers it) realtime
    // updates wait, so a large Home is not re-read beside the player's buffer.
    androidx.lifecycle.compose.LifecycleResumeEffect(viewModel) {
        viewModel.onShown()
        // The effect runs as soon as it is added. On a new ViewModel that is
        // while its own first load is still running, and a second full Home
        // read would only supersede it; the first load runs a waiting update.
        if (shouldRefreshOnResume() && !viewModel.uiState.value.isLoading) {
            viewModel.refreshFromRealtime()
        }
        onPauseOrDispose { viewModel.onHidden() }
    }
    // A return from a detail page skips the resume refresh until the shell has
    // restored focus; Home re-reads then, every time, so progress and new
    // items are current as soon as it is back on screen.
    val refreshAllowed = shouldRefreshOnResume()
    LaunchedEffect(refreshAllowed) {
        if (refreshAllowed && !viewModel.uiState.value.isLoading) viewModel.refreshFromRealtime()
    }

    when {
        state.isLoading && state.sections.isEmpty() -> TvLoadingScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
        state.error != null && state.sections.isEmpty() -> TvErrorScreen(
            message = state.error!!,
            onRetry = viewModel::loadSections,
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
        shouldShowHomeEmptyState(state.isLoading, state.error, visibleSections.size) ->
            TvHomeEmptyState(
                onRefresh = viewModel::loadSections,
                onOpenSettings = onOpenSettings,
                sectionsHiddenByPreference = sectionsHiddenByPreference,
                focusRequester = firstRowFocusRequester,
                focusRequest = focusRequest,
                onInitialContentFocus = onInitialContentFocus,
            )
        else -> key(homeSectionPreferenceState.layoutRevision) {
            val heroSection = visibleSections.firstOrNull { it.isTvContinueWatchingRow() && it.items.isNotEmpty() }
            // Home opens on the Continue Watching hero; Down leaves it for the
            // rows and Up from the top row comes back.
            var heroActive by rememberSaveable { mutableStateOf(true) }
            var rowsFocusRequest by remember { mutableIntStateOf(0) }
            // The shell tells a feed's up-fallback apart by identity, so each
            // registration keeps one wrapper for its whole life.
            val upWrappers = remember { java.util.IdentityHashMap<(Boolean) -> Boolean, (Boolean) -> Boolean>() }
            if (heroSection != null && heroActive) {
                // The hero leaves room at the bottom for the first line of the
                // next row, as the phone does, so Home shows there is more below.
                val nextSection = visibleSections.firstOrNull { it !== heroSection && it.items.isNotEmpty() }
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                TvContinueWatchingHero(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    section = heroSection,
                    onResume = { item -> onPlayItem(item.contentId, item.type, item.positionSeconds) },
                    onDetails = { item ->
                        val target = item.tvContinueWatchingDetailTarget()
                        onItemDetailSelection(target.contentId, target.seasonNumber, target.episodeContentId)
                    },
                    onDown = {
                        heroActive = false
                        rowsFocusRequest++
                    },
                    onInitialContentFocus = onInitialContentFocus,
                    focusRequest = focusRequest,
                    detailReturnFocusRequest = detailReturnFocusRequest,
                    resumeFocusRequester = firstRowFocusRequester,
                    detailsFocusRequester = detailReturnCardFocusRequester,
                    buttonsFocusRequester = firstRowContainerFocusRequester,
                )
                if (nextSection != null) TvNextRowPeek(nextSection)
                }
            } else {
                TvHomeContent(
                    // The hero is Continue Watching, so the rows below leave it out.
                    sections = if (heroSection != null) visibleSections - heroSection else visibleSections,
                    sectionsFullyResolved = state.sectionsFullyResolved,
                    onItemClick = onItemClick,
                    onItemDetailSelection = onItemDetailSelection,
                    onPlayItem = onPlayItem,
                    onSeeAll = onSeeAll,
                    onOpenForYou = onOpenForYou,
                    onInitialContentFocus = onInitialContentFocus,
                    focusRequest = focusRequest + rowsFocusRequest,
                    detailReturnFocusRequest = detailReturnFocusRequest,
                    detailReturnCardFocusRequester = detailReturnCardFocusRequester,
                    firstRowFocusRequester = firstRowFocusRequester,
                    firstRowContainerFocusRequester = firstRowContainerFocusRequester,
                    onContentUpFallbackChanged = if (heroSection == null || onContentUpFallbackChanged == null) {
                        onContentUpFallbackChanged
                    } else {
                        { inner ->
                            onContentUpFallbackChanged(
                                inner?.let {
                                    upWrappers.getOrPut(it) {
                                        { isRepeat ->
                                            val handled = it(isRepeat)
                                            // Up from the top row returns to the hero
                                            // instead of the menu bar.
                                            if (!handled && !isRepeat) {
                                                heroActive = true
                                                true
                                            } else {
                                                handled
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    },
                    onSetWatched = viewModel::setWatched,
                    onMarqueeTranslated = viewModel::refreshFromRealtime,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onToggleWatchlist = viewModel::toggleWatchlist,
                    onRemoveFromHome = viewModel::dismissFromHome,
                )
            }
        }
    }
}

@Composable
private fun TvHomeEmptyState(
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    sectionsHiddenByPreference: Boolean,
    focusRequester: FocusRequester?,
    focusRequest: Int,
    onInitialContentFocus: () -> Unit,
) {
    val refreshFocusRequester = focusRequester ?: remember { FocusRequester() }
    var homeContentHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(focusRequest) {
        val landed = requestFocusUntilObserved(
            maxAttempts = TvContentInitialFocusMaxAttempts,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = refreshFocusRequester::requestFocus,
            isFocused = { homeContentHasFocus },
        )
        if (landed == TvObservedFocusResult.Focused) onInitialContentFocus()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { homeContentHasFocus = it.hasFocus }
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (sectionsHiddenByPreference) {
                    "Home sections are hidden"
                } else {
                    "Nothing to watch yet"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (sectionsHiddenByPreference) {
                    "Choose which rows appear in Settings → General → Home Sections."
                } else {
                    "Add media to your libraries or start watching to see it here."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = if (sectionsHiddenByPreference) onOpenSettings else onRefresh,
                modifier = Modifier.focusRequester(refreshFocusRequester),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
            ) {
                Text(
                    text = if (sectionsHiddenByPreference) "Open Settings" else "Refresh",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun TvHomeContent(
    sections: List<ResolvedSection>,
    onItemClick: (String) -> Unit,
    onItemDetailSelection: (String, Int?, String?) -> Unit,
    onPlayItem: (contentId: String, type: String?, resumePositionSeconds: Double?) -> Unit,
    onSeeAll: () -> Unit = {},
    onOpenForYou: () -> Unit = {},
    onInitialContentFocus: () -> Unit,
    focusRequest: Int,
    detailReturnFocusRequest: Int,
    detailReturnCardFocusRequester: FocusRequester?,
    firstRowFocusRequester: FocusRequester?,
    firstRowContainerFocusRequester: FocusRequester?,
    onContentUpFallbackChanged: ((((Boolean) -> Boolean)?) -> Unit)?,
    onSetWatched: (String, Boolean) -> Unit = { _, _ -> },
    onToggleFavorite: (String, Boolean) -> Unit = { _, _ -> },
    onToggleWatchlist: (String, Boolean) -> Unit = { _, _ -> },
    onRemoveFromHome: (SectionItem) -> Unit = {},
    sectionsFullyResolved: Boolean = true,
    onMarqueeTranslated: () -> Unit = {},
) {
    TvSkylineSectionFeed(
        onMarqueeTranslated = onMarqueeTranslated,
        surfaceKey = "home",
        // tvOS Home drops the foreground marquee + row band by 56pt while
        // keeping the ambient art and top navigation fixed (28dp at TV scale).
        contentVerticalOffset = 28.dp,
        sections = sections,
        sectionsComplete = sectionsFullyResolved,
        onItemClick = onItemClick,
        focusRequest = focusRequest,
        detailReturnFocusRequest = detailReturnFocusRequest,
        detailReturnCardFocusRequester = detailReturnCardFocusRequester,
        firstRowFocusRequester = firstRowFocusRequester,
        firstRowContainerRequester = firstRowContainerFocusRequester,
        onInitialContentFocus = onInitialContentFocus,
        onContentUpFallbackChanged = onContentUpFallbackChanged,
        iconForSection = { section ->
            if (section.isTvProgressRow()) Icons.Filled.PlayCircle else null
        },
        onSeeAllClickForSection = { section ->
            if (section.isForYouRow()) onOpenForYou else null
        },
        showProgressForSection = { section ->
            section.isTvProgressRow()
        },
        styleForSection = { section ->
            section.tvHomeRowStyle()
        },
        clickActionForSection = { section, item ->
            if (section.isTvContinueWatchingRow()) {
                {
                    val target = item.tvContinueWatchingDetailTarget()
                    onItemDetailSelection(
                        target.contentId,
                        target.seasonNumber,
                        target.episodeContentId,
                    )
                }
            } else {
                null
            }
        },
        priorityDetailPrefetchTargetForSection = { section, item ->
            if (section.isTvContinueWatchingRow()) {
                item.tvContinueWatchingPrefetchTarget()
            } else {
                null
            }
        },
        cardActions = { section, item ->
            val isContinueWatching = section.isTvContinueWatchingRow()
            val dismissal = if (section.isTvProgressRow()) HomeDismissal.of(item) else null
            TvMediaCardActions(
                onPlay = if (isContinueWatching) {
                    {
                        onPlayItem(
                            item.contentId,
                            item.type,
                            item.positionSeconds,
                        )
                    }
                } else {
                    null
                },
                playLabel = if (item.positionSeconds != null) "Resume" else "Play",
                onSetWatched = { watched -> onSetWatched(item.contentId, watched) },
                onToggleFavorite = { fav -> onToggleFavorite(item.contentId, fav) },
                onToggleWatchlist = { wl -> onToggleWatchlist(item.contentId, wl) },
                onRemoveFromHome = dismissal?.let { { onRemoveFromHome(item) } },
                removeFromHomeLabel = dismissal?.menuLabel.orEmpty(),
            )
        },
    )
}

internal data class TvContinueWatchingDetailTarget(
    val contentId: String,
    val seasonNumber: Int? = null,
    val episodeContentId: String? = null,
)

/** Opens episode progress on the combined Series page; movies keep their own detail. */
internal fun SectionItem.tvContinueWatchingDetailTarget(): TvContinueWatchingDetailTarget {
    val seriesContentId = seriesId?.takeIf { it.isNotBlank() }
    val isEpisode = type.equals("episode", ignoreCase = true) || episodeNumber != null
    return if (isEpisode && seriesContentId != null && seasonNumber != null) {
        TvContinueWatchingDetailTarget(
            contentId = seriesContentId,
            seasonNumber = seasonNumber,
            episodeContentId = contentId,
        )
    } else {
        TvContinueWatchingDetailTarget(contentId = contentId)
    }
}

/** Warms exactly what the Continue Watching Select action will open. */
internal fun SectionItem.tvContinueWatchingPrefetchTarget(): TvSkylineDetailPrefetchTarget {
    val navigation = tvContinueWatchingDetailTarget()
    return TvSkylineDetailPrefetchTarget(
        detailContentId = navigation.contentId,
        seriesId = navigation.contentId.takeIf { navigation.episodeContentId != null },
        seasonNumber = navigation.seasonNumber,
        episodeContentId = navigation.episodeContentId,
    )
}

private fun ResolvedSection.isForYouRow(): Boolean {
    val type = sectionType.lowercase()
    return type == "for_you" ||
        type == "foryou" ||
        type == "recommendations" ||
        title.equals("for you", ignoreCase = true) ||
        title.equals("recommended for you", ignoreCase = true)
}

/** How much of the next row shows under the hero: its title and the top of its cards. */
private val NextRowPeekHeight = 112.dp

/**
 * The first line of the row after the hero: its title and the tops of its
 * first posters, cut off by the screen's edge. It only shows what is below;
 * Down from the hero moves into the rows themselves.
 */
@Composable
private fun TvNextRowPeek(section: ResolvedSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(NextRowPeekHeight)
            .clipToBounds()
            .padding(start = 58.dp, top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = section.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            section.items.take(12).forEach { item ->
                Box(
                    modifier = Modifier
                        .size(width = RowDimens.DensePosterWidth, height = RowDimens.DensePosterHeight)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                ) {
                    ThumbhashImage(
                        url = item.posterUrl ?: item.backdropUrl,
                        thumbhash = item.posterThumbhash ?: item.backdropThumbhash,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
