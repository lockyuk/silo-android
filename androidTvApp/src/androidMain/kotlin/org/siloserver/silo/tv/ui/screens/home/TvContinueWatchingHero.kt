package org.siloserver.silo.tv.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
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
private const val HeroDwellMillis = 8_000

/** The crossfade between two items. */
private const val HeroFadeMillis = 2_200

/** Eases the crossfade in and out, so it starts and settles gently. */
private val HeroEasing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

/** How far the slow zoom goes over an item's whole time on screen. */
private const val HeroZoom = 1.08f

/**
 * Continue Watching as a Netflix-style hero, laid out like the phone's: under
 * the row's title, a rounded card shows the episode's still, or a movie's
 * backdrop, behind its logo, episode, synopsis, progress and buttons, with
 * the item dots below. Every eight seconds it crossfades to the next item
 * with a slow zoom. Resume starts playback; Details opens the show. Left from Resume and Right from
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
    modifier: Modifier = Modifier,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = HeroSideMargin, end = HeroSideMargin, top = 24.dp)
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
        Text(
            text = section.title.ifBlank { "Continue Watching" },
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        // The card: the picture, faded to black behind the details, with
        // rounded corners as on the phone.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
        ) {
            AnimatedContent(
                targetState = items[index],
                // The next picture fades in over the current one, which stays
                // fully drawn underneath until it is covered, so the picture
                // never dims mid-way.
                transitionSpec = {
                    (fadeIn(tween(HeroFadeMillis, easing = HeroEasing)) togetherWith
                        fadeOut(tween(200, delayMillis = HeroFadeMillis)))
                        .apply { targetContentZIndex = 1f }
                },
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
                            0f to Color.Black.copy(alpha = 0.85f),
                            0.45f to Color.Black.copy(alpha = 0.45f),
                            0.75f to Color.Transparent,
                        ),
                    ),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black)),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 32.dp, end = 32.dp, bottom = 28.dp),
            ) {
                AnimatedContent(
                    targetState = items[index],
                    // The old text drifts up and away first; the new text rises
                    // into place over the second half of the crossfade.
                    transitionSpec = {
                        (
                            fadeIn(tween(HeroFadeMillis / 2, delayMillis = HeroFadeMillis / 2, easing = HeroEasing)) +
                                slideInVertically(tween(HeroFadeMillis / 2, delayMillis = HeroFadeMillis / 2, easing = HeroEasing)) { it / 10 }
                            ) togetherWith (
                            fadeOut(tween(HeroFadeMillis * 2 / 5, easing = HeroEasing)) +
                                slideOutVertically(tween(HeroFadeMillis * 2 / 5, easing = HeroEasing)) { -it / 14 }
                            ) using SizeTransform(clip = false)
                    },
                    contentKey = { it.contentId },
                    contentAlignment = Alignment.BottomStart,
                    label = "hero-details",
                ) { item ->
                    HeroText(item)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .then(if (buttonsFocusRequester != null) Modifier.focusRequester(buttonsFocusRequester) else Modifier)
                        .focusGroup(),
                ) {
                    val item = items[index]
                    Button(
                        onClick = { onResume(item) },
                        colors = heroButtonColors(),
                        shape = heroButtonShape(),
                        scale = ButtonDefaults.scale(focusedScale = 1.04f),
                        contentPadding = HeroButtonPadding,
                        modifier = Modifier
                            .widthIn(min = HeroButtonMinWidth)
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
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if ((item.positionSeconds ?: 0.0) > 0) "Resume" else "Play", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onDetails(item) },
                        colors = heroButtonColors(),
                        shape = heroButtonShape(),
                        scale = ButtonDefaults.scale(focusedScale = 1.04f),
                        contentPadding = HeroButtonPadding,
                        modifier = Modifier
                            .widthIn(min = HeroButtonMinWidth)
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
                        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Details", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // The dots sit under the card, centred, as on the phone; with a
        // single item the space stays so the card keeps its size.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
        ) {
            if (items.size > 1) {
                HeroIndicators(count = items.size, current = index, progress = { dwell.value })
            }
        }
    }
}

/** Matches the rows' left edge, so the hero lines up with the peek below it. */
private val HeroSideMargin = 58.dp

private val HeroButtonMinWidth = 190.dp

private val HeroButtonPadding = PaddingValues(horizontal = 26.dp, vertical = 11.dp)

@Composable
private fun heroButtonShape() = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))

/**
 * As on the phone, Resume is solid white and Details translucent grey; on the
 * TV that follows the focus, so the button the remote is on is the white one.
 */
@Composable
private fun heroButtonColors() = ButtonDefaults.colors(
    containerColor = Color.White.copy(alpha = 0.2f),
    contentColor = Color.White,
    focusedContainerColor = Color.White,
    focusedContentColor = Color.Black,
)

@Composable
private fun HeroBackdrop(item: SectionItem) {
    val artwork = rememberHeroArtwork(item)
    val zoom = remember(item.contentId) { Animatable(1f) }
    LaunchedEffect(item.contentId) {
        zoom.animateTo(HeroZoom, tween(HeroDwellMillis + HeroFadeMillis * 2, easing = LinearEasing))
    }
    // Each item drifts one way or the other as it zooms.
    val drift = if (item.contentId.hashCode() and 1 == 0) 1f else -1f
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
                    translationX = (zoom.value - 1f) * size.width * 0.3f * drift
                },
        )
    }
}

@Composable
private fun HeroText(item: SectionItem) {
    val artwork = rememberHeroArtwork(item)
    val isEpisode = item.type.equals("episode", ignoreCase = true)
    val showTitle = if (isEpisode) item.seriesTitle?.takeIf { it.isNotBlank() } ?: item.title else item.title
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 520.dp)) {
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
                modifier = Modifier.sizeIn(maxWidth = 340.dp, maxHeight = 96.dp),
            )
        } else {
            Text(showTitle, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        heroSubtitle(item)?.let {
            Text(it, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        item.overview?.trim()?.takeIf { it.isNotEmpty() }?.let {
            Text(it, fontSize = 15.sp, lineHeight = 21.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                .width(380.dp)
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
