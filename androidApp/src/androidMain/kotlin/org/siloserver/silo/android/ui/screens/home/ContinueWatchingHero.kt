package org.siloserver.silo.android.ui.screens.home

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.delay
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.section.ResolvedSection
import org.siloserver.silo.model.section.SectionItem
import kotlin.math.absoluteValue

/** How long each item stays before the hero moves to the next. */
private const val HeroDwellMillis = 7_000L

/** How long the hero takes to glide to the next item. */
private const val HeroTransitionMillis = 1_100

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
        pagerState.animateScrollToPage(next, animationSpec = tween(HeroTransitionMillis))
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
            HeroPage(
                item = item,
                offset = offset,
                // Only the page on screen drifts, so a page returns to its
                // resting framing before it comes back.
                zooming = page == pagerState.settledPage && !dragged,
                onOpen = { onOpen(item) },
                onResume = { onResume(item) },
                modifier = Modifier.graphicsLayer {
                    // Crossfade rather than slide: the outgoing page holds its
                    // place and fades while the next fades in over it.
                    translationX = size.width * (pagerState.currentPage - page + pagerState.currentPageOffsetFraction) * 0.85f
                    alpha = 1f - offset
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
    zooming: Boolean,
    onOpen: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoom = remember(item.contentId) { Animatable(1f) }
    LaunchedEffect(zooming) {
        if (zooming) {
            zoom.animateTo(1.08f, tween(HeroDwellMillis.toInt() + HeroTransitionMillis, easing = LinearEasing))
        } else {
            zoom.animateTo(1f, tween(600))
        }
    }
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF15171C))
            .clickable(onClick = onOpen),
    ) {
        val artwork = item.backdropUrl ?: item.posterUrl
        if (artwork != null) {
            ThumbhashImage(
                url = artwork,
                thumbhash = item.backdropThumbhash ?: item.posterThumbhash,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = zoom.value + offset * 0.04f
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
        // Darken the lower half so the text reads on any artwork.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Transparent,
                        0.72f to Color.Black.copy(alpha = 0.72f),
                        1f to Color.Black.copy(alpha = 0.94f),
                    ),
                ),
        )
        HeroDetails(
            item = item,
            onOpen = onOpen,
            onResume = onResume,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp)
                .graphicsLayer { translationY = offset * 40f },
        )
    }
}

@Composable
private fun HeroDetails(
    item: SectionItem,
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
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .heightIn(max = 72.dp),
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
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(if ((item.positionSeconds ?: 0.0) > 0) "Resume" else "Play", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            FilledTonalButton(
                onClick = onOpen,
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
