package org.siloserver.silo.android.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.CatalogRepository
import org.siloserver.silo.model.section.ResolvedSection
import org.siloserver.silo.model.section.SectionItem
import kotlin.math.absoluteValue

/** How long each item stays before the hero moves to the next. */
private const val HeroDwellMillis = 8_000L

/** How long the hero takes to glide to the next item. */
private const val HeroTransitionMillis = 2_200

/** Eases the crossfade in and out, so it starts and settles gently. */
private val HeroEasing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

/** How far the slow zoom goes over an item's whole time on screen. */
private const val HeroZoom = 1.1f

/**
 * Continue Watching as a Netflix-style hero: one large backdrop at a time,
 * moving on to the next item every seven seconds with a crossfade and a slow
 * zoom. Under the title sit the synopsis, how far through it is, and Resume,
 * which starts playback straight from the hero. Swiping pauses the timer
 * until the swipe ends.
 */
@Composable
internal fun ContinueWatchingHero(
    section: ResolvedSection,
    onOpen: (SectionItem) -> Unit,
    onResume: (SectionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = section.items
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    // The bar under the dots fills over each item's dwell time.
    val dwell = remember { Animatable(0f) }

    LaunchedEffect(pagerState.settledPage, dragged, items.size) {
        dwell.snapTo(0f)
        if (dragged || items.size < 2) return@LaunchedEffect
        dwell.animateTo(1f, tween(HeroDwellMillis.toInt(), easing = LinearEasing))
        delay(50)
        val next = (pagerState.settledPage + 1) % items.size
        pagerState.animateScrollToPage(next, animationSpec = tween(HeroTransitionMillis, easing = HeroEasing))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = section.title.ifBlank { "Continue Watching" },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
        HorizontalPager(
            state = pagerState,
            key = { items[it].contentId },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val item = items[page]
            // 0 when this page is centred, 1 when it is a full page away.
            val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            val settled = pagerState.settledPage
            val target = pagerState.targetPage
            // The incoming page fades in over the outgoing one, which stays
            // fully drawn underneath, so the picture never dims mid-way.
            val imageAlpha = if (page == settled && target != settled) 1f else 1f - offset
            HeroPage(
                item = item,
                offset = offset,
                imageAlpha = imageAlpha,
                // The old text is gone by half-way and the new text appears
                // over the second half, so the two never overlap.
                textAlpha = (1f - 2f * offset).coerceIn(0f, 1f),
                // Pages overlap during the crossfade; only the one in front
                // takes taps, so a tap never opens the item underneath.
                interactive = page == target,
                // The outgoing page keeps drifting while it is covered, and
                // the incoming one starts as it appears.
                zooming = (page == settled || page == target) && !dragged,
                onOpen = { onOpen(item) },
                onResume = { onResume(item) },
                modifier = Modifier
                    .zIndex(if (page == target) 1f else 0f)
                    .graphicsLayer {
                        // Crossfade rather than slide: the pages stack, with
                        // a slight drift that follows a swipe.
                        translationX = size.width * (pagerState.currentPage - page + pagerState.currentPageOffsetFraction) * 0.96f
                        alpha = imageAlpha
                    },
            )
        }
        if (items.size > 1) {
            HeroIndicators(
                count = items.size,
                current = pagerState.currentPage,
                progress = dwell.value,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun HeroPage(
    item: SectionItem,
    offset: Float,
    imageAlpha: Float,
    textAlpha: Float,
    interactive: Boolean,
    zooming: Boolean,
    onOpen: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artwork = rememberHeroArtwork(item)
    val zoom = remember(item.contentId) { Animatable(1f) }
    val visible by rememberUpdatedState(imageAlpha > 0.001f)
    LaunchedEffect(zooming) {
        if (zooming) {
            zoom.animateTo(HeroZoom, tween(HeroDwellMillis.toInt() + HeroTransitionMillis * 2, easing = LinearEasing))
        } else {
            // Back to the resting framing only once nobody can see it.
            snapshotFlow { visible }.first { !it }
            zoom.snapTo(1f)
        }
    }
    // Each item drifts one way or the other as it zooms.
    val drift = if (item.contentId.hashCode() and 1 == 0) 1f else -1f
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .then(if (interactive) Modifier.clickable(onClick = onOpen) else Modifier),
    ) {
        // The backdrop keeps close to its own 16:9 shape, so a 1920-pixel
        // image stays sharp on a phone; the details sit below it as it
        // fades into black.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
        ) {
            if (artwork.url != null) {
                ThumbhashImage(
                    url = artwork.url,
                    thumbhash = artwork.thumbhash,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    crossfadeMillis = 500,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom.value
                            scaleY = zoom.value
                            translationX = (zoom.value - 1f) * size.width * 0.3f * drift
                        },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            1f to Color.Black,
                        ),
                    ),
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f * 1.55f),
            )
            HeroDetails(
                item = item,
                interactive = interactive,
                onOpen = onOpen,
                onResume = onResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .graphicsLayer {
                        alpha = textAlpha
                        translationY = offset * 28.dp.toPx()
                    },
            )
        }
    }
}

/** Artwork for the hero: the episode's still, or the title's backdrop. */
private data class HeroArtwork(val url: String?, val thumbhash: String?)

/**
 * An episode draws its own still, which Continue Watching carries at 1280
 * wide. A movie, or an episode without a still, draws the title's own
 * backdrop: the detail is read (cached and shared with Home's warm-up) for
 * it, and the item's own picture stands in until it arrives.
 */
@Composable
private fun rememberHeroArtwork(item: SectionItem): HeroArtwork {
    val catalog: CatalogRepository = koinInject()
    val fallback = HeroArtwork(item.backdropUrl ?: item.posterUrl, item.backdropThumbhash ?: item.posterThumbhash)
    val detailId = heroBackdropDetailId(item)
    val artwork by produceState(fallback, item.contentId, detailId) {
        value = fallback
        if (detailId == null) return@produceState
        val detail = (catalog.warmItemDetail(detailId) as? ApiResult.Success)?.data ?: return@produceState
        val url = detail.backdropUrl?.takeIf { it.isNotBlank() } ?: return@produceState
        value = HeroArtwork(url, detail.backdropThumbhash ?: fallback.thumbhash)
    }
    return artwork
}

/**
 * The item whose detail holds the hero's backdrop: none for an episode with its
 * own still, the show for an episode without one, the movie itself otherwise.
 */
private fun heroBackdropDetailId(item: SectionItem): String? {
    if (!item.type.equals("episode", ignoreCase = true)) return item.contentId
    if (item.backdropIsEpisodeStill == true && !item.backdropUrl.isNullOrBlank()) return null
    return item.seriesId?.takeIf { it.isNotBlank() }
}

@Composable
private fun HeroDetails(
    item: SectionItem,
    interactive: Boolean,
    onOpen: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEpisode = item.type.equals("episode", ignoreCase = true)
    val showTitle = if (isEpisode) item.seriesTitle?.takeIf { it.isNotBlank() } ?: item.title else item.title
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val logo = item.logoUrl
        if (logo != null) {
            ThumbhashImage(
                url = logo,
                thumbhash = null,
                contentDescription = showTitle,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                transparent = true,
                // Sized to the logo's own shape, so it starts at the text's edge.
                modifier = Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 72.dp),
            )
        } else {
            Text(
                text = showTitle,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        heroSubtitle(item)?.let {
            Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        item.overview?.trim()?.takeIf { it.isNotEmpty() }?.let {
            Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = Color.White.copy(alpha = 0.78f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        HeroProgress(item)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { if (interactive) onResume() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(if ((item.positionSeconds ?: 0.0) > 0) "Resume" else "Play", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            FilledTonalButton(
                onClick = { if (interactive) onOpen() },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Details", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
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

/** How far through the item is, and how long is left. */
@Composable
private fun HeroProgress(item: SectionItem) {
    val position = item.positionSeconds ?: return
    val duration = item.durationSeconds?.takeIf { it > 0 } ?: return
    val fraction = (position / duration).toFloat().coerceIn(0f, 1f)
    val remaining = ((duration - position) / 60).toInt().coerceAtLeast(1)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.28f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(Color(0xFFE50914)),
            )
        }
        Text(
            text = if (remaining >= 60) "${remaining / 60}h ${remaining % 60}m left" else "${remaining}m left",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

/** A dot per item; the current one stretches and fills over its dwell time. */
@Composable
private fun HeroIndicators(count: Int, current: Int, progress: Float, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count.coerceAtMost(12)) { index ->
            if (index == current) {
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(lerp(0.15f, 1f, progress))
                            .height(6.dp)
                            .background(Color.White),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.35f)),
                )
            }
        }
    }
}
