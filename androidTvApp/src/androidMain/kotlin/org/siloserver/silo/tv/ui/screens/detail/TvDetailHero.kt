package org.siloserver.silo.tv.ui.screens.detail

import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.siloserver.silo.common.cards.LocalCardPresentation
import org.siloserver.silo.model.settings.CardPosterSize
import org.siloserver.silo.common.settings.titleLogoUrl
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.tv.R
import org.siloserver.silo.tv.ui.components.TvFactsRow
import org.siloserver.silo.tv.ui.components.TvHeroFactToken

/**
 * Approved tvOS detail hero, mapped onto Android TV's half-scale layout
 * canvas. The page owns the sampled opaque tint; this composable draws only
 * the crisp top-trailing artwork and its alpha masks, so the image naturally
 * scrolls away with the hero instead of behaving like a fixed backdrop.
 *
 * The editorial and action stack is top-leading (50dp / 58dp = tvOS
 * 100pt / 116pt), while the artwork occupies the trailing 64 percent and
 * dissolves into the page surface at its leading and lower edges. The action
 * cluster remains full-width and focus-grouped; Version and Subtitles live in
 * that same row as circular actions.
 */
@Composable
internal fun TvDetailHero(
    title: String,
    seriesTitle: String?,
    logoUrl: String?,
    backdropUrl: String?,
    backdropThumbhash: String?,
    sourceTokens: List<String>,
    ratingChip: String?,
    overview: String?,
    tagline: String?,
    factsLine: List<TvHeroFactToken>,
    directorText: String?,
    actions: @Composable () -> Unit,
    playbackSummary: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Series reserves its editorial and disclosure slots as episodes change. */
    compactSeries: Boolean = false,
    // Optional description-translation affordance (Apple tvOS parity),
    // rendered as its own focus stop directly under the synopsis.
    translation: (@Composable () -> Unit)? = null,
    /** The shown synopsis was machine-translated: mark it "Translated by AI". */
    machineTranslated: Boolean = false,
    /** Grows the hero, not the artwork, for a taller summary strip (request
     *  detail's status and stage track) so the actions stay inside its clip. */
    extraHeight: Dp = 0.dp,
    /** Lets a page start focus on the synopsis "Read more" pill when there is one. */
    synopsisPillFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    onSynopsisPillPresent: ((Boolean) -> Unit)? = null,
    /** Vantage: the Details and Your progress glass cards at the top right. */
    sidePanel: (@Composable () -> Unit)? = null,
    /** An episode on screen: its season, episode number and name, shown as "Season 2 | Episode 4 | Name". */
    episodeKicker: Triple<String, String, String>? = null,
) {
    val grid = org.siloserver.silo.tv.ui.theme.TvGrid
    val seriesContext = seriesTitle?.trim()?.takeIf { it.isNotEmpty() }
    val shownLogoUrl = titleLogoUrl(logoUrl)

    // The hero ends where the first body section starts on the grid.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(grid.bodyTop)
            .clipToBounds(),
    ) {
        // Callers that still hand the hero its own backdrop get it behind the grid.
        if (!backdropUrl.isNullOrEmpty() || !backdropThumbhash.isNullOrEmpty()) {
            val bg = org.siloserver.silo.tv.ui.theme.Level.Bg
            ThumbhashImage(
                url = backdropUrl,
                thumbhash = backdropThumbhash,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(maxWidth)
                    .height(maxWidth * 9f / 16f)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.horizontalGradient(
                                0.00f to bg.copy(alpha = 0.90f),
                                0.45f to bg.copy(alpha = 0.50f),
                                0.75f to Color.Transparent,
                            ),
                        )
                    },
            )
        }

        // Right column: Details and progress cards.
        if (sidePanel != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = grid.rightTop, end = grid.edge),
            ) { sidePanel() }
        }

        // Kicker: type chips, and the episode name for an episode.
        Row(
            modifier = Modifier
                .offset(x = grid.edge, y = grid.kickerTop)
                .width(grid.leftW)
                .height(grid.kickerH),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (episodeKicker != null) {
                val bar = @androidx.compose.runtime.Composable {
                    Box(Modifier.width(1.5.dp).height(14.dp).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.45f)))
                }
                val mono = { t: String -> org.siloserver.silo.tv.ui.components.vantage.mono(12, androidx.compose.ui.text.font.FontWeight.SemiBold, org.siloserver.silo.tv.ui.theme.Level.Fg, 0.12f) }
                androidx.tv.material3.Text(episodeKicker.first.uppercase(), style = mono(""), maxLines = 1)
                bar()
                androidx.tv.material3.Text(episodeKicker.second.uppercase(), style = mono(""), maxLines = 1)
                if (episodeKicker.third.isNotBlank()) {
                    bar()
                    androidx.tv.material3.Text(
                        episodeKicker.third,
                        style = org.siloserver.silo.tv.ui.components.vantage.ui(14, androidx.compose.ui.text.font.FontWeight.Medium),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            } else {
                sourceTokens.take(3).forEach { org.siloserver.silo.tv.ui.components.vantage.VChip(it.uppercase()) }
                if (seriesContext != null) {
                    org.siloserver.silo.tv.ui.components.vantage.VTag(title)
                }
            }
        }

        // Clear logo: always the same box, fitted with no waste.
        Box(
            modifier = Modifier
                .offset(x = grid.edge, y = grid.logoTop)
                .size(grid.logoW, grid.logoH),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (!shownLogoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = shownLogoUrl,
                    contentDescription = seriesContext ?: title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                HeroTextTitle(title = seriesContext ?: title)
            }
        }

        // Facts: certificate, year, runtime, ratings on one line.
        Box(
            modifier = Modifier
                .offset(x = grid.edge, y = grid.factsTop)
                .width(grid.leftW)
                // Not clipped: the TMDB logo and score chips are taller than the line and were cut.
                .height(grid.factsH),
            contentAlignment = Alignment.CenterStart,
        ) {
            MetadataRow(
                tokens = factsLine,
                sourceTokens = emptyList(),
                ratingChip = ratingChip,
                compactRating = true,
            )
        }

        // Synopsis: five lines tall even when the text is shorter.
        Box(
            modifier = Modifier
                .offset(x = grid.edge, y = grid.synopsisTop)
                .width(grid.synopsisW)
                .height(grid.synopsisH)
                .clipToBounds(),
        ) {
            overview?.takeIf { it.isNotBlank() }?.let { line ->
                TvExpandableSynopsis(
                    overview = line,
                    tagline = tagline,
                    collapsedMaxLines = 5,
                    dialogTitle = title,
                    pillFocusRequester = synopsisPillFocusRequester,
                    onPillPresent = onSynopsisPillPresent,
                    onExtraHeight = null,
                )
            }
        }

        // Actions.
        Box(
            modifier = Modifier
                .offset(x = grid.edge, y = grid.actionTop)
                .width(grid.leftW)
                .height(grid.actionH)
                .focusGroup(),
            contentAlignment = Alignment.CenterStart,
        ) { actions() }
    }
}

@Composable
private fun EditorialColumn(
    title: String,
    seriesTitle: String?,
    logoUrl: String?,
    sourceTokens: List<String>,
    ratingChip: String?,
    overview: String?,
    tagline: String?,
    factsLine: List<TvHeroFactToken>,
    directorText: String?,
    contentMaxWidth: androidx.compose.ui.unit.Dp,
    verticalSpacing: androidx.compose.ui.unit.Dp,
    collapsedSynopsisLines: Int,
    compactSeries: Boolean,
    translation: (@Composable () -> Unit)? = null,
    machineTranslated: Boolean = false,
    playbackSummary: (@Composable () -> Unit)? = null,
    synopsisExtra: androidx.compose.ui.unit.Dp = 0.dp,
    onSynopsisExtraHeight: ((androidx.compose.ui.unit.Dp) -> Unit)? = null,
    synopsisPillFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    onSynopsisPillPresent: ((Boolean) -> Unit)? = null,
) {
    val isCombinedSeriesEpisode = compactSeries && !seriesTitle.isNullOrBlank()
    Column(
        modifier = Modifier.widthIn(max = contentMaxWidth)
            // tvOS reserves 435pt for Series editorial content and its disclosure block.
            .then(if (compactSeries) Modifier.height(246.dp + synopsisExtra) else Modifier),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(if (compactSeries) 2.dp else verticalSpacing),
    ) {
        Column(
            modifier = if (compactSeries) Modifier.weight(1f).clipToBounds() else Modifier,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            TitleBlock(
                // A focused episode must not shrink or move the Show identity.
                // Its name belongs to the bounded synopsis slot below instead.
                title = if (isCombinedSeriesEpisode) seriesTitle!! else title,
                seriesTitle = seriesTitle.takeUnless { isCombinedSeriesEpisode },
                logoUrl = logoUrl,
            )

            val hasEpisodeHierarchy = !compactSeries && !seriesTitle.isNullOrBlank()
            if (hasEpisodeHierarchy && sourceTokens.isNotEmpty()) SourceRow(tokens = sourceTokens)
            val metadataSourceTokens = sourceTokens.takeUnless { hasEpisodeHierarchy }.orEmpty()
            val hasMetadata = factsLine.isNotEmpty() || !ratingChip.isNullOrBlank() || metadataSourceTokens.isNotEmpty()
            if (compactSeries || hasMetadata) {
                Box(
                    modifier = Modifier
                        .height(
                            if (factsLine.any { it is TvHeroFactToken.ExternalRating }) {
                                SERIES_METADATA_SLOT_HEIGHT + SERIES_RATINGS_ROW_HEIGHT
                            } else {
                                SERIES_METADATA_SLOT_HEIGHT
                            },
                        )
                        .clipToBounds(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (hasMetadata) {
                        MetadataRow(
                            tokens = factsLine,
                            sourceTokens = metadataSourceTokens,
                            ratingChip = ratingChip,
                            compactRating = true,
                        )
                    }
                }
            }

            // The synopsis opens its full text without resizing the hero.
            if (compactSeries) {
                // Reserve three lines even when the focused episode has no synopsis.
                Box(
                    modifier = Modifier
                        .height(SERIES_EPISODE_SYNOPSIS_HEIGHT + synopsisExtra)
                        .clipToBounds(),
                ) {
                    overview?.takeIf { it.isNotBlank() }?.let { line ->
                        TvExpandableSynopsis(
                            overview = line,
                            tagline = tagline.takeUnless { isCombinedSeriesEpisode },
                            collapsedMaxLines = 5,
                            dialogTitle = title,
                            // The title is always isolated so the overview, not the
                            // title, sets the paragraph direction.
                            previewText = if (isCombinedSeriesEpisode) "\u2068$title\u2069 · $line" else line,
                            pillFocusRequester = synopsisPillFocusRequester,
                            onPillPresent = onSynopsisPillPresent,
                            onExtraHeight = onSynopsisExtraHeight,
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.height(SYNOPSIS_SLOT_HEIGHT + synopsisExtra)) {
                    overview?.takeIf { it.isNotBlank() }?.let { line ->
                        TvExpandableSynopsis(
                            overview = line,
                            tagline = tagline,
                            collapsedMaxLines = collapsedSynopsisLines,
                            dialogTitle = title,
                            pillFocusRequester = synopsisPillFocusRequester,
                            onPillPresent = onSynopsisPillPresent,
                            onExtraHeight = onSynopsisExtraHeight,
                        )
                    }
                }
            }
            if (machineTranslated && !overview.isNullOrBlank()) TvMachineTranslatedLabel()
            translation?.invoke()
        }
        // Bottom-lock the credit and playback readout independently of synopsis length.
        Column(verticalArrangement = Arrangement.spacedBy(if (compactSeries) 4.dp else verticalSpacing)) {
            if (compactSeries) {
                // Keep this slot mounted even while episode data changes so the
                // playback readout and action row remain completely locked.
                Box(
                    modifier = Modifier
                        .height(SERIES_CREDIT_SLOT_HEIGHT)
                        .clipToBounds(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    directorText?.takeIf { it.isNotBlank() }?.let { line ->
                        HeroCreditLine(line)
                    }
                }
            } else {
                directorText?.takeIf { it.isNotBlank() }?.let { line ->
                    HeroCreditLine(line)
                }
            }
            playbackSummary?.invoke()
        }
    }
}

@Composable
private fun HeroCreditLine(line: String) {
    Text(
        text = line,
        fontFamily = org.siloserver.silo.tv.ui.theme.VantageMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = org.siloserver.silo.tv.ui.theme.Level.Dim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TitleBlock(
    title: String,
    seriesTitle: String?,
    logoUrl: String?,
) {
    val seriesContext = seriesTitle?.trim()?.takeIf { it.isNotEmpty() }
    // "Show title art" off: every title here is text, episodes included.
    val shownLogoUrl = titleLogoUrl(logoUrl)

    when {
        seriesContext != null -> EpisodeHierarchyTitle(
            seriesTitle = seriesContext,
            episodeTitle = title,
            logoUrl = shownLogoUrl,
        )
        !shownLogoUrl.isNullOrBlank() -> AsyncImage(
            model = shownLogoUrl,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            // Reserve the framed logo area (approved tvOS maxHeight 160pt) so a
            // loading/failed logo can't measure as 0 and collapse the editorial
            // stack. Fixed height + Fit keeps the logo's aspect within it.
            modifier = Modifier
                .height(80.dp)
                .widthIn(max = 325.dp),
        )
        else -> Box(
            modifier = Modifier.height(80.dp).clipToBounds(),
            contentAlignment = Alignment.BottomStart,
        ) { HeroTextTitle(title = title) }
    }
}

@Composable
private fun HeroTextTitle(title: String) {
    val parts = remember(title) { splitDisplayTitle(title) }
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = parts.first,
            style = heroDisplayHero,
            color = Color.White,
            textAlign = TextAlign.Start,
            maxLines = 2,
        )
        parts.second?.let { sub ->
            Text(
                text = sub,
                style = heroDisplayHero.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                    lineHeight = 22.sp,
                    // tvOS `.tracking(1.5)` → 0.75sp at half scale.
                    letterSpacing = 0.75.sp,
                ),
                color = Color.White.copy(alpha = 0.95f),
                textAlign = TextAlign.Start,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun EpisodeHierarchyTitle(
    seriesTitle: String,
    episodeTitle: String,
    logoUrl: String?,
) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = seriesTitle,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                modifier = Modifier
                    .height(64.dp)
                    .widthIn(max = 325.dp),
            )
        } else {
            Text(
                text = seriesTitle,
                style = heroDisplayHero,
                color = Color.White,
                textAlign = TextAlign.Start,
                maxLines = 2,
            )
        }
        Text(
            text = episodeTitle,
            style = heroDisplayHero.copy(
                fontWeight = FontWeight.Medium,
                // The episode hierarchy shares the compact Series hero with
                // its persistent actions. A slightly smaller two-line title
                // keeps long episode names readable without colliding with
                // Play or the selector below.
                fontSize = 20.sp,
                lineHeight = 22.sp,
            ),
            color = Color.White.copy(alpha = 0.94f),
            textAlign = TextAlign.Start,
            maxLines = 2,
        )
    }
}

@Composable
private fun SourceRow(tokens: List<String>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        tokens.forEachIndexed { index, token ->
            if (index > 0) {
                Text(
                    text = "·",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Text(
                text = token,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MetadataRow(
    tokens: List<TvHeroFactToken>,
    sourceTokens: List<String>,
    ratingChip: String?,
    compactRating: Boolean = false,
) {
    // Like tvOS: the source tokens (type, genres) give way first, then
    // ratings from the end of the list, so the other facts and any playback
    // formats stay visible. External ratings (IMDb, RT, TMDB) sit on their own
    // row directly under the year / runtime / seasons line.
    val ratingTokens = tokens.filterIsInstance<TvHeroFactToken.ExternalRating>()
    val factTokens = tokens - ratingTokens.toSet()
    val style = LocalTextStyle.current.merge(
        TextStyle(
            fontFamily = org.siloserver.silo.tv.ui.theme.VantageMonoFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.5.sp,
            color = org.siloserver.silo.tv.ui.theme.Level.Dim,
        ),
    )
    // One line: the certificate, the facts, then the ratings. The facts box is a fixed height, so a
    // second row of ratings under it was clipped away.
    TvFactsRow(
        tokens = factTokens + ratingTokens + sourceTokens.map { TvHeroFactToken.TextToken(it, givesWayFirst = true) },
        style = style,
        dividerColor = Color.White.copy(alpha = 0.45f),
        ratingsDropFirst = true,
        leading = ratingChip?.takeIf { it.isNotBlank() }?.let { rating ->
            { RatingChip(text = rating, compact = compactRating) }
        },
    )
}

@Composable
private fun RatingChip(text: String, compact: Boolean) {
    // Vantage: the certificate is the solid white chip.
    org.siloserver.silo.tv.ui.components.vantage.VChip(text.uppercase(), org.siloserver.silo.tv.ui.components.vantage.ChipTone.Solid)
}

private fun splitDisplayTitle(raw: String): Pair<String, String?> {
    val separators = listOf(": ", " — ", " – ", " - ")
    for (sep in separators) {
        val idx = raw.indexOf(sep)
        if (idx > 0) {
            val head = raw.substring(0, idx).trim()
            val tail = raw.substring(idx + sep.length).trim()
            if (head.isNotEmpty() && tail.isNotEmpty()) return head to tail
        }
    }
    return raw to null
}

/** Approved tvOS detail geometry mapped onto Android TV's half-scale canvas. */
private const val HERO_HEIGHT_FRACTION = 690f / 1080f
private const val ARTWORK_WIDTH_FRACTION = 0.64f
private const val ARTWORK_HEIGHT_FRACTION = 0.70f
private val HERO_TOP_INSET = 24.dp

/** The top inset given back, so the rest of the page moves up with the title. */
private val HERO_TOP_TIGHTEN = 34.dp
private val SERIES_LARGE_CARD_LIFT = 16.dp
private val HERO_CONTENT_MAX_WIDTH = 450.dp
private val HERO_CONTENT_SPACING = 9.dp
private val EDITORIAL_SPACING = 7.dp
private val SERIES_METADATA_SLOT_HEIGHT = 18.dp
private val SYNOPSIS_PILL_HEIGHT = 36.dp
private val SERIES_RATINGS_ROW_HEIGHT = 24.dp
private val SERIES_EPISODE_SYNOPSIS_HEIGHT = 88.dp

/** Four synopsis lines (17.5dp each): the synopsis slot is always this tall. */
private val SYNOPSIS_SLOT_HEIGHT = 70.dp
private val SERIES_CREDIT_SLOT_HEIGHT = 14.dp

/**
 * Condensed family for the hero display title. tvOS renders the title in SF
 * Pro `.width(.compressed)` at `.black`; Inter has no width axis, so this
 * uses Inter Tight Black — the official narrow Inter companion — keeping the
 * hero on-brand with the app's Inter type system. (The most condensed system
 * alternative is `DeviceFontFamilyName("sans-serif-condensed")` / Roboto
 * Condensed, which is narrower but off-brand.)
 */
private val heroCondensedFamily = FontFamily(
    Font(R.font.inter_tight_black, weight = FontWeight.Bold),
    Font(R.font.inter_tight_black, weight = FontWeight.ExtraBold),
    Font(R.font.inter_tight_black, weight = FontWeight.Black),
)

/**
 * Hero display title — primary line. Mirrors tvOS `TVHeroTitle` /
 * `TVEpisodeHierarchyTitle` (92pt `.black` `.width(.compressed)`) on Android
 * TV's half-scale layout canvas, via the condensed device family above. Kept
 * local so the shared home `heroDisplay` token stays unchanged.
 */
private val heroDisplayHero = TextStyle(
    fontFamily = org.siloserver.silo.tv.ui.theme.VantageUiFamily,
    fontWeight = FontWeight.SemiBold,
    // tvOS uses 92pt in its 1920x1080 POINT canvas; Android TV is a 960x540 DP
    // canvas (≈half) → 46sp, trimmed to 42sp per design review (2026-07-11).
    fontSize = 42.sp,
    lineHeight = 46.sp,
    letterSpacing = 0.sp,
    textDirection = TextDirection.Content,
    // Apple shadows the hero title (black@0.55, r16, y4) for legibility on
    // bright backdrops. Inherited by the subtitle/episode `.copy()` variants.
    shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 4f), blurRadius = 16f),
)
