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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
            delay(1_000)
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
                ServerTab.System -> SystemPage(repo, serverName)
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
private fun OverviewPage(o: ServerOverview?, repo: TvServerRepository) {
    val scope = rememberCoroutineScope()
    var scanNote by remember { mutableStateOf<String?>(null) }
    if (o == null) { Loading(); return }
    val warnings = buildList {
        o.disks.filter { it.fraction > 0.9f }.forEach { add("${it.label} disk above 90%") }
        if (o.restartRequired) add("Restart required")
        if (!o.dbOk) add("Database not answering")
        if (!o.redisOk) add("Redis not answering")
    }
    val healthy = warnings.isEmpty() && o.errors24h == 0
    Column {
        PageHead(
            kick = "● Server Control · ${o.serverName} · ${uptime(o.startedAt)}",
            title = if (healthy) "Healthy" else if (warnings.isEmpty()) "Running" else "Healthy",
            warn = when {
                warnings.size == 1 -> "1 warning"
                warnings.size > 1 -> "${warnings.size} warnings"
                o.errors24h > 0 -> "${o.errors24h} errors today"
                else -> null
            },
            kickColor = if (healthy) Level.Good else Level.Warn,
        )
        Spacer(Modifier.height(20.mx))
        Row(horizontalArrangement = Arrangement.spacedBy(20.mx)) {
            // Left column: streams + bandwidth.
            Column(Modifier.width(560.mx), verticalArrangement = Arrangement.spacedBy(16.mx)) {
                VPanel(Modifier.fillMaxWidth().height(290.mx), title = "Live streams", sub = "${o.streams.size} playing · ${o.users} users") {
                    if (o.streams.isEmpty()) {
                        Text("Nothing is playing right now.", style = mono(12, color = Level.Dim))
                    }
                    o.streams.take(3).forEachIndexed { i, s ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Level.Edge))
                        StreamRow(s)
                    }
                }
                VPanel(Modifier.fillMaxWidth().height(158.mx), title = "Bandwidth out", sub = "last 3 hours · peak ${"%.0f".format(o.egress.maxOrNull() ?: 0f)} Mbps") {
                    VLineChart(o.egress, modifier = Modifier.fillMaxWidth().height(80.mx))
                }
            }
            // Middle: hardware + library.
            Column(Modifier.width(300.mx), verticalArrangement = Arrangement.spacedBy(16.mx)) {
                VPanel(Modifier.fillMaxWidth().height(290.mx), title = "Hardware", sub = "NAS") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        VRing((o.cpuPct ?: 0.0).toInt(), Level.Good, "CPU %", 74.mx)
                        VRing(((o.gpuSessions ?: 0) * 20).coerceAtMost(100), Level.liveAccent, "GPU jobs", 74.mx)
                        VRing(if ((o.memTotalMb ?: 0.0) > 0) ((o.memUsedMb ?: 0.0) * 100 / o.memTotalMb!!).toInt() else 0, Level.liveAccent, "RAM %", 74.mx)
                    }
                    KeyValues(
                        "Load" to "%.2f · %d cores".format(o.load1 ?: 0.0, o.cores ?: 0),
                        "Network" to "↑ %s  ↓ %s".format(mbps(o.netTxBps), mbps(o.netRxBps)),
                        "Database" to (if (o.dbOk) "OK · %.1f ms".format(o.dbLatencyMs ?: 0.0) else "DOWN"),
                        "Cache" to (if (o.redisOk) "Redis OK" else "Redis DOWN"),
                        "GPU" to (o.gpu ?: "none"),
                        colors = mapOf("Database" to if (o.dbOk) Level.Good else Level.Bad, "Cache" to if (o.redisOk) Level.Good else Level.Bad),
                    )
                }
                VPanel(Modifier.fillMaxWidth().height(158.mx), title = "Library", sub = "%,d files".format(o.files)) {
                    VBig("%,d".format(o.totalItems), "titles")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.mx)) {
                        VChip("%,d movies".format(o.movies))
                        VChip("%,d series".format(o.shows))
                    }
                }
            }
            // Right: storage + health log + actions.
            Column(Modifier.width(292.mx), verticalArrangement = Arrangement.spacedBy(16.mx)) {
                VPanel(Modifier.fillMaxWidth(), title = "Storage", sub = "${o.disks.size} volumes") {
                    o.disks.forEach { DiskBar(it.label, it.usedGb, it.totalGb, it.fraction) }
                }
                VPanel(
                    Modifier.fillMaxWidth().height(166.mx),
                    title = "Health log",
                    sub = if (warnings.isEmpty()) "all clear" else "${warnings.size} warning${if (warnings.size > 1) "s" else ""}",
                    subColor = if (warnings.isEmpty()) Level.Good else Level.Warn,
                    edge = if (warnings.isEmpty()) Level.Edge else Level.Warn.copy(alpha = 0.45f),
                    gap = 6.mx,
                ) {
                    warnings.take(2).forEach { HealthLine("▲", Level.Warn, "now", it) }
                    o.logs.take(4 - warnings.size.coerceAtMost(2)).forEach { l ->
                        HealthLine("●", if (l.level == "error") Level.Bad else if (l.level == "warn") Level.Warn else Level.Good, l.time.hm(), l.message)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.mx)) {
                    VButton("Scan library", onClick = {
                        scope.launch { scanNote = if (repo.scanLibraries()) "Scan started" else "Scan failed" }
                    }, primary = true, height = 42.mx, textPx = 13)
                    if (scanNote != null) Text(scanNote!!, style = mono(11, color = Level.Dim), modifier = Modifier.align(Alignment.CenterVertically))
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

@Composable
private fun StreamRow(s: org.siloserver.silo.tv.server.LiveStream, big: Boolean = false) {
    val direct = s.method.contains("direct", true)
    val remux = s.method.contains("remux", true)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mx)) {
        VArt(s.posterUrl, if (big) 176.mx else 116.mx, if (big) 99.mx else 65.mx, progress = s.progress)
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

@Composable
private fun StreamsPage(o: ServerOverview?) {
    if (o == null) { Loading(); return }
    Column {
        PageHead("Streams · ${o.streams.size} playing", if (o.streams.isEmpty()) "Quiet" else "Now playing")
        Spacer(Modifier.height(20.mx))
        Row(horizontalArrangement = Arrangement.spacedBy(16.mx)) {
            VPanel(Modifier.width(760.mx).height(470.mx), title = "Sessions", sub = "live") {
                if (o.streams.isEmpty()) Text("Nobody is watching right now.", style = mono(12, color = Level.Dim))
                o.streams.forEachIndexed { i, s ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Level.Edge))
                    StreamRow(s, big = true)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.mx)) {
                VPanel(Modifier.width(416.mx).height(227.mx), title = "Concurrent streams", sub = "last 3 hours") {
                    VLineChart(o.streamCounts, Level.Good, Modifier.fillMaxWidth().height(130.mx), maxOverride = (o.streamCounts.maxOrNull() ?: 1f).coerceAtLeast(1f))
                }
                VPanel(Modifier.width(416.mx).height(227.mx), title = "Bandwidth out", sub = "now %s".format(mbps(o.netTxBps))) {
                    VLineChart(o.egress, modifier = Modifier.fillMaxWidth().height(130.mx))
                }
            }
        }
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
    Column {
        PageHead(
            "${app.title} · ${if (isRadarr) "movies" else "series"}",
            if (isRadarr) "Movies" else "Series",
            version = "v${d.version.substringBeforeLast('.').ifBlank { d.version }} · ${if (d.health.none { it.tone == Tone.Bad }) "healthy" else "check health"}",
        )
        Spacer(Modifier.height(18.mx))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.mx)) {
            if (isRadarr) VStat("Movies", "%,d".format(d.total ?: 0), d.totalLabel, modifier = Modifier.weight(1f))
            else VStat("Airing this week", "${d.upcoming ?: 0}", "episodes", modifier = Modifier.weight(1f))
            VStat("Missing", "${d.missing ?: 0}", if (isRadarr) "monitored, no file" else "episodes", if ((d.missing ?: 0) > 0) Level.Warn else Level.Fg, Modifier.weight(1f))
            VStat(if (isRadarr) "Upcoming" else "Cutoff unmet", "${if (isRadarr) d.upcoming ?: 0 else d.cutoffUnmet ?: 0}", if (isRadarr) "next 30 days" else "upgrades wanted", modifier = Modifier.weight(1f))
            VStat("Queue", "${d.queue.size}", if (d.queue.isEmpty()) "idle" else "${d.queue.count { (it.progress ?: 0f) > 0f }} downloading", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.mx))
        if (!isRadarr) {
            // Week calendar.
            VPanel(Modifier.fillMaxWidth().height(196.mx), title = "Airing this week", sub = "${d.calendar.firstOrNull()?.date?.format(DateTimeFormatter.ofPattern("EEE d")) ?: ""} to ${d.calendar.lastOrNull()?.date?.format(DateTimeFormatter.ofPattern("EEE d")) ?: ""}") {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.mx)) {
                    val today = java.time.LocalDate.now()
                    d.calendar.forEach { day ->
                        val isToday = day.date == today
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(if (isToday) Level.liveAccent.copy(alpha = 0.16f) else Level.Glass2, androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
                                .border(1.dp, if (isToday) Level.liveAccent else Level.Edge, androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
                                .padding(8.mx),
                            verticalArrangement = Arrangement.spacedBy(5.mx),
                        ) {
                            VLabel(day.date.format(DateTimeFormatter.ofPattern("EEE d")), if (isToday) Level.liveAccent else Level.Faint)
                            if (day.items.isEmpty()) Text("nothing airing", style = mono(10, color = Level.Faint))
                            day.items.take(2).forEach { e -> VArt(e.imageUrl, 132.mx, 52.mx) }
                            if (day.items.size > 2) Text("+${day.items.size - 2} more", style = mono(10, color = Level.Dim))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.mx))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.mx)) {
            Column(Modifier.width(600.mx), verticalArrangement = Arrangement.spacedBy(16.mx)) {
                VPanel(Modifier.fillMaxWidth().height(if (isRadarr) 206.mx else 186.mx), title = "Queue", sub = "from the download client", gap = 12.mx) {
                    if (d.queue.isEmpty()) Text("Nothing downloading.", style = mono(12, color = Level.Dim))
                    d.queue.take(2).forEachIndexed { i, q -> VListRow(q.title, q.subtitle, q.right, q.imageUrl, q.progress, divider = i == 0 && d.queue.size > 1) }
                }
                if (isRadarr) {
                    VPanel(Modifier.fillMaxWidth().height(186.mx), title = "Recent activity", sub = "latest first", gap = 9.mx) {
                        d.activity.take(4).forEach { VEventLine(it.text, it.tone.color(), it.time.hm()) }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.mx)) {
                if (isRadarr) {
                    VPanel(Modifier.fillMaxWidth().height(206.mx), title = "Coming soon", sub = "release dates") {
                        val soon = d.calendar.flatMap { day -> day.items.map { day.date to it } }.take(4)
                        if (soon.isEmpty()) Text("Nothing due in the next 30 days.", style = mono(12, color = Level.Dim))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.mx)) {
                            soon.forEach { (date, m) ->
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.mx)) {
                                    VArt(m.imageUrl, 128.mx, 72.mx)
                                    VChip(date.format(DateTimeFormatter.ofPattern("EEE d")))
                                }
                            }
                        }
                        Text(soon.joinToString(" · ") { it.second.title }, style = mono(11, color = Level.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                VPanel(Modifier.fillMaxWidth().height(186.mx), title = if (isRadarr) "Health and wanted" else "Recent activity and wanted", sub = if (d.health.isEmpty()) "no health issues" else "${d.health.size} notices", gap = 8.mx) {
                    if (isRadarr) {
                        d.health.take(2).forEach { VEventLine(it.text, it.tone.color(), "") }
                        if (d.health.isEmpty()) VEventLine("Indexers and download client", Level.Good, "OK")
                    } else {
                        d.activity.take(2).forEach { VEventLine(it.text, it.tone.color(), it.time.hm()) }
                        d.health.take(1).forEach { VEventLine(it.text, it.tone.color(), "") }
                    }
                    VEventLine("Cutoff unmet", Level.Warn, "${d.cutoffUnmet ?: 0} ${if (isRadarr) "movies" else "episodes"}")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.mx), verticalAlignment = Alignment.CenterVertically) {
                        VButton("Search missing", onClick = {
                            scope.launch { note = if (repo.arrCommand(app, if (isRadarr) "MissingMoviesSearch" else "MissingEpisodeSearch")) "Search started" else "Failed" }
                        }, primary = true, height = 32.mx, textPx = 12)
                        VButton(if (isRadarr) "Refresh library" else "Refresh series", onClick = {
                            scope.launch { note = if (repo.arrCommand(app, if (isRadarr) "RefreshMovie" else "RefreshSeries")) "Refresh started" else "Failed" }
                        }, height = 32.mx, textPx = 12)
                        note?.let { Text(it, style = mono(11, color = Level.Dim)) }
                    }
                }
            }
        }
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
    Column {
        PageHead("SABnzbd · downloads", if (d.paused) "Paused" else if (d.queue.isEmpty()) "Idle" else "Downloading", version = "v${d.version} · ${if (d.paused) "paused" else "running"}")
        Spacer(Modifier.height(18.mx))
        Row(horizontalArrangement = Arrangement.spacedBy(16.mx)) {
            Column(Modifier.width(600.mx), verticalArrangement = Arrangement.spacedBy(16.mx)) {
                VPanel(Modifier.fillMaxWidth().height(206.mx), title = "Speed", sub = "last 3 minutes") {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(22.mx)) {
                        VBig("%.1f".format(d.speedMBs), "MB/s", 58)
                        Text("avg %.1f · peak %.1f\nlimit: %s".format(if (sp.isEmpty()) 0f else sp.average().toFloat(), sp.maxOrNull() ?: 0f, d.speedLimit), style = mono(11, color = Level.Dim))
                    }
                    VLineChart(sp, modifier = Modifier.fillMaxWidth().height(60.mx))
                }
                VPanel(Modifier.fillMaxWidth().height(260.mx), title = "Queue", sub = "${d.queue.size} jobs · ${d.sizeLeft} left · ETA ${d.timeLeft}", gap = 12.mx) {
                    if (d.queue.isEmpty()) Text("The queue is empty.", style = mono(12, color = Level.Dim))
                    d.queue.take(3).forEachIndexed { i, q -> VListRow(q.title, q.subtitle, q.right, null, q.progress, divider = i < minOf(2, d.queue.size - 1), artWidth = 0.mx) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.mx)) {
                VPanel(Modifier.fillMaxWidth().height(206.mx), title = "Usenet servers", sub = "${d.servers.size} providers", gap = 11.mx) {
                    d.servers.take(2).forEach { s ->
                        Column {
                            Row {
                                Text(s.name, style = mono(12))
                                Text("  ● ${s.activeConnections ?: 0}${s.maxConnections?.let { " / $it" } ?: ""} connections", style = mono(12, color = Level.Good))
                                Spacer(Modifier.weight(1f))
                                Text(s.detail, style = mono(12, color = Level.Dim))
                            }
                            Spacer(Modifier.height(5.mx))
                            VBar(if ((s.maxConnections ?: 0) > 0) (s.activeConnections ?: 0).toFloat() / s.maxConnections!! else 0f)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.mx)) {
                        VButton(if (d.paused) "Resume" else "Pause", onClick = {
                            scope.launch { repo.sabAction(if (d.paused) "resume" else "pause"); refresh++ }
                        }, primary = true, height = 32.mx, textPx = 12)
                        VButton(if (d.speedLimit == "none") "Limit to 50%" else "Remove limit", onClick = {
                            scope.launch { repo.sabAction("speedlimit", if (d.speedLimit == "none") 50 else 100); refresh++ }
                        }, height = 32.mx, textPx = 12)
                    }
                }
                VPanel(Modifier.fillMaxWidth().height(160.mx), title = "History", sub = "latest", gap = 9.mx) {
                    d.history.take(3).forEach { VEventLine(it.text, it.tone.color(), it.time.hm()) }
                }
                VPanel(Modifier.fillMaxWidth().height(84.mx), title = "Disk and warnings", sub = "incomplete folder", gap = 8.mx) {
                    val total = d.diskTotalGb ?: 0.0; val free = d.diskFreeGb ?: 0.0
                    Row {
                        Text("Free ${storageTb(free)} of ${storageTb(total)}", style = mono(12, color = Level.Dim), modifier = Modifier.weight(1f))
                        Text("${d.warnings} warnings", style = mono(12, color = if (d.warnings == 0) Level.Good else Level.Warn))
                    }
                    VBar(if (total > 0) ((total - free) / total).toFloat() else 0f)
                }
            }
        }
    }
}

@Composable
private fun StoragePage(o: ServerOverview?) {
    if (o == null) { Loading(); return }
    val used = o.disks.filter { it.label != "Transcode scratch" }.sumOf { it.usedGb }
    val total = o.disks.filter { it.label != "Transcode scratch" }.sumOf { it.totalGb }
    Column {
        PageHead("Storage · ${o.disks.size} volumes", "${storageTb(total - used)} free")
        Spacer(Modifier.height(20.mx))
        Row(horizontalArrangement = Arrangement.spacedBy(16.mx)) {
            VPanel(Modifier.width(760.mx), title = "Volumes", sub = "library and scratch", gap = 18.mx) {
                o.disks.forEach { DiskBar(it.label, it.usedGb, it.totalGb, it.fraction) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.mx)) {
                VStat("Library size", storageTb(o.storageBytes / 1e9), "on disk", modifier = Modifier.width(416.mx))
                VStat("Files", "%,d".format(o.files), "${o.movies} movies · ${o.shows} series", modifier = Modifier.width(416.mx))
                VStat("Fullest volume", "${((o.disks.maxOfOrNull { it.fraction } ?: 0f) * 100).toInt()}%", o.disks.maxByOrNull { it.fraction }?.label.orEmpty(), if ((o.disks.maxOfOrNull { it.fraction } ?: 0f) > 0.9f) Level.Warn else Level.Fg, Modifier.width(416.mx))
            }
        }
    }
}

@Composable
private fun LogsPage(o: ServerOverview?, repo: TvServerRepository) {
    var warns by remember { mutableStateOf<List<LogLine>>(emptyList()) }
    LaunchedEffect(Unit) { while (true) { warns = runCatching { repo.warningLogs() }.getOrDefault(warns); delay(10_000) } }
    if (o == null) { Loading(); return }
    val all = (warns + o.logs).distinctBy { it.time to it.message }.sortedByDescending { it.time }
    Column {
        PageHead("Logs · ${o.errors24h} errors · ${o.warnings24h} warnings today", "Server log")
        Spacer(Modifier.height(16.mx))
        Box(Modifier.fillMaxSize().padding(bottom = 28.mx).background(Level.Glass, VantagePanel).border(1.dp, Level.Edge, VantagePanel).padding(16.mx)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.mx)) {
                items(all) { l ->
                    val c = when (l.level) { "error" -> Level.Bad; "warn", "warning" -> Level.Warn; else -> Level.Good }
                    val source = remember { MutableInteractionSource() }
                    val f by source.collectIsFocusedAsState()
                    Row(
                        Modifier.fillMaxWidth().background(if (f) Level.Glass2 else Color.Transparent, VantagePill).focusable(interactionSource = source).padding(horizontal = 8.mx, vertical = 3.mx),
                        horizontalArrangement = Arrangement.spacedBy(12.mx),
                    ) {
                        Text(l.time.hm(), style = mono(12, color = c))
                        Text(l.level.uppercase().take(4), style = mono(12, color = c), modifier = Modifier.width(44.mx))
                        Text(l.component, style = mono(12, color = Level.Faint), maxLines = 1, modifier = Modifier.width(110.mx))
                        Text(l.message, style = mono(12), maxLines = 1, overflow = TextOverflow.Ellipsis)
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

private data class SysSample(val hostCpu: Float, val siloCpu: Float, val ffCpu: Float, val goroutines: Float, val ioMBs: Float)

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
private fun SystemPage(repo: TvServerRepository, serverName: String) {
    var stats by remember { mutableStateOf<org.siloserver.silo.tv.server.SystemStats?>(null) }
    val history = remember { mutableStateOf(listOf<SysSample>()) }
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
                        history.value = (history.value + SysSample(now.hostCpuPct.toFloat(), pct(now.processCpuSeconds, p.processCpuSeconds), pct(now.ffmpegCpuSeconds, p.ffmpegCpuSeconds), (now.goroutines ?: 0).toFloat(), io)).takeLast(3600)
                    }
                }
                prev = now; stats = now
            }
            delay(1_000)
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
    val otherGb = (usedGb - procGb - contGb - ffGb).coerceAtLeast(0.0)
    val siloGb = procGb + contGb + ffGb
    val swapGb = (s.swapBytes ?: 0) / 1.073741824e9
    val busy = s.ffmpegCount > 0 || s.hostCpuPct > 60
    val tight = usedGb / totalGb.coerceAtLeast(0.1) > 0.85 || (s.oomKills ?: 0) > 0 || (s.cpuThrottledSeconds ?: 0.0) > 0
    Column {
        PageHead(
            kick = "● System · $serverName · ${s.cores}-core · %.1f GB RAM".format(totalGb),
            title = when { tight -> "Running tight"; busy -> "Busy"; else -> "Plenty of headroom" },
            kickColor = if (tight) Level.Warn else Level.Good,
        )
        Spacer(Modifier.height(20.mx))
        Row(horizontalArrangement = Arrangement.spacedBy(16.mx)) {
            VPanel(Modifier.width(600.mx).height(282.mx), title = "Memory", sub = "host RAM, and what Silo is using", gap = 9.mx) {
                Row(horizontalArrangement = Arrangement.spacedBy(22.mx), verticalAlignment = Alignment.Bottom) {
                    Column { VLabel("NAS in use"); VBig("%.1f".format(usedGb), "of %.1f GB · %d%%".format(totalGb, (usedGb * 100 / totalGb.coerceAtLeast(0.1)).toInt())) }
                    Column { VLabel("Silo"); VBig(if (siloGb >= 1) "%.2f GB".format(siloGb) else "%.0f MB".format(siloGb * 1024), if (ffGb > 0) "incl. ${s.ffmpegCount} transcoder${if (s.ffmpegCount > 1) "s" else ""}" else "container · process ${bytesShort(s.processRssBytes)}", color = Level.liveAccent) }
                }
                val segs = listOf(
                    Triple(procGb, Level.liveAccent, "Silo process"),
                    Triple(contGb, Level.liveAccent.copy(alpha = 0.45f), "Rest of Silo container"),
                    Triple(ffGb, Purple, "Transcoders (ffmpeg)"),
                    Triple(otherGb, Color.White.copy(alpha = 0.32f), "Other apps on the NAS"),
                )
                Row(Modifier.fillMaxWidth().height(16.mx).clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Level.Edge, androidx.compose.foundation.shape.RoundedCornerShape(5.dp))) {
                    segs.forEach { (gb, c, _) -> if (gb > 0) Box(Modifier.weight((gb / totalGb).toFloat().coerceAtLeast(0.002f)).fillMaxHeight().background(c)) }
                    val free = (totalGb - usedGb).coerceAtLeast(0.0)
                    if (free > 0) Spacer(Modifier.weight((free / totalGb).toFloat().coerceAtLeast(0.002f)))
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.mx)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.mx)) { segs.take(2).forEach { Legend(it.second, it.third, gbText(it.first)) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.mx)) {
                        segs.drop(2).forEach { Legend(it.second, it.third, gbText(it.first)) }
                        Legend(Color.White.copy(alpha = 0.08f), "Free", gbText(totalGb - usedGb))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Level.Edge))
                Row(horizontalArrangement = Arrangement.spacedBy(20.mx)) {
                    Column(Modifier.weight(1f)) { KeyValues("Go heap" to "${bytesShort(s.heapLiveBytes)} live", "Container charged" to "${bytesShort(s.containerChargedBytes)} incl. cache") }
                    Column(Modifier.weight(1f)) {
                        KeyValues("Swap (container)" to "%.2f GB".format(swapGb), "Out-of-memory kills" to "${s.oomKills ?: 0}",
                            colors = mapOf("Out-of-memory kills" to if ((s.oomKills ?: 0) == 0L) Level.Good else Level.Bad))
                    }
                }
            }
            VPanel(Modifier.weight(1f).height(282.mx), title = "CPU", sub = "live · since this page opened", gap = 10.mx) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.mx), verticalAlignment = Alignment.CenterVertically) {
                    CpuRing(s.hostCpuPct.toInt(), Level.Good, "NAS", "${s.cores} cores")
                    CpuRing((last?.siloCpu ?: 0f).toInt(), Level.liveAccent, "Silo", "%.1f cores".format((last?.siloCpu ?: 0f) * s.cores / 100))
                    CpuRing((last?.ffCpu ?: 0f).toInt(), Purple, "ffmpeg", if (s.ffmpegCount == 0) "idle" else "${s.ffmpegCount} running")
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().height(70.mx)) {
                            VLineChart(h.map { it.hostCpu }, Level.Good, Modifier.fillMaxSize(), maxOverride = 100f)
                            VLineChart(h.map { it.siloCpu }, Level.liveAccent, Modifier.fillMaxSize(), maxOverride = 100f)
                        }
                        Row(Modifier.padding(top = 4.mx), horizontalArrangement = Arrangement.spacedBy(12.mx)) { Legend(Level.Good, "NAS", null); Legend(Level.liveAccent, "Silo", null) }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Level.Edge))
                Row(horizontalArrangement = Arrangement.spacedBy(20.mx)) {
                    Column(Modifier.weight(1f)) { KeyValues("Load (1 min)" to "%.2f / %d".format(s.load1, s.cores), "Throttled" to "%.0f s".format(s.cpuThrottledSeconds ?: 0.0), colors = mapOf("Throttled" to if ((s.cpuThrottledSeconds ?: 0.0) > 0) Level.Warn else Level.Good)) }
                    Column(Modifier.weight(1f)) { KeyValues("Silo CPU time" to cpuTime(s.processCpuSeconds), "CPU pressure" to "%.0f%%".format(s.cpuPressurePct ?: 0.0)) }
                }
            }
        }
        Spacer(Modifier.height(16.mx))
        Row(horizontalArrangement = Arrangement.spacedBy(16.mx)) {
            VPanel(Modifier.weight(1f).height(228.mx), title = "Uptime", sub = "Silo server") {
                val up = s.startedAt?.let { Duration.between(it, Instant.now()) }
                if (up != null) VBig(if (up.toDays() > 0) "${up.toDays()}d ${up.toHours() % 24}h" else "${up.toHours()}h", "${up.toMinutes() % 60}m", 44)
                Text(
                    "since ${s.startedAt?.let { DateTimeFormatter.ofPattern("EEE d MMM, H:mm").withZone(ZoneId.systemDefault()).format(it) } ?: "–"}",
                    style = mono(11, color = Level.Dim),
                )
                Row { Text("restart needed: ", style = mono(11, color = Level.Dim)); Text(if (s.restartRequired) "yes" else "no", style = mono(11, color = if (s.restartRequired) Level.Warn else Level.Good)) }
                Spacer(Modifier.weight(1f))
                Text("build ${s.build.ifBlank { "–" }}" + (s.builtAt?.let { " · built " + DateTimeFormatter.ofPattern("d MMM H:mm").withZone(ZoneId.systemDefault()).format(it) } ?: ""), style = mono(10.5, color = Level.Faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            VPanel(Modifier.weight(1f).height(228.mx), title = "Silo process", sub = "Go runtime") {
                Row { MiniStat("Goroutines", "${s.goroutines ?: "–"}", null, Modifier.weight(1f)); MiniStat("Threads", "${s.threads ?: "–"}", null, Modifier.weight(1f)) }
                Row {
                    MiniStat("Open files", "${s.openFds ?: "–"}", s.maxFds?.let { if (it >= 1000) "of ${it / 1000}k" else "of $it" }, Modifier.weight(1f))
                    MiniStat("Go memory", "${(s.goMemoryBytes ?: 0) / 1_000_000}", "MB", Modifier.weight(1f))
                }
                VLineChart(h.map { it.goroutines }, modifier = Modifier.fillMaxWidth().height(30.mx), maxOverride = (h.maxOfOrNull { it.goroutines } ?: 1f) * 1.1f)
                VLabel("goroutines")
            }
            VPanel(Modifier.weight(1f).height(228.mx), title = "Disk I/O", sub = "by Silo, since start") {
                Row { MiniStat("Read", "%.1f".format((s.readBytes ?: 0) / 1e9), "GB", Modifier.weight(1f)); MiniStat("Written", "%.1f".format((s.writeBytes ?: 0) / 1e9), "GB", Modifier.weight(1f)) }
                VLineChart(h.map { it.ioMBs }, modifier = Modifier.fillMaxWidth().height(46.mx), maxOverride = (h.maxOfOrNull { it.ioMBs } ?: 1f).coerceAtLeast(1f) * 1.1f)
                Text("now %.1f MB/s".format(last?.ioMBs ?: 0f), style = mono(11, color = Level.Dim))
            }
            VPanel(Modifier.weight(1f).height(228.mx), title = "Transcoding", sub = if (s.ffmpegCount > 0) "busy" else "idle", edge = if (s.ffmpegCount > 0) Purple.copy(alpha = 0.55f) else Level.Edge) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.mx)) {
                    if (s.hwAccel.isEmpty()) VChip("Software only", ChipTone.Warn)
                    s.hwAccel.forEachIndexed { i, b -> VChip(if (i == 0) "$b verified" else b, if (i == 0) ChipTone.Good else ChipTone.Plain, dot = i == 0) }
                }
                Text(listOfNotNull(s.gpuName, s.gpuDevice).joinToString(" · "), style = mono(12, color = Level.Dim), maxLines = 1)
                Row { MiniStat("ffmpeg", "${s.ffmpegCount}", "running", Modifier.weight(1f)); MiniStat("RAM", if (ffGb >= 1) "%.1f".format(ffGb) else "%.0f".format(ffGb * 1024), if (ffGb >= 1) "GB" else "MB", Modifier.weight(1f)) }
                VLabel("GPU sessions")
                VBar((s.gpuSessions / 4f).coerceAtMost(1f), Purple, 8.mx)
                Text(if (s.ffmpegCount == 0) "No transcodes. Direct play uses no GPU." else "${s.ffmpegCount} transcode${if (s.ffmpegCount > 1) "s" else ""} on the GPU", style = mono(10.5, color = Level.Faint), maxLines = 1)
            }
        }
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
