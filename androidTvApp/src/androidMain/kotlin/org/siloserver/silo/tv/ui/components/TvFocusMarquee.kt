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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
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
    /** Season count of a show, shown above the logo when no episode is being resumed. */
    seasonCount: Int? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // topPadding reserves the top-menu-bar zone: the block is
            // bottom-anchored, so any growth (typography floors) pushes it
            // upward — without this clamp it renders beneath the bar.
            // Vantage: the block hangs from just under the menu bar, so the logo always starts
            // at the same height and nothing leaves a gap at the top left.
            .padding(start = startPadding, top = topPadding + MarqueeTopGap, bottom = bottomPadding)
            .clipToBounds(),
        contentAlignment = Alignment.TopStart,
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
                    contentAlignment = Alignment.TopStart,
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
                            seasonCount = seasonCount,
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
                seasonCount = seasonCount,
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
    seasonCount: Int? = null,
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
        modifier = Modifier.widthIn(max = org.siloserver.silo.tv.ui.theme.TvGrid.leftW),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Every row below has a fixed height, so the chips, ratings, synopsis and buttons sit
        // at the same place for every title, whatever its logo, season or synopsis length.
        // Vantage: SEASON x and EPISODE x pills, then a quiet tag (Continue / the row's name, time left).
        val position = src.positionSeconds
        val duration = src.durationSeconds
        val tagParts = buildList {
            add(if (position != null && position > 0) "Continue" else (eyebrow ?: ""))
            if (position != null && duration != null && duration > position && position > 0) {
                add("${((duration - position) / 60.0).toInt().coerceAtLeast(1)} min left")
            }
        }.filter { it.isNotBlank() }
        val season = src.seasonNumber
        val episode = src.episodeNumber
        val showsSeasons = season == null && episode == null && !content.isEpisode && (seasonCount ?: 0) > 0 &&
            src.type.equals("series", ignoreCase = true)
        Box(Modifier.height(org.siloserver.silo.tv.ui.theme.TvGrid.kickerH), contentAlignment = Alignment.CenterStart) {
        if (season != null || episode != null || tagParts.isNotEmpty() || showsSeasons) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showsSeasons) org.siloserver.silo.tv.ui.components.vantage.VChip("${seasonCount} ${if (seasonCount == 1) "SEASON" else "SEASONS"}")
                season?.let { org.siloserver.silo.tv.ui.components.vantage.VChip("SEASON $it") }
                episode?.let { org.siloserver.silo.tv.ui.components.vantage.VChip("EPISODE $it") }
                if (tagParts.isNotEmpty()) {
                    Box(Modifier.padding(start = if (season != null || episode != null) 4.dp else 0.dp)) {
                        org.siloserver.silo.tv.ui.components.vantage.VTag(tagParts.joinToString(" \u00B7 "))
                    }
                }
            }
        }
        }
        // The clear logo; the title in Outfit when there is none. One fixed slot for all of them.
        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
        Box(Modifier.size(org.siloserver.silo.tv.ui.theme.TvGrid.logoW, org.siloserver.silo.tv.ui.theme.TvGrid.logoH), contentAlignment = Alignment.CenterStart) {
            VantageLogo(content, onLines = { titleLineCount = it })
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(9.dp))
        // Chips: certificate (solid), year, genre, quality; then IMDb / RT / TMDB scores.
        Row(
            modifier = Modifier.height(org.siloserver.silo.tv.ui.theme.TvGrid.factsH),
            horizontalArrangement = Arrangement.spacedBy(8.dp * 0.75f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val cert = src.contentRating?.takeIf { it.isNotBlank() } ?: content.badges.firstOrNull()
            cert?.let { org.siloserver.silo.tv.ui.components.vantage.VChip(it.uppercase(), org.siloserver.silo.tv.ui.components.vantage.ChipTone.Solid) }
            src.year.takeIf { it > 0 }?.let { org.siloserver.silo.tv.ui.components.vantage.VChip(it.toString()) }
            src.genres.firstOrNull()?.let { org.siloserver.silo.tv.ui.components.vantage.VChip(it.uppercase()) }
            val spec = content.specLine?.split(" \u00B7 ")?.filter { it.isNotBlank() }.orEmpty()
            val quality = spec.firstOrNull()?.let { q ->
                when {
                    q.contains("4K", true) || q.contains("2160") -> "4K"
                    q.contains("1080") -> "1080p"
                    q.contains("720") -> "720p"
                    else -> q.uppercase()
                }
            }
            if (quality != null) {
                val extra = spec.drop(1).take(1).joinToString(" \u00B7 ") { it.uppercase() }
                org.siloserver.silo.tv.ui.components.vantage.VChip(if (extra.isBlank()) "" else " $extra", bold = quality)
            }
            if (src.ratingImdb != null || src.ratingRtCritic != null || src.ratingTmdb != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                src.ratingImdb?.let { VantageRate("IMDb", Color(0xFFF5C518), Color.Black, String.format("%.1f", it)) }
                src.ratingRtCritic?.let { VantageRate("RT", Color(0xFFFA320A), Color.White, "$it%") }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(7.5.dp))
        Box(Modifier.height(90.dp)) {
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
                style = org.siloserver.silo.tv.ui.components.vantage.ui(16, FontWeight.Normal, level.Synopsis),
                lineHeight = 20.9.sp,
                minLines = 5,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = org.siloserver.silo.tv.ui.theme.TvGrid.synopsisW)
                    .drawAlpha { pulse?.value ?: 1f },
            )
        }
        }

        footer?.invoke(content)
    }
}

/**
 * The clear logo, falling back to the title in Outfit. Logos come in every shape, so each is
 * sized to the same visual weight: about the same area, whatever its aspect ratio, within a
 * fixed height and width range. A wide wordmark and a stacked square logo then look alike.
 */
@Composable
private fun VantageLogo(content: TvMarqueeContent, onLines: (Int) -> Unit) {
    val logoUrl = titleLogoUrl(content.logoUrl)
    var failed by remember(logoUrl) { mutableStateOf(false) }
    var aspect by remember(logoUrl) { mutableStateOf<Float?>(null) }
    if (logoUrl.isNullOrBlank() || failed) {
        Text(
            text = content.title,
            style = org.siloserver.silo.tv.ui.components.vantage.ui(38, FontWeight.SemiBold),
            lineHeight = 36.sp,
            letterSpacing = (-0.6).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { onLines(it.lineCount) },
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            coil3.compose.AsyncImage(
                model = logoUrl,
                contentDescription = content.title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onSuccess = { state ->
                    val img = state.result.image
                    if (img.width > 0 && img.height > 0) aspect = img.width.toFloat() / img.height
                },
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val LogoArea = 300f * 74f // legacy sizing constants, unused by the grid logo box
private const val LogoMinWidth = 130f
private const val LogoMaxWidth = 360f
private const val LogoMinHeight = 36f
private const val LogoMaxHeight = 72f

/** A score: a coloured brand tag and the value in mono (.rate). */
@Composable
private fun VantageRate(tag: String, bg: Color, fg: Color, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.background(bg, androidx.compose.ui.graphics.RectangleShape).padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text(tag, style = org.siloserver.silo.tv.ui.components.vantage.mono(9, FontWeight.Bold, fg))
        }
        Text(value, style = org.siloserver.silo.tv.ui.components.vantage.mono(13))
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
    val shape = androidx.compose.ui.graphics.RectangleShape
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
private val MarqueeContentWidth = 420.dp
private val HeroEyebrowHeight = 20.dp
private val HeroLogoSlotHeight = 72.dp
private val HeroRatingsHeight = 18.dp
private val HeroSynopsisHeight = 96.dp

/** Space between the menu bar and the top of the block. */
private val MarqueeTopGap = 2.dp
private val MarqueeSynopsisMaxWidth = 405.dp
private val MarqueeLogoMaxWidth = 340.dp
private val MarqueeLogoMaxHeight = 92.dp
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
