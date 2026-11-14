package org.siloserver.silo.tv.ui.screens.server

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.tv.server.ArrDashboard
import org.siloserver.silo.tv.server.ArrItem
import org.siloserver.silo.tv.server.ArrEvent
import org.siloserver.silo.tv.server.AutomationApp
import org.siloserver.silo.tv.server.LogLine
import org.siloserver.silo.tv.server.RequestRow
import org.siloserver.silo.tv.server.SabDashboard
import org.siloserver.silo.tv.server.ServerOverview
import org.siloserver.silo.tv.server.Tone
import org.siloserver.silo.tv.server.TvServerRepository
import org.siloserver.silo.tv.server.storageTb
import org.siloserver.silo.tv.ui.components.vantage.ChipTone
import org.siloserver.silo.tv.ui.components.vantage.VArt
import org.siloserver.silo.tv.ui.components.vantage.VBar
import org.siloserver.silo.tv.ui.components.vantage.VBig
import org.siloserver.silo.tv.ui.components.vantage.VButton
import org.siloserver.silo.tv.ui.components.vantage.VChip
import org.siloserver.silo.tv.ui.components.vantage.VDot
import org.siloserver.silo.tv.ui.components.vantage.VEventLine
import org.siloserver.silo.tv.ui.components.vantage.VKicker
import org.siloserver.silo.tv.ui.components.vantage.VLabel
import org.siloserver.silo.tv.ui.components.vantage.VLineChart
import org.siloserver.silo.tv.ui.components.vantage.VListRow
import org.siloserver.silo.tv.ui.components.vantage.VPanel
import org.siloserver.silo.tv.ui.components.vantage.VRing
import org.siloserver.silo.tv.ui.components.vantage.VSection
import org.siloserver.silo.tv.ui.components.vantage.VStat
import org.siloserver.silo.tv.ui.components.vantage.VTabChip
import org.siloserver.silo.tv.ui.components.vantage.mono
import org.siloserver.silo.tv.ui.components.vantage.mx
import org.siloserver.silo.tv.ui.components.vantage.ui
import org.siloserver.silo.tv.ui.theme.Level
import org.siloserver.silo.tv.ui.theme.VantageCard
import org.siloserver.silo.tv.ui.theme.VantagePanel
import org.siloserver.silo.tv.ui.theme.VantagePill
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ServerTab(val title: String) {
    Overview("Overview"), System("System"), Streams("Streams"), Requests("Requests"), Radarr("Radarr"),
    Sonarr("Sonarr"), Sabnzbd("SABnzbd"), Storage("Storage"), Logs("Logs"),
}

private val clock = DateTimeFormatter.ofPattern("H:mm").withZone(ZoneId.systemDefault())
private fun Instant?.hm(): String = this?.let { clock.format(it) } ?: ""
private fun Tone.color(): Color = when (this) { Tone.Good -> Level.Good; Tone.Info -> Level.liveAccent; Tone.Warn -> Level.Warn; Tone.Bad -> Level.Bad }

/**
 * Server Control: the only place server information lives (Vantage). A row of sub-tab chips
 * switches between Overview, Streams, Requests, the Radarr, Sonarr and SABnzbd dashboards,
 * Storage and Logs. Silo data comes from the admin API; the automation apps are read directly
 * on the LAN with connections saved on this TV.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TvServerScreen(
    serverName: String,
    modifier: Modifier = Modifier,
) {
    val http: org.siloserver.silo.network.api.RawHttpApi = koinInject()
    val repo = remember(http) { TvServerRepository(http) }
    var tab by rememberSaveable { mutableStateOf(ServerTab.Overview) }

    var overview by remember { mutableStateOf<ServerOverview?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            overview = runCatching { repo.overview(serverName) }.getOrNull() ?: overview
            delay(500)
        }
    }

    // Each page sets its own accent, as in the mock-up.
    LaunchedEffect(tab) {
        Level.liveAccent = when (tab) {
            ServerTab.Radarr -> Level.Radarr
            ServerTab.Sonarr -> Level.Sonarr
            ServerTab.Sabnzbd -> Level.Sabnzbd
            else -> Level.DefaultAccent
        }
    }

    val backdrop = overview?.streams?.firstOrNull()?.posterUrl

    Box(modifier.fillMaxSize().background(Level.Bg)) {
        // Blurred, darkened backdrop.
        if (backdrop != null) {
            ThumbhashImage(
                url = backdrop, thumbhash = null, contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                decodeSizePx = 48,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.4f, 0.4f, 0.4f, 1f) }),
            )
        } else {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Level.liveAccent.copy(alpha = 0.16f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(300f, 200f), radius = 1400f)))
        }

        // Sub-tabs, top right under the bar.
        // Focus coming into the chips (Up from a page, Down from the menu) lands on the page
        // being shown, so moving up never switches to a neighbouring page by accident.
        val chipRequesters = remember { ServerTab.entries.associateWith { FocusRequester() } }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 92.mx, end = 44.mx)
                .focusProperties { enter = { chipRequesters.getValue(tab) } }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.mx),
        ) {
            ServerTab.entries.forEach { t ->
                VTabChip(t.title, selected = tab == t, onFocus = { tab = t }, focusRequester = chipRequesters.getValue(t))
            }
        }

        Box(Modifier.fillMaxSize().padding(start = 44.mx, end = 44.mx, top = 88.mx)) {
            when (tab) {
                ServerTab.Overview -> OverviewPage(overview, repo)
                ServerTab.System -> SystemPage(repo, serverName, overview)
                ServerTab.Streams -> StreamsPage(overview)
                ServerTab.Requests -> RequestsPage(repo)
                ServerTab.Radarr -> ArrPage(AutomationApp.Radarr, repo)
                ServerTab.Sonarr -> ArrPage(AutomationApp.Sonarr, repo)
                ServerTab.Sabnzbd -> SabPage(repo)
                ServerTab.Storage -> StoragePage(overview)
                ServerTab.Logs -> LogsPage(overview, repo)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun PageHead(kick: String, title: String, warn: String? = null, kickColor: Color = Level.liveAccent, version: String? = null) {
    Column {
        VKicker(kick, kickColor)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.mx)) {
            Text(title, style = ui(42, FontWeight.SemiBold))
            if (warn != null) Text(" · $warn", style = ui(42, FontWeight.SemiBold, Level.Warn))
            if (version != null) Text("  $version", style = mono(12, color = Level.Faint))
        }
    }
}

private fun uptime(start: Instant?): String {
    if (start == null) return ""
    val d = Duration.between(start, Instant.now())
    return "up ${d.toDays()}d %02dh".format(d.toHours() % 24)
}

@Composable
private fun BigCard(label: String, value: String, suffix: String, color: Color = Level.Fg, modifier: Modifier) {
    Column(
        modifier
            .height(112.mx)
            .background(Level.Glass, VantagePanel)
            .border(1.dp, Level.Edge, VantagePanel)
            .padding(horizontal = 18.mx),
        verticalArrangement = Arrangement.spacedBy(6.mx, Alignment.CenterVertically),
    ) {
        VLabel(label)
        VBig(value, suffix, 34, color)
    }
}

@Composable
private fun OverviewPage(o: ServerOverview?, repo: TvServerRepository) {
    val scope = rememberCoroutineScope()
    var scanNote by remember { mutableStateOf<String?>(null) }
    var recent by remember { mutableStateOf<List<LogLine>>(emptyList()) }
    LaunchedEffect(Unit) { while (true) { recent = runCatching { repo.warningLogs() }.getOrDefault(recent); delay(10_000) } }
    if (o == null) { Loading(); return }
    val warnings = buildList {
        o.disks.filter { it.fraction > 0.9f }.forEach { add("${it.label} disk above 90%") }
        if (o.restartRequired) add("Restart required")
        if (!o.dbOk) add("Database not answering")
        if (!o.redisOk) add("Redis not answering")
    }
    val warnCount = warnings.size + o.warnings24h
    val kickColor = if (o.errors24h == 0 && warnings.isEmpty()) Level.Good else Level.Warn
    val problems = (recent + o.logs).filter { it.level == "error" || it.level == "warn" || it.level == "warning" }.distinctBy { it.time to it.message }.sortedByDescending { it.time }.take(3)
    val events = o.logs.filterNot { it.message.equals("api request", true) }.take(4 - warnings.size.coerceAtMost(2))
    Column {
        // Kicker sits level with the sub-tabs (y92); cards at y142, panels at y270, errors at y526.
        Spacer(Modifier.height(4.mx))
        Row(Modifier.height(34.mx), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append("● SERVER · ${uptime(o.startedAt).uppercase()}")
                    if (warnCount > 0) { append(" · "); withStyle(SpanStyle(color = Level.Warn)) { append("$warnCount ${if (warnCount == 1) "WARNING" else "WARNINGS"}") } }
                    if (o.errors24h > 0) { append(" · "); withStyle(SpanStyle(color = Level.Bad)) { append("${o.errors24h} ${if (o.errors24h == 1) "ERROR" else "ERRORS"}") } }
                },
                style = mono(11, color = kickColor, tracking = 0.16f), maxLines = 1,
            )
        }
        Spacer(Modifier.height(16.mx))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.mx)) {
            val ram = if ((o.memTotalMb ?: 0.0) > 0) ((o.memUsedMb ?: 0.0) * 100 / o.memTotalMb!!).toInt() else 0
            BigCard("Streams", "${o.streams.size}", "/ ${o.users} users", if (o.streams.isNotEmpty()) Level.Good else Level.Fg, Modifier.weight(1f))
            BigCard("CPU", "${(o.cpuPct ?: 0.0).toInt()}", "%", modifier = Modifier.weight(1f))
            BigCard("RAM", "$ram", "%", modifier = Modifier.weight(1f))
            BigCard("GPU", "${o.gpuSessions ?: 0}", "jobs", modifier = Modifier.weight(1f))
            BigCard("Network out", "%.0f".format((o.netTxBps ?: 0.0) * 8 / 1e6), "Mbps", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.mx))
        Row(horizontalArrangement = Arrangement.spacedBy(16.mx)) {
            VPanel(Modifier.width(700.mx).height(240.mx), title = "Live streams", sub = "${o.streams.size} of ${o.users} users", gap = 14.mx) {
                if (o.streams.isEmpty()) Text("Nothing is playing right now.", style = mono(12, color = Level.Dim))
                Column(verticalArrangement = Arrangement.spacedBy(10.mx)) {
                    o.streams.take(3).forEach { s ->
                        val direct = s.method.contains("direct", true) || s.method.contains("remux", true)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mx)) {
                            VArt(s.posterUrl, 80.mx, 45.mx, progress = s.progress)
                            Text("${s.who} · ${s.title}", style = ui(14, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            VChip(
                                if (direct) (if (s.method.contains("direct", true)) "Direct play" else "Direct stream") else "Transcode" + (s.resolution?.let { " $it" } ?: ""),
                                tone = if (direct) ChipTone.Good else ChipTone.Warn, dot = true,
                            )
                        }
                    }
                }
            }
            VPanel(Modifier.width(476.mx).height(240.mx), title = "Health", sub = "last hour", gap = 14.mx) {
                Column(verticalArrangement = Arrangement.spacedBy(14.mx)) {
                    warnings.take(2).forEach { VEventLine(it, Level.Warn, "now") }
                    if (warnings.isEmpty() && events.isEmpty()) VEventLine("All systems normal", Level.Good, "now")
                    events.forEach { l ->
                        VEventLine(l.message, if (l.level == "error") Level.Bad else if (l.level == "warn") Level.Warn else Level.Good, l.time.hm())
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.mx), verticalAlignment = Alignment.CenterVertically) {
                    VButton("Scan library", onClick = {
                        scope.launch { scanNote = if (repo.scanLibraries()) "Scan started" else "Scan failed" }
                    }, primary = true, height = 42.mx, textPx = 13)
                    if (scanNote != null) Text(scanNote!!, style = mono(11, color = Level.Dim))
                }
            }
        }
        Spacer(Modifier.height(16.mx))
        VPanel(Modifier.fillMaxWidth().height(166.mx), title = "Recent errors", sub = "last 24 hours", gap = 10.mx) {
            if (problems.isEmpty()) Text("No errors or warnings.", style = mono(12, color = Level.Good))
            problems.forEach { e ->
                val bad = e.level == "error"
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mx)) {
                    Text(e.time.hm(), style = mono(12, color = Level.Faint), modifier = Modifier.width(60.mx), maxLines = 1)
                    VChip(if (bad) "ERROR" else "WARN", if (bad) ChipTone.Bad else ChipTone.Warn, modifier = Modifier.width(72.mx))
                    Text(e.component, style = mono(12, color = Level.Dim), modifier = Modifier.width(84.mx), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.message, style = mono(12), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun mbps(bps: Double?): String = if (bps == null) "–" else if (bps * 8 >= 1e6) "%.1f Mbps".format(bps * 8 / 1e6) else "%.0f kbps".format(bps * 8 / 1e3)

@Composable
private fun KeyValues(vararg rows: Pair<String, String>, colors: Map<String, Color> = emptyMap()) {
    Column(verticalArrangement = Arrangement.spacedBy(5.mx)) {
        rows.forEach { (k, v) ->
            Row {
                Text(k, style = mono(12, color = Level.Faint))
                Spacer(Modifier.weight(1f))
                Text(v, style = mono(12, color = colors[k] ?: Level.Fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HealthLine(mark: String, color: Color, time: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.mx)) {
        Text("$mark $time", style = mono(12, color = color), maxLines = 1)
        Text(text, style = mono(12), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DiskBar(label: String, usedGb: Double, totalGb: Double, fraction: Float) {
    Column {
        Row {
            Text(label, style = mono(12), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${storageTb(usedGb)} / ${storageTb(totalGb)}", style = mono(12, color = Level.Dim))
        }
        Spacer(Modifier.height(6.mx))
        VBar(fraction, if (fraction > 0.85f) Level.Warn else Level.liveAccent, 8.mx)
    }
}

/**
 * Where the viewer is up to: the seek-preview frame for the stream's current position, refreshed
 * as the position moves. Falls back to the poster when the server has no previews for the file.
 */
@Composable
private fun LiveFrame(s: org.siloserver.silo.tv.server.LiveStream, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    val manifests: org.siloserver.silo.playback.trickplay.TrickplayManifestSource = koinInject()
    val scope = rememberCoroutineScope()
    val previews = remember { org.siloserver.silo.common.player.trickplay.trickplayPreviews(manifests, scope) }
    val frame by previews.thumbnail.collectAsState()
    val target = remember(s.contentId, s.fileId) {
        if (!s.contentId.isNullOrBlank() && s.fileId != null) org.siloserver.silo.playback.trickplay.TrickplayTarget(s.contentId, s.fileId) else null
    }
    LaunchedEffect(target) { previews.bind(target) }
    LaunchedEffect(target, s.positionSeconds) { if (target != null) previews.show(s.positionSeconds) }
    val img = frame
    if (img == null) {
        VArt(s.posterUrl, w, h, progress = s.progress)
    } else {
        Box(Modifier.size(w, h).clip(VantageCard).border(1.dp, Color.White.copy(alpha = 0.1f), VantageCard)) {
            androidx.compose.foundation.Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 7.mx, vertical = 6.mx).fillMaxWidth().height(4.mx).clip(RoundedCornerShape(2.dp)).background(Color.Black.copy(alpha = 0.55f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(s.progress.coerceIn(0f, 1f)).background(Level.liveAccent))
            }
        }
    }
}

@Composable
private fun StreamRow(s: org.siloserver.silo.tv.server.LiveStream, big: Boolean = false) {
    val direct = s.method.contains("direct", true)
    val remux = s.method.contains("remux", true)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mx)) {
        LiveFrame(s, if (big) 176.mx else 116.mx, if (big) 99.mx else 65.mx)
        Column(Modifier.weight(1f)) {
            Text("${s.who} · ${s.title}", style = ui(if (big) 18 else 14, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 6.mx), horizontalArrangement = Arrangement.spacedBy(6.mx)) {
                VChip(
                    when {
                        direct -> "Direct play"
                        remux -> "Direct stream"
                        else -> "Transcode" + (s.resolution?.let { " $it" } ?: "")
                    },
                    tone = if (direct || remux) ChipTone.Good else ChipTone.Warn, dot = true,
                )
                s.bitrateMbps?.let { VChip("%.1f Mbps".format(it)) }
                if (s.paused) VChip("Paused", ChipTone.Warn)
                if (big && s.client.isNotBlank()) VChip(s.client)
            }
        }
    }
}

private fun hms(sec: Double): String {
    val t = sec.toLong().coerceAtLeast(0)
    return "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60)
}

@Composable
private fun StreamCard(s: org.siloserver.silo.tv.server.LiveStream) {
    val direct = s.method.contains("direct", true)
    val remux = s.method.contains("remux", true)
    val dur = if (s.progress > 0.001f) s.positionSeconds / s.progress else 0.0
    val method = when { direct -> "Direct play"; remux -> "Direct stream"; else -> "Transcode" + (s.resolution?.let { " $it" } ?: "") }
    Row(
        Modifier.fillMaxWidth().height(114.mx).background(Level.Glass, VantagePanel).border(1.dp, Level.Edge, VantagePanel).padding(start = 12.mx, end = 20.mx, top = 12.mx, bottom = 12.mx),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.mx),
    ) {
        Box {
            LiveFrame(s, 160.mx, 90.mx)
            Row(
                Modifier.padding(8.mx).background(Color.Black.copy(alpha = 0.6f), VantagePill).padding(horizontal = 8.mx, vertical = 3.mx),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.mx),
            ) {
                VDot(if (s.paused) Level.Warn else Level.Good, 6.mx)
                Text(hms(s.positionSeconds), style = mono(11, FontWeight.SemiBold), maxLines = 1)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.mx)) {
            Text(s.title, style = ui(17, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${s.who} · ${s.client}", style = mono(12, color = Level.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.mx)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.mx)) {
                if (s.paused) VChip("Paused", ChipTone.Warn)
                VChip(method, if (direct || remux) ChipTone.Good else ChipTone.Warn, dot = true)
            }
            s.bitrateMbps?.let { Row(verticalAlignment = Alignment.Bottom) { Text("%.1f".format(it), style = ui(15, FontWeight.SemiBold)); Text(" Mbps", style = mono(11, color = Level.Dim)) } }
            Text(if (dur > 0) "${hms(s.positionSeconds)} of ${hms(dur)}" else hms(s.positionSeconds), style = mono(11, color = Level.Faint), maxLines = 1)
        }
    }
}

@Composable
private fun StreamsPage(o: ServerOverview?) {
    if (o == null) { Loading(); return }
    val direct = o.streams.count { it.method.contains("direct", true) }
    val trans = o.streams.size - direct
    Column {
        Spacer(Modifier.height(4.mx))
        Row(Modifier.height(34.mx), verticalAlignment = Alignment.CenterVertically) {
            Text("● STREAMS · ${o.streams.size} LIVE", style = mono(11, color = if (o.streams.isEmpty()) Level.Faint else Level.Good, tracking = 0.16f), maxLines = 1)
        }
        Spacer(Modifier.height(16.mx))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.mx)) {
            BigCard("Streams", "${o.streams.size}", "/ ${o.users} users", if (o.streams.isNotEmpty()) Level.Good else Level.Fg, Modifier.weight(1f))
            BigCard("Direct play", "$direct", "", modifier = Modifier.weight(1f))
            BigCard("Transcoding", "$trans", "", if (trans > 0) Level.Warn else Level.Fg, Modifier.weight(1f))
            BigCard("Network out", "%.0f".format((o.netTxBps ?: 0.0) * 8 / 1e6), "Mbps", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.mx))
        Row(Modifier.fillMaxWidth().height(26.mx), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.mx)) {
            Text("Now playing", style = ui(17, FontWeight.SemiBold), maxLines = 1)
            Text("${o.streams.size} of ${o.users} users", style = mono(11, color = Level.Faint), maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.mx)) {
                VDot(Level.Good)
                Text("Live · frames and positions refresh every second", style = mono(11, color = Level.Good), maxLines = 1)
            }
        }
        Spacer(Modifier.height(16.mx))
        if (o.streams.isEmpty()) Text("Nobody is watching right now.", style = mono(12, color = Level.Dim))
        Column(verticalArrangement = Arrangement.spacedBy(12.mx)) { o.streams.take(3).forEach { StreamCard(it) } }
    }
}

@Composable
private fun RequestsPage(repo: TvServerRepository) {
    var rows by remember { mutableStateOf<List<RequestRow>?>(null) }
    LaunchedEffect(Unit) { while (true) { rows = runCatching { repo.requests() }.getOrNull() ?: rows; delay(15_000) } }
    val list = rows ?: run { Loading(); return }
    val active = list.filter { it.outcome == "active" || it.status !in setOf("available", "completed", "declined", "failed") }
    Column {
        PageHead("Queue · ${active.size} active", "Coming up")
        Spacer(Modifier.height(16.mx))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.mx), modifier = Modifier.fillMaxSize()) {
            items(list) { r -> RequestPipelineRow(r) }
        }
    }
}

private val Pipeline = listOf("Requested", "Approved", "Searching", "Downloading", "Importing", "Ready")

private fun stageOf(r: RequestRow): Int = when (r.status.lowercase()) {
    "pending", "requested" -> 0
    "approved" -> 1
    "processing", "searching", "queued" -> 2
    "downloading" -> 3
    "importing", "partially_available" -> 4
    "available", "completed" -> 5
    else -> 1
}

@Composable
private fun RequestPipelineRow(r: RequestRow) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val at = stageOf(r)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (focused) Modifier.border(3.dp, Level.Fg, VantagePanel) else Modifier)
            .background(Level.Glass, VantagePanel)
            .border(1.dp, Level.Edge, VantagePanel)
            .focusable(interactionSource = source)
            .padding(12.mx),
        horizontalArrangement = Arrangement.spacedBy(20.mx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VArt(r.backdropUrl, 176.mx, 99.mx)
        Column(Modifier.width(300.mx)) {
            Text(r.title + (r.year?.let { " ($it)" } ?: ""), style = ui(20, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.detail.ifBlank { r.status }, style = mono(12, color = Level.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.mx))
            Text("Requested ${r.requestedAt?.let { DateTimeFormatter.ofPattern("d MMM").withZone(ZoneId.systemDefault()).format(it) } ?: ""} · ${r.mediaType}", style = mono(11, color = Level.Faint), modifier = Modifier.padding(top = 3.mx))
        }
        Column(Modifier.weight(1f).padding(end = 10.mx)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.mx)) {
                Pipeline.indices.forEach { i ->
                    Box(
                        Modifier.weight(1f).height(6.mx).clip(VantagePill).background(
                            when {
                                i < at -> Level.Good
                                i == at -> Level.liveAccent
                                else -> Color.White.copy(alpha = 0.14f)
                            },
                        ),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.mx), horizontalArrangement = Arrangement.SpaceBetween) {
                Pipeline.forEachIndexed { i, t -> Text(t, style = mono(10, color = if (i == at) Level.Fg else Level.Faint)) }
            }
        }
    }
}

// ---- Automation apps ----------------------------------------------------------------------

/**
 * The app is not set up: its address and API key live in Silo's server settings (the Automation
 * dashboards plugin), never on the TV.
 */
@Composable
private fun SetupPanel(app: AutomationApp, pluginInstalled: Boolean) {
    Column {
        PageHead("${app.title} · not connected", "Set up ${app.title} in Silo")
        Spacer(Modifier.height(20.mx))
        VPanel(Modifier.width(680.mx), title = "Where to set it up", sub = "on the server, not this TV") {
            Text(
                if (pluginInstalled) {
                    "In Silo on the web: Admin → Plugins → Automation dashboards → ${app.title}. Enter the address and API key and save. This page fills in by itself."
                } else {
                    "Install the Automation dashboards plugin in Silo (Admin → Plugins), then enter ${app.title}'s address and API key in its settings."
                },
                style = ui(15, FontWeight.Normal, Level.Synopsis),
            )
            Text("The keys stay on the server; the TV only ever talks to Silo.", style = mono(11, color = Level.Faint))
        }
    }
}

private data class ServerCard(val label: String, val value: String, val suffix: String = "", val color: Color = Level.Fg)

/** The shared Server layout from the mock-up: a kicker at y92, big numbers at y142 and panels from y270. */
@Composable
private fun ServerFrame(kick: String, kickColor: Color, cards: List<ServerCard>, content: @Composable () -> Unit) {
    Column {
        Spacer(Modifier.height(4.mx))
        Row(Modifier.height(34.mx), verticalAlignment = Alignment.CenterVertically) {
            Text(kick.uppercase(), style = mono(11, color = kickColor, tracking = 0.16f), maxLines = 1)
        }
        Spacer(Modifier.height(16.mx))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.mx)) {
            cards.forEach { BigCard(it.label, it.value, it.suffix, it.color, Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(16.mx))
        content()
    }
}

/** A queue line: art, title, detail and a bar on the left; percent and rate on the right. */
@Composable
private fun QueueRow(item: ArrItem, withArt: Boolean = true) {
    val stalled = item.right.contains("stalled", true) || item.subtitle.contains("stalled", true)
    val bar = if (stalled) Level.Warn else Level.liveAccent
    Row(
        Modifier.fillMaxWidth().height(78.mx).background(Level.Glass, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .border(1.dp, Level.Edge, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(start = 10.mx, end = 14.mx),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mx),
    ) {
        if (withArt) VArt(item.imageUrl, 96.mx, 54.mx)
        Column(Modifier.weight(1f)) {
            Text(item.title, style = ui(14, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, style = mono(11, color = Level.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.mx))
            VBar(item.progress ?: 0f, bar, 4.mx, Modifier.padding(top = 7.mx))
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(110.mx)) {
            Text("${((item.progress ?: 0f) * 100).toInt()}%", style = ui(14, FontWeight.SemiBold), maxLines = 1)
            Text(item.right, style = mono(11, color = Level.Faint), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.mx))
        }
    }
}

/** A line with art, a title and detail, and a status chip at the right. */
@Composable
private fun StatusRow(imageUrl: String?, title: String, sub: String, chip: String, tone: ChipTone, withArt: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().height(78.mx).background(Level.Glass, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .border(1.dp, Level.Edge, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(start = 10.mx, end = 14.mx),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mx),
    ) {
        if (withArt) VArt(imageUrl, 96.mx, 54.mx)
        Column(Modifier.weight(1f)) {
            Text(title, style = ui(14, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = mono(11, color = Level.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.mx))
        }
        if (chip.isNotBlank()) VChip(chip, tone)
    }
}

private fun Tone.chip(): ChipTone = when (this) { Tone.Good -> ChipTone.Good; Tone.Warn -> ChipTone.Warn; Tone.Bad -> ChipTone.Bad; Tone.Info -> ChipTone.Plain }

@Composable
private fun ServerPanels(
    leftTitle: String, leftSub: String, left: @Composable () -> Unit,
    rightTitle: String, rightSub: String, right: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.mx)) {
        VPanel(Modifier.width(588.mx).height(376.mx), title = leftTitle, sub = leftSub, gap = 12.mx) {
            Column(verticalArrangement = Arrangement.spacedBy(10.mx)) { left() }
        }
        VPanel(Modifier.width(588.mx).height(376.mx), title = rightTitle, sub = rightSub, gap = 12.mx) {
            Column(verticalArrangement = Arrangement.spacedBy(10.mx)) { right() }
        }
    }
    Spacer(Modifier.height(16.mx))
    Row(horizontalArrangement = Arrangement.spacedBy(10.mx), verticalAlignment = Alignment.CenterVertically) { actions() }
}

@Composable
private fun ArrPage(app: AutomationApp, repo: TvServerRepository) {
    var status by remember { mutableStateOf<org.siloserver.silo.tv.server.AutomationStatus?>(null) }
    var checked by remember { mutableStateOf(false) }
    var dash by remember { mutableStateOf<ArrDashboard?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(app) {
        while (true) {
            val st = repo.automationStatus()
            status = st; checked = true
            if (st?.has(app) == true) {
                val d = if (app == AutomationApp.Radarr) repo.radarr() else repo.sonarr()
                if (d != null) { dash = d; failed = false } else if (dash == null) failed = true
            }
            delay(15_000)
        }
    }
    if (!checked) { Loading(); return }
    if (status?.has(app) != true) { SetupPanel(app, status != null); return }
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    if (failed) { Unreachable(app); return }
    val d = dash ?: run { Loading(); return }
    val isRadarr = app == AutomationApp.Radarr
    val healthy = d.health.none { it.tone == Tone.Bad }
    val cards = if (isRadarr) listOf(
        ServerCard("Movies", "%,d".format(d.total ?: 0), d.totalLabel.takeIf { it.length < 10 } ?: ""),
        ServerCard("Missing", "${d.missing ?: 0}", "", if ((d.missing ?: 0) > 0) Level.Warn else Level.Fg),
        ServerCard("In queue", "${d.queue.size}"),
        ServerCard("Upcoming", "${d.upcoming ?: 0}", "next 30 days"),
        ServerCard("Cutoff unmet", "${d.cutoffUnmet ?: 0}"),
    ) else listOf(
        ServerCard("Series", "%,d".format(d.total ?: 0)),
        ServerCard("Airing this week", "${d.upcoming ?: 0}", "episodes"),
        ServerCard("Missing", "${d.missing ?: 0}", "", if ((d.missing ?: 0) > 0) Level.Warn else Level.Fg),
        ServerCard("In queue", "${d.queue.size}"),
        ServerCard("Cutoff unmet", "${d.cutoffUnmet ?: 0}"),
    )
    ServerFrame(
        kick = "● ${app.title} · v${d.version.substringBeforeLast('.').ifBlank { d.version }} · ${if (healthy) "connected" else "check health"}",
        kickColor = if (healthy) Level.Good else Level.Warn,
        cards = cards,
    ) {
        ServerPanels(
            leftTitle = "Queue", leftSub = if (d.queue.isEmpty()) "idle" else "${d.queue.size} downloading",
            left = {
                if (d.queue.isEmpty()) Text("Nothing downloading.", style = mono(12, color = Level.Dim))
                d.queue.take(3).forEach { QueueRow(it) }
            },
            rightTitle = if (isRadarr) "Missing and upcoming" else "Airing this week",
            rightSub = if (isRadarr) "monitored, not on disk" else "${d.calendar.firstOrNull()?.date?.format(DateTimeFormatter.ofPattern("EEE d")) ?: ""} to ${d.calendar.lastOrNull()?.date?.format(DateTimeFormatter.ofPattern("EEE d")) ?: ""}",
            right = {
                val soon = d.calendar.flatMap { day -> day.items.map { day.date to it } }.take(3)
                if (soon.isEmpty()) Text(if (isRadarr) "Nothing due in the next 30 days." else "Nothing airing this week.", style = mono(12, color = Level.Dim))
                soon.forEach { (date, m) -> StatusRow(m.imageUrl, m.title, "${date.format(DateTimeFormatter.ofPattern("EEE d"))} · ${m.subtitle}".trim(' ', '·'), m.right.takeIf { it.isNotBlank() } ?: "", ChipTone.Plain) }
            },
            actions = {
                VButton("Search missing", onClick = {
                    scope.launch { note = if (repo.arrCommand(app, if (isRadarr) "MissingMoviesSearch" else "MissingEpisodeSearch")) "Search started" else "Failed" }
                }, primary = true, height = 42.mx, textPx = 13)
                VButton(if (isRadarr) "Refresh library" else "Refresh series", onClick = {
                    scope.launch { note = if (repo.arrCommand(app, if (isRadarr) "RefreshMovie" else "RefreshSeries")) "Refresh started" else "Failed" }
                }, height = 42.mx, textPx = 13)
                note?.let { Text(it, style = mono(11, color = Level.Dim)) }
            },
        )
    }
}

@Composable
private fun SabPage(repo: TvServerRepository) {
    var status by remember { mutableStateOf<org.siloserver.silo.tv.server.AutomationStatus?>(null) }
    var checked by remember { mutableStateOf(false) }
    var dash by remember { mutableStateOf<SabDashboard?>(null) }
    var failed by remember { mutableStateOf(false) }
    val speeds = remember { mutableStateOf(listOf<Float>()) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        var ticks = 0
        while (true) {
            if (ticks % 10 == 0) { status = repo.automationStatus(); checked = true }
            ticks++
            if (status?.sabnzbd == true) {
                val d = repo.sabnzbd()
                if (d != null) {
                    dash = d; failed = false
                    speeds.value = (speeds.value + d.speedMBs.toFloat()).takeLast(60)
                } else if (dash == null) failed = true
            }
            delay(3_000)
        }
    }
    val scope = rememberCoroutineScope()
    if (!checked) { Loading(); return }
    if (status?.sabnzbd != true) { SetupPanel(AutomationApp.Sabnzbd, status != null); return }
    if (failed) { Unreachable(AutomationApp.Sabnzbd); return }
    val d = dash ?: run { Loading(); return }
    val sp = speeds.value
    ServerFrame(
        kick = "● SABnzbd · v${d.version} · ${if (d.paused) "paused" else "running"}",
        kickColor = if (d.paused) Level.Warn else Level.Good,
        cards = listOf(
            ServerCard("Speed", "%.1f".format(d.speedMBs), "MB/s", if (d.speedMBs > 0) Level.Good else Level.Fg),
            ServerCard("Queue", "${d.queue.size}", "jobs"),
            ServerCard("Remaining", d.sizeLeft.substringBefore(' '), d.sizeLeft.substringAfter(' ', "")),
            ServerCard("Time left", d.timeLeft.ifBlank { "–" }),
            ServerCard("Free space", storageTb(d.diskFreeGb ?: 0.0).substringBefore(' '), storageTb(d.diskFreeGb ?: 0.0).substringAfter(' ', ""), if (d.diskTotalGb != null && d.diskTotalGb > 0 && (d.diskFreeGb ?: 0.0) / d.diskTotalGb < 0.1) Level.Warn else Level.Fg),
        ),
    ) {
        ServerPanels(
            leftTitle = "Queue", leftSub = if (d.queue.isEmpty()) "idle" else "downloading · ${d.timeLeft} left",
            left = {
                if (d.queue.isEmpty()) Text("The queue is empty.", style = mono(12, color = Level.Dim))
                d.queue.take(3).forEach { QueueRow(it, withArt = false) }
            },
            rightTitle = "History", rightSub = "latest",
            right = {
                if (d.history.isEmpty()) Text("Nothing finished yet.", style = mono(12, color = Level.Dim))
                d.history.take(3).forEach { StatusRow(null, it.text, it.time.hm(), if (it.tone == Tone.Bad) "Failed" else "Completed", it.tone.chip(), withArt = false) }
            },
            actions = {
                VButton(if (d.paused) "Resume" else "Pause", onClick = {
                    scope.launch { repo.sabAction(if (d.paused) "resume" else "pause"); refresh++ }
                }, primary = true, height = 42.mx, textPx = 13)
                VButton(if (d.speedLimit == "none") "Limit to 50%" else "Remove limit", onClick = {
                    scope.launch { repo.sabAction("speedlimit", if (d.speedLimit == "none") 50 else 100); refresh++ }
                }, height = 42.mx, textPx = 13)
                if (sp.isNotEmpty()) Text("avg %.1f · peak %.1f MB/s".format(sp.average().toFloat(), sp.maxOrNull() ?: 0f), style = mono(11, color = Level.Dim))
            },
        )
    }
}

@Composable
private fun StoragePage(o: ServerOverview?) {
    if (o == null) { Loading(); return }
    val library = o.disks.filter { it.label != "Transcode scratch" }
    val used = library.sumOf { it.usedGb }
    val total = library.sumOf { it.totalGb }
    val fullest = o.disks.maxOfOrNull { it.fraction } ?: 0f
    fun tb(gb: Double) = storageTb(gb)
    ServerFrame(
        kick = "● Storage · ${o.disks.size} volumes",
        kickColor = if (fullest > 0.9f) Level.Warn else Level.Good,
        cards = listOf(
            ServerCard("Used", tb(used).substringBefore(' '), tb(used).substringAfter(' ', "")),
            ServerCard("Free", tb(total - used).substringBefore(' '), tb(total - used).substringAfter(' ', "")),
            ServerCard("Library", tb(o.storageBytes / 1e9).substringBefore(' '), tb(o.storageBytes / 1e9).substringAfter(' ', "")),
            ServerCard("Fullest volume", "${(fullest * 100).toInt()}", "%", if (fullest > 0.85f) Level.Warn else Level.Fg),
            ServerCard("Files", "%,d".format(o.files)),
        ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.mx)) {
            VPanel(Modifier.width(588.mx).height(376.mx), title = "Volumes", sub = "${o.disks.size} mounted", gap = 12.mx) {
                Column(verticalArrangement = Arrangement.spacedBy(12.mx)) {
                    o.disks.take(3).forEach { d ->
                        val pct = (d.fraction * 100).toInt()
                        val c = if (d.fraction > 0.85f) Level.Warn else Level.liveAccent
                        Column(
                            Modifier.fillMaxWidth().background(Level.Glass, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                .border(1.dp, Level.Edge, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(horizontal = 16.mx, vertical = 14.mx),
                            verticalArrangement = Arrangement.spacedBy(9.mx),
                        ) {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.mx)) {
                                Text(d.label, style = ui(16, FontWeight.SemiBold), maxLines = 1, modifier = Modifier.weight(1f))
                                Text("$pct%", style = ui(16, FontWeight.SemiBold, if (d.fraction > 0.85f) Level.Warn else Level.Fg))
                            }
                            VBar(d.fraction, c, 10.mx)
                            Row {
                                Text("${tb(d.usedGb)} of ${tb(d.totalGb)}", style = mono(12, color = Level.Dim), modifier = Modifier.weight(1f))
                                Text("${tb(d.totalGb - d.usedGb)} free", style = mono(12, color = if (d.fraction > 0.85f) Level.Warn else Level.Faint))
                            }
                        }
                    }
                }
            }
            VPanel(Modifier.width(588.mx).height(376.mx), title = "Library", sub = "what is on the disks", gap = 12.mx) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.mx)) {
                    VChip("%,d titles".format(o.totalItems))
                    VChip("%,d movies".format(o.movies))
                    VChip("%,d series".format(o.shows))
                }
                Spacer(Modifier.height(8.mx))
                VLabel("Library size")
                VBig(tb(o.storageBytes / 1e9).substringBefore(' '), tb(o.storageBytes / 1e9).substringAfter(' ', ""), 48)
                if (fullest > 0.9f) {
                    val worst = o.disks.maxByOrNull { it.fraction }
                    VChip("${worst?.label} is above 90%", ChipTone.Warn, dot = true)
                }
            }
        }
    }
}

@Composable
private fun LogsPage(o: ServerOverview?, repo: TvServerRepository) {
    var warns by remember { mutableStateOf<List<LogLine>>(emptyList()) }
    var filter by remember { mutableStateOf("all") }
    LaunchedEffect(Unit) { while (true) { warns = runCatching { repo.warningLogs() }.getOrDefault(warns); delay(10_000) } }
    if (o == null) { Loading(); return }
    val all = (warns + o.logs).distinctBy { it.time to it.message }.sortedByDescending { it.time }
    fun isWarn(l: LogLine) = l.level == "warn" || l.level == "warning"
    val shown = when (filter) {
        "error" -> all.filter { it.level == "error" }
        "warn" -> all.filter { isWarn(it) }
        "info" -> all.filter { it.level != "error" && !isWarn(it) }
        else -> all
    }
    val lastError = all.firstOrNull { it.level == "error" }
    ServerFrame(
        kick = "● Logs · last 24 hours",
        kickColor = if (o.errors24h > 0) Level.Bad else Level.Good,
        cards = listOf(
            ServerCard("Errors", "${o.errors24h}", "", if (o.errors24h > 0) Level.Bad else Level.Fg),
            ServerCard("Warnings", "${o.warnings24h}", "", if (o.warnings24h > 0) Level.Warn else Level.Fg),
            ServerCard("Lines loaded", "${all.size}"),
            ServerCard("Last error", lastError?.time.hm().ifBlank { "–" }, if (lastError != null) "today" else ""),
        ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.mx), verticalAlignment = Alignment.CenterVertically) {
            listOf("all" to "All", "error" to "Errors ${o.errors24h}", "warn" to "Warnings ${o.warnings24h}", "info" to "Info").forEach { (k, label) ->
                VButton(label, onClick = { filter = k }, primary = filter == k, height = 34.mx, textPx = 12)
            }
        }
        Spacer(Modifier.height(16.mx))
        VPanel(Modifier.fillMaxWidth().height(372.mx), title = "Log", sub = "newest first · showing ${shown.size.coerceAtMost(50)} of ${shown.size}", gap = 8.mx) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.mx)) {
                items(shown.take(50)) { l ->
                    val c = when { l.level == "error" -> Level.Bad; isWarn(l) -> Level.Warn; else -> Level.Good }
                    val source = remember { MutableInteractionSource() }
                    val f by source.collectIsFocusedAsState()
                    Row(
                        Modifier.fillMaxWidth().height(30.mx).background(if (f) Color.White.copy(alpha = 0.07f) else Color.Transparent, VantagePill)
                            .focusable(interactionSource = source).padding(horizontal = 12.mx),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mx),
                    ) {
                        Text(l.time.hm(), style = mono(12, color = Level.Faint), modifier = Modifier.width(52.mx), maxLines = 1)
                        VChip(l.level.uppercase().take(5), if (l.level == "error") ChipTone.Bad else if (isWarn(l)) ChipTone.Warn else ChipTone.Plain, size = 10, modifier = Modifier.width(64.mx))
                        Text(l.component, style = mono(12, color = Level.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.mx))
                        Text(l.message, style = mono(12), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Unreachable(app: AutomationApp) {
    Column {
        PageHead("${app.title} · not answering", "Can't reach ${app.title}", kickColor = Level.Bad)
        Spacer(Modifier.height(20.mx))
        VPanel(Modifier.width(680.mx), title = "Check the server settings", sub = "retrying") {
            Text(
                "Silo can't reach ${app.title} or it refused the API key. Check Admin → Plugins → Automation dashboards → ${app.title} in Silo.",
                style = ui(15, FontWeight.Normal, Level.Synopsis),
            )
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.padding(top = 60.mx)) { Text("Loading…", style = mono(14, color = Level.Dim)) }
}

// ---- System -------------------------------------------------------------------------------

private data class SysSample(val hostCpu: Float, val siloCpu: Float, val ffCpu: Float, val goroutines: Float, val ioMBs: Float, val gpu: Float = 0f, val net: Float = 0f)

private fun bytesShort(b: Long?): String = when {
    b == null -> "–"
    b >= 1_000_000_000L -> "%.2f GB".format(b / 1e9)
    else -> "%.0f MB".format(b / 1e6)
}

private fun cpuTime(sec: Double?): String {
    if (sec == null) return "–"
    val s = sec.toLong()
    return if (s >= 3600) "%dh %02dm".format(s / 3600, (s % 3600) / 60) else "%dm %02ds".format(s / 60, s % 60)
}

private val Purple = Color(0xFFC79BFF)

@Composable
private fun SystemPage(repo: TvServerRepository, serverName: String, overview: ServerOverview?) {
    var stats by remember { mutableStateOf<org.siloserver.silo.tv.server.SystemStats?>(null) }
    val history = remember { mutableStateOf(listOf<SysSample>()) }
    val netMbps by rememberUpdatedState(((overview?.netTxBps ?: 0.0) * 8 / 1e6).toFloat())
    LaunchedEffect(Unit) {
        var prev: org.siloserver.silo.tv.server.SystemStats? = null
        while (true) {
            val now = runCatching { repo.system() }.getOrNull()
            if (now != null) {
                val p = prev
                if (p != null) {
                    val dt = (now.sampledAt.toEpochMilli() - p.sampledAt.toEpochMilli()) / 1000.0
                    if (dt > 0.5) {
                        fun pct(a: Double?, b: Double?) = if (a != null && b != null) (((a - b) / dt) / now.cores * 100).coerceIn(0.0, 100.0).toFloat() else 0f
                        val io = if (now.readBytes != null && p.readBytes != null) ((now.readBytes - p.readBytes + (now.writeBytes ?: 0) - (p.writeBytes ?: 0)) / dt / 1e6).toFloat().coerceAtLeast(0f) else 0f
                        history.value = (history.value + SysSample(now.hostCpuPct.toFloat(), pct(now.processCpuSeconds, p.processCpuSeconds), pct(now.ffmpegCpuSeconds, p.ffmpegCpuSeconds), (now.goroutines ?: 0).toFloat(), io, (now.gpuSessions * 25f).coerceAtMost(100f), netMbps)).takeLast(3600)
                    }
                }
                prev = now; stats = now
            }
            delay(500)
        }
    }
    val s = stats ?: run { Loading(); return }
    val h = history.value
    val last = h.lastOrNull()
    val totalGb = s.memTotalMb / 1024.0
    val usedGb = s.memUsedMb / 1024.0
    val procGb = (s.processRssBytes ?: 0) / 1.073741824e9
    val contGb = ((s.containerWorkingSetBytes ?: 0) / 1.073741824e9 - procGb).coerceAtLeast(0.0)
    val ffGb = s.ffmpegRssBytes / 1.073741824e9
    val siloGb = procGb + contGb
    val otherGb = (usedGb - siloGb - ffGb).coerceAtLeast(0.0)
    val freeGb = (totalGb - usedGb).coerceAtLeast(0.0)
    val tight = usedGb / totalGb.coerceAtLeast(0.1) > 0.85 || (s.oomKills ?: 0) > 0 || (s.cpuThrottledSeconds ?: 0.0) > 0
    val gpuPct = (s.gpuSessions * 25).coerceAtMost(100)
    val io = last?.ioMBs ?: 0f
    val uplink = 110f
    Column {
        Spacer(Modifier.height(4.mx))
        Row(Modifier.height(34.mx), verticalAlignment = Alignment.CenterVertically) {
            Text("● SYSTEM · ${serverName.uppercase()} · ${s.cores}-CORE", style = mono(11, color = if (tight) Level.Warn else Level.Good, tracking = 0.16f), maxLines = 1)
        }
        Spacer(Modifier.height(16.mx))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.mx)) {
            BigCard("RAM", "%.1f".format(usedGb), "GB", modifier = Modifier.weight(1f))
            BigCard("CPU", "${s.hostCpuPct.toInt()}", "%", if (s.hostCpuPct > 85) Level.Warn else Level.Fg, Modifier.weight(1f))
            BigCard("GPU", "$gpuPct", "%", modifier = Modifier.weight(1f))
            BigCard("Network out", "%.0f".format(netMbps), "Mbps", modifier = Modifier.weight(1f))
            BigCard("Disk I/O", "%.1f".format(io), "MB/s", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.mx))
        Row(horizontalArrangement = Arrangement.spacedBy(16.mx)) {
            VPanel(Modifier.width(294.mx).height(336.mx), title = "Memory", sub = "host RAM", gap = 14.mx) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.mx)) {
                    Text("%.1f".format(usedGb), style = ui(40, FontWeight.SemiBold), maxLines = 1)
                    Text("GB of %.1f".format(totalGb), style = mono(13, color = Level.Dim), maxLines = 1, modifier = Modifier.padding(bottom = 4.mx))
                }
                val segs = listOf(
                    Triple(siloGb, Level.liveAccent, "Silo"),
                    Triple(ffGb, Purple, "Transcoding"),
                    Triple(otherGb, Color.White.copy(alpha = 0.38f), "Other apps"),
                )
                Row(Modifier.fillMaxWidth().height(22.mx).clip(androidx.compose.foundation.shape.RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.08f)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    segs.forEach { (gb, c, _) -> if (gb > 0) Box(Modifier.weight((gb / totalGb).toFloat().coerceAtLeast(0.002f)).fillMaxHeight().background(c)) }
                    if (freeGb > 0) Spacer(Modifier.weight((freeGb / totalGb).toFloat().coerceAtLeast(0.002f)))
                }
                Column(verticalArrangement = Arrangement.spacedBy(11.mx)) {
                    (segs + Triple(freeGb, Color.White.copy(alpha = 0.10f), "Free")).forEach { (gb, c, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mx)) {
                            Box(Modifier.size(14.mx).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(c).border(1.dp, Color.White.copy(alpha = 0.18f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
                            Text(label, style = ui(14, FontWeight.SemiBold), maxLines = 1, modifier = Modifier.weight(1f))
                            Text("%.1f".format(gb), style = mono(13, color = Level.Dim), maxLines = 1)
                            Text("%d%%".format(Math.round(gb * 100 / totalGb.coerceAtLeast(0.1))), style = mono(13, FontWeight.SemiBold), maxLines = 1, modifier = Modifier.width(38.mx))
                        }
                    }
                }
            }
            VPanel(Modifier.width(882.mx).height(336.mx), title = "Last hour", sub = "CPU · GPU · network · disk", gap = 10.mx) {
                val ioMax = (h.maxOfOrNull { it.ioMBs } ?: 1f).coerceAtLeast(1f)
                Row(horizontalArrangement = Arrangement.spacedBy(10.mx)) {
                    Column(Modifier.height(150.mx), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                        listOf("100%", "75%", "50%", "25%", "0%").forEach { Text(it, style = mono(10.5, color = Level.Faint), maxLines = 1) }
                    }
                    Box(Modifier.weight(1f).height(150.mx)) {
                        VLineChart(h.map { it.hostCpu }, Level.liveAccent, Modifier.fillMaxSize(), maxOverride = 100f)
                        VLineChart(h.map { it.gpu }, Purple, Modifier.fillMaxSize(), maxOverride = 100f)
                        VLineChart(h.map { it.net }, Level.Good, Modifier.fillMaxSize(), maxOverride = uplink)
                        VLineChart(h.map { it.ioMBs }, Color(0xFFF5C26B), Modifier.fillMaxSize(), maxOverride = ioMax * 1.1f)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.mx)) {
                    SysKey(Level.liveAccent, "CPU", "${s.hostCpuPct.toInt()}%")
                    SysKey(Purple, "GPU", "$gpuPct%")
                    SysKey(Level.Good, "Network", "%.0f Mbps".format(netMbps))
                    SysKey(Color(0xFFF5C26B), "Disk I/O", "%.1f MB/s".format(io))
                }
                Text("Network is drawn as a share of your 110 Mbps uplink; disk I/O against its busiest moment", style = mono(11, color = Level.Faint))
            }
        }
    }
}

@Composable
private fun SysKey(color: Color, label: String, now: String) {
    Row(
        Modifier.background(Level.Glass2, VantagePill).border(1.dp, Level.Edge, VantagePill).padding(horizontal = 12.mx, vertical = 6.mx),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.mx),
    ) {
        Box(Modifier.width(22.mx).height(4.mx).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(color))
        Text(label, style = ui(13, FontWeight.SemiBold), maxLines = 1)
        Text("now $now", style = mono(12, color = Level.Dim), maxLines = 1)
    }
}

private fun gbText(gb: Double): String = if (gb < 1) "%.0f MB".format(gb * 1024) else "%.1f GB".format(gb)

@Composable
private fun Legend(color: Color, label: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.mx)) {
        Box(Modifier.size(9.mx).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(color).border(1.dp, Level.Edge, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
        Text(label, style = mono(11, color = Level.Dim))
        if (value != null) Text(value, style = mono(11))
    }
}

@Composable
private fun CpuRing(pct: Int, color: Color, label: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        VRing(pct, color, label, 70.mx)
        Text(sub, style = mono(10, color = Level.Faint), maxLines = 1)
    }
}

@Composable
private fun MiniStat(label: String, value: String, suffix: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        VLabel(label)
        VBig(value, suffix, 28)
    }
}
