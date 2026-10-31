package org.siloserver.silo.tv.ui.screens.personal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.koin.compose.koinInject
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.apiv2.HistoryWatchRowV2
import org.siloserver.silo.repository.PersonalDataRepository
import org.siloserver.silo.tv.ui.components.TvFilterChip
import org.siloserver.silo.tv.ui.shell.TvTopMenuLayout
import org.siloserver.silo.tv.ui.theme.AccentLavender
import org.siloserver.silo.tv.ui.theme.FocusedContainer
import org.siloserver.silo.tv.ui.theme.FocusedContent
import org.siloserver.silo.tv.ui.theme.TvAccentTheme

/** The watch list's periods, picked with pills above the list. */
private enum class WatchPeriod(val label: String, val days: Int) {
    Week("7 days", 7),
    TwoWeeks("2 weeks", 14),
    Month("1 month", 30),
}

/**
 * Watch History as a list: one row per watch, newest first, with the show,
 * the episode (S02E10 and its title) or film, and when it was watched, for
 * the last 7 days, 2 weeks or month.
 */
@Composable
fun TvWatchHistoryScreen(
    onItemClick: (contentId: String) -> Unit,
    onInitialContentFocus: () -> Unit = {},
) {
    val repository: PersonalDataRepository = koinInject()
    var periodName by rememberSaveable { mutableStateOf(WatchPeriod.Week.name) }
    val period = WatchPeriod.valueOf(periodName)
    var rows by remember { mutableStateOf<List<HistoryWatchRowV2>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val firstPill = remember { FocusRequester() }

    LaunchedEffect(period, reload) {
        error = null
        when (val result = repository.listHistoryWatches(period.days)) {
            is ApiResult.Success -> rows = result.data
            is ApiResult.Error -> error = result.message ?: "Couldn't load your watch history."
            is ApiResult.NetworkError -> error = "Couldn't reach the server."
        }
    }
    LaunchedEffect(Unit) {
        runCatching { firstPill.requestFocus() }
        onInitialContentFocus()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = org.siloserver.silo.tv.ui.theme.Spacing.safeArea,
            end = org.siloserver.silo.tv.ui.theme.Spacing.safeArea,
            top = TvTopMenuLayout.contentTopInset,
            bottom = 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = "Watch History",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WatchPeriod.entries.forEach { p ->
                        TvFilterChip(
                            text = p.label,
                            selected = p == period,
                            onClick = { periodName = p.name },
                            modifier = if (p == WatchPeriod.Week) Modifier.focusRequester(firstPill) else Modifier,
                        )
                    }
                }
            }
        }
        val list = rows
        when {
            error != null && list == null -> item(key = "error") {
                Text(error.orEmpty(), color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
            }
            list == null -> item(key = "loading") {
                Text("Loading…", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
            }
            list.isEmpty() -> item(key = "empty") {
                Text(
                    "Nothing watched in the last ${period.label}.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                )
            }
            else -> {
                var lastDay: LocalDate? = null
                list.forEachIndexed { index, row ->
                    val day = row.localDate()
                    if (day != null && day != lastDay) {
                        lastDay = day
                        item(key = "day-$day") { DayHeading(day) }
                    }
                    item(key = "watch-$index-${row.contentId}") {
                        WatchRow(row, onClick = { onItemClick(row.seriesId ?: row.contentId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeading(day: LocalDate) {
    val today = LocalDate.now()
    val label = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
        fontWeight = FontWeight.SemiBold,
        color = TvAccentTheme.current.indicator,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WatchRow(row: HistoryWatchRowV2, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val theme = TvAccentTheme.current
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = theme.rest(Color.White.copy(alpha = 0.05f)),
            contentColor = Color.White,
            // The theme's fill with its matched text colour while focused.
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
            pressedContainerColor = FocusedContainer,
            pressedContentColor = FocusedContent,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, theme.restOutline(Color.White.copy(alpha = 0.08f))), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, AccentLavender), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(width = 40.dp, height = 60.dp).clip(RoundedCornerShape(6.dp))) {
                ThumbhashImage(
                    url = row.posterUrl,
                    thumbhash = null,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.seriesTitle?.takeIf { it.isNotBlank() } ?: row.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.episodeLine()?.let {
                    Text(text = it, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(140.dp)) {
                Text(text = row.localTime().orEmpty(), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (!row.completed) Text(text = "Partly watched", fontSize = 14.sp)
            }
        }
    }
}

/** "S02E10 · The Episode" for an episode; null for a film (its title is above). */
private fun HistoryWatchRowV2.episodeLine(): String? {
    if (type != "episode" && seriesTitle.isNullOrBlank()) return null
    val number = if (seasonNumber != null && episodeNumber != null) "S%02dE%02d".format(seasonNumber, episodeNumber) else null
    return listOfNotNull(number, title.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { null }
}

private fun HistoryWatchRowV2.instant(): Instant? = runCatching { Instant.parse(watchedAt) }.getOrNull()

private fun HistoryWatchRowV2.localDate(): LocalDate? = instant()?.atZone(ZoneId.systemDefault())?.toLocalDate()

private fun HistoryWatchRowV2.localTime(): String? =
    instant()?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm"))
