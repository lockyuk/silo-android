package org.siloserver.silo.tv.ui.screens.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.koin.compose.koinInject
import org.siloserver.silo.model.catalog.Person
import org.siloserver.silo.model.catalog.PluginCredit
import org.siloserver.silo.model.catalog.PluginLibraryFilter
import org.siloserver.silo.model.catalog.filterCredits
import org.siloserver.silo.model.catalog.PluginTypeFilter
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.network.apiv2.FilmographyPluginApi

private const val InitialRows = 40
private val InLibraryGreen = Color(0xFF4BE08A)
private val MissingGrey = Color(0xFF9AA7B8)

/**
 * The person's full credits from the server's Filmography plugin as plain text:
 * year, title, "as" the character, and whether the title is in the library.
 * Library titles open their page; others are greyed and open the request
 * page. Draws nothing until the plugin answers, or when it cannot.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvPersonCredits(
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
    val all = credits?.takeIf { it.isNotEmpty() } ?: return

    var library by remember { mutableStateOf(PluginLibraryFilter.All) }
    var type by remember { mutableStateOf(PluginTypeFilter.All) }
    var showAll by remember { mutableStateOf(false) }
    // Credits with no date (TBA / upcoming) stay hidden until this is switched on.
    var showTba by remember { mutableStateOf(false) }
    val tbaCount = remember(all) { all.count { it.year == null } }
    val visible = remember(all, library, type, showTba) {
        all.filterCredits(type, library, showSelf = false).filter { showTba || it.year != null }
    }
    val shown = if (showAll) visible else visible.take(InitialRows)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "Credits · ${all.size} total · ${all.count { it.inLibrary }} in your library",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PluginLibraryFilter.entries.forEach { f ->
                Button(onClick = { library = f; showAll = false }) {
                    Text(if (f == library) "● ${f.title}" else f.title, fontSize = 14.sp)
                }
            }
            PluginTypeFilter.entries.filter { it != PluginTypeFilter.All }.forEach { f ->
                Button(onClick = { type = if (type == f) PluginTypeFilter.All else f; showAll = false }) {
                    Text(if (type == f) "● ${f.title}" else f.title, fontSize = 14.sp)
                }
            }
            if (tbaCount > 0) {
                Button(onClick = { showTba = !showTba; showAll = false }) {
                    Text(if (showTba) "● Show TBA ($tbaCount)" else "Show TBA ($tbaCount)", fontSize = 14.sp)
                }
            }
        }
        shown.forEachIndexed { index, credit ->
            val showYear = index == 0 || shown[index - 1].yearLabel != credit.yearLabel
            CreditRow(credit, showYear, index == 0, index == shown.lastIndex, onOpenItemDetail, onOpenRequest)
        }
        if (!showAll && visible.size > InitialRows) {
            Button(onClick = { showAll = true }) { Text("Show all ${visible.size} credits", fontSize = 14.sp) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CreditRow(
    rawCredit: PluginCredit,
    showYear: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onOpenItemDetail: (String) -> Unit,
    onOpenRequest: (mediaType: String, tmdbId: Int) -> Unit,
) {
    val credit = org.siloserver.silo.common.ui.rememberLibraryResolvedCredit(rawCredit)
    val inLib = credit.inLibrary
    val role = credit.roleSummary.takeIf { it.isNotBlank() }
        ?.let { if (credit.isActing || credit.isSelf) "as $it" else it }
    val line = listOfNotNull(role, if (credit.isSeries) "TV series" else null).joinToString(" · ")
    val rail = Color.White.copy(alpha = 0.16f)
    val dot = if (inLib) InLibraryGreen else Color(0xFF55637A)
    Surface(
        onClick = {
            val id = credit.itemId
            if (id != null) onOpenItemDetail(id)
            else onOpenRequest(if (credit.isSeries) "series" else "movie", credit.tmdbId)
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            Text(
                if (showYear) (credit.year?.toString() ?: "TBA") else "",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.End,
                modifier = Modifier.width(60.dp).padding(top = 6.dp),
            )
            Box(
                modifier = Modifier.width(16.dp).fillMaxHeight().drawBehind {
                    val x = size.width / 2
                    val cy = 15.dp.toPx()
                    drawLine(rail, Offset(x, if (isFirst) cy else 0f), Offset(x, if (isLast) cy else size.height), strokeWidth = 2.dp.toPx())
                    drawCircle(Color(0xFF0E1116), radius = 6.dp.toPx(), center = Offset(x, cy))
                    drawCircle(dot, radius = 4.dp.toPx(), center = Offset(x, cy))
                },
            )
            Row(
                modifier = Modifier.weight(1f).padding(vertical = 5.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Title and character on one line. In-library rows are bright with a green tick;
                // the rest are dimmed, so the list itself says what the library has.
                Text(
                    text = androidx.compose.ui.text.buildAnnotatedString {
                        withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = if (inLib) Color.White else MissingGrey)) {
                            append(credit.title)
                        }
                        if (line.isNotBlank()) {
                            withStyle(androidx.compose.ui.text.SpanStyle(fontSize = 16.sp, color = if (inLib) Color.White.copy(alpha = 0.75f) else MissingGrey.copy(alpha = 0.7f))) {
                                append("  ·  $line")
                            }
                        }
                    },
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (inLib) {
                    Text("✓", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = InLibraryGreen)
                }
            }
        }
    }
}
