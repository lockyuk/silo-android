package org.siloserver.silo.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import org.siloserver.silo.model.catalog.PluginTitle
import org.siloserver.silo.model.catalog.PluginCast
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextAlign
import org.siloserver.silo.android.ui.theme.PillShape
import org.siloserver.silo.android.ui.theme.SiloSurfaceVariant
import org.siloserver.silo.android.ui.theme.SiloOnSurface
import org.siloserver.silo.android.ui.theme.SiloSecondaryText
import org.siloserver.silo.android.ui.theme.SiloSurfaceElevated
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.catalog.CastMember
import org.siloserver.silo.model.catalog.PluginCredit
import org.siloserver.silo.model.catalog.PluginFilmography
import org.siloserver.silo.model.catalog.PluginLibraryFilter
import org.siloserver.silo.model.catalog.PluginTypeFilter
import org.siloserver.silo.model.catalog.filterCredits
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.network.apiv2.FilmographyPluginApi

private sealed interface FilmographyLoad {
    data object Loading : FilmographyLoad
    data class Unavailable(val message: String) : FilmographyLoad
    data class Ready(val filmography: PluginFilmography) : FilmographyLoad
}

/**
 * Opens from a cast tile on a movie or episode page: the actor's whole
 * filmography in place, from the server's Filmography plugin, filterable by
 * type and by whether the title is in the library, one rich row per title.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ActorFilmographySheet(
    member: CastMember,
    onDismiss: () -> Unit,
    onItemClick: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val api: FilmographyPluginApi = koinInject()
    val tokens: TokenManager = koinInject()
    val load by produceState<FilmographyLoad>(FilmographyLoad.Loading, member.tmdbId) {
        val owner = tokens.snapshotCurrentScope()
        val tmdb = member.tmdbId
        value = when {
            owner == null -> FilmographyLoad.Unavailable("Sign in to see this filmography.")
            tmdb.isNullOrBlank() -> FilmographyLoad.Unavailable("This person has no TMDB id, so their filmography can't be listed.")
            else -> when (val r = api.person(tmdb, owner)) {
                is ApiResult.Success -> FilmographyLoad.Ready(r.data)
                is ApiResult.Error -> FilmographyLoad.Unavailable(
                    if (r.message.contains("not installed")) "The Filmography plugin isn't installed on this server. Ask the server admin to add it."
                    else "Couldn't load the filmography. ${r.message}",
                )
                is ApiResult.NetworkError -> FilmographyLoad.Unavailable("Network error. Check your connection.")
            }
        }
    }
    var type by remember { mutableStateOf(PluginTypeFilter.All) }
    var library by remember { mutableStateOf(PluginLibraryFilter.All) }
    var showSelf by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PluginCredit?>(null) }
    val openRequest = LocalOpenRequestDetail.current
    preview?.let { TitlePreviewDialog(it, onDismiss = { preview = null }, onLibraryItem = { id -> preview = null; onItemClick(id) }) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(SiloSurfaceElevated)) {
                    ThumbhashImage(
                        url = member.photoUrl,
                        thumbhash = member.photoThumbhash,
                        contentDescription = member.name,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(member.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = SiloOnSurface)
                    val credits = (load as? FilmographyLoad.Ready)?.filmography?.credits.orEmpty()
                    Text(
                        text = when {
                            credits.isNotEmpty() -> "${credits.size} titles · ${credits.count { it.inLibrary }} in your library"
                            !member.character.isNullOrBlank() -> "as ${member.character}"
                            else -> "Filmography"
                        },
                        fontSize = 13.sp,
                        color = SiloSecondaryText,
                    )
                }
            }
            when (val state = load) {
                FilmographyLoad.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is FilmographyLoad.Unavailable -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(state.message, color = SiloSecondaryText, fontSize = 14.sp)
                }
                is FilmographyLoad.Ready -> {
                    val all = state.filmography.credits
                    val visible = remember(all, type, library, showSelf) { all.filterCredits(type, library, showSelf) }
                    val hasSelf = remember(all) { all.any { it.isSelf } }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        PluginLibraryFilter.entries.forEach { f -> FilterChip(f.title, f == library) { library = f } }
                        PluginTypeFilter.entries.forEach { f -> FilterChip(f.title, f == type) { type = f } }
                        if (hasSelf) FilterChip("As themselves", showSelf) { showSelf = !showSelf }
                    }
                    if (visible.isEmpty()) {
                        Text("Nothing matches these filters.", color = SiloSecondaryText, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                    }
                    val groups = remember(visible) { visible.groupBy { it.yearLabel }.toList() }
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        groups.forEach { (label, credits) ->
                            stickyHeader(key = "year-$label") {
                                Text(
                                    text = label,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF9CC4FF),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                        .padding(top = 12.dp, bottom = 4.dp),
                                )
                            }
                            items(credits, key = { "${if (it.isSeries) "tv" else "movie"}-${it.tmdbId}" }) { credit ->
                                CreditLinkRow(credit) {
                                    val id = credit.itemId
                                    when {
                                        id != null -> onItemClick(id)
                                        openRequest != null -> { onDismiss(); openRequest(credit.requestMediaType(), credit.tmdbId) }
                                        else -> preview = credit
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val OwnedLink = Color(0xFF7FB0FF)
private val MissingLink = Color(0xFF8FA3BF)

/** One credit as a text link: the title, then its type and role. */
@Composable
private fun CreditLinkRow(credit: PluginCredit, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                credit.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (credit.inLibrary) OwnedLink else MissingLink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(if (credit.isSeries) "TV series" else "Movie", credit.roleSummary).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 12.sp,
                color = SiloSecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (credit.inLibrary) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "In library", tint = Color(0xFF66BB6A), modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * A temporary page for a title the library does not have. It is built from the
 * plugin's data, shows no way to play, and disappears when closed.
 */
@Composable
internal fun TitlePreviewDialog(credit: PluginCredit, onDismiss: () -> Unit, onLibraryItem: ((String) -> Unit)? = null) {
    val api: FilmographyPluginApi = koinInject()
    val tokens: TokenManager = koinInject()
    val uriHandler = LocalUriHandler.current
    // The extra detail loads after the page opens; the page works without it.
    val extra by produceState<PluginTitle?>(null, credit.tmdbId) {
        val owner = tokens.snapshotCurrentScope() ?: return@produceState
        value = (api.title(credit.isSeries, credit.tmdbId, owner) as? ApiResult.Success)?.data
    }
    var actor by remember { mutableStateOf<PluginCast?>(null) }
    actor?.let { c ->
        // Opens the actor's own filmography; there is no library profile to link to for this person.
        ActorFilmographySheet(
            member = CastMember(name = c.name, character = c.character, tmdbId = c.tmdbId, photoUrl = c.photoUrl),
            onDismiss = { actor = null },
            onItemClick = { id -> actor = null; onDismiss(); onLibraryItem?.invoke(id) },
            onOpenProfile = {},
        )
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(260.dp).background(SiloSurfaceElevated)) {
                ThumbhashImage(
                    url = credit.backdropUrl ?: credit.posterUrl,
                    thumbhash = null,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart),
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                Text(
                    credit.title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = SiloOnSurface,
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 32.dp),
            ) {
                val info = extra
                Text(
                    listOfNotNull(
                        credit.year?.toString(),
                        info?.runtimeMinutes?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" },
                        info?.rating,
                        if (credit.isSeries) "TV series" else null,
                        credit.voteAverage?.let { "★ ${"%.1f".format(it)}" },
                    ).joinToString(" · "),
                    fontSize = 14.sp,
                    color = SiloSecondaryText,
                )
                org.siloserver.silo.model.catalog.ExternalRatings.tmdb(credit.voteAverage)?.let {
                    RatingGauges(listOf(it), Modifier.fillMaxWidth())
                }
                if (info != null && info.genres.isNotEmpty()) {
                    Text(info.genres.joinToString(", "), fontSize = 14.sp, color = SiloSecondaryText)
                }
                if (credit.roleSummary.isNotBlank()) {
                    Text(credit.roleSummary, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SiloOnSurface)
                }
                Text("Not in your library", fontSize = 12.sp, color = SiloSecondaryText)
                // Placeholder: requests are not wired up yet, so this does nothing.
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Request in Seerr") }
                info?.trailerUrl?.let { url ->
                    OutlinedButton(onClick = { uriHandler.openUri(url) }, modifier = Modifier.fillMaxWidth()) { Text("Watch trailer") }
                }
                info?.tagline?.let { Text(it, fontSize = 14.sp, fontStyle = FontStyle.Italic, color = SiloSecondaryText) }
                Text(
                    credit.overview ?: "No synopsis yet.",
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = SiloOnSurface.copy(alpha = 0.85f),
                )
                if (info != null) {
                    if (info.directors.isNotEmpty()) {
                        Text(
                            (if (credit.isSeries) "Created by " else "Directed by ") + info.directors.joinToString(", "),
                            fontSize = 14.sp,
                            color = SiloSecondaryText,
                        )
                    }
                    val facts = listOfNotNull(
                        info.seasons?.let { if (it == 1) "1 season" else "$it seasons" },
                        info.episodes?.let { "$it episodes" },
                        info.network,
                        info.status,
                    )
                    if (facts.isNotEmpty()) Text(facts.joinToString(" · "), fontSize = 13.sp, color = SiloSecondaryText)
                    if (info.cast.isNotEmpty()) {
                        Text("Cast & Crew", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = SiloOnSurface)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            info.cast.forEach { c ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.width(88.dp).clickable { actor = c },
                                ) {
                                    Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(SiloSurfaceElevated)) {
                                        ThumbhashImage(
                                            url = c.photoUrl,
                                            thumbhash = null,
                                            contentDescription = c.name,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        )
                                    }
                                    Text(c.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SiloOnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                    c.character?.let { Text(it, fontSize = 11.sp, color = SiloSecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = PillShape,
        color = if (isSelected) SiloSurfaceVariant else SiloSurfaceElevated.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (isSelected) 0.16f else 0.08f)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = if (isSelected) SiloOnSurface else SiloSecondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}
