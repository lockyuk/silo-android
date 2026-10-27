package org.siloserver.silo.android.ui.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.siloserver.silo.android.ui.theme.SiloOnSurface
import org.siloserver.silo.android.ui.theme.SiloSecondaryText
import org.siloserver.silo.android.ui.theme.SiloSurfaceElevated
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.catalog.FilmographyCredit
import org.siloserver.silo.model.catalog.FilmographyLibraryFilter
import org.siloserver.silo.model.catalog.FilmographyTypeFilter
import org.siloserver.silo.model.catalog.Person
import org.siloserver.silo.model.catalog.groupByYear

/**
 * The person page when the server lists everything the person worked on:
 * Plex-style, one row per title under year headings. A title in the library
 * opens its page; any other opens a sheet that can send it to the request
 * server.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonFullFilmography(
    person: Person,
    state: PersonDetailUiState,
    onTypeSelected: (FilmographyTypeFilter) -> Unit,
    onLibrarySelected: (FilmographyLibraryFilter) -> Unit,
    onToggleSelf: () -> Unit,
    onItemClick: (String) -> Unit,
) {
    var selected by remember { mutableStateOf<FilmographyCredit?>(null) }
    val credits = state.filmography?.credits.orEmpty()
    val groups = remember(state.visibleCredits) { state.visibleCredits.groupByYear() }
    val owned = remember(credits) { credits.count { it.inLibrary } }
    val hasSelf = remember(credits) { credits.any { it.isOnlySelf } }

    LazyColumn(
        contentPadding = PaddingValues(start = SafePadding, end = SafePadding, bottom = LargePadding),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 56.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PersonHeader(person = person, isRefreshingMetadata = state.isRefreshingMetadata)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Filmography", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = SiloOnSurface)
                    Text(
                        text = "${if (credits.size == 1) "1 title" else "${credits.size} titles"} · $owned in your library",
                        fontSize = 13.sp,
                        color = SiloSecondaryText,
                        modifier = Modifier.padding(bottom = 1.dp),
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilmographyTypeFilter.entries.forEach { filter ->
                        FilterChip(filter.title, filter == state.filmographyType) { onTypeSelected(filter) }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilmographyLibraryFilter.entries.forEach { filter ->
                        FilterChip(filter.title, filter == state.filmographyLibrary) { onLibrarySelected(filter) }
                    }
                    if (hasSelf) {
                        FilterChip("Appearances as themselves", state.showSelfAppearances, onToggleSelf)
                    }
                }
            }
        }
        if (groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Nothing matches these filters.",
                    color = SiloSecondaryText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        groups.forEach { (label, groupCredits) ->
            item(key = "year-$label") {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SiloSecondaryText,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(groupCredits, key = { it.key }) { credit ->
                CreditRow(credit) {
                    val itemId = credit.itemId
                    if (itemId != null) onItemClick(itemId) else selected = credit
                }
            }
        }
    }

    selected?.let { credit -> CreditSheet(credit = credit, onDismiss = { selected = null }) }
}

@Composable
private fun CreditPoster(credit: FilmographyCredit, width: Int) {
    Box(
        modifier = Modifier
            .size(width = width.dp, height = (width * 1.5f).dp)
            .clip(RoundedCornerShape(4.dp))
            .background(SiloSurfaceElevated),
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
            Text(credit.title.take(1).uppercase(), fontSize = (width / 3).sp, color = SiloSecondaryText)
        }
    }
}

@Composable
private fun CreditRow(credit: FilmographyCredit, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        CreditPoster(credit, width = 40)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                credit.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = SiloOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(if (credit.isSeries) "TV" else "Movie", credit.roleSummary).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 12.sp,
                color = SiloSecondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (credit.inLibrary) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "In library",
                tint = Color(0xFF66BB6A),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreditSheet(credit: FilmographyCredit, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CreditPoster(credit, width = 96)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    Text(credit.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = SiloOnSurface)
                    Text(
                        listOfNotNull(
                            if (credit.isSeries) "TV series" else "Movie",
                            credit.yearLabel,
                            credit.voteAverage?.takeIf { it > 0 }?.let { "★ ${"%.1f".format(it)}" },
                        ).joinToString(" · "),
                        fontSize = 13.sp,
                        color = SiloSecondaryText,
                    )
                    if (credit.roleSummary.isNotBlank()) {
                        Text(credit.roleSummary, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SiloOnSurface)
                    }
                    Text("Not in your library", fontSize = 12.sp, color = SiloSecondaryText)
                }
            }
            Text(
                credit.overview ?: "No synopsis yet.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = SiloOnSurface.copy(alpha = 0.85f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                credit.requestUrl?.let { url ->
                    Button(onClick = { uriHandler.openUri(url) }) { Text("Request in Seerr") }
                }
                credit.tmdbId?.let { id ->
                    OutlinedButton(onClick = {
                        uriHandler.openUri("https://www.themoviedb.org/${if (credit.isSeries) "tv" else "movie"}/$id")
                    }) { Text("TMDB") }
                }
            }
        }
    }
}
