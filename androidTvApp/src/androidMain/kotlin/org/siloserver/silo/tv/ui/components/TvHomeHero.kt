package org.siloserver.silo.tv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.siloserver.silo.common.settings.titleLogoUrl
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.section.ResolvedSection
import org.siloserver.silo.model.section.SectionItem

/**
 * The Home hero: the first section of the user's Home order, drawn as a large
 * billboard above the rows. The rows below start at the NEXT section, so the
 * hero's items never repeat there.
 */
class TvHomeHeroFeed(
    val section: ResolvedSection,
    val onPlay: (SectionItem) -> Unit,
    val onMoreInfo: (SectionItem) -> Unit,
    val onToggleWatchlist: (SectionItem, Boolean) -> Unit,
)

/** How many of the section's items the hero rotates through (one pager dot each). */
internal const val TvHeroMaxItems = 10

/** How long each hero item shows before the next one. */
internal const val TvHeroRotateMs = 7_000L

// The billboard sits on the 960x540dp TV layout: information bottom-left over a
// full-bleed backdrop, the rows starting beneath it.
internal val TvHeroLeft = 47.dp
internal val TvHeroTop = 114.dp
internal val TvHeroDotsTop = 422.dp


/**
 * Where the first row's header sits while the hero shows: low enough that only
 * its title text is on screen and its cards are below the bottom edge. Fixed,
 * so every library's hero sits in the same place whatever its card size.
 */
internal val TvHeroRowHeaderTop = 496.dp

/** How far below the row band's top edge a row's header text starts. */
internal val TvHeroRowHeaderInset = 8.dp

private val HeroAccent = Color(0xFF3AA0FF)
private val HeroText = Color(0xFFF4F6FA)
private val HeroMuted = Color(0xFFC3C9D6)
private val HeroSynopsis = Color(0xFFDFE4ED)
private val HeroRating = Color(0xFF7FE0A1)
private val HeroChipBorder = Color(0xFF8A93A6)
private val HeroInk = Color(0xFF0B0D12)

/** Height of everything above the buttons, so crossfading between items never moves them. */
private val HeroInfoHeight = 244.dp

/**
 * The hero's content for [content]: eyebrow, clear logo, meta line, synopsis
 * and resume line, then the Play / More info / watchlist buttons. The buttons
 * stay put while the information above them crossfades between items.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun TvHomeHeroPanel(
    content: TvMarqueeContent?,
    eyebrow: String,
    playLabel: String,
    inWatchlist: Boolean,
    playFocusRequester: FocusRequester,
    onFocusedChange: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onMoreInfo: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onDown: () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Every part sits at fixed screen coordinates (dp on the 960 x 540 layout), the same
    // on Home, For You and every library, so nothing moves between pages or items.
    Box(modifier = modifier.fillMaxSize()) {
        Crossfade(
            targetState = content,
            animationSpec = tween(350),
            label = "tvHomeHero",
            modifier = Modifier.fillMaxSize(),
        ) { value ->
            if (value != null) HeroInfo(value, eyebrow)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .offset(x = 47.dp, y = 366.dp)
                // Entering the buttons from the menu or the row always lands on
                // Play, whichever button is nearest.
                .focusProperties { enter = { playFocusRequester } }
                .focusGroup()
                .onFocusChanged { onFocusedChange(it.hasFocus) }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        onDown()
                        true
                    } else {
                        false
                    }
                },
        ) {
            // Focus is only ever on Resume or More info. Left from Resume goes to
            // the previous item and Right from More info to the next one; the
            // arrows at the screen edges show where each goes.
            HeroPill(
                label = playLabel,
                icon = Icons.Filled.PlayArrow,
                primary = true,
                onClick = onPlay,
                modifier = Modifier
                    .focusRequester(playFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && onPrevious != null) {
                            onPrevious()
                            true
                        } else {
                            false
                        }
                    },
            )
            HeroPill(
                label = "More info",
                icon = Icons.Outlined.Info,
                primary = false,
                onClick = onMoreInfo,
                modifier = Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight && onNext != null) {
                        onNext()
                        true
                    } else {
                        false
                    }
                },
            )
        }
    }
}

@Composable
private fun HeroInfo(content: TvMarqueeContent, eyebrow: String) {
    val item = content.source
    Box(Modifier.fillMaxSize()) {
        // Eyebrow: the source section's name.
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.offset(x = 47.dp, y = 85.dp),
        ) {
            Box(Modifier.size(width = 3.dp, height = 13.dp).background(HeroAccent, RoundedCornerShape(2.dp)))
            Text(
                text = eyebrow.uppercase(),
                color = HeroMuted,
                fontSize = 11.sp,
                letterSpacing = 1.54.sp,
                maxLines = 1,
            )
        }

        Box(Modifier.offset(x = 49.dp, y = 102.dp)) { HeroLogo(content) }

        Box(Modifier.offset(x = 48.dp, y = 172.dp)) { HeroRatings(item) }

        content.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
            Text(
                text = synopsis,
                color = HeroSynopsis,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset(x = 49.dp, y = 220.dp).widthIn(max = 400.dp),
            )
        }

        Box(Modifier.offset(x = 47.dp, y = 298.dp)) { HeroResume(item) }
    }
}

/** The clear logo in a 320x95dp slot; the title in text if there is none or it fails. */
@Composable
private fun HeroLogo(content: TvMarqueeContent) {
    val logoUrl = titleLogoUrl(content.logoUrl)
    var failed by remember(logoUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier.width(272.dp).height(67.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (logoUrl.isNullOrBlank() || failed) {
            Text(
                text = content.title,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 32.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            ThumbhashImage(
                url = logoUrl,
                thumbhash = null,
                contentDescription = content.title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                transparent = true,
                crossfadeMillis = 0,
                onSuccess = {},
                onError = { failed = true },
                modifier = Modifier.fillMaxHeight().widthIn(max = 272.dp),
            )
        }
    }
}

/** Rating (green), year, certificate chip, length or season, and a resolution chip. */
@Composable
private fun HeroMeta(content: TvMarqueeContent, item: SectionItem) {
    val rating = item.ratingImdb ?: item.ratingTmdb
    val ratingLabel = rating?.let { String.format("%.1f %s", it, if (item.ratingImdb != null) "IMDb" else "TMDB") }
    val certificate = item.contentRating?.takeIf { it.isNotBlank() } ?: content.badges.firstOrNull()
    val length = when {
        item.seasonNumber != null -> "Season ${item.seasonNumber}"
        else -> item.runtime?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" }
    }
    val resolution = content.specLine?.split(" · ")?.firstOrNull { it.isNotBlank() }?.uppercase()
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.year.takeIf { it > 0 }?.let { Text(it.toString(), color = HeroMuted, fontSize = 13.sp, maxLines = 1) }
        certificate?.let { HeroChip(it) }
        length?.let { Text(it, color = HeroMuted, fontSize = 13.sp, maxLines = 1) }
        resolution?.let { HeroChip(it) }
    }
}

@Composable
private fun HeroChip(text: String) {
    Text(
        text = text,
        color = HeroMuted,
        fontSize = 11.sp,
        maxLines = 1,
        modifier = Modifier
            .border(1.dp, HeroChipBorder, RoundedCornerShape(2.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** "S3:E7  The Matador  ▬▬▬  21m left" for an item that has been started. */
@Composable
private fun HeroResume(item: SectionItem) {
    val position = item.positionSeconds ?: return
    val duration = item.durationSeconds?.takeIf { it > 0.0 } ?: return
    val fraction = (position / duration).toFloat().coerceIn(0f, 1f)
    val minutes = ((duration - position) / 60.0).toInt().coerceAtLeast(1)
    val left = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m left" else "${minutes}m left"
    val code = if (item.seasonNumber != null && item.episodeNumber != null) {
        "S${item.seasonNumber}:E${item.episodeNumber}"
    } else {
        null
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        code?.let { Text(it, color = HeroText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        if (code != null) {
            Text(item.title, color = HeroMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
        }
        Box(Modifier.width(130.dp).height(3.dp).background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(HeroAccent, RoundedCornerShape(2.dp)))
        }
        Text(left, color = HeroMuted, fontSize = 12.sp, maxLines = 1)
    }
}

/** A hero pill: Resume/Play (white) or More info (translucent); focus brightens it and adds the accent ring. */
@Composable
private fun HeroPill(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "heroPillScale")
    val shape = RoundedCornerShape(6.dp)
    val light = primary || focused
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.border(3.dp, HeroAccent.copy(alpha = 0.55f), shape) else Modifier)
            .background(
                when {
                    focused -> HeroText
                    primary -> HeroText.copy(alpha = 0.86f)
                    else -> Color.White.copy(alpha = 0.16f)
                },
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = if (primary) 28.dp else 22.dp, vertical = 11.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (light) HeroInk else HeroText, modifier = Modifier.size(19.dp))
        Text(
            text = label,
            color = if (light) HeroInk else HeroText,
            fontSize = 17.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Individual scores on one line: IMDb and TMDB as badges, Rotten Tomatoes critics
 * as a fresh tomato (60% and over) or a green splat, and the audience score as a
 * full popcorn bucket (60% and over) or a spilled one. A source with no score is left out.
 */
@Composable
private fun HeroRatings(item: SectionItem) {
    val imdb = item.ratingImdb
    val tmdb = item.ratingTmdb
    val critic = item.ratingRtCritic
    val audience = item.ratingRtAudience
    if (imdb == null && tmdb == null && critic == null && audience == null) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        imdb?.let { HeroScoreChip(badge = "IMDb", badgeColor = Color(0xFFF5C518), value = String.format("%.1f", it)) }
        tmdb?.let { HeroScoreChip(badge = "TMDB", badgeColor = Color(0xFF01B4E4), value = "${Math.round(it * 10)}%") }
        critic?.let { HeroScoreChip(icon = if (it >= 60) HeroScoreIcon.Tomato else HeroScoreIcon.Splat, value = "$it%", caption = "Critics") }
        audience?.let { HeroScoreChip(icon = if (it >= 60) HeroScoreIcon.Popcorn else HeroScoreIcon.Spilled, value = "$it%", caption = "Audience") }
    }
}

private enum class HeroScoreIcon { Tomato, Splat, Popcorn, Spilled }

@Composable
private fun HeroScoreChip(
    value: String,
    badge: String? = null,
    badgeColor: Color = Color.Unspecified,
    icon: HeroScoreIcon? = null,
    caption: String? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        if (badge != null) {
            Text(
                text = badge,
                color = Color(0xFF10151C),
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier.background(badgeColor, RoundedCornerShape(2.dp)).padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
        if (icon != null) HeroScoreGlyph(icon)
        Text(value, color = HeroText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        caption?.let { Text(it, color = HeroMuted, fontSize = 8.sp, maxLines = 1) }
    }
}

@Composable
private fun HeroScoreGlyph(icon: HeroScoreIcon) {
    androidx.compose.foundation.Canvas(Modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        val red = Color(0xFFFA320A)
        val green = Color(0xFF3FA535)
        when (icon) {
            HeroScoreIcon.Tomato -> {
                drawCircle(red, radius = w * 0.36f, center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.58f))
                val leaf = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.5f, h * 0.26f); lineTo(w * 0.38f, h * 0.1f); lineTo(w * 0.42f, h * 0.28f)
                    lineTo(w * 0.27f, h * 0.3f); lineTo(w * 0.42f, h * 0.38f); lineTo(w * 0.5f, h * 0.34f)
                    lineTo(w * 0.58f, h * 0.38f); lineTo(w * 0.73f, h * 0.3f); lineTo(w * 0.58f, h * 0.28f)
                    lineTo(w * 0.62f, h * 0.1f); close()
                }
                drawPath(leaf, green)
            }
            HeroScoreIcon.Splat -> {
                val splat = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.5f, h * 0.08f); lineTo(w * 0.6f, h * 0.28f); lineTo(w * 0.78f, h * 0.17f)
                    lineTo(w * 0.74f, h * 0.38f); lineTo(w * 0.95f, h * 0.35f); lineTo(w * 0.8f, h * 0.52f)
                    lineTo(w * 0.93f, h * 0.68f); lineTo(w * 0.72f, h * 0.7f); lineTo(w * 0.78f, h * 0.9f)
                    lineTo(w * 0.58f, h * 0.8f); lineTo(w * 0.5f, h * 0.95f); lineTo(w * 0.4f, h * 0.8f)
                    lineTo(w * 0.2f, h * 0.9f); lineTo(w * 0.26f, h * 0.7f); lineTo(w * 0.07f, h * 0.68f)
                    lineTo(w * 0.2f, h * 0.52f); lineTo(w * 0.05f, h * 0.35f); lineTo(w * 0.26f, h * 0.38f)
                    lineTo(w * 0.22f, h * 0.17f); lineTo(w * 0.4f, h * 0.28f); close()
                }
                drawPath(splat, Color(0xFF6FBF4A))
                drawCircle(Color(0xFF2D6A1F), radius = w * 0.045f, center = androidx.compose.ui.geometry.Offset(w * 0.4f, h * 0.48f))
                drawCircle(Color(0xFF2D6A1F), radius = w * 0.045f, center = androidx.compose.ui.geometry.Offset(w * 0.6f, h * 0.48f))
            }
            HeroScoreIcon.Popcorn -> {
                listOf(0.31f to 0.3f, 0.5f to 0.22f, 0.69f to 0.3f, 0.41f to 0.38f, 0.59f to 0.38f).forEach { (x, y) ->
                    drawCircle(Color.White, radius = w * 0.13f, center = androidx.compose.ui.geometry.Offset(w * x, h * y))
                }
                val bucket = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.23f, h * 0.4f); lineTo(w * 0.77f, h * 0.4f); lineTo(w * 0.7f, h * 0.93f); lineTo(w * 0.3f, h * 0.93f); close()
                }
                drawPath(bucket, red)
                listOf(0.38f, 0.5f, 0.62f).forEach { x ->
                    drawLine(Color.White, androidx.compose.ui.geometry.Offset(w * x, h * 0.42f), androidx.compose.ui.geometry.Offset(w * x, h * 0.92f), strokeWidth = w * 0.06f)
                }
            }
            HeroScoreIcon.Spilled -> {
                listOf(0.2f to 0.8f, 0.37f to 0.85f, 0.54f to 0.83f, 0.29f to 0.68f).forEach { (x, y) ->
                    drawCircle(Color.White, radius = w * 0.09f, center = androidx.compose.ui.geometry.Offset(w * x, h * y))
                }
                rotate(-62f, androidx.compose.ui.geometry.Offset(w * 0.62f, h * 0.42f)) {
                    val bucket = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.42f, h * 0.2f); lineTo(w * 0.83f, h * 0.2f); lineTo(w * 0.77f, h * 0.67f); lineTo(w * 0.48f, h * 0.67f); close()
                    }
                    drawPath(bucket, red)
                    listOf(0.55f, 0.62f, 0.7f).forEach { x ->
                        drawLine(Color.White, androidx.compose.ui.geometry.Offset(w * x, h * 0.22f), androidx.compose.ui.geometry.Offset(w * x, h * 0.66f), strokeWidth = w * 0.05f)
                    }
                }
            }
        }
    }
}

/**
 * The billboard hero's position indicator: the same bar as the first row's,
 * driven by its own timer that runs while [running].
 */
@Composable
internal fun TvHomeHeroStrip(
    items: List<SectionItem>,
    current: Int,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    if (items.size <= 1) return
    val fill = remember { Animatable(0f) }
    LaunchedEffect(current, running) {
        fill.snapTo(0f)
        if (running) fill.animateTo(1f, tween(TvHeroRotateMs.toInt(), easing = LinearEasing))
    }
    TvHeroPositionBar(
        count = items.size,
        current = current,
        progress = { fill.value },
        modifier = modifier.fillMaxWidth().padding(horizontal = TvHeroLeft),
    )
}

/**
 * A "4 / 10" counter, a Previous cue, one bar segment per item and a Next cue.
 * An item that has been left shows a full segment (whether the timer ran out
 * or the viewer stepped away), the current one fills with [progress] (0..1),
 * items not yet visited are empty. Stepping back clears the segments from the
 * item landed on onwards, and looping from the last item back to the first
 * clears them all.
 */
@Composable
internal fun TvHeroPositionBar(
    count: Int,
    current: Int,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return
    val seen = remember(count) { androidx.compose.runtime.mutableStateListOf<Boolean>().apply { repeat(count) { add(it < current) } } }
    var previous by remember(count) { androidx.compose.runtime.mutableIntStateOf(current) }
    LaunchedEffect(current) {
        if (current != previous) {
            val loopedForward = current == 0 && previous == count - 1
            val steppedBack = (current < previous && !loopedForward) || (previous == 0 && current == count - 1)
            if (loopedForward) {
                for (i in seen.indices) seen[i] = false
            } else if (steppedBack) {
                for (i in current until count) seen[i] = false
            } else if (previous in seen.indices) {
                seen[previous] = true
            }
            previous = current
        }
        if (current in seen.indices) seen[current] = false
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(
            text = "${current + 1} / $count",
            color = HeroText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.width(44.dp),
        )
        Text("\u2039", color = HeroText, fontSize = 18.sp, maxLines = 1)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            for (index in 0 until count) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.28f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .graphicsLayer { scaleX = if (index == current) progress().coerceIn(0f, 1f) else if (seen.getOrElse(index) { false }) 1f else 0f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                            .fillMaxWidth()
                            .background(HeroAccent),
                    )
                }
            }
        }
        Text("\u203a", color = HeroText, fontSize = 18.sp, maxLines = 1)
    }
}

/**
 * The hero's backdrop: the artwork runs edge to edge across the top three
 * quarters of the screen at full brightness, with only a left scrim for the
 * text and a fade into the page below, so the picture does the talking.
 */
@Composable
internal fun TvHeroBillboardBackdrop(content: TvMarqueeContent?, modifier: Modifier = Modifier) {
    val page = MaterialTheme.colorScheme.background
    Box(modifier = modifier.fillMaxSize().background(page)) {
        Crossfade(
            targetState = content?.heroBackdropUrl to content?.heroBackdropThumbhash,
            animationSpec = tween(500),
            label = "tvHeroBillboardArt",
        ) { (url, thumbhash) ->
            if (url != null) {
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
                    ThumbhashImage(
                        url = url,
                        thumbhash = thumbhash,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        transparent = true,
                        crossfadeMillis = 0,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Left scrim: text sits on near-solid page colour that
                    // opens out to the picture by the middle of the screen.
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                0.00f to page.copy(alpha = 0.96f),
                                0.30f to page.copy(alpha = 0.80f),
                                0.55f to page.copy(alpha = 0.25f),
                                0.80f to Color.Transparent,
                            ),
                        ),
                    )
                    // Bottom fade into the rows.
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0.00f to Color.Transparent,
                                0.62f to Color.Transparent,
                                1.00f to page,
                            ),
                        ),
                    )
                    // A light top shade keeps the menu legible on bright art.
                    Box(
                        Modifier.fillMaxWidth().height(90.dp).background(
                            Brush.verticalGradient(
                                0.0f to page.copy(alpha = 0.65f),
                                1.0f to Color.Transparent,
                            ),
                        ),
                    )
                }
            }
        }
    }
}
