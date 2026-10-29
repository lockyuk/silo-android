package org.siloserver.silo.tv.ui.screens.home

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.imageLoader
import coil3.request.ImageRequest
import kotlin.math.roundToInt
import org.koin.compose.koinInject
import org.siloserver.silo.common.cards.LocalEpisodeSpoilerPrefs
import org.siloserver.silo.common.settings.titleLogoUrl
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.section.ResolvedSection
import org.siloserver.silo.model.section.SectionItem
import org.siloserver.silo.model.settings.EpisodeSpoilerPrefs
import org.siloserver.silo.model.settings.EpisodeSpoilers
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.CatalogRepository
import org.siloserver.silo.tv.ui.components.LocalAmbientBackdropTint
import org.siloserver.silo.tv.ui.components.TvMarqueeContent
import org.siloserver.silo.tv.ui.components.TvMarqueeEnrichment
import org.siloserver.silo.tv.ui.components.TvPrimaryPillButton
import org.siloserver.silo.tv.ui.components.TvRootHeroBackdrop
import org.siloserver.silo.tv.ui.components.TvSecondaryPillButton
import org.siloserver.silo.tv.ui.components.rememberAmbientBackdropTintState
import org.siloserver.silo.tv.ui.focus.TvContentInitialFocusMaxAttempts
import org.siloserver.silo.tv.ui.focus.TvObservedFocusResult
import org.siloserver.silo.tv.ui.focus.requestFocusUntilObserved
import org.siloserver.silo.tv.ui.theme.TvSkyline

/** The most items the hero rotates through. */
private const val HeroMaxItems = 10

/** How long each item stays before the hero moves to the next. */
private const val HeroDwellMillis = 6_000

/** The artwork and tint crossfade between two items. */
private const val HeroFadeMillis = 1_600

/** The title block's crossfade, quicker than the art so the copy never doubles up for long. */
private const val HeroCopyFadeMillis = 700

/**
 * Continue Watching as Home's opening hero, laid out like the web client's
 * hero: over the corner-anchored backdrop and its sampled tint, the title's
 * clear logo sits just under the menu bar's Silo logo, followed by the
 * episode title, the dot-separated facts, the synopsis and the resume bar.
 * Resume and More Info sit at the bottom beside a row of dots, one per item
 * (up to ten). The current dot fills over six seconds;
 * when it is full the hero crossfades to the next item. The next row's
 * header peeks at the bottom. Left from Resume and Right from More Info step
 * through the items; Down leaves for the rows below.
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
    val items = section.items.take(HeroMaxItems)
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
            Crossfade(
                targetState = item,
                animationSpec = tween(HeroCopyFadeMillis),
                label = "tvContinueWatchingHeroCopy",
                modifier = Modifier
                    .fillMaxSize()
                    // The logo sits just under the Silo logo in the menu
                    // bar and the copy runs down from it toward the buttons.
                    .padding(
                        start = TvSkyline.safeAreaX,
                        top = TvSkyline.barTopInset + TvSkyline.barHeight + HeroLogoTopGap,
                        bottom = NextRowPeekHeight + HeroControlsHeight + HeroControlsGap * 2,
                    )
                    .clipToBounds(),
            ) { shown ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                    HeroCopy(content = contents[shown])
                }
            }

            Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TvSkyline.safeAreaX)
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
                    TvSecondaryPillButton(
                        icon = Icons.Outlined.Info,
                        title = "More Info",
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

private val HeroCopyWidth = 520.dp

private val HeroLogoHeight = 116.dp

/** From the menu bar to the top of the logo. */
private val HeroLogoTopGap = 10.dp

private val HeroLogoMaxWidth = 460.dp

private val HeroShadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), 12f)

/**
 * The web hero's copy with the clear logo in place of its eyebrow and title:
 * logo, episode title, facts, synopsis and the resume bar.
 */
@Composable
private fun HeroCopy(content: TvMarqueeContent) {
    val item = content.source
    val isEpisode = item.type.equals("episode", ignoreCase = true)
    val hidesSynopsis = isEpisode && LocalEpisodeSpoilerPrefs.current.hidesOverview(EpisodeSpoilers.isUnwatched(item))
    Column(modifier = Modifier.widthIn(max = HeroCopyWidth)) {
        HeroLogo(
            logoUrl = titleLogoUrl(content.logoUrl),
            title = if (isEpisode) item.seriesTitle?.takeIf { it.isNotBlank() } ?: content.title else item.title,
        )
        if (isEpisode && item.title.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                item.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = HeroShadow),
            )
        }
        val facts = heroFacts(item)
        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                facts.joinToString("  ·  "),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = HeroShadow),
            )
        }
        content.synopsis?.takeIf { it.isNotBlank() && !hidesSynopsis }?.let { synopsis ->
            Spacer(Modifier.height(10.dp))
            Text(
                synopsis,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = HeroShadow),
            )
        }
        HeroProgress(item)
    }
}

/** The clear logo; the title in text until it decodes, or when there is none. */
@Composable
private fun HeroLogo(logoUrl: String?, title: String) {
    var logoLoaded by remember(logoUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier.height(HeroLogoHeight).fillMaxWidth(),
        contentAlignment = Alignment.TopStart,
    ) {
        if (logoUrl.isNullOrBlank() || !logoLoaded) {
            Text(
                title,
                color = Color.White,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = HeroShadow),
            )
        }
        if (!logoUrl.isNullOrBlank()) {
            ThumbhashImage(
                url = logoUrl,
                thumbhash = null,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopStart,
                transparent = true,
                crossfadeMillis = 0,
                onSuccess = { logoLoaded = true },
                modifier = Modifier.fillMaxHeight().widthIn(max = HeroLogoMaxWidth),
            )
        }
    }
}

/**
 * The web hero's facts: `S4 · E2 · 52 min · TV-MA` for an episode, and year,
 * runtime, rating, two genres and certificate for a movie.
 */
private fun heroFacts(item: SectionItem): List<String> {
    val facts = mutableListOf<String>()
    val runtimeSeconds = item.runtime?.takeIf { it > 0 }?.let { it * 60.0 }
        ?: item.durationSeconds?.takeIf { it > 0 }
    val runtime = runtimeSeconds?.let { seconds ->
        val minutes = (seconds / 60).roundToInt()
        when {
            minutes <= 0 -> null
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0 -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }
    val certificate = item.contentRating?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
    if (item.type.equals("episode", ignoreCase = true)) {
        val season = item.seasonNumber
        val episode = item.episodeNumber
        if (season != null && episode != null && season >= 0 && episode >= 0) facts += "S$season · E$episode"
        runtime?.let { facts += it }
        certificate?.let { facts += it }
        return facts
    }
    if (item.year > 0) facts += item.year.toString()
    runtime?.let { facts += it }
    val imdb = item.ratingImdb?.takeIf { it > 0 && it <= 10 }
    val tmdb = item.ratingTmdb?.takeIf { it > 0 && it <= 10 }
    when {
        imdb != null -> facts += "IMDb ${"%.1f".format(imdb)}"
        tmdb != null -> facts += "TMDB ${"%.1f".format(tmdb)}"
    }
    item.genres.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(2).forEach { facts += it }
    certificate?.let { facts += it }
    return facts
}

/** The red resume bar and the time left, under the synopsis. */
@Composable
private fun HeroProgress(item: SectionItem) {
    val position = item.positionSeconds ?: return
    val duration = item.durationSeconds?.takeIf { it > 0 } ?: return
    val fraction = (position / duration).toFloat().coerceIn(0f, 1f)
    val remaining = ((duration - position) / 60).toInt().coerceAtLeast(1)
    Spacer(Modifier.height(12.dp))
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
            style = TextStyle(shadow = HeroShadow),
        )
    }
}

/**
 * One dot per item, Netflix style; the current one is a short bar that fills
 * as the timer runs.
 */
@Composable
private fun HeroIndicators(count: Int, current: Int, progress: () -> Float, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
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
