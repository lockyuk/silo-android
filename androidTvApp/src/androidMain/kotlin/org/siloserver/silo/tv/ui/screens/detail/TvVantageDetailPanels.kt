package org.siloserver.silo.tv.ui.screens.detail

import org.siloserver.silo.tv.ui.components.gradientScrim
import org.siloserver.silo.tv.ui.components.gradientScrimAlpha
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import org.siloserver.silo.model.catalog.EpisodeListItem
import org.siloserver.silo.model.catalog.ItemDetail
import org.siloserver.silo.model.catalog.Season
import org.siloserver.silo.tv.ui.components.vantage.VBar
import org.siloserver.silo.tv.ui.components.vantage.VBig
import org.siloserver.silo.tv.ui.components.vantage.VLabel
import org.siloserver.silo.tv.ui.components.vantage.VPanel
import org.siloserver.silo.tv.ui.components.vantage.mono
import org.siloserver.silo.tv.ui.components.vantage.mx
import org.siloserver.silo.tv.ui.theme.Level
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Vantage detail page's right-hand glass cards: Details (creators, director, genres,
 * languages, next episode, seasons) and Your progress (episodes watched or minutes in, with a
 * bar and where you left off). Display only; focus stays on the hero actions and rows.
 */
@Composable
internal fun TvVantageDetailPanels(
    detail: ItemDetail,
    seasons: List<Season>,
    nextUp: EpisodeListItem?,
    modifier: Modifier = Modifier,
    /** An episode that has not aired: the progress card becomes a countdown. */
    upcoming: EpisodeListItem? = null,
) {
    val isSeries = detail.type.equals("series", true) || detail.type.equals("show", true)
    val rows = buildList {
        val creators = detail.crew.names("creator")
        if (creators != null) add("Created by" to creators)
        detail.crew.names("director")?.let { add((if (isSeries) "Directed by" else "Director") to it) }
        if (!isSeries) detail.crew.names("writer", "screenplay")?.let { add("Written by" to it) }
        detail.genres.filter { it.isNotBlank() }.take(3).takeIf { it.isNotEmpty() }?.let { add("Genres" to it.joinToString(" · ")) }
        languages(detail)?.let { add("Languages" to it) }
        if (isSeries) {
            nextUp?.let { ep ->
                val day = ep.airDate?.let { d -> runCatching { LocalDate.parse(d.take(10)) }.getOrNull() }
                    ?.takeIf { it.isAfter(LocalDate.now().minusDays(1)) }
                    ?.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
                add("Next episode" to "S${ep.seasonNumber} E${ep.episodeNumber}" + (day?.let { " · $it" } ?: ""))
            }
            val real = seasons.filter { !it.isSpecials }
            if (real.isNotEmpty()) {
                val total = real.sumOf { it.episodeCount }
                val watched = real.sumOf { it.userData?.watchedCount ?: 0 }
                add("Seasons" to "${real.size} · $watched of $total watched")
            }
        } else {
            if (detail.runtime > 0) add("Runtime" to if (detail.runtime >= 60) "${detail.runtime / 60}h ${detail.runtime % 60}m" else "${detail.runtime}m")
            detail.studios.firstOrNull()?.let { add("Studio" to it) }
        }
    }.take(5)
    val provider = (detail.networks.firstOrNull() ?: detail.studios.firstOrNull())

    Column(modifier.width(org.siloserver.silo.tv.ui.theme.TvGrid.rightW), verticalArrangement = Arrangement.spacedBy(12.mx)) {
        if (rows.isNotEmpty()) {
            VPanel(Modifier.fillMaxWidth().height(org.siloserver.silo.tv.ui.theme.TvGrid.detailsH), title = "Details", sub = provider, gap = 8.mx, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.mx, vertical = 14.mx)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.mx)) {
                    rows.forEach { (k, v) ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(k, style = mono(12, color = Level.Faint), maxLines = 1)
                            Spacer(Modifier.width(14.mx))
                            Text(
                                v, style = mono(12), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        if (upcoming != null) CountdownCard(upcoming) else ProgressCard(detail, seasons, nextUp, isSeries)
    }
}

@Composable
private fun ProgressCard(detail: ItemDetail, seasons: List<Season>, nextUp: EpisodeListItem?, isSeries: Boolean) {
    val accent = org.siloserver.silo.tv.ui.theme.animatedAccent()
    val ringPct: Int
    val ringLabel: String
    val lines: String
    val fraction: Float
    val note: String?
    if (isSeries) {
        val real = seasons.filter { !it.isSpecials }
        val total = real.sumOf { it.episodeCount }
        if (total <= 0) return
        val watched = real.sumOf { it.userData?.watchedCount ?: 0 }
        val pos = nextUp?.userData?.positionSeconds?.takeIf { it > 0 && nextUp.userData?.played != true }
        val inProgress = if (pos != null) 1 else 0
        ringPct = (watched * 100 / total).coerceIn(0, 100)
        ringLabel = "$watched/$total"
        lines = "$watched watched\n$inProgress in progress\n${(total - watched - inProgress).coerceAtLeast(0)} to go"
        fraction = watched.toFloat() / total
        note = when {
            pos != null -> "Left off ${clock(pos)} · S${nextUp!!.seasonNumber} E${nextUp.episodeNumber}"
            watched >= total -> "All watched"
            nextUp != null -> "Next up · S${nextUp.seasonNumber} E${nextUp.episodeNumber}"
            else -> null
        }
    } else {
        val ud = detail.userData ?: return
        val dur = ud.durationSeconds ?: (detail.runtime * 60.0).takeIf { it > 0 } ?: return
        val pos = ud.positionSeconds ?: 0.0
        if (!ud.played && pos <= 0) return
        val frac = if (ud.played) 1f else (pos / dur).toFloat().coerceIn(0f, 1f)
        ringPct = (frac * 100).toInt()
        ringLabel = if (ud.played) "Seen" else "$ringPct%"
        lines = if (ud.played) "Watched" else "left off ${clock(pos)}\n${((dur - pos) / 60).toInt()} min left"
        fraction = frac
        note = null
    }
    VPanel(
        Modifier.fillMaxWidth().height(org.siloserver.silo.tv.ui.theme.TvGrid.progressH),
        gap = 10.mx,
        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.mx, vertical = 14.mx),
    ) {
        VLabel("Your progress")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mx)) {
            TvGridRing(ringPct, ringLabel)
            Text(
                lines,
                style = mono(12, color = Level.Dim),
                lineHeight = androidx.compose.ui.unit.TextUnit(19.2f * 0.75f, androidx.compose.ui.unit.TextUnitType.Sp),
            )
        }
        VBar(fraction)
        note?.let { Text(it, style = mono(11, color = Level.Faint), maxLines = 1) }
    }
}


private fun clock(sec: Double): String {
    val t = sec.toLong(); val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun List<org.siloserver.silo.model.catalog.CrewMember>.names(vararg jobs: String): String? {
    val picked = filter { m -> val j = m.job?.lowercase().orEmpty(); jobs.any { it in j } }
        .map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()
    if (picked.isEmpty()) return null
    return picked.take(2).joinToString(", ") + if (picked.size > 2) " +${picked.size - 2}" else ""
}

private fun languages(detail: ItemDetail): String? {
    val audio = detail.versions.flatMap { v -> v.audioTracks.orEmpty().mapNotNull { it.language } }
        .map { it.lowercase() }.distinct()
    val subs = detail.subtitles.mapNotNull { it.language?.lowercase() }.distinct()
    fun name(code: String) = runCatching { Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH) }.getOrNull()?.takeIf { it.isNotBlank() && it != code } ?: code.uppercase()
    val main = audio.take(2).joinToString(", ") { name(it) }.ifBlank { detail.originalLanguage?.let { name(it) }.orEmpty() }
    val subText = if (subs.isEmpty()) "" else " · " + subs.take(3).joinToString(", ") { it.uppercase() } + " subs"
    return (main + subText).trim(' ', '·').ifBlank { null }
}


/**
 * Vantage option B: the title's facts as one glass strip across the page, directly above cast
 * and crew. Director (or creators), writers, genres, budget / revenue from TMDB through the
 * filmography plugin (left out when unknown), runtime or seasons, and studio or network.
 */
@Composable
internal fun TvVantageDetailsStrip(
    detail: ItemDetail,
    seasons: List<Season>,
    modifier: Modifier = Modifier,
) {
    val api: org.siloserver.silo.network.apiv2.FilmographyPluginApi = org.koin.compose.koinInject()
    val tokens: org.siloserver.silo.network.TokenManager = org.koin.compose.koinInject()
    val isSeries = detail.type.equals("series", true) || detail.type.equals("show", true)
    val extra by androidx.compose.runtime.produceState<org.siloserver.silo.model.catalog.PluginTitle?>(null, detail.tmdbId, detail.type) {
        val tmdb = detail.tmdbId?.toIntOrNull() ?: return@produceState
        if (detail.type.equals("episode", true)) return@produceState
        val owner = tokens.snapshotCurrentScope() ?: return@produceState
        value = (api.title(isSeries, tmdb, owner) as? org.siloserver.silo.network.ApiResult.Success)?.data
    }
    val cells = buildList {
        val creators = detail.crew.names("creator")
        if (isSeries && creators != null) add("Created by" to creators)
        val director = detail.crew.names("director")
            ?: extra?.directors?.takeIf { it.isNotEmpty() }?.take(2)?.joinToString(", ")
        if (director != null) add("Director" to director)
        if (!isSeries) detail.crew.names("writer", "screenplay")?.let { add("Written by" to it) }
        (detail.genres.ifEmpty { extra?.genres.orEmpty() }).filter { it.isNotBlank() }.take(2)
            .takeIf { it.isNotEmpty() }?.let { add("Genres" to it.joinToString(" · ")) }
        val budget = extra?.budget?.takeIf { it > 0 }
        val revenue = extra?.revenue?.takeIf { it > 0 }
        if (budget != null || revenue != null) add("Budget / Revenue" to "${money(budget)} / ${money(revenue)}")
        if (isSeries) {
            val real = seasons.filter { !it.isSpecials }
            if (real.isNotEmpty()) add("Seasons" to "${real.size} · ${real.sumOf { it.episodeCount }} episodes")
        } else if (detail.runtime > 0) {
            add("Runtime" to if (detail.runtime >= 60) "${detail.runtime / 60}h ${detail.runtime % 60}m" else "${detail.runtime}m")
        }
        (detail.networks.firstOrNull() ?: detail.studios.firstOrNull() ?: extra?.companies?.firstOrNull())
            ?.let { add((if (isSeries && detail.networks.isNotEmpty()) "Network" else "Studio") to it) }
    }.take(6)
    TvVantageFactsStrip(cells, modifier)
}

/** The glass strip of label / value cells (also used by the page for a title that is not in the library). */
@Composable
internal fun TvVantageFactsStrip(
    cells: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    if (cells.isEmpty()) return
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Level.Glass, org.siloserver.silo.tv.ui.theme.VantagePanel)
            .border(1.dp, Level.Edge, org.siloserver.silo.tv.ui.theme.VantagePanel)
            .padding(vertical = 14.mx, horizontal = 6.mx),
    ) {
        cells.forEachIndexed { i, (k, v) ->
            Column(Modifier.weight(1f).padding(horizontal = 16.mx)) {
                VLabel(k)
                Text(v, style = org.siloserver.silo.tv.ui.components.vantage.ui(16, androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.mx))
            }
            if (i < cells.size - 1) {
                androidx.compose.foundation.layout.Box(
                    Modifier.width(1.dp).height(34.mx).background(Level.Edge).align(Alignment.CenterVertically),
                )
            }
        }
    }
}

internal fun money(v: Long?): String = when {
    v == null -> "–"
    v >= 1_000_000_000L -> "$%.1fB".format(v / 1e9)
    v >= 1_000_000L -> "$%.1fM".format(v / 1e6)
    v >= 1_000L -> "$%.0fK".format(v / 1e3)
    else -> "$$v"
}


/**
 * The Vantage page backdrop: the picture covers the whole page and stays put while the page scrolls
 * over it, softening once past the first screen (the layers the library title page uses).
 */
@Composable
internal fun TvVantagePageBackdrop(
    url: String?,
    thumbhash: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    val scrolledPast by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 }
    }
    val softAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (scrolledPast) 1f else 0f, androidx.compose.animation.core.tween(450), label = "pageSoftBackdrop",
    )
    val bg = Level.Bg
    org.siloserver.silo.common.ui.components.ThumbhashImage(
        url = url, thumbhash = thumbhash, contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
    )
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().gradientScrim(
            true,
            0.00f to bg.copy(alpha = 0.62f), 0.45f to bg.copy(alpha = 0.32f), 0.75f to bg.copy(alpha = 0.06f),
        ),
    )
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().gradientScrimAlpha(
            false,
            { 0.55f + 0.35f * softAlpha },
            0.35f to bg.copy(alpha = 0f), 0.75f to bg.copy(alpha = 0.55f), 1.00f to bg.copy(alpha = 0.85f),
        ),
    )
}

private fun Modifier.androidx_graphicsAlpha(a: Float): Modifier = this.graphicsLayer { alpha = a }

/** The progress ring of the title pages: 64 px, label inside (mock-up .ringw). */
@Composable
internal fun TvGridRing(percent: Int, label: String) {
    val accent = org.siloserver.silo.tv.ui.theme.animatedAccent()
    androidx.compose.foundation.layout.Box(Modifier.height(64.mx).width(64.mx), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.mx.toPx()
            val inset = stroke / 2 + 1.mx.toPx()
            val arc = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f), 0f, 360f, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(accent, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Text(label, style = org.siloserver.silo.tv.ui.components.vantage.ui(14, FontWeight.SemiBold))
    }
}

/** True when [date] (yyyy-MM-dd...) is today or later. */
internal fun isFutureAirDate(date: String?): Boolean {
    val d = date?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    return !d.isBefore(LocalDate.now())
}

/** "Fri 13 Nov" for an episode's air date. */
internal fun airDateLabel(date: String?): String? {
    val d = date?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    return d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
}

/** The upcoming-episode card: days until it airs (mock-up countdown). */
@Composable
private fun CountdownCard(episode: EpisodeListItem) {
    val d = episode.airDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val days = d?.let { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), it).toInt() }?.coerceAtLeast(0) ?: 0
    VPanel(
        Modifier.fillMaxWidth().height(org.siloserver.silo.tv.ui.theme.TvGrid.progressH),
        gap = 8.mx,
        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.mx, vertical = 14.mx),
    ) {
        VLabel(if (days == 0) "Airs today" else "Airs in", color = org.siloserver.silo.tv.ui.theme.animatedAccent())
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.mx)) {
            Text(if (days == 0) "Tonight" else "$days", style = org.siloserver.silo.tv.ui.components.vantage.ui(34, FontWeight.SemiBold))
            if (days > 0) Text(if (days == 1) "DAY" else "DAYS", style = mono(11, color = Level.Faint, tracking = 0.12f), modifier = Modifier.padding(bottom = 6.mx))
        }
        Text(
            "${airDateLabel(episode.airDate).orEmpty()}\nIt is added to your library once it is released.",
            style = mono(12, color = Level.Dim),
        )
    }
}
