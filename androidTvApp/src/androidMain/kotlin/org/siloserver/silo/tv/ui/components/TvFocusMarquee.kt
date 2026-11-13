package org.siloserver.silo.tv.ui.components

import androidx.compose.ui.text.withStyle
import org.siloserver.silo.tv.ui.theme.Aurora
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.siloserver.silo.common.settings.titleLogoUrl
import org.siloserver.silo.common.ui.components.MachineTranslatedLabel
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.catalog.hasMachineTranslatedOverview
import org.siloserver.silo.tv.ui.theme.LevelDisplayFamily
import org.siloserver.silo.tv.ui.theme.LevelMonoFamily
import org.siloserver.silo.tv.ui.theme.SiloOnSurface
import org.siloserver.silo.tv.ui.theme.SiloSecondaryText
import org.siloserver.silo.common.cards.LocalEpisodeSpoilerPrefs

/**
 * Passive Skyline billboard, anchored bottom-left, that previews whichever row
 * card currently holds focus. Faithful Android port of tvOS `TVFocusMarquee` /
 * `TVMarqueeBlock` (§5.4): logo-or-title, a badge + meta line, a 3-line
 * synopsis, and a quieter detail line. It is never focusable or hit-testable —
 * the rows below own all focus. Content crossfades (~240 ms) when the focused
 * item changes; the parent debounces the focus-rest by ~150 ms before swapping.
 *
 * Metrics are scaled ~0.5x from the tvOS 1920×1080 tokens to the Android TV
 * ~960dp layout, matching the rest of the Skyline chrome (see [TvSkyline]).
 */
@Composable
fun TvFocusMarquee(
    content: TvMarqueeContent?,
    detailLine: String? = content?.detailLine,
    /** The full description from the item's detail, shown in place of the shortened one in the section payload. */
    fullOverview: String? = null,
    /** The rested item's description translation; like [detailLine], it
     *  updates the block in place instead of replaying the crossfade. */
    translation: TvMarqueeTranslation? = null,
    modifier: Modifier = Modifier,
    startPadding: androidx.compose.ui.unit.Dp = 44.dp,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    animateTransition: Boolean = true,
    /** An extra line under the block for pages that preview more than catalog
     *  items (the Requests page's status and stage track). */
    footer: (@Composable (TvMarqueeContent) -> Unit)? = null,
    /** The Aurora eyebrow pill above the title (the row the item is in); null shows none. */
    eyebrow: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // topPadding reserves the top-menu-bar zone: the block is
            // bottom-anchored, so any growth (typography floors) pushes it
            // upward — without this clamp it renders beneath the bar.
            .padding(start = startPadding, top = topPadding, bottom = bottomPadding)
            .clipToBounds(),
        contentAlignment = Alignment.BottomStart,
    ) {
        // tvOS first-frame parity: the FIRST content snaps in with no
        // animation — a cold entry paints the finished marquee block instead
        // of fading it up. Focus-driven swaps keep the crossfade.
        var hasDisplayedContent by remember { mutableStateOf(false) }
        val spoilerPrefs = LocalEpisodeSpoilerPrefs.current
        val snapInitialContent = content != null && !hasDisplayedContent
        LaunchedEffect(content != null) {
            if (content != null) hasDisplayedContent = true
        }
        if (animateTransition) {
            Crossfade(
                targetState = content,
                animationSpec = tween(
                    if (snapInitialContent) 0 else TvMarqueeCrossfadeMs,
                    easing = TvMarqueeEasing,
                ),
                label = "tvFocusMarquee",
                // Keep the transition viewport fixed. A wrapping Crossfade
                // animates its own measured size between differently tall hero
                // blocks, which moves bottom-anchored copy while it fades.
                modifier = Modifier.fillMaxSize(),
            ) { value ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    if (value != null) {
                        // A layer for the item still shown takes its current
                        // copy at once, so a synopsis protection now hides
                        // doesn't fade out.
                        TvMarqueeBlock(
                            content = if (content != null && value.id == content.id) {
                                content
                            } else {
                                value.underPrefs(spoilerPrefs)
                            },
                            detailLine = detailLine.takeIf { value.id == content?.id },
                            fullOverview = fullOverview.takeIf { value.id == content?.id },
                            translation = translation.takeIf { it?.contentId == value.contentId },
                            footer = footer,
                            eyebrow = eyebrow,
                        )
                    }
                }
            }
        } else if (content != null) {
            TvMarqueeBlock(
                content = content,
                detailLine = detailLine,
                fullOverview = fullOverview,
                translation = translation.takeIf { it?.contentId == content.contentId },
                footer = footer,
                eyebrow = eyebrow,
            )
        }
    }
}

@Composable
private fun TvMarqueeBlock(
    content: TvMarqueeContent,
    detailLine: String?,
    fullOverview: String? = null,
    translation: TvMarqueeTranslation? = null,
    footer: (@Composable (TvMarqueeContent) -> Unit)? = null,
    eyebrow: String? = null,
) {
    val level = org.siloserver.silo.tv.ui.theme.Level
    val src = content.source
    val hidesSynopsis = content.isEpisode && LocalEpisodeSpoilerPrefs.current.hidesOverview(
        org.siloserver.silo.model.settings.EpisodeSpoilers.isUnwatched(src),
    )
    val synopsisText = (translation?.synopsis ?: fullOverview?.takeIf { it.length > (content.synopsis?.length ?: 0) } ?: content.synopsis)
        ?.takeIf { it.isNotBlank() && !hidesSynopsis }
    val translating = translation?.translating == true
    val machineTranslated = !translating && synopsisText != null &&
        (translation?.machineTranslated ?: hasMachineTranslatedOverview(src.machineTranslatedFields))
    var titleLineCount by remember(content.id) { mutableStateOf(1) }

    Column(
        modifier = Modifier.widthIn(max = MarqueeContentWidth),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Label: RESUME / the row's name, the episode and the time left, in accent mono.
        val position = src.positionSeconds
        val duration = src.durationSeconds
        val labelParts = buildList {
            add(if (position != null) "Resume" else (eyebrow ?: ""))
            val season = src.seasonNumber
            val episode = src.episodeNumber
            if (season != null && episode != null) add("S$season\u00B7E$episode")
            if (position != null && duration != null && duration > position) {
                add("${((duration - position) / 60.0).toInt().coerceAtLeast(1)} min left")
            }
        }.filter { it.isNotBlank() }
        if (labelParts.isNotEmpty()) {
            Text(
                text = labelParts.joinToString(" \u00B7 ").uppercase(),
                color = level.Accent,
                fontFamily = LevelMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 9.5.sp,
                letterSpacing = 1.3.sp,
                maxLines = 1,
            )
        }
        // The title in condensed display type: text, never a logo image.
        Text(
            text = content.title.uppercase(),
            color = level.Fg,
            fontFamily = LevelDisplayFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 60.sp,
            lineHeight = 56.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { titleLineCount = it.lineCount },
            modifier = Modifier.padding(top = 4.dp),
        )
        // Chips: certificate, year, scores and quality.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            content.badges.forEach { LevelChip(it) }
            src.year.takeIf { it > 0 }?.let { LevelChip(it.toString()) }
            src.ratingImdb?.let { LevelChip(String.format("%.1f", it), tag = "IMDb") }
            src.ratingRtCritic?.let { LevelChip("$it%", tag = "RT") }
            val quality = content.specLine?.split(" \u00B7 ")?.firstOrNull { it.isNotBlank() }?.let { q ->
                when {
                    q.contains("4K", true) || q.contains("2160") -> "4K"
                    q.contains("1080") || q.contains("720") || q.contains("HD", true) -> "HD"
                    else -> q.uppercase()
                }
            }
            quality?.let { LevelChip(it) }
        }

        synopsisText?.let { synopsis ->
            val pulse: State<Float>? = if (translating) {
                rememberInfiniteTransition(label = "marqueeTranslating").animateFloat(
                    initialValue = 0.35f,
                    targetValue = 0.7f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                    label = "marqueeTranslatingAlpha",
                )
            } else {
                null
            }
            Text(
                text = synopsis,
                color = level.Muted,
                fontSize = 13.sp,
                lineHeight = 18.5.sp,
                maxLines = if (titleLineCount > 1) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = MarqueeSynopsisMaxWidth)
                    .drawAlpha { pulse?.value ?: 1f },
            )
        }

        footer?.invoke(content)
    }
}

/** A bordered mono data chip: an optional accent tag then the value. */
@Composable
private fun LevelChip(value: String, tag: String? = null) {
    val level = org.siloserver.silo.tv.ui.theme.Level
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(1.dp, level.Line)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        if (tag != null) {
            Text(tag.uppercase(), color = level.Accent, fontFamily = LevelMonoFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.6.sp, maxLines = 1)
        }
        Text(value.uppercase(), color = level.Fg, fontFamily = LevelMonoFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.6.sp, maxLines = 1)
    }
}

/**
 * [Modifier.alpha] that reads [alpha] in the draw phase, so an animated value
 * redraws the layer without recomposing its caller. Like [Modifier.alpha], it
 * clips only while translucent.
 */
private fun Modifier.drawAlpha(alpha: () -> Float): Modifier = graphicsLayer {
    val value = alpha()
    this.alpha = value
    clip = value != 1f
}

@Composable
private fun MarqueeBadge(label: String) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.08f), shape)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.24f),
                shape = shape,
            )
            .padding(horizontal = 6.dp, vertical = 2.5.dp),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = MarqueeBadgeSize,
            lineHeight = MarqueeBadgeSize * 1.25f,
            letterSpacing = 0.55.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

// Skyline marquee metrics. Geometry stays ~0.5x of the tvOS 1920×1080 tokens;
// body text stays at the 10-ft legibility floor; the compact outlined badges
// intentionally follow the smaller tvOS metadata treatment.
private val MarqueeContentWidth = 470.dp
private val MarqueeSynopsisMaxWidth = 470.dp
private val MarqueeLogoMaxWidth = 440.dp
private val MarqueeLogoMaxHeight = 72.dp
private val MarqueeDetailLineHeight = 20.dp
private val MarqueeTitleSize = 44.sp
private val MarqueeMetaSize = 14.sp

/** About a space at [MarqueeMetaSize], so `·` spacing matches the joined text. */
private val MarqueeMetaGap = 4.dp
private val MarqueeDetailSize = 14.sp
private val MarqueeSynopsisSize = 16.sp
private val MarqueeBadgeSize = 10.5.sp

/** How long the marquee waits on a logo before showing the text title. */
private const val MarqueeLogoGraceMs = 700L

/** The title for the text fallback: the part after a colon in the Aurora gradient on its own line, else all of it. */
private fun marqueeTitleAnnotated(title: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        val i = title.indexOf(": ")
        if (i > 0 && i < title.length - 2) {
            append(title.substring(0, i + 1))
            append("\n")
            withStyle(androidx.compose.ui.text.SpanStyle(brush = Aurora.Gradient)) { append(title.substring(i + 2)) }
        } else {
            withStyle(androidx.compose.ui.text.SpanStyle(brush = Aurora.Gradient)) { append(title) }
        }
    }
