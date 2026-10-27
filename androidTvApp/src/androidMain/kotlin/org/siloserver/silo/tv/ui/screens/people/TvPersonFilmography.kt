package org.siloserver.silo.tv.ui.screens.people

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import kotlinx.coroutines.launch
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.catalog.FilmographyCredit
import org.siloserver.silo.model.catalog.FilmographyLibraryFilter
import org.siloserver.silo.model.catalog.FilmographyTypeFilter
import org.siloserver.silo.model.catalog.Person
import org.siloserver.silo.model.catalog.groupByYear
import org.siloserver.silo.tv.ui.components.TvCatalogEmptyState
import org.siloserver.silo.tv.ui.components.TvDialogDefaults
import org.siloserver.silo.tv.ui.components.tvDialogSurface
import org.siloserver.silo.tv.ui.focus.TvContentInitialFocusMaxAttempts
import org.siloserver.silo.tv.ui.focus.claimFocusOrReport
import org.siloserver.silo.tv.ui.focus.requestFocusUntilObserved
import org.siloserver.silo.tv.ui.screens.auth.QrCodePanel
import org.siloserver.silo.tv.ui.theme.DarkSurfaceElevated
import org.siloserver.silo.tv.ui.theme.Spacing

/**
 * The person page when the server lists everything the person worked on:
 * Plex-style, one row per title under year headings. A title in the library
 * opens its page; any other opens a details panel with a code to request it.
 */
@Composable
internal fun TvFullFilmographyContent(
    person: Person,
    state: TvPersonDetailUiState,
    onTypeSelected: (FilmographyTypeFilter) -> Unit,
    onLibrarySelected: (FilmographyLibraryFilter) -> Unit,
    onToggleSelf: () -> Unit,
    onOpenItemDetail: (contentId: String) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val chipFocusRequester = remember { FocusRequester() }
    val bioFocusRequester = remember { FocusRequester() }
    var chipsHaveFocus by remember { mutableStateOf(false) }
    var initialFocusDone by rememberSaveable(person.id) { mutableStateOf(false) }
    var selected by remember { mutableStateOf<FilmographyCredit?>(null) }
    val hasBio = remember(person.bio) { cleanPersonBio(person.bio) != null }
    val credits = state.filmography?.credits.orEmpty()
    val visible = state.visibleCredits
    val groups = remember(visible) { visible.groupByYear() }
    val owned = remember(credits) { credits.count { it.inLibrary } }
    val hasSelf = remember(credits) { credits.any { it.isOnlySelf } }

    // Enter on the chips so the identity header stays in view, as the grid page does.
    LaunchedEffect(Unit) {
        if (initialFocusDone) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        requestFocusUntilObserved(
            maxAttempts = TvContentInitialFocusMaxAttempts,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = chipFocusRequester::requestFocus,
            isFocused = { chipsHaveFocus },
        )
        initialFocusDone = true
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.safeArea, top = 24.dp, end = Spacing.safeArea, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "header") {
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                PersonHeader(person = person, bioFocusRequester = bioFocusRequester)
                FilmographyChips(
                    state = state,
                    total = credits.size,
                    owned = owned,
                    hasSelf = hasSelf,
                    chipFocusRequester = chipFocusRequester,
                    onFocusChanged = { chipsHaveFocus = it },
                    onMoveUp = {
                        if (hasBio) bioFocusRequester.claimFocusOrReport(target = "person_bio", action = "focus_bio")
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    onTypeSelected = onTypeSelected,
                    onLibrarySelected = onLibrarySelected,
                    onToggleSelf = onToggleSelf,
                )
            }
        }
        if (groups.isEmpty()) {
            item(key = "empty") { TvCatalogEmptyState(message = "Nothing matches these filters.") }
        }
        groups.forEach { (label, groupCredits) ->
            item(key = "year-$label") {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = PersonSecondaryText,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(groupCredits, key = { it.key }) { credit ->
                CreditRow(
                    credit = credit,
                    onClick = {
                        val itemId = credit.itemId
                        if (itemId != null) onOpenItemDetail(itemId) else selected = credit
                    },
                )
            }
        }
    }

    selected?.let { credit -> TvCreditDialog(credit = credit, onDismiss = { selected = null }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilmographyChips(
    state: TvPersonDetailUiState,
    total: Int,
    owned: Int,
    hasSelf: Boolean,
    chipFocusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onTypeSelected: (FilmographyTypeFilter) -> Unit,
    onLibrarySelected: (FilmographyLibraryFilter) -> Unit,
    onToggleSelf: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Filmography",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Text(
                text = "${if (total == 1) "1 title" else "$total titles"} · $owned in your library",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = PersonSecondaryText,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChanged(it.hasFocus) }
                .onPreviewKeyEvent { event ->
                    // Only the first chip row walks up into the header; the
                    // identity header is not otherwise focusable.
                    if (event.key == Key.DirectionUp) {
                        if (event.type == KeyEventType.KeyDown) onMoveUp()
                        true
                    } else {
                        false
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilmographyTypeFilter.entries.forEach { filter ->
                FilterChoiceChip(
                    label = filter.title,
                    selected = filter == state.filmographyType,
                    onClick = { onTypeSelected(filter) },
                    modifier = if (filter == state.filmographyType) Modifier.focusRequester(chipFocusRequester) else Modifier,
                )
            }
            Spacer(Modifier.width(12.dp))
            FilmographyLibraryFilter.entries.forEach { filter ->
                FilterChoiceChip(
                    label = filter.title,
                    selected = filter == state.filmographyLibrary,
                    onClick = { onLibrarySelected(filter) },
                )
            }
            if (hasSelf) {
                Spacer(Modifier.width(12.dp))
                FilterChoiceChip(
                    label = "Appearances as themselves",
                    selected = state.showSelfAppearances,
                    onClick = onToggleSelf,
                )
            }
        }
    }
}

@Composable
private fun CreditPoster(credit: FilmographyCredit, width: Int) {
    Box(
        modifier = Modifier
            .size(width = width.dp, height = (width * 1.5f).dp)
            .clip(RoundedCornerShape(4.dp))
            .background(DarkSurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (credit.posterUrl != null) {
            ThumbhashImage(
                url = credit.posterUrl,
                thumbhash = null,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = credit.title.take(1).uppercase(),
                fontSize = (width / 3).sp,
                fontWeight = FontWeight.SemiBold,
                color = PersonSecondaryText,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CreditRow(credit: FilmographyCredit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)), shape = shape),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            CreditPoster(credit, width = 44)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = credit.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(if (credit.isSeries) "TV" else "Movie", credit.roleSummary)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = PersonSecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LibraryBadge(inLibrary = credit.inLibrary)
        }
    }
}

@Composable
private fun LibraryBadge(inLibrary: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (inLibrary) Color(0xFF2E7D32).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (inLibrary) "✓ In library" else "Not in library",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            color = if (inLibrary) Color.White else PersonSecondaryText,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * A title outside the library: poster, role and synopsis, and, when the
 * server has a request server, a code to scan with a phone to request it.
 * Follows the bio dialog's window-level Popup idiom; Back closes it.
 */
@Composable
private fun TvCreditDialog(credit: FilmographyCredit, onDismiss: () -> Unit) {
    val closeFocus = remember { FocusRequester() }
    var closeFocused by remember { mutableStateOf(false) }
    LaunchedEffect(credit.key) {
        kotlinx.coroutines.delay(50)
        requestFocusUntilObserved(
            maxAttempts = TvContentInitialFocusMaxAttempts,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = closeFocus::requestFocus,
            isFocused = { closeFocused },
        )
    }
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(TvDialogDefaults.Scrim),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .tvDialogSurface()
                    .padding(horizontal = 32.dp, vertical = 28.dp),
            ) {
                CreditPoster(credit, width = 150)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = credit.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    Text(
                        text = listOfNotNull(
                            if (credit.isSeries) "TV series" else "Movie",
                            credit.yearLabel,
                            credit.voteAverage?.takeIf { it > 0 }?.let { "★ ${"%.1f".format(it)}" },
                            "Not in your library",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = PersonSecondaryText,
                    )
                    if (credit.roleSummary.isNotBlank()) {
                        Text(
                            text = credit.roleSummary,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                            color = Color.White,
                        )
                    }
                    Text(
                        text = credit.overview ?: "No synopsis yet.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 7,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        credit.requestUrl?.let { url ->
                            QrCodePanel(content = url, size = 120.dp, description = "Code to request ${credit.title}")
                            Text(
                                text = "Scan to request it in Seerr",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = PersonSecondaryText,
                                modifier = Modifier.width(150.dp),
                            )
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .focusRequester(closeFocus)
                                .onFocusChanged { closeFocused = it.isFocused },
                        ) {
                            Text("Close", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
