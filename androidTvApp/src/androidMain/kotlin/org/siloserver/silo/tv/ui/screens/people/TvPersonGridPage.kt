package org.siloserver.silo.tv.ui.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.koin.compose.koinInject
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.catalog.Person
import org.siloserver.silo.model.catalog.PluginCredit
import org.siloserver.silo.model.catalog.PluginLibraryFilter
import org.siloserver.silo.model.catalog.PluginTypeFilter
import org.siloserver.silo.model.catalog.filterCredits
import org.siloserver.silo.model.catalog.formattedPersonDate
import org.siloserver.silo.model.catalog.personMetadataBadges
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.network.apiv2.FilmographyPluginApi
import org.siloserver.silo.tv.ui.components.vantage.ChipTone
import org.siloserver.silo.tv.ui.components.vantage.VBar
import org.siloserver.silo.tv.ui.components.vantage.VChip
import org.siloserver.silo.tv.ui.components.vantage.VLabel
import org.siloserver.silo.tv.ui.components.vantage.VPanel
import org.siloserver.silo.tv.ui.components.vantage.VTabChip
import org.siloserver.silo.tv.ui.components.vantage.VTag
import org.siloserver.silo.tv.ui.components.vantage.mono
import org.siloserver.silo.tv.ui.components.vantage.mx
import org.siloserver.silo.tv.ui.components.vantage.ui
import org.siloserver.silo.tv.ui.screens.detail.TvGridRing
import org.siloserver.silo.tv.ui.theme.Level
import org.siloserver.silo.tv.ui.theme.TvGrid
import java.time.LocalDate

/**
 * The person page on the shared grid (mock-up "Actor, filmography"): name in the logo box,
 * biography, a Details card and an In-your-library card on the left, and every credit as one
 * vertical list on the right (year, still, title with character, and its library status).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvPersonGridPage(
    person: Person,
    onOpenItemDetail: (String) -> Unit,
    onOpenRequest: (mediaType: String, tmdbId: Int) -> Unit,
) {
    val api: FilmographyPluginApi = koinInject()
    val tokens: TokenManager = koinInject()
    val credits by produceState<List<PluginCredit>?>(null, person.id, person.tmdbId) {
        val tmdb = person.tmdbId
        val owner = tokens.snapshotCurrentScope()
        value = if (tmdb.isNullOrBlank() || owner == null) null
        else (api.person(tmdb, owner) as? ApiResult.Success)?.data?.credits
    }
    val all = credits.orEmpty()
    val inLibrary = all.count { it.inLibrary }
    val badges = remember(person) { personMetadataBadges(person, todayIso = LocalDate.now().toString()) }
    val grid = TvGrid

    Box(Modifier.fillMaxSize()) {
        // Kicker.
        Row(
            Modifier.offset(x = grid.edge, y = grid.kickerTop).width(grid.leftW).height(grid.kickerH),
            horizontalArrangement = Arrangement.spacedBy(8.mx), verticalAlignment = Alignment.CenterVertically,
        ) {
            VChip("ACTOR")
            if (all.isNotEmpty()) VChip("${all.size} CREDITS")
            badges.firstOrNull { it.startsWith("Born") }?.let { Spacer(Modifier.width(6.mx)); VTag(it.removePrefix("Born ")) }
        }
        // Name, fitted in the logo box.
        Box(Modifier.offset(x = grid.edge, y = grid.logoTop).size(grid.logoW, grid.logoH), contentAlignment = Alignment.CenterStart) {
            Text(
                person.name.uppercase(),
                style = ui(if (person.name.length > 16) 40 else 54, FontWeight.Bold),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        // Facts.
        Row(
            Modifier.offset(x = grid.edge, y = grid.factsTop).width(grid.leftW).height(grid.factsH),
            horizontalArrangement = Arrangement.spacedBy(8.mx), verticalAlignment = Alignment.CenterVertically,
        ) {
            badges.filter { it.endsWith("years old") }.forEach { VChip(it.uppercase(), tone = ChipTone.Solid) }
            if (all.isNotEmpty()) VChip("$inLibrary IN LIBRARY")
        }
        // Headshot, beside the biography.
        Box(
            Modifier.offset(x = grid.edge + 452.mx, y = grid.synopsisTop).size(104.mx, 139.mx)
                .clip(RoundedCornerShape(12.mx)).background(androidx.compose.ui.graphics.Color(0xFF151824))
                .border(1.5.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.mx)),
            contentAlignment = Alignment.Center,
        ) {
            if (!person.photoUrl.isNullOrBlank()) {
                ThumbhashImage(
                    url = person.photoUrl, thumbhash = person.photoThumbhash, contentDescription = person.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(person.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase(), style = ui(30, FontWeight.SemiBold, Level.Dim))
            }
        }
        // Biography: five lines.
        Box(Modifier.offset(x = grid.edge, y = grid.synopsisTop).width(440.mx).height(grid.synopsisH).clipToBounds()) {
            val bio = cleanPersonBio(person.bio)
            Text(
                bio ?: "No biography or personal details are available yet.",
                style = ui(16, FontWeight.Normal, Level.Dim),
                maxLines = 5, overflow = TextOverflow.Ellipsis,
            )
        }
        // Details card.
        VPanel(
            Modifier.offset(x = grid.edge, y = 504.mx).width(272.mx).height(186.mx),
            title = "Details", sub = "TMDB", gap = 8.mx,
            padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.mx, vertical = 14.mx),
        ) {
            val rows = buildList {
                formattedPersonDate(person.birthDate)?.let { add("Born" to it) }
                person.birthplace?.takeIf { it.isNotBlank() }?.let { add("Birthplace" to it) }
                badges.firstOrNull { it.endsWith("years old") }?.let { add("Age" to it.removeSuffix(" years old")) }
                if (all.isNotEmpty()) add("Credits" to "${all.size} total")
                if (all.isNotEmpty()) add("In library" to "$inLibrary")
            }
            rows.take(5).forEach { (k, v) ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(k, style = mono(12, color = Level.Faint), maxLines = 1)
                    Spacer(Modifier.width(10.mx))
                    Text(v, style = mono(12), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
        }
        // In your library card.
        VPanel(
            Modifier.offset(x = grid.edge + 288.mx, y = 504.mx).width(272.mx).height(186.mx),
            gap = 12.mx,
            padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.mx, vertical = 14.mx),
        ) {
            VLabel("In your library")
            val pct = if (all.isEmpty()) 0 else inLibrary * 100 / all.size
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mx)) {
                TvGridRing(pct, "$inLibrary/${all.size}")
                Text(
                    "$inLibrary on disk\n${all.size - inLibrary} not in library",
                    style = mono(12, color = Level.Dim),
                    lineHeight = androidx.compose.ui.unit.TextUnit(19.2f * 0.75f, androidx.compose.ui.unit.TextUnitType.Sp),
                )
            }
            VBar(pct / 100f)
        }
        // Filmography card: the vertical list.
        if (all.isNotEmpty()) {
            FilmographyCard(
                all = all,
                modifier = Modifier.offset(x = 640.mx, y = grid.rightTop).width(596.mx).height(602.mx),
                onOpenItemDetail = onOpenItemDetail,
                onOpenRequest = onOpenRequest,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilmographyCard(
    all: List<PluginCredit>,
    modifier: Modifier,
    onOpenItemDetail: (String) -> Unit,
    onOpenRequest: (mediaType: String, tmdbId: Int) -> Unit,
) {
    var library by remember { mutableStateOf(PluginLibraryFilter.All) }
    var type by remember { mutableStateOf(PluginTypeFilter.All) }
    val visible = remember(all, library, type) {
        all.filterCredits(type, library, showSelf = false).filter { it.year != null }
    }
    VPanel(
        modifier,
        title = "Filmography", sub = "Credits · ${all.size} total · ${all.count { it.inLibrary }} in library", gap = 10.mx,
        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.mx, vertical = 16.mx),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.mx)) {
            PluginLibraryFilter.entries.forEach { f ->
                VTabChip(f.title, selected = library == f, onFocus = { library = f })
            }
            PluginTypeFilter.entries.filter { it != PluginTypeFilter.All }.forEach { f ->
                VTabChip(f.title, selected = type == f, onFocus = { type = f })
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.mx), modifier = Modifier.fillMaxSize()) {
            items(visible, key = { "${it.tmdbId}-${it.isSeries}" }) { credit ->
                CreditLine(credit, onOpenItemDetail, onOpenRequest)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CreditLine(
    rawCredit: PluginCredit,
    onOpenItemDetail: (String) -> Unit,
    onOpenRequest: (mediaType: String, tmdbId: Int) -> Unit,
) {
    val credit = org.siloserver.silo.common.ui.rememberLibraryResolvedCredit(rawCredit)
    val inLib = credit.inLibrary
    val role = credit.roleSummary.takeIf { it.isNotBlank() }?.let { if (credit.isActing || credit.isSelf) "as $it" else it }
    val sub = listOfNotNull(role, if (credit.isSeries) "TV series" else null).joinToString(" · ")
    Surface(
        onClick = {
            val id = credit.itemId
            if (id != null) onOpenItemDetail(id) else onOpenRequest(if (credit.isSeries) "series" else "movie", credit.tmdbId)
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.mx)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.25.dp, Level.Fg), shape = RoundedCornerShape(12.mx),
            ),
        ),
        modifier = Modifier.fillMaxWidth().height(54.mx),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.mx).then(if (inLib) Modifier else Modifier.alpha(0.55f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.mx),
        ) {
            Text(credit.year?.toString() ?: "", style = mono(13, FontWeight.SemiBold), modifier = Modifier.width(40.mx))
            Box(
                Modifier.size(80.mx, 45.mx).clip(RoundedCornerShape(8.mx))
                    .background(androidx.compose.ui.graphics.Color(0xFF151824)),
            ) {
                val still = credit.backdropUrl ?: credit.posterUrl
                if (!still.isNullOrBlank()) {
                    ThumbhashImage(
                        url = still, thumbhash = null, contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(credit.title, style = ui(14, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub.isNotBlank()) Text(sub, style = mono(11, color = Level.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (inLib) VChip("✓ In library", tone = ChipTone.Good) else VChip("+ Request", tone = ChipTone.Plain)
        }
    }
}
