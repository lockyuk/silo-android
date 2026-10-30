package org.siloserver.silo.tv.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import org.siloserver.silo.tv.ui.components.TvHeroArtFrame
import org.siloserver.silo.tv.ui.components.TvMarqueeEasing
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Size as CoilSize
import coil3.transform.Transformation
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.koin.compose.koinInject
import org.siloserver.silo.common.cards.LocalEpisodeSpoilerPrefs
import org.siloserver.silo.common.settings.titleLogoUrl
import org.siloserver.silo.model.catalog.OverlaySummary
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

/**
 * The title block fades through rather than crossfading, so two logos and two
 * synopses never sit on top of each other: the old copy fades out, then the
 * new copy fades in, rising slightly, while the art underneath is mid-fade.
 */
private const val HeroCopyFadeOutMillis = 360
private const val HeroCopyFadeInMillis = 640
private const val HeroCopyRise = 10

/** How long a logo may take to decode before the title shows in text instead. */
private const val HeroLogoTextFallbackMillis = 900L

/**
 * Home's opening hero (Continue Watching unless the profile chose another
 * row), laid out like the web client's
 * hero: over the corner-anchored backdrop and its sampled tint, the title's
 * clear logo sits just under the menu bar's Silo logo, followed by the
 * episode title, the dot-separated facts, the synopsis and the resume bar.
 * Play or Resume sits at the bottom between a left and a right arrow, beside
 * a row of dots, one per item (up to ten). The current dot fills over six
 * seconds; when it is full the hero crossfades to the next item. The next
 * row's header peeks at the bottom. Left and Right on the button step through
 * the items, flashing the arrow; Down leaves for the rows below.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun TvContinueWatchingHero(
    section: ResolvedSection,
    onResume: (SectionItem) -> Unit,
    onDown: () -> Unit,
    onInitialContentFocus: () -> Unit,
    focusRequest: Int,
    detailReturnFocusRequest: Int,
    resumeFocusRequester: FocusRequester?,
    detailsFocusRequester: FocusRequester?,
    /** The shell's first-row container requester; it lands on the button. */
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
    var heroHasFocus by remember { mutableStateOf(false) }
    // The arrow the last manual step went, so that arrow can flash.
    var lastStep by remember { mutableIntStateOf(0) }
    val step = { delta: Int ->
        index = (index + delta + items.size) % items.size
        lastStep = delta
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
                artFrame = HeroArtFrame,
                modifier = Modifier.fillMaxSize(),
            )
            val copyRisePx = with(androidx.compose.ui.platform.LocalDensity.current) { HeroCopyRise.dp.roundToPx() }
            AnimatedContent(
                targetState = item,
                transitionSpec = {
                    (
                        fadeIn(tween(HeroCopyFadeInMillis, delayMillis = HeroCopyFadeOutMillis, easing = TvMarqueeEasing)) +
                            slideInVertically(tween(HeroCopyFadeInMillis, delayMillis = HeroCopyFadeOutMillis, easing = TvMarqueeEasing)) { copyRisePx }
                        ) togetherWith fadeOut(tween(HeroCopyFadeOutMillis, easing = TvMarqueeEasing)) using SizeTransform(clip = false)
                },
                contentKey = { it.contentId },
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
                    if (items.size > 1) {
                        HeroArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, flash = lastStep < 0, revision = stepRevision)
                    }
                    TvPrimaryPillButton(
                        stableHero = true,
                        icon = if (item.isTvHeroPlayable()) Icons.Filled.PlayArrow else Icons.Outlined.Info,
                        title = when {
                            !item.isTvHeroPlayable() -> "More Info"
                            (item.positionSeconds ?: 0.0) > 0 -> "Resume"
                            else -> "Play"
                        },
                        onClick = { onResume(item) },
                        focusRequester = resume,
                        // The shell's detail-return requester lands here too,
                        // now that the hero has no Details button.
                        modifier = Modifier
                            .then(if (detailsFocusRequester != null) Modifier.focusRequester(detailsFocusRequester) else Modifier)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown || items.size < 2) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionLeft -> { step(-1); true }
                                    Key.DirectionRight -> { step(1); true }
                                    else -> false
                                }
                            },
                    )
                    if (items.size > 1) {
                        HeroArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, flash = lastStep > 0, revision = stepRevision)
                    }
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

/**
 * The hero's art reaches down behind the buttons and dots and further left
 * than the feed's: full strength to about three quarters of the way down,
 * fading out across the next row's peek.
 */
private val HeroArtFrame = TvHeroArtFrame(
    widthFraction = 0.90f,
    heightFraction = 0.92f,
    horizontalOpaqueFrom = 0.45f,
    verticalOpaqueUntil = 0.80f,
)

/** Tall enough for the next row's header and the top third of its cards. */
private val NextRowPeekHeight = 112.dp

private val HeroControlsHeight = 44.dp

private val HeroControlsGap = 10.dp

private val HeroCopyWidth = 520.dp

private val HeroLogoHeight = 70.dp

/**
 * The area every logo is drawn at, so a wide wordmark and a stacked one read
 * about the same size; [HeroLogoMaxWidth] and [HeroLogoHeight] cap the
 * extremes. The caps sit close to the area's own shape, so most logos land
 * between 140 and 250 dp wide and 50 and 70 dp tall.
 */
private const val HeroLogoArea = 70f * 179f

/** From the menu bar to the top of the logo. */
private val HeroLogoTopGap = 10.dp

private val HeroLogoMaxWidth = 250.dp

private val HeroShadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), 12f)

/**
 * The web hero's copy with the clear logo in place of its eyebrow and title:
 * the logo at the top, under the menu bar, and the episode title, facts,
 * synopsis and resume bar at the bottom, over the buttons.
 */
@Composable
private fun HeroCopy(content: TvMarqueeContent) {
    val item = content.source
    val isEpisode = item.type.equals("episode", ignoreCase = true)
    val hidesSynopsis = isEpisode && LocalEpisodeSpoilerPrefs.current.hidesOverview(EpisodeSpoilers.isUnwatched(item))
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopStart).widthIn(max = HeroCopyWidth)) {
            HeroLogo(
                logoUrl = titleLogoUrl(content.logoUrl),
                title = if (isEpisode) item.seriesTitle?.takeIf { it.isNotBlank() } ?: content.title else item.title,
            )
        }
        Column(Modifier.align(Alignment.BottomStart).widthIn(max = HeroCopyWidth)) {
            HeroDetails(content, isEpisode, hidesSynopsis)
        }
    }
}

/** The episode title, facts, synopsis and resume bar, sitting just above the buttons. */
@Composable
private fun HeroDetails(content: TvMarqueeContent, isEpisode: Boolean, hidesSynopsis: Boolean) {
    val item = content.source
    Column {
        if (isEpisode && item.title.isNotBlank()) {
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
            if (isEpisode && item.title.isNotBlank()) Spacer(Modifier.height(8.dp))
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

/**
 * The clear logo, shown only once it has decoded and been sized, so it never
 * jumps from a guessed size to its own. The title shows in text when there is
 * no logo, when it fails, or when it is still decoding after
 * [HeroLogoTextFallbackMillis]; a cached logo is ready long before that.
 */
@Composable
private fun HeroLogo(logoUrl: String?, title: String) {
    var logoLoaded by remember(logoUrl) { mutableStateOf(false) }
    var logoFailed by remember(logoUrl) { mutableStateOf(false) }
    var slow by remember(logoUrl) { mutableStateOf(false) }
    LaunchedEffect(logoUrl) {
        delay(HeroLogoTextFallbackMillis)
        slow = true
    }
    // Width over height of the trimmed logo, known once it decodes.
    var aspect by remember(logoUrl) { mutableStateOf<Float?>(null) }
    Box(
        modifier = Modifier.height(HeroLogoHeight).fillMaxWidth(),
        contentAlignment = Alignment.TopStart,
    ) {
        if (logoUrl.isNullOrBlank() || logoFailed || (slow && !logoLoaded)) {
            Text(
                title,
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = HeroShadow),
            )
        }
        if (!logoUrl.isNullOrBlank()) {
            val context = LocalContext.current
            val request = remember(logoUrl) {
                ImageRequest.Builder(context)
                    .data(logoUrl)
                    .transformations(TrimTransparentEdges)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopStart,
                onSuccess = { success ->
                    val intrinsic = success.painter.intrinsicSize
                    aspect = if (intrinsic.width > 0f && intrinsic.height > 0f) intrinsic.width / intrinsic.height else 3f
                    logoLoaded = true
                },
                onError = { logoFailed = true },
                // Sized to the logo's own shape: drawn in a wider box, a
                // narrow logo was centred in it instead of starting at the left.
                // Hidden until then, and while the text title stands in.
                modifier = (
                    aspect?.let { Modifier.size(heroLogoSize(it)) }
                        ?: Modifier.sizeIn(maxWidth = HeroLogoMaxWidth, maxHeight = HeroLogoHeight)
                    )
                    .graphicsLayer { alpha = if (aspect != null && !(slow && !logoLoaded)) 1f else 0f },
            )
        }
    }
}

/** The logo's size at [HeroLogoArea] for its [aspect], inside the caps. */
private fun heroLogoSize(aspect: Float): DpSize {
    var width = sqrt(HeroLogoArea * aspect)
    var height = width / aspect
    if (height > HeroLogoHeight.value) {
        height = HeroLogoHeight.value
        width = height * aspect
    }
    if (width > HeroLogoMaxWidth.value) {
        width = HeroLogoMaxWidth.value
        height = width / aspect
    }
    return DpSize(width.dp, height.dp)
}

/**
 * Crops a logo to its visible pixels. Many clear logos carry a wide
 * transparent border (Top Boy's starts 132 of 500 pixels in), which left them
 * floating right of the Silo logo above; trimmed, every logo starts on the
 * same edge. Coil runs this once per decode, off the main thread, and caches
 * the result.
 */
private object TrimTransparentEdges : Transformation() {
    override val cacheKey = "trim-transparent-edges"

    override suspend fun transform(input: Bitmap, size: CoilSize): Bitmap {
        val width = input.width
        val height = input.height
        val row = IntArray(width)
        var left = width
        var right = -1
        var top = -1
        var bottom = -1
        for (y in 0 until height) {
            input.getPixels(row, 0, width, 0, y, width, 1)
            var first = -1
            var last = -1
            for (x in 0 until width) {
                if ((row[x] ushr 24) > TrimAlphaFloor) {
                    if (first < 0) first = x
                    last = x
                }
            }
            if (first < 0) continue
            if (top < 0) top = y
            bottom = y
            if (first < left) left = first
            if (last > right) right = last
        }
        if (top < 0 || (left == 0 && top == 0 && right == width - 1 && bottom == height - 1)) return input
        return Bitmap.createBitmap(input, left, top, right - left + 1, bottom - top + 1)
    }
}

/** Alpha at or under this counts as empty: soft shadows and compression haze. */
private const val TrimAlphaFloor = 16

/**
 * The web hero's facts with the picture quality in place of the age rating:
 * `S4 · E2 · 52 min · 4K · Dolby Vision` for an episode, and year, runtime,
 * rating, two genres and quality for a movie.
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
    val quality = heroQuality(item.overlaySummary)
    if (item.type.equals("episode", ignoreCase = true)) {
        val season = item.seasonNumber
        val episode = item.episodeNumber
        if (season != null && episode != null && season >= 0 && episode >= 0) facts += "S$season · E$episode"
        runtime?.let { facts += it }
        facts += quality
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
    facts += quality
    return facts
}

/** The file's resolution and dynamic range: `4K`, `1080p`, `Dolby Vision`, `HDR10`. */
private fun heroQuality(summary: OverlaySummary?): List<String> {
    if (summary == null) return emptyList()
    val quality = mutableListOf<String>()
    summary.resolution?.trim()?.takeIf { it.isNotEmpty() }?.let { resolution ->
        quality += when (resolution.lowercase()) {
            "2160p", "4k", "uhd" -> "4K"
            "4320p", "8k" -> "8K"
            else -> resolution.lowercase()
        }
    }
    summary.hdr?.trim()?.takeIf { it.isNotEmpty() }?.let { hdr ->
        quality += if (hdr.contains("dv", ignoreCase = true) || hdr.contains("dolby", ignoreCase = true)) {
            "Dolby Vision"
        } else {
            hdr.uppercase()
        }
    }
    return quality
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
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.8f),
            style = TextStyle(shadow = HeroShadow),
        )
    }
}

/**
 * A round arrow beside the hero's button. Only a cue: focus stays on the
 * button, and Left or Right there steps the hero and flashes this arrow.
 */
@Composable
private fun HeroArrow(icon: ImageVector, flash: Boolean, revision: Int) {
    val glow = remember { Animatable(0f) }
    LaunchedEffect(revision) {
        if (!flash) return@LaunchedEffect
        glow.snapTo(1f)
        glow.animateTo(0f, tween(HeroArrowFlashMillis))
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(HeroControlsHeight)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.14f + 0.86f * glow.value))
            },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

private const val HeroArrowFlashMillis = 350

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
            val show = showId?.let { (catalog.getItemDetailForPrefetch(it) as? ApiResult.Success)?.data }
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
            val own = if (isEpisode) (catalog.getItemDetailForPrefetch(item.contentId) as? ApiResult.Success)?.data else show
            own?.let { content = content.copy(detailLine = TvMarqueeEnrichment.from(it).detailLine) }
            content.heroBackdropUrl?.let { url ->
                context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
            }
            // Fetched to disk ahead too, so the logo decodes in time for the copy's fade-in.
            content.logoUrl?.takeIf { it.isNotBlank() }?.let { url ->
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

/** Whether the hero's button can start this item, rather than open it. */
internal fun SectionItem.isTvHeroPlayable(): Boolean =
    positionSeconds != null || type.lowercase() in HeroPlayableTypes

private val HeroPlayableTypes = setOf("movie", "episode", "video", "audiobook")
