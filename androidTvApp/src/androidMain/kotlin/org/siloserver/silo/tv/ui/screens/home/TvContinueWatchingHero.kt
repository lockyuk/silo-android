package org.siloserver.silo.tv.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.imageLoader
import coil3.request.ImageRequest
import org.koin.compose.koinInject
import org.siloserver.silo.common.cards.LocalEpisodeSpoilerPrefs
import org.siloserver.silo.model.section.ResolvedSection
import org.siloserver.silo.model.section.SectionItem
import org.siloserver.silo.model.settings.EpisodeSpoilerPrefs
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.CatalogRepository
import org.siloserver.silo.tv.ui.components.LocalAmbientBackdropTint
import org.siloserver.silo.tv.ui.components.TvFocusMarquee
import org.siloserver.silo.tv.ui.components.TvMarqueeContent
import org.siloserver.silo.tv.ui.components.TvMarqueeEnrichment
import org.siloserver.silo.tv.ui.components.TvPrimaryPillButton
import org.siloserver.silo.tv.ui.components.TvRootHeroBackdrop
import org.siloserver.silo.tv.ui.components.TvSquareToggleButton
import org.siloserver.silo.tv.ui.components.rememberAmbientBackdropTintState
import org.siloserver.silo.tv.ui.focus.TvContentInitialFocusMaxAttempts
import org.siloserver.silo.tv.ui.focus.TvObservedFocusResult
import org.siloserver.silo.tv.ui.focus.requestFocusUntilObserved
import org.siloserver.silo.tv.ui.theme.TvSkyline

/** How long each item stays before the hero moves to the next. */
private const val HeroDwellMillis = 8_000

/** The artwork and tint crossfade between two items. */
private const val HeroFadeMillis = 1_600

/** The title block's crossfade, quicker than the art so the copy never doubles up for long. */
private const val HeroCopyFadeMillis = 700

/**
 * Continue Watching as Home's opening hero, built from the same pieces as
 * every other Skyline page: the corner-anchored backdrop and its sampled tint,
 * the bottom-left marquee (logo, episode, synopsis, cast line) kept below the
 * menu bar, and the next row's header peeking at the bottom. Under the
 * marquee sit Resume and Details, with the item dots whose current bar fills
 * as the eight-second timer runs; when it is full the hero crossfades to the
 * next item. Left from Resume and Right from Details step through the items;
 * Down leaves for the rows below.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun TvContinueWatchingHero(
    section: ResolvedSection,
    onResume: (SectionItem) -> Unit,
    onDetails: (SectionItem) -> Unit,
    onDown: () -> Unit,
    onInitialContentFocus: () -> Unit,
    focusRequest: Int,
    detailReturnFocusRequest: Int,
    resumeFocusRequester: FocusRequester?,
    detailsFocusRequester: FocusRequester?,
    /** The shell's first-row container requester; it lands on the buttons. */
    buttonsFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
    /** The row under the hero, drawn as it is in the feed and cut off by the screen. */
    nextRowPeek: (@Composable () -> Unit)? = null,
) {
    val items = section.items
    if (items.isEmpty()) return
    var index by rememberSaveable(section.id) { mutableIntStateOf(0) }
    if (index >= items.size) index = 0
    // Bumped by a manual step so the dwell timer starts over.
    var stepRevision by remember { mutableIntStateOf(0) }
    val dwell = remember { Animatable(0f) }
    val resume = resumeFocusRequester ?: remember { FocusRequester() }
    val details = detailsFocusRequester ?: remember { FocusRequester() }
    var heroHasFocus by remember { mutableStateOf(false) }
    val step = { delta: Int ->
        index = (index + delta + items.size) % items.size
        stepRevision++
    }

    LaunchedEffect(index, stepRevision, items.size) {
        dwell.snapTo(0f)
        if (items.size < 2) return@LaunchedEffect
        dwell.animateTo(1f, tween(HeroDwellMillis, easing = LinearEasing))
        index = (index + 1) % items.size
    }
    LaunchedEffect(focusRequest, detailReturnFocusRequest) {
        val landed = requestFocusUntilObserved(
            maxAttempts = TvContentInitialFocusMaxAttempts,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = resume::requestFocus,
            isFocused = { heroHasFocus },
        )
        if (landed == TvObservedFocusResult.Focused) onInitialContentFocus()
    }

    val item = items[index]
    val spoilers = LocalEpisodeSpoilerPrefs.current
    val contents = rememberHeroContents(spoilers)
    // The current and next items are resolved ahead, so a rotation swaps in
    // finished copy and art instead of upgrading it mid-crossfade.
    contents.Resolve(item)
    contents.Resolve(items[(index + 1) % items.size])
    val content = contents[item]

    val tintState = rememberAmbientBackdropTintState()
    LaunchedEffect(content.contentId, content.heroBackdropUrl) {
        tintState.set(content.source, content.heroBackdropUrl)
    }

    CompositionLocalProvider(LocalAmbientBackdropTint provides tintState) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .onFocusChanged { heroHasFocus = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        onDown()
                        true
                    } else {
                        false
                    }
                },
        ) {
            TvRootHeroBackdrop(
                content = content,
                crossfadeMillis = HeroFadeMillis,
                modifier = Modifier.fillMaxSize(),
            )
            TvFocusMarquee(
                content = content,
                detailLine = content.detailLine,
                startPadding = TvSkyline.safeAreaX,
                // Below the menu bar, as on the other pages; the block grows
                // upward from the buttons.
                topPadding = TvSkyline.barTopInset + TvSkyline.barHeight,
                bottomPadding = NextRowPeekHeight + HeroControlsHeight + HeroControlsGap * 2,
                crossfadeMillis = HeroCopyFadeMillis,
                footer = { HeroProgress(it.source) },
                modifier = Modifier.fillMaxSize(),
            )

            Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier
                        .padding(start = TvSkyline.safeAreaX)
                        .height(HeroControlsHeight)
                        .then(if (buttonsFocusRequester != null) Modifier.focusRequester(buttonsFocusRequester) else Modifier)
                        .focusProperties { enter = { resume } }
                        .focusGroup(),
                ) {
                    TvPrimaryPillButton(
                        stableHero = true,
                        icon = Icons.Filled.PlayArrow,
                        title = if ((item.positionSeconds ?: 0.0) > 0) "Resume" else "Play",
                        onClick = { onResume(item) },
                        focusRequester = resume,
                        modifier = Modifier.onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && items.size > 1) {
                                step(-1)
                                true
                            } else {
                                false
                            }
                        },
                    )
                    TvSquareToggleButton(
                        icon = Icons.Outlined.Info,
                        iconActive = Icons.Outlined.Info,
                        isActive = false,
                        contentDescription = "Details",
                        titleOnFocus = "Details",
                        onClick = { onDetails(item) },
                        focusRequester = details,
                        modifier = Modifier.onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight && items.size > 1) {
                                step(1)
                                true
                            } else {
                                false
                            }
                        },
                    )
                    if (items.size > 1) {
                        Spacer(Modifier.width(14.dp))
                        HeroIndicators(count = items.size, current = index, progress = { dwell.value })
                    }
                }
                Spacer(Modifier.height(HeroControlsGap))
                if (nextRowPeek != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(NextRowPeekHeight)
                            .clipToBounds()
                            // Only a preview: Down from the hero moves into
                            // the rows themselves, so focus never enters here.
                            .focusProperties { canFocus = false; enter = { FocusRequester.Cancel } }
                            .focusGroup(),
                    ) {
                        Box(Modifier.wrapContentHeight(Alignment.Top, unbounded = true)) {
                            nextRowPeek()
                        }
                    }
                } else {
                    Spacer(Modifier.height(NextRowPeekHeight))
                }
            }
        }
    }
}

/** Tall enough for the next row's header and the top third of its cards. */
private val NextRowPeekHeight = 112.dp

private val HeroControlsHeight = 44.dp

private val HeroControlsGap = 10.dp

/** The red resume bar and the time left, under the marquee's lines. */
@Composable
private fun HeroProgress(item: SectionItem) {
    val position = item.positionSeconds ?: return
    val duration = item.durationSeconds?.takeIf { it > 0 } ?: return
    val fraction = (position / duration).toFloat().coerceIn(0f, 1f)
    val remaining = ((duration - position) / 60).toInt().coerceAtLeast(1)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.28f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(Color(0xFFE50914)),
            )
        }
        Text(
            if (remaining >= 60) "${remaining / 60}h ${remaining % 60}m left" else "${remaining}m left",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun HeroIndicators(count: Int, current: Int, progress: () -> Float, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count.coerceAtMost(12)) { i ->
            if (i == current) {
                Box(
                    Modifier
                        .width(22.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                        // Drawn from the timer each frame without recomposing
                        // the hero: only this bar changes as it runs.
                        .drawBehind {
                            drawRect(Color.White, size = Size(size.width * (0.15f + 0.85f * progress()), size.height))
                        },
                )
            } else {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.35f)),
                )
            }
        }
    }
}

/**
 * The marquee content for each hero item: the feed's own content for the
 * item, with the show's logo and, for a movie or an episode without a still,
 * the title's full-size backdrop from its detail; an episode keeps its still.
 * The episode's own detail adds the aired/cast line, as the feed's enrichment
 * does. Details are cached and shared with the rest of Home.
 */
private class HeroContents(
    private val catalog: CatalogRepository,
    private val spoilers: EpisodeSpoilerPrefs,
) {
    private val resolved = mutableStateMapOf<String, TvMarqueeContent>()

    operator fun get(item: SectionItem): TvMarqueeContent =
        resolved[item.contentId]?.takeIf { it.source == item } ?: base(item)

    private fun base(item: SectionItem) =
        TvMarqueeContent.from(item, rowTitle = "", rowIdentity = "continue-watching-hero", spoilers = spoilers)

    @Composable
    fun Resolve(item: SectionItem) {
        val context = LocalContext.current
        LaunchedEffect(item) {
            if (resolved[item.contentId]?.source == item) return@LaunchedEffect
            var content = base(item)
            val isEpisode = item.type.equals("episode", ignoreCase = true)
            val showId = if (isEpisode) item.seriesId?.takeIf { it.isNotBlank() } else item.contentId
            val show = showId?.let { (catalog.warmItemDetail(it) as? ApiResult.Success)?.data }
            if (show != null) {
                val keepStill = isEpisode && item.backdropIsEpisodeStill == true && content.backdropUrl != null
                val backdrop = show.backdropUrl?.takeIf { it.isNotBlank() }
                content = content.copy(
                    logoUrl = show.logoUrl?.takeIf { it.isNotBlank() } ?: content.logoUrl,
                    backdropUrl = if (keepStill || backdrop == null) content.backdropUrl else backdrop,
                    backdropThumbhash = if (keepStill || backdrop == null) content.backdropThumbhash else show.backdropThumbhash,
                    backdropIsEpisodeStill = if (keepStill || backdrop == null) content.backdropIsEpisodeStill else false,
                )
            }
            val own = if (isEpisode) (catalog.warmItemDetail(item.contentId) as? ApiResult.Success)?.data else show
            own?.let { content = content.copy(detailLine = TvMarqueeEnrichment.from(it).detailLine) }
            content.heroBackdropUrl?.let { url ->
                context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
            }
            resolved[item.contentId] = content
        }
    }
}

@Composable
private fun rememberHeroContents(spoilers: EpisodeSpoilerPrefs): HeroContents {
    val catalog: CatalogRepository = koinInject()
    return remember(catalog, spoilers) { HeroContents(catalog, spoilers) }
}
