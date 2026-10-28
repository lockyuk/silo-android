package org.siloserver.silo.tv.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.imageLoader
import coil3.request.ImageRequest
import org.koin.compose.koinInject
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.section.ResolvedSection
import org.siloserver.silo.model.section.SectionItem
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.CatalogRepository
import org.siloserver.silo.tv.ui.focus.TvContentInitialFocusMaxAttempts
import org.siloserver.silo.tv.ui.focus.TvObservedFocusResult
import org.siloserver.silo.tv.ui.focus.requestFocusUntilObserved

/** How long each item stays before the hero moves to the next. */
private const val HeroDwellMillis = 7_000

/** The crossfade between two items. */
private const val HeroFadeMillis = 1_000

/**
 * Continue Watching as a full-screen, Netflix-style hero: the episode's still,
 * or a movie's backdrop, fills the screen behind its logo, episode, synopsis
 * and progress. Every seven seconds it crossfades to the next item with a slow zoom. Resume
 * starts playback; Details opens the show. Left from Resume and Right from
 * Details step through the items; Down leaves for the rows below.
 */
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
    // Keep the next backdrop in the cache so the crossfade never waits.
    PrefetchHeroArtwork(items[(index + 1) % items.size])

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
        AnimatedContent(
            targetState = items[index],
            transitionSpec = { fadeIn(tween(HeroFadeMillis)) togetherWith fadeOut(tween(HeroFadeMillis)) },
            contentKey = { it.contentId },
            label = "hero",
        ) { item ->
            HeroBackdrop(item)
        }
        // Scrims: darken the left and the bottom for the text.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.88f),
                        0.42f to Color.Black.copy(alpha = 0.55f),
                        0.75f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
        )

        AnimatedContent(
            targetState = items[index],
            transitionSpec = { fadeIn(tween(HeroFadeMillis / 2, delayMillis = HeroFadeMillis / 3)) togetherWith fadeOut(tween(HeroFadeMillis / 3)) },
            contentKey = { it.contentId },
            label = "hero-details",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 58.dp, bottom = 96.dp),
        ) { item ->
            HeroText(item)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 58.dp, bottom = 40.dp)
                .then(if (buttonsFocusRequester != null) Modifier.focusRequester(buttonsFocusRequester) else Modifier)
                .focusGroup(),
        ) {
            val item = items[index]
            Button(
                onClick = { onResume(item) },
                colors = heroButtonColors(),
                modifier = Modifier
                    .focusRequester(resume)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && items.size > 1) {
                            step(-1)
                            true
                        } else {
                            false
                        }
                    },
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(if ((item.positionSeconds ?: 0.0) > 0) "Resume" else "Play", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { onDetails(item) },
                colors = heroButtonColors(),
                modifier = Modifier
                    .focusRequester(details)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight && items.size > 1) {
                            step(1)
                            true
                        } else {
                            false
                        }
                    },
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Details", fontWeight = FontWeight.SemiBold)
            }
        }

        if (items.size > 1) {
            HeroIndicators(
                count = items.size,
                current = index,
                progress = dwell.value,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 58.dp, bottom = 52.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            Text("More to watch", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun heroButtonColors() = ButtonDefaults.colors(
    containerColor = Color.White.copy(alpha = 0.18f),
    contentColor = Color.White,
    focusedContainerColor = Color.White,
    focusedContentColor = Color.Black,
)

@Composable
private fun HeroBackdrop(item: SectionItem) {
    val artwork = rememberHeroArtwork(item)
    val zoom = remember(item.contentId) { Animatable(1f) }
    LaunchedEffect(item.contentId) {
        zoom.animateTo(1.07f, tween(HeroDwellMillis + HeroFadeMillis * 2, easing = LinearEasing))
    }
    if (artwork.backdrop != null) {
        ThumbhashImage(
            url = artwork.backdrop,
            thumbhash = artwork.thumbhash,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            crossfadeMillis = 400,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.value
                    scaleY = zoom.value
                },
        )
    }
}

@Composable
private fun HeroText(item: SectionItem) {
    val artwork = rememberHeroArtwork(item)
    val isEpisode = item.type.equals("episode", ignoreCase = true)
    val showTitle = if (isEpisode) item.seriesTitle?.takeIf { it.isNotBlank() } ?: item.title else item.title
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 520.dp)) {
        Text("CONTINUE WATCHING", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f), letterSpacing = 2.sp)
        val logo = artwork.logo
        if (logo != null) {
            ThumbhashImage(
                url = logo,
                thumbhash = null,
                contentDescription = showTitle,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                transparent = true,
                // Sized to the logo's own shape, so it starts at the text's edge.
                modifier = Modifier.sizeIn(maxWidth = 380.dp, maxHeight = 130.dp),
            )
        } else {
            Text(showTitle, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        heroSubtitle(item)?.let {
            Text(it, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        item.overview?.trim()?.takeIf { it.isNotEmpty() }?.let {
            Text(it, fontSize = 15.sp, lineHeight = 21.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        HeroProgress(item)
    }
}

/** "S2:E4 · The Way Out", or the year and genre for a movie. */
private fun heroSubtitle(item: SectionItem): String? {
    if (item.type.equals("episode", ignoreCase = true)) {
        val code = if (item.seasonNumber != null && item.episodeNumber != null) "S${item.seasonNumber}:E${item.episodeNumber}" else null
        return listOfNotNull(code, item.title.takeIf { it.isNotBlank() && it != item.seriesTitle }).joinToString(" · ").ifBlank { null }
    }
    return listOfNotNull(item.year.takeIf { it > 0 }?.toString(), item.genres.firstOrNull()).joinToString(" · ").ifBlank { null }
}

@Composable
private fun HeroProgress(item: SectionItem) {
    val position = item.positionSeconds ?: return
    val duration = item.durationSeconds?.takeIf { it > 0 } ?: return
    val fraction = (position / duration).toFloat().coerceIn(0f, 1f)
    val remaining = ((duration - position) / 60).toInt().coerceAtLeast(1)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .width(300.dp)
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
        )
    }
}

@Composable
private fun HeroIndicators(count: Int, current: Int, progress: Float, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count.coerceAtMost(12)) { i ->
            if (i == current) {
                Box(
                    Modifier
                        .width(26.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.15f + 0.85f * progress)
                            .height(6.dp)
                            .background(Color.White),
                    )
                }
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

private data class HeroArtwork(val backdrop: String?, val thumbhash: String?, val logo: String?)

/**
 * An episode draws its own still, which Home asks for at hero width, and the
 * show's logo; a movie, or an episode without a still, the title's own
 * full-size backdrop. The show's or movie's detail is read for the logo and
 * backdrop (cached and shared with the rest of Home); the item's own picture
 * stands in until it arrives.
 */
@Composable
private fun rememberHeroArtwork(item: SectionItem): HeroArtwork {
    val catalog: CatalogRepository = koinInject()
    val fallback = HeroArtwork(item.backdropUrl ?: item.posterUrl, item.backdropThumbhash ?: item.posterThumbhash, item.logoUrl)
    val detailId = heroDetailId(item)
    val episodeStill = item.type.equals("episode", ignoreCase = true) &&
        item.backdropIsEpisodeStill == true && !item.backdropUrl.isNullOrBlank()
    val artwork by produceState(fallback, item.contentId, detailId) {
        value = fallback
        if (detailId == null) return@produceState
        val detail = (catalog.warmItemDetail(detailId) as? ApiResult.Success)?.data ?: return@produceState
        value = HeroArtwork(
            backdrop = if (episodeStill) fallback.backdrop else detail.backdropUrl?.takeIf { it.isNotBlank() } ?: fallback.backdrop,
            thumbhash = if (episodeStill) fallback.thumbhash else detail.backdropThumbhash ?: fallback.thumbhash,
            logo = detail.logoUrl?.takeIf { it.isNotBlank() } ?: fallback.logo,
        )
    }
    return artwork
}

private fun heroDetailId(item: SectionItem): String? =
    if (item.type.equals("episode", ignoreCase = true)) item.seriesId?.takeIf { it.isNotBlank() } else item.contentId

@Composable
private fun PrefetchHeroArtwork(item: SectionItem) {
    val context = LocalContext.current
    val artwork = rememberHeroArtwork(item)
    LaunchedEffect(artwork.backdrop) {
        val url = artwork.backdrop ?: return@LaunchedEffect
        context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
    }
}
