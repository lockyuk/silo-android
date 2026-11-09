package org.siloserver.silo.android.ui.screens.people

import org.siloserver.silo.common.ui.rememberLibraryResolvedCredit
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import org.siloserver.silo.android.ui.screens.detail.TitlePreviewDialog
import org.siloserver.silo.android.ui.theme.PillShape
import org.siloserver.silo.android.ui.theme.SiloOnSurface
import org.siloserver.silo.android.ui.theme.SiloSecondaryText
import org.siloserver.silo.android.ui.theme.SiloSurfaceElevated
import org.siloserver.silo.android.ui.theme.SiloSurfaceVariant
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.catalog.Person
import org.siloserver.silo.model.catalog.PluginCredit
import org.siloserver.silo.model.catalog.PluginLibraryFilter
import org.siloserver.silo.model.catalog.PluginTypeFilter
import org.siloserver.silo.model.catalog.filterCredits
import org.siloserver.silo.model.catalog.formattedPersonDate
import org.siloserver.silo.model.catalog.personAge
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.network.apiv2.FilmographyPluginApi

/**
 * The person's full filmography from the server's Filmography plugin, or null
 * while loading and when it can't be had (no TMDB id, plugin not installed,
 * offline). The caller then keeps the library-only poster grid.
 */
@Composable
internal fun rememberPluginCredits(person: Person): State<List<PluginCredit>?> {
    val api: FilmographyPluginApi = koinInject()
    val tokens: TokenManager = koinInject()
    return produceState<List<PluginCredit>?>(null, person.id, person.tmdbId) {
        val tmdb = person.tmdbId
        val owner = tokens.snapshotCurrentScope()
        value = if (tmdb.isNullOrBlank() || owner == null) null
        else (api.person(tmdb, owner) as? ApiResult.Success)?.data?.credits
    }
}

private val OwnedTitle = Color(0xFF7FB0FF)
private val MissingTitle = Color(0xFF8FA3BF)
private val YearBadge = Color(0xFF2F4A73)

/** Person page body when the plugin filmography is available: filters + one rich row per credit. */
@Composable
internal fun PersonFullFilmography(
    person: Person,
    isRefreshingMetadata: Boolean,
    credits: List<PluginCredit>,
    onItemClick: (String) -> Unit,
    isLoadingCredits: Boolean = false,
    /** Called just before a credit opens another page, such as to close a sheet this list sits in. */
    onBeforeNavigate: () -> Unit = {},
) {
    var type by remember { mutableStateOf(PluginTypeFilter.All) }
    var library by remember { mutableStateOf(PluginLibraryFilter.All) }
    var showSelf by remember { mutableStateOf(false) }
    // Credits with no date (TBA / upcoming) stay hidden until this is switched on.
    var showTba by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PluginCredit?>(null) }
    val openRequest = org.siloserver.silo.android.ui.screens.detail.LocalOpenRequestDetail.current
    preview?.let { TitlePreviewDialog(it, onDismiss = { preview = null }, onLibraryItem = { id -> preview = null; onItemClick(id) }) }

    val visible = remember(credits, type, library, showSelf, showTba) {
        credits.filterCredits(type, library, showSelf).filter { showTba || it.year != null }
    }
    val tbaCount = remember(credits) { credits.count { it.year == null } }
    val hasSelf = remember(credits) { credits.any { it.isSelf } }

    Box(modifier = Modifier.fillMaxSize()) {
    // The person's photo, darkened and faded into the page colour, so the page
    // matches the title pages instead of sitting on plain black.
    PersonBackdrop(person)
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 56.dp, bottom = 8.dp),
            ) {
                PersonDetailsBlock(person = person, credits = credits, isRefreshingMetadata = isRefreshingMetadata)
                Text("Credits", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SiloOnSurface, modifier = Modifier.padding(top = 12.dp))
                ChipRow {
                    PluginTypeFilter.entries.forEach { f -> Chip(f.title, f == type) { type = f } }
                }
                ChipRow {
                    PluginLibraryFilter.entries.forEach { f -> Chip(f.title, f == library) { library = f } }
                    if (hasSelf) Chip("As themselves", showSelf) { showSelf = !showSelf }
                    if (tbaCount > 0) Chip("Show TBA ($tbaCount)", showTba) { showTba = !showTba }
                }
            }
        }
        if (visible.isEmpty()) {
            item { Text(if (credits.isEmpty()) (if (isLoadingCredits) "Loading credits…" else "Credits are not available right now. Check that the Filmography plugin is installed and has its TMDB key.") else "Nothing matches these filters.", color = SiloSecondaryText, fontSize = 14.sp, modifier = Modifier.padding(vertical = 24.dp)) }
        }
        itemsIndexed(visible, key = { _, it -> "${if (it.isSeries) "tv" else "movie"}-${it.tmdbId}" }) { index, credit ->
            val showYear = index == 0 || visible[index - 1].yearLabel != credit.yearLabel
            CreditRow(credit, showYear = showYear, isFirst = index == 0, isLast = index == visible.lastIndex) {
                val id = credit.itemId
                onBeforeNavigate()
                when {
                    id != null -> onItemClick(id)
                    openRequest != null -> openRequest(
                        if (credit.isSeries) org.siloserver.silo.model.request.RequestMediaType.Series else org.siloserver.silo.model.request.RequestMediaType.Movie,
                        credit.tmdbId,
                    )
                    else -> preview = credit
                }
            }
        }
    }
    }
}

@Composable
private fun PersonBackdrop(person: Person) {
    val page = androidx.compose.material3.MaterialTheme.colorScheme.background
    Box(modifier = Modifier.fillMaxSize()) {
        if (!person.photoUrl.isNullOrBlank()) {
            ThumbhashImage(
                url = person.photoUrl,
                thumbhash = person.photoThumbhash,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(420.dp).alpha(0.55f),
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                // Only fades the photo into the page colour at the bottom; no dark tint over it.
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.38f to Color.Transparent,
                    0.62f to page,
                    1f to page,
                ),
            ),
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) { content() }
}

@Composable
private fun Chip(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = PillShape,
        color = if (isSelected) SiloSurfaceVariant else SiloSurfaceElevated.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (isSelected) 0.16f else 0.08f)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            title,
            fontSize = 12.sp,
            color = if (isSelected) SiloOnSurface else SiloSecondaryText,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/** Born / age / birthplace / credit counts / biography, shown between the portrait and the credits. */
@Composable
private fun PersonDetailsBlock(person: Person, credits: List<PluginCredit>, isRefreshingMetadata: Boolean) {
    var bioExpanded by remember { mutableStateOf(false) }
    val today = remember { java.time.LocalDate.now().toString() }
    val born = formattedPersonDate(person.birthDate)
    val died = formattedPersonDate(person.deathDate)
    val age = personAge(person.birthDate, person.deathDate, today)
    val facts = buildList {
        if (born != null) add("Born" to (born + if (died == null && age != null) " (age $age)" else ""))
        if (died != null) add("Died" to (died + if (age != null) " (age $age)" else ""))
        person.birthplace?.trim()?.takeIf { it.isNotBlank() }?.let { add("Birthplace" to it) }
        val roles = buildList {
            if (credits.any { it.isActing && !it.isSelf }) add("Acting")
            if (credits.any { it.isSelf }) add("Appearances")
        }
        if (roles.isNotEmpty()) add("Known for" to roles.joinToString(", "))
        add("Credits" to "${credits.size} total · ${credits.count { it.inLibrary }} in your library")
        person.homepage?.trim()?.takeIf { it.isNotBlank() }?.let { add("Website" to it) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PersonPortrait(person = person)
            Text(person.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SiloOnSurface, textAlign = TextAlign.Center)
            if (isRefreshingMetadata) Text("Loading metadata…", fontSize = 13.sp, color = SiloSecondaryText)
        }
        if (facts.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(14.dp), color = SiloSurfaceElevated.copy(alpha = 0.7f)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    facts.forEach { (label, value) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(label, fontSize = 15.sp, color = SiloSecondaryText, modifier = Modifier.width(96.dp))
                            Text(value, fontSize = 15.sp, color = SiloOnSurface, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        val bio = person.bio?.trim()?.takeIf { it.isNotBlank() }
        if (bio != null) {
            var overflows by remember(bio) { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    bio,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = SiloOnSurface.copy(alpha = 0.88f),
                    maxLines = if (bioExpanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!bioExpanded) overflows = it.hasVisualOverflow },
                )
                if (overflows || bioExpanded) {
                    org.siloserver.silo.android.ui.screens.detail.ReadMorePill(expanded = bioExpanded, onClick = { bioExpanded = !bioExpanded })
                }
            }
        }
    }
}

/** One credit as plain text: year, title, "as" the character, then library status. No poster, no Request button. */
/** One credit on the timeline: the year once per year on the left, a rail with a dot (green when in the library), then title and character. */
@Composable
private fun CreditRow(rawCredit: PluginCredit, showYear: Boolean, isFirst: Boolean, isLast: Boolean, onClick: () -> Unit) {
    val credit = rememberLibraryResolvedCredit(rawCredit)
    val inLib = credit.inLibrary
    val rail = SiloSurfaceVariant
    val dot = if (inLib) Color(0xFF4BE08A) else Color(0xFF55637A)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).height(IntrinsicSize.Min),
    ) {
        Text(
            if (showYear) (credit.year?.toString() ?: "TBA") else "",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = SiloSecondaryText,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp).padding(top = 7.dp),
        )
        Box(
            modifier = Modifier.width(14.dp).fillMaxHeight().drawBehind {
                val x = size.width / 2
                drawLine(rail, Offset(x, if (isFirst) 13.dp.toPx() else 0f), Offset(x, if (isLast) 13.dp.toPx() else size.height), strokeWidth = 2.dp.toPx())
                drawCircle(Color(0xFF0E1116), radius = 6.dp.toPx(), center = Offset(x, 13.dp.toPx()))
                drawCircle(dot, radius = 4.5.dp.toPx(), center = Offset(x, 13.dp.toPx()))
            },
        )
        Row(
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val role = credit.roleSummary.takeIf { it.isNotBlank() }
            val kind = if (credit.isSeries) "TV series" else null
            val line = listOfNotNull(role?.let { if (credit.isActing || credit.isSelf) "as $it" else it }, kind).joinToString(" · ")
            // Title and character on one line; in-library rows are bright with a tick, the rest dimmed.
            Text(
                text = androidx.compose.ui.text.buildAnnotatedString {
                    append(credit.title)
                    if (line.isNotBlank()) {
                        addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = if (inLib) SiloOnSurface else MissingTitle), 0, credit.title.length)
                        val start = length
                        append("  ·  $line")
                        addStyle(androidx.compose.ui.text.SpanStyle(fontSize = 14.sp, color = if (inLib) SiloOnSurface.copy(alpha = 0.75f) else MissingTitle.copy(alpha = 0.7f)), start, length)
                    } else {
                        addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = if (inLib) SiloOnSurface else MissingTitle), 0, credit.title.length)
                    }
                },
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (inLib) {
                Text("✓", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4BE08A))
            }
        }
    }
}
