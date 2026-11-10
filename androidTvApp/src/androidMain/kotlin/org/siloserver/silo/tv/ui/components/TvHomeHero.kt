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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
internal const val TvHeroMaxItems = 4

/** How long each hero item shows before the next one. */
internal const val TvHeroRotateMs = 5_000L

// The billboard sits on the 960x540dp TV layout: information bottom-left over a
// full-bleed backdrop, the rows starting beneath it.
internal val TvHeroLeft = 47.dp
internal val TvHeroTop = 84.dp
internal val TvHeroDotsTop = 338.dp

/** Where the first row's header sits while the hero shows. */
internal val TvHeroRowHeaderTop = 390.dp

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
private val HeroInfoHeight = 212.dp

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
    Column(
        modifier = modifier.widthIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(HeroInfoHeight)) {
            Crossfade(
                targetState = content,
                animationSpec = tween(350),
                label = "tvHomeHero",
            ) { value ->
                if (value != null) HeroInfo(value, eyebrow)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 5.dp)
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
            HeroPill(
                label = playLabel,
                icon = Icons.Filled.PlayArrow,
                primary = true,
                onClick = onPlay,
                modifier = Modifier.focusRequester(playFocusRequester),
            )
            HeroPill(label = "More info", icon = Icons.Outlined.Info, primary = false, onClick = onMoreInfo)
            HeroCircle(
                icon = if (inWatchlist) Icons.Filled.Check else Icons.Filled.Add,
                description = if (inWatchlist) "Remove from watchlist" else "Add to watchlist",
                onClick = onToggleWatchlist,
            )
            // Step through the billboard by hand; the timer starts over.
            if (onPrevious != null) {
                HeroCircle(icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, description = "Previous", onClick = onPrevious)
            }
            if (onNext != null) {
                HeroCircle(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, description = "Next", onClick = onNext)
            }
        }
    }
}

@Composable
private fun HeroInfo(content: TvMarqueeContent, eyebrow: String) {
    val item = content.source
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Eyebrow: the source section's name.
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
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

        HeroLogo(content)

        HeroMeta(content, item)

        content.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
            Text(
                text = synopsis,
                color = HeroSynopsis,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 400.dp),
            )
        }

        HeroResume(item)
    }
}

/** The clear logo in a 320x95dp slot; the title in text if there is none or it fails. */
@Composable
private fun HeroLogo(content: TvMarqueeContent) {
    val logoUrl = titleLogoUrl(content.logoUrl)
    var failed by remember(logoUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier.width(340.dp).height(84.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (logoUrl.isNullOrBlank() || failed) {
            Text(
                text = content.title,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 40.sp,
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
                modifier = Modifier.fillMaxHeight().widthIn(max = 340.dp),
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
        ratingLabel?.let { Text(it, color = HeroRating, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
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

@Composable
private fun HeroCircle(icon: ImageVector, description: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "heroCircleScale")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.border(3.dp, HeroAccent.copy(alpha = 0.55f), CircleShape) else Modifier)
            .background(if (focused) HeroText else Color.White.copy(alpha = 0.16f), CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = description, tint = if (focused) HeroInk else HeroText, modifier = Modifier.size(17.dp))
    }
}

/**
 * The pager: one segment per item, right-aligned under the artwork. The current
 * segment fills over [TvHeroRotateMs] while the hero is rotating, so the next
 * change is visible before it happens.
 */
@Composable
internal fun TvHomeHeroDots(
    count: Int,
    current: Int,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(end = TvHeroLeft),
    ) {
        repeat(count) { index ->
            val fill = remember(index) { Animatable(0f) }
            LaunchedEffect(current, running) {
                when {
                    index < current -> fill.snapTo(1f)
                    index > current -> fill.snapTo(0f)
                    running -> {
                        fill.snapTo(0f)
                        fill.animateTo(1f, tween(TvHeroRotateMs.toInt(), easing = LinearEasing))
                    }
                    else -> fill.snapTo(0f)
                }
            }
            Box(
                Modifier
                    .size(width = 30.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(2.dp)),
            ) {
                val active = index == current
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(if (active || index < current) fill.value else 0f)
                        .background(if (active) HeroAccent else Color.White.copy(alpha = 0.8f), RoundedCornerShape(2.dp)),
                )
            }
        }
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
