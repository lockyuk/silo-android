package org.siloserver.silo.tv.ui.screens.people

import org.siloserver.silo.tv.ui.theme.auroraBackground
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import org.siloserver.silo.tv.ui.focus.TvContentInitialFocusMaxAttempts
import org.siloserver.silo.tv.ui.focus.TvFrameRelocationMaxAttempts
import org.siloserver.silo.tv.ui.focus.rememberTvFlatReturnRestoration
import org.siloserver.silo.tv.ui.focus.claimFocusOrReport
import org.siloserver.silo.tv.ui.focus.requestFocusUntilObserved
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.catalog.BrowseItem
import org.siloserver.silo.model.catalog.Person
import org.siloserver.silo.model.catalog.personMetadataBadges
import org.siloserver.silo.tv.ui.components.TvCatalogEmptyState
import org.siloserver.silo.tv.ui.components.TvCatalogGrid
import org.siloserver.silo.tv.ui.components.TvDialogDefaults
import org.siloserver.silo.tv.ui.components.TvErrorScreen
import org.siloserver.silo.tv.ui.components.TvLoadingScreen
import org.siloserver.silo.tv.ui.components.tvDialogSurface
import org.siloserver.silo.tv.ui.theme.DarkSurfaceElevated
import org.siloserver.silo.tv.ui.theme.Spacing
import org.siloserver.silo.tv.ui.theme.tvPresetGridColumns
import java.time.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.launch

/**
 * Android TV person detail surface — the cast/crew profile plus their
 * filmography. Mirrors the phone `PersonDetailScreen`: a header (large portrait
 * + name + birth/death/birthplace badges + bio) over a media-type filter row
 * and a poster grid.
 *
 * TV idioms follow the tvOS person screen: the identity, filters, and
 * filmography form one continuous scrolling surface. `onOpenItemDetail` routes
 * a poster to its detail page.
 */
@Composable
fun TvPersonDetailScreen(
    personId: Long,
    onOpenItemDetail: (contentId: String) -> Unit,
    onBack: () -> Unit,
    onOpenRequest: (mediaType: String, tmdbId: Int) -> Unit = { _, _ -> },
    viewModel: TvPersonDetailViewModel = koinViewModel(
        key = "person-$personId",
        parameters = { parametersOf(personId) },
    ),
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler(enabled = true) { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auroraBackground(),
    ) {
        when {
            state.isLoading && state.person == null -> TvLoadingScreen(
                modifier = Modifier.auroraBackground(),
            )
            state.error != null && state.person == null -> TvErrorScreen(
                message = state.error!!,
                onRetry = viewModel::reload,
                modifier = Modifier.auroraBackground(),
            )
            state.person != null -> TvPersonDetailContent(
                person = state.person!!,
                onOpenItemDetail = onOpenItemDetail,
                onOpenRequest = onOpenRequest,
            )
        }
    }
}

@Composable
internal fun TvPersonDetailContent(
    person: Person,
    onOpenItemDetail: (contentId: String) -> Unit,
    onOpenRequest: (mediaType: String, tmdbId: Int) -> Unit,
) {
    val bioFocusRequester = remember { FocusRequester() }
    val hasBio = remember(person.bio) { cleanPersonBio(person.bio) != null }
    val page = MaterialTheme.colorScheme.background
    Box(modifier = Modifier.fillMaxSize()) {
        // A soft colour wash instead of artwork: a hue picked from the person's name (so each
        // page has its own colour, stable between visits) fading into the page colour.
        val hue = remember(person.name) { (person.name.hashCode().toLong() and 0x7fffffffL).rem(360L).toFloat() }
        val tint = remember(hue) { Color.hsv(hue, 0.55f, 0.55f) }
        Box(
            modifier = Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    0f to tint.copy(alpha = 0.55f),
                    0.6f to tint.copy(alpha = 0.18f),
                    1f to Color.Transparent,
                    start = androidx.compose.ui.geometry.Offset.Zero,
                    end = androidx.compose.ui.geometry.Offset(1400f, 1000f),
                ),
            ),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.4f to Color.Transparent,
                    0.75f to page,
                    1f to page,
                ),
            ),
        )
        val pageScroll = rememberScrollState()
        val pageScope = androidx.compose.runtime.rememberCoroutineScope()
        androidx.compose.runtime.CompositionLocalProvider(
            org.siloserver.silo.tv.ui.screens.detail.LocalTvPageScrollBy provides { dy: Float -> pageScope.launch { pageScroll.animateScrollBy(dy) } },
        ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(pageScroll)
                .padding(start = Spacing.safeArea, end = Spacing.safeArea, top = 40.dp, bottom = 48.dp),
        ) {
            // Focus on the "Read more" pill brings the whole header (portrait, name) back into view.
            Box(modifier = Modifier.onFocusChanged { if (it.hasFocus) pageScope.launch { pageScroll.animateScrollTo(0) } }) {
                PersonHeader(person = person, bioFocusRequester = bioFocusRequester)
            }
            TvPersonCredits(person = person, onOpenItemDetail = onOpenItemDetail, onOpenRequest = onOpenRequest)
        }
        }
    }
}

// ============================================================================
// Header (portrait + name + metadata badges + bio)
// ============================================================================

// tvOS `TVPersonDetailContent.header` at half scale: 300pt portrait, 48pt
// portrait↔text gap, 72pt bold name, 22pt column spacing, 10pt column top
// inset, secondary-gray bio capped at 7 lines / 920pt width.
@Composable
private fun PersonHeader(person: Person, bioFocusRequester: FocusRequester) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PersonPortrait(person = person)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 5.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                text = person.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val badges = personMetadataBadges(person, todayIso = LocalDate.now().toString())
            if (badges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { badge -> MetadataBadge(text = badge) }
                }
            }
            val bio = cleanPersonBio(person.bio)
            if (bio != null) {
                TvExpandablePersonBio(bio = bio, focusRequester = bioFocusRequester)
            } else {
                Text(
                    text = "No biography or personal details are available yet.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                    color = Color.White.copy(alpha = 0.48f),
                    modifier = Modifier.widthIn(max = 530.dp),
                )
            }
        }
    }
}

/**
 * The person bio as a focus stop: reachable by pressing Up from the filter
 * chips, clamped to 7 lines at rest with the [TvExpandableSynopsis]-style
 * focus fill. OK/Select opens the full bio in a scrollable modal overlay
 * ([TvPersonBioDialog]) instead of expanding in place — long bios (which
 * push the whole page down when inlined) scroll inside the modal.
 */
@Composable
private fun TvExpandablePersonBio(
    bio: String,
    focusRequester: FocusRequester,
) {
    var expanded by remember(bio) { mutableStateOf(false) }
    var fullLines by remember(bio) { mutableStateOf(0) }
    val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp)
    val overflows = fullLines > 7
    Column(
        modifier = Modifier.widthIn(max = 530.dp).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            // Zero-size copy that counts lines with no height limit.
            Box(
                Modifier.layout { measurable, constraints ->
                    measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                    layout(0, 0) {}
                },
            ) {
                Text(text = bio, style = style, onTextLayout = { fullLines = it.lineCount })
            }
            // The bio expands where it stands and the rest of the page moves down.
            Text(
                text = bio,
                style = style,
                color = PersonSecondaryText,
                maxLines = if (expanded) Int.MAX_VALUE else 7,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (overflows) {
            org.siloserver.silo.tv.ui.screens.detail.TvReadMorePill(
                expanded = expanded,
                onClick = { expanded = !expanded },
                focusRequester = focusRequester,
            )
        }
    }
}

/**
 * Full-bio modal for TV, following the [TvMediaInfoDialog] idiom: a
 * window-level focusable Popup over a dimmed scrim, D-pad Up/Down scrolls
 * the text, Back or OK dismisses.
 */
@Composable
private fun TvPersonBioDialog(
    bio: String,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var bioModalHasFocus by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50)
        requestFocusUntilObserved(
            maxAttempts = TvContentInitialFocusMaxAttempts,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = focus::requestFocus,
            isFocused = { bioModalHasFocus },
        )
    }
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvDialogDefaults.Scrim),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.56f)
                    .fillMaxHeight(0.76f)
                    .tvDialogSurface()
                    .onPreviewKeyEvent { ev ->
                        when {
                            ev.type == KeyEventType.KeyUp &&
                                (ev.key == Key.Back || ev.key == Key.Escape ||
                                    ev.key == Key.DirectionCenter || ev.key == Key.Enter) -> {
                                onDismiss()
                                true
                            }
                            ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown -> {
                                scrollScope.launch {
                                    scrollState.animateScrollTo(
                                        (scrollState.value + 180).coerceAtMost(scrollState.maxValue),
                                    )
                                }
                                true
                            }
                            ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp -> {
                                scrollScope.launch {
                                    scrollState.animateScrollTo((scrollState.value - 180).coerceAtLeast(0))
                                }
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .focusRequester(focus)
                        .onFocusChanged { bioModalHasFocus = it.hasFocus }
                        .focusable()
                        .padding(horizontal = 32.dp, vertical = 28.dp),
                ) {
                    Text(
                        text = bio,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 16.sp,
                            lineHeight = 23.sp,
                        ),
                        color = Color.White.copy(alpha = 0.82f),
                    )
                }
            }
        }
    }
}

// tvOS `PersonPortrait(width: 300)` at half scale: 150×225dp, 12pt corner
// radius → 6dp, hairline white stroke, initials fallback at width × 0.28.
@Composable
private fun PersonPortrait(person: Person) {
    val width = 150.dp
    val height = (width.value * 1.5f).dp
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(shape)
            .background(DarkSurfaceElevated)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!person.photoUrl.isNullOrBlank()) {
            ThumbhashImage(
                url = person.photoUrl,
                thumbhash = person.photoThumbhash,
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = person.initials(),
                fontSize = 42.sp,
                fontWeight = FontWeight.SemiBold,
                color = PersonSecondaryText,
            )
        }
    }
}

private fun Person.initials(): String {
    val letters = name
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull() }
        .joinToString("")
        .uppercase()
    return letters.ifEmpty { "?" }
}

// tvOS metadata badge (20pt small font, 16×8 capsule padding), sized up from
// the strict halving for readability alongside the bio and filter pills.
@Composable
private fun MetadataBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.Medium,
        )
    }
}

// ============================================================================
// Filmography header (title + count + media-type filter chips)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilmographyHeader(
    selected: TvPersonMediaFilter,
    availableFilters: List<TvPersonMediaFilter>,
    totalLoaded: Int,
    totalItems: Int,
    hasMore: Boolean,
    /**
     * Attaches to the SELECTED chip — the entry point on a fresh arrival, and
     * the chip to restore after a filter change recreates this row.
     */
    selectedFilterFocusRequester: FocusRequester,
    onMoveUp: () -> Unit,
    onSelect: (TvPersonMediaFilter) -> Unit,
) {
    // tvOS `filmographyHeader` at half scale: title-case 36pt semibold
    // headline with the count label sitting beside it at the baseline,
    // 20pt gap down to the filter chips.
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Filmography",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                ),
                color = Color.White,
            )
            tvPersonFilmographyCountLabel(total = totalItems, loaded = totalLoaded, hasMore = hasMore)?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    color = PersonSecondaryText,
                    // Optical baseline alignment against the headline.
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.DirectionUp) {
                        if (event.type == KeyEventType.KeyDown) onMoveUp()
                        // The identity header is intentionally non-focusable;
                        // consume both phases so Compose's geometric search
                        // cannot dead-end or jump outside the page.
                        true
                    } else {
                        false
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            availableFilters.forEachIndexed { index, filter ->
                FilterChoiceChip(
                    label = filter.title,
                    selected = filter == selected,
                    onClick = { onSelect(filter) },
                    // Falls back to the first chip when the selection is not
                    // among the available filters, so the requester is never
                    // left unattached.
                    modifier = if (
                        filter == selected ||
                        (index == 0 && selected !in availableFilters)
                    ) {
                        Modifier.focusRequester(selectedFilterFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val container = when {
        isFocused -> Color.White.copy(alpha = 0.94f)
        selected -> Color.White.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val foreground = when {
        isFocused -> Color.Black
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.85f)
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = foreground,
            focusedContainerColor = Color.White.copy(alpha = 0.94f),
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.94f),
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f),
                ),
                shape = RoundedCornerShape(999.dp),
            ),
            focusedBorder = Border(
                border = BorderStroke(0.dp, Color.Transparent),
                shape = RoundedCornerShape(999.dp),
            ),
        ),
        modifier = modifier,
    ) {
        Text(
            text = label,
            // Sized up from the strict tvOS halving (22pt caption → 11sp)
            // for readability, padding scaled with it.
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
            color = foreground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
        )
    }
}

// Matches BrowseGridColumns — the person grid used 7 columns, so its cards
// rendered smaller than every other grid in the app (QA 2026-07-08). tvOS
// `TVCatalogGrid` runs 6 columns with 40pt column spacing.
private const val PersonGridColumns = 6
private val PersonGridItemSpacing = 20.dp

/** tvOS `continuumSecondaryText` (#99EDEDED). */
private val PersonSecondaryText = Color(0x99EDEDED)

// Server bios often arrive with a dangling "..."/"…" truncation marker —
// on its own line (any Unicode line break) or stacked straight after the
// sentence period ("career...."). Strip the whole trailing dot/whitespace
// run and restore a single period, so the bio ends cleanly; genuinely long
// bios still get Compose's own overflow ellipsis at the 7-line cap.
private fun cleanPersonBio(raw: String?): String? {
    var text = raw?.trim().orEmpty()
    // TMDB bios sourced from Wikipedia carry a trailing attribution
    // paragraph ("Description above from the Wikipedia article …, licensed
    // under CC-BY-SA, full list of contributors on Wikipedia."). The 7-line
    // clamp truncated at the BLANK separator line before it, rendering a
    // lone "…" on its own line. Drop the paragraph — the same net content
    // tvOS displays after its own line clamp, minus the artifact.
    val attributionIndex = text.indexOf(
        "Description above from the Wikipedia",
        ignoreCase = true,
    )
    if (attributionIndex > 0) {
        text = text.substring(0, attributionIndex).trim()
    }
    if (text.endsWith("…") || text.endsWith("...")) {
        text = text.trimEnd { it == '.' || it == '…' || it.isWhitespace() }
        if (text.isNotEmpty() && !text.last().isLetterOrDigit()) {
            // Ends in other punctuation ("?!,\"") — leave it as-is.
        } else if (text.isNotEmpty()) {
            text += "."
        }
    }
    return text.takeIf { it.isNotBlank() }
}

// tvOS `personFilmographyCountLabel`: the known total wins ("72 titles"),
// otherwise the loaded count with a "+" while more pages remain.
private fun tvPersonFilmographyCountLabel(total: Int, loaded: Int, hasMore: Boolean): String? = when {
    total > 0 -> if (total == 1) "1 title" else "$total titles"
    loaded <= 0 -> null
    hasMore -> "$loaded+ titles"
    loaded == 1 -> "1 title"
    else -> "$loaded titles"
}

private fun personWorkCardAspectRatio(item: BrowseItem): Float? =
    if (item.type == "audiobook") {
        1f
    } else {
        null
    }

/** The person header is a single full-span slot ahead of the poster grid. */
private const val PersonGridHeaderSlots: Int = 1
