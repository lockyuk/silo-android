package org.siloserver.silo.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    }.take(6)
    val provider = (detail.networks.firstOrNull() ?: detail.studios.firstOrNull())

    Column(modifier.width(376.mx), verticalArrangement = Arrangement.spacedBy(14.mx)) {
        if (rows.isNotEmpty()) {
            VPanel(Modifier.fillMaxWidth(), title = "Details", sub = provider, gap = 9.mx) {
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
        ProgressCard(detail, seasons, nextUp, isSeries)
    }
}

@Composable
private fun ProgressCard(detail: ItemDetail, seasons: List<Season>, nextUp: EpisodeListItem?, isSeries: Boolean) {
    if (isSeries) {
        val real = seasons.filter { !it.isSpecials }
        val total = real.sumOf { it.episodeCount }
        if (total <= 0) return
        val watched = real.sumOf { it.userData?.watchedCount ?: 0 }
        val pos = nextUp?.userData?.positionSeconds?.takeIf { it > 0 && nextUp.userData?.played != true }
        VPanel(Modifier.fillMaxWidth(), gap = 0.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    VLabel("Your progress")
                    Spacer(Modifier.padding(top = 4.mx))
                    VBig("$watched", "of $total episodes", 32)
                    VBar(watched.toFloat() / total, modifier = Modifier.padding(top = 10.mx))
                }
                Spacer(Modifier.width(14.mx))
                Text(
                    buildString {
                        when {
                            pos != null -> append("left off ${clock(pos)}\nS${nextUp!!.seasonNumber} E${nextUp.episodeNumber}")
                            watched >= total -> append("all watched")
                            nextUp != null -> append("next up\nS${nextUp.seasonNumber} E${nextUp.episodeNumber}")
                            else -> append("${total - watched} to go")
                        }
                    },
                    style = mono(11, color = Level.Dim),
                    textAlign = TextAlign.End,
                    lineHeight = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp),
                )
            }
        }
    } else {
        val ud = detail.userData ?: return
        val dur = ud.durationSeconds ?: (detail.runtime * 60.0).takeIf { it > 0 } ?: return
        val pos = ud.positionSeconds ?: 0.0
        if (!ud.played && pos <= 0) return
        VPanel(Modifier.fillMaxWidth(), gap = 0.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    VLabel("Your progress")
                    Spacer(Modifier.padding(top = 4.mx))
                    if (ud.played) VBig("Watched", null, 32)
                    else VBig("${(pos / dur * 100).toInt()}%", "of ${clock(dur)}", 32)
                    VBar(if (ud.played) 1f else (pos / dur).toFloat(), modifier = Modifier.padding(top = 10.mx))
                }
                Spacer(Modifier.width(14.mx))
                Text(
                    if (ud.played) "seen it" else "left off ${clock(pos)}\n${((dur - pos) / 60).toInt()} min left",
                    style = mono(11, color = Level.Dim),
                    textAlign = TextAlign.End,
                )
            }
        }
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
                Text(v, style = mono(13), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.mx))
            }
            if (i < cells.size - 1) {
                androidx.compose.foundation.layout.Box(
                    Modifier.width(1.dp).height(34.mx).background(Level.Edge).align(Alignment.CenterVertically),
                )
            }
        }
    }
}

private fun money(v: Long?): String = when {
    v == null -> "–"
    v >= 1_000_000_000L -> "$%.1fB".format(v / 1e9)
    v >= 1_000_000L -> "$%.1fM".format(v / 1e6)
    v >= 1_000L -> "$%.0fK".format(v / 1e3)
    else -> "$$v"
}
