package org.siloserver.silo.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.siloserver.silo.model.catalog.DetailFacts
import org.siloserver.silo.model.catalog.formatMoney
import org.siloserver.silo.model.catalog.revenueMultiple
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private class Cell(val label: String, val value: String, val note: String? = null, val noteColor: Color? = null, val wide: Boolean = false)

private val Good = Color(0xFF4BE08A)
private val Bad = Color(0xFFFF7A70)

private fun prettyDate(iso: String?): String? = iso?.let {
    runCatching { LocalDate.parse(it.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())) }.getOrNull()
}

private fun runtimeText(minutes: Int): String = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

private fun cells(f: DetailFacts): List<Cell> = buildList {
    val date = prettyDate(f.releaseDate)
    if (date != null || f.year != null) add(Cell(if (f.isSeries) "First aired" else "Released", (f.year ?: f.releaseDate?.take(4)).toString(), note = date))
    f.runtimeMinutes?.let { add(Cell(if (f.isSeries) "Episode length" else "Runtime", runtimeText(it))) }
    f.seasons?.takeIf { f.isSeries }?.let { add(Cell("Seasons", it.toString())) }
    f.budget?.let { add(Cell("Budget", formatMoney(it))) }
    f.revenue?.let { rev ->
        val multiple = revenueMultiple(f.budget, rev)
        add(
            Cell(
                "Revenue", formatMoney(rev),
                note = multiple?.let { "${org.siloserver.silo.model.catalog.ExternalRatings.formatOneDecimal(it)}× the budget" },
                noteColor = multiple?.let { if (it >= 1.0) Good else Bad },
            ),
        )
    }
    f.language?.let { add(Cell("Language", it)) }
    f.status?.let { add(Cell("Status", it)) }
    if (f.networks.isNotEmpty()) add(Cell("Network", f.networks.joinToString(" · "), wide = true))
    if (f.studios.isNotEmpty()) add(Cell("Studios", f.studios.take(5).joinToString(" · "), wide = true))
    if (f.countries.isNotEmpty()) add(Cell("Made in", f.countries.joinToString(" · "), wide = true))
}

/**
 * The Details block under the synopsis: release, runtime, budget, revenue,
 * language, status, studios and countries as a grid. Draws nothing when the
 * facts are empty. Colours are fixed light-on-dark like the title pages.
 */
@Composable
fun DetailFactsBlock(
    facts: DetailFacts,
    modifier: Modifier = Modifier,
    labelSize: Int = 12,
    valueSize: Int = 17,
    cellPadding: Dp = 12.dp,
) {
    if (facts.isEmpty) return
    val all = remember(facts) { cells(facts) }
    if (all.isEmpty()) return
    // Pair the short cells two to a row; wide cells take a row to themselves.
    val rows = remember(all) {
        val out = mutableListOf<List<Cell>>()
        var pending: Cell? = null
        all.filter { !it.wide }.forEach { c ->
            if (pending == null) pending = c else { out += listOf(pending!!, c); pending = null }
        }
        pending?.let { out += listOf(it) }
        all.filter { it.wide }.forEach { out += listOf(it) }
        out
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        BasicText(
            "DETAILS",
            style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.12f)),
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    row.forEach { c ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0xFF1B222E))
                                .padding(cellPadding),
                        ) {
                            BasicText(
                                c.label.uppercase(),
                                style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = labelSize.sp, letterSpacing = 0.9.sp),
                            )
                            BasicText(
                                c.value,
                                style = TextStyle(color = Color.White, fontSize = valueSize.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            c.note?.let {
                                BasicText(
                                    it,
                                    style = TextStyle(color = c.noteColor ?: Color.White.copy(alpha = 0.78f), fontSize = (valueSize - 4).sp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
