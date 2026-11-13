package org.siloserver.silo.tv.server

import android.content.Context
import org.siloserver.silo.network.api.RawHttpApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ---------------------------------------------------------------------------------------------
// JSON helpers: the Server page reads several APIs it does not own (Silo admin, Radarr, Sonarr,
// SABnzbd), so it reads them loosely rather than through strict models that break on drift.
// ---------------------------------------------------------------------------------------------

internal val LooseJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray
internal operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
internal fun JsonElement?.str(): String? =
    (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
internal fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
internal fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toDoubleOrNull()?.toLong() }
internal fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

// ---------------------------------------------------------------------------------------------
// Automation app connections (Radarr, Sonarr, SABnzbd), stored on this TV only.
// ---------------------------------------------------------------------------------------------

enum class AutomationApp(val title: String, val defaultPort: Int) {
    Radarr("Radarr", 7878),
    Sonarr("Sonarr", 8989),
    Sabnzbd("SABnzbd", 8080),
}

data class AutomationConnection(val baseUrl: String, val apiKey: String) {
    val isComplete: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()
}

class AutomationSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("silo_vantage_automation", Context.MODE_PRIVATE)

    fun get(app: AutomationApp): AutomationConnection = AutomationConnection(
        baseUrl = prefs.getString("${app.name}_url", "").orEmpty(),
        apiKey = prefs.getString("${app.name}_key", "").orEmpty(),
    )

    fun set(app: AutomationApp, connection: AutomationConnection) {
        prefs.edit()
            .putString("${app.name}_url", connection.baseUrl.trim().trimEnd('/'))
            .putString("${app.name}_key", connection.apiKey.trim())
            .apply()
    }
}

// ---------------------------------------------------------------------------------------------
// Models the Server page draws.
// ---------------------------------------------------------------------------------------------

data class LiveStream(
    val who: String,
    val title: String,
    val client: String,
    val method: String,
    val bitrateMbps: Double?,
    val progress: Float,
    val paused: Boolean,
    val posterUrl: String?,
    val resolution: String?,
)

data class Disk(val label: String, val usedGb: Double, val totalGb: Double) {
    val fraction: Float get() = if (totalGb <= 0) 0f else (usedGb / totalGb).toFloat().coerceIn(0f, 1f)
}

data class LogLine(val time: Instant, val level: String, val component: String, val message: String)

data class ServerOverview(
    val serverName: String,
    val startedAt: Instant?,
    val dbOk: Boolean,
    val redisOk: Boolean,
    val dbLatencyMs: Double?,
    val errors24h: Int,
    val warnings24h: Int,
    val restartRequired: Boolean,
    val cpuPct: Double?,
    val memUsedMb: Double?,
    val memTotalMb: Double?,
    val cores: Int?,
    val load1: Double?,
    val netTxBps: Double?,
    val netRxBps: Double?,
    val gpu: String?,
    val gpuSessions: Int?,
    val disks: List<Disk>,
    val totalItems: Long,
    val movies: Long,
    val shows: Long,
    val files: Long,
    val users: Long,
    val storageBytes: Long,
    val streams: List<LiveStream>,
    /** Egress in Mbps over the last hour or so (oldest first). */
    val egress: List<Float>,
    val streamCounts: List<Float>,
    val logs: List<LogLine>,
)

data class RequestRow(
    val title: String,
    val year: Int?,
    val mediaType: String,
    val status: String,
    val outcome: String,
    val backdropUrl: String?,
    val requestedAt: Instant?,
    val detail: String,
)

data class ArrItem(
    val title: String,
    val subtitle: String,
    val right: String,
    val progress: Float? = null,
    val imageUrl: String? = null,
)

data class ArrEvent(val text: String, val time: Instant?, val tone: Tone)

enum class Tone { Good, Info, Warn, Bad }

data class CalendarDay(val date: LocalDate, val items: List<ArrItem>)

data class ArrDashboard(
    val app: AutomationApp,
    val version: String,
    val total: Long?,
    val totalLabel: String,
    val missing: Long?,
    val cutoffUnmet: Long?,
    val upcoming: Long?,
    val queue: List<ArrItem>,
    val activity: List<ArrEvent>,
    val calendar: List<CalendarDay>,
    val health: List<ArrEvent>,
)

data class SabServer(val name: String, val activeConnections: Int?, val maxConnections: Int?, val detail: String)

data class SabDashboard(
    val version: String,
    val paused: Boolean,
    val speedMBs: Double,
    val speedLimit: String,
    val sizeLeft: String,
    val timeLeft: String,
    val diskFreeGb: Double?,
    val diskTotalGb: Double?,
    val queue: List<ArrItem>,
    val history: List<ArrEvent>,
    val servers: List<SabServer>,
    val warnings: Int,
)

data class SystemStats(
    val sampledAt: Instant,
    val cores: Int,
    val hostCpuPct: Double,
    val load1: Double,
    val memUsedMb: Double,
    val memTotalMb: Double,
    val processRssBytes: Long?,
    val processCpuSeconds: Double?,
    val threads: Long?,
    val openFds: Long?,
    val maxFds: Long?,
    val readBytes: Long?,
    val writeBytes: Long?,
    val heapLiveBytes: Long?,
    val goMemoryBytes: Long?,
    val goroutines: Long?,
    val containerWorkingSetBytes: Long?,
    val containerChargedBytes: Long?,
    val swapBytes: Long?,
    val oomKills: Long?,
    val cpuThrottledSeconds: Double?,
    val cpuPressurePct: Double?,
    val containerCpuSeconds: Double?,
    val ffmpegCount: Int,
    val ffmpegRssBytes: Long,
    val ffmpegCpuSeconds: Double?,
    val startedAt: Instant?,
    val restartRequired: Boolean,
    val build: String,
    val builtAt: Instant?,
    val hwAccel: List<String>,
    val gpuName: String?,
    val gpuDevice: String?,
    val gpuSessions: Int,
)

// ---------------------------------------------------------------------------------------------
// Loading.
// ---------------------------------------------------------------------------------------------

class TvServerRepository(private val http: RawHttpApi) {
    private suspend fun siloJson(path: String): JsonElement? =
        http.siloGet(path)?.let { runCatching { LooseJson.parseToJsonElement(it) }.getOrNull() }

    suspend fun overview(serverName: String): ServerOverview = coroutineScope {
        val statsD = async { siloJson("/api/v1/admin/stats") }
        val statusD = async { siloJson("/api/v1/admin/server/status") }
        val resD = async { siloJson("/api/v1/admin/system/resources") }
        val sessD = async { siloJson("/api/v1/admin/sessions") }
        val tsD = async {
            val from = Instant.now().minusSeconds(3 * 3600)
            siloJson("/api/v1/admin/stats/timeseries?from=$from")
        }
        val logD = async { siloJson("/api/v1/admin/logs/app?limit=60&level=info") }
        val stats = statsD.await(); val status = statusD.await(); val res = resD.await()
        val sys = res["system"]
        val gpu0 = res["gpu"].arr()?.firstOrNull()
        val points = tsD.await()["points"].arr().orEmpty().takeLast(36)
        ServerOverview(
            serverName = serverName,
            startedAt = status["started_at"].str()?.let { runCatching { Instant.parse(it) }.getOrNull() },
            dbOk = status["health"]["postgres"]["ok"].bool() ?: false,
            redisOk = status["health"]["redis"]["ok"].bool() ?: false,
            dbLatencyMs = status["health"]["postgres"]["latency_ms"].num(),
            errors24h = status["health"]["errors_24h"].long()?.toInt() ?: 0,
            warnings24h = status["health"]["warnings_24h"].long()?.toInt() ?: 0,
            restartRequired = status["restart_required"].bool() ?: false,
            cpuPct = sys["cpu_pct"].num(),
            memUsedMb = sys["mem_used_mb"].num(),
            memTotalMb = sys["mem_total_mb"].num(),
            cores = sys["cores"].long()?.toInt(),
            load1 = sys["load1"].num(),
            netTxBps = sys["net_tx_bps"].num(),
            netRxBps = sys["net_rx_bps"].num(),
            gpu = gpu0?.let { g -> listOfNotNull(g["vendor"].str()?.replaceFirstChar { it.uppercase() }, g["device"].str()?.substringAfterLast('/')).joinToString(" · ") },
            gpuSessions = gpu0["sessions"].long()?.toInt(),
            disks = sys["disks"].arr().orEmpty().map { d ->
                val path = d["path"].str().orEmpty()
                val role = d["role"].str().orEmpty()
                Disk(
                    label = when {
                        d["scratch"].bool() == true -> "Transcode scratch"
                        path.isNotBlank() -> path.trimStart('/').replaceFirstChar { it.uppercase() }
                        else -> role
                    },
                    usedGb = d["used_gb"].num() ?: 0.0,
                    totalGb = d["total_gb"].num() ?: 0.0,
                )
            },
            totalItems = stats["total_items"].long() ?: 0,
            movies = stats["total_movies"].long() ?: 0,
            shows = stats["total_shows"].long() ?: 0,
            files = stats["total_files"].long() ?: 0,
            users = stats["total_users"].long() ?: 0,
            storageBytes = stats["total_storage_bytes"].long() ?: 0,
            streams = sessD.await().arr().orEmpty().map { s ->
                val dur = s["file_duration"].num() ?: 0.0
                val pos = s["position_seconds"].num() ?: 0.0
                val ep = if (s["series_name"].str() != null) {
                    val se = s["season_number"].long(); val en = s["episode_number"].long()
                    "${s["series_name"].str()} S${se ?: "?"}E${en ?: "?"}"
                } else null
                LiveStream(
                    who = s["profile_name"].str() ?: s["username"].str() ?: "Someone",
                    title = ep ?: s["media_title"].str().orEmpty(),
                    client = s["client_label"].str() ?: s["client_name"].str() ?: "",
                    method = s["play_method"].str().orEmpty(),
                    bitrateMbps = s["stream_bitrate_kbps"].num()?.div(1000.0),
                    progress = if (dur > 0) (pos / dur).toFloat().coerceIn(0f, 1f) else 0f,
                    paused = s["is_paused"].bool() ?: false,
                    posterUrl = s["poster_url"].str(),
                    resolution = s["target_resolution"].str(),
                )
            },
            egress = points.map { ((it["egress_kbps"].num() ?: 0.0) / 1000.0).toFloat() },
            streamCounts = points.map { (it["streams"].num() ?: 0.0).toFloat() },
            logs = logD.await()["entries"].arr().orEmpty().mapNotNull { e ->
                val msg = e["message"].str() ?: return@mapNotNull null
                // Request access logs drown everything else; keep the events.
                if (msg == "apiv2 request" || msg == "http request") return@mapNotNull null
                LogLine(
                    time = e["timestamp"].str()?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH,
                    level = e["level"].str().orEmpty(),
                    component = e["component"].str().orEmpty(),
                    message = msg,
                )
            },
        )
    }

    suspend fun system(): SystemStats? = coroutineScope {
        val resD = async { siloJson("/api/v2/admin/system/resources") }
        val statusD = async { siloJson("/api/v1/admin/server/status") }
        val buildD = async { siloJson("/api/v1/admin/system/build") }
        val hwD = async { siloJson("/api/v1/admin/system/hw-accel") }
        val res = resD.await() ?: return@coroutineScope null
        val sys = res["system"]; val at = res["attribution"]; val proc = at["process"]
        val cgm = at["cgroup_memory"]; val cgc = at["cgroup_cpu"]; val kids = at["children"]
        val status = statusD.await(); val build = buildD.await(); val hw = hwD.await()
        val gpu0 = res["gpu"].arr()?.firstOrNull()
        SystemStats(
            sampledAt = res["sampled_at"].str()?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now(),
            cores = sys["cores"].long()?.toInt() ?: 1,
            hostCpuPct = sys["cpu_pct"].num() ?: 0.0,
            load1 = sys["load1"].num() ?: 0.0,
            memUsedMb = sys["mem_used_mb"].num() ?: 0.0,
            memTotalMb = sys["mem_total_mb"].num() ?: 0.0,
            processRssBytes = proc["resident_bytes"].long(),
            processCpuSeconds = proc["cpu_seconds"].num(),
            threads = proc["threads"].long(),
            openFds = proc["open_fds"].long(),
            maxFds = proc["max_fds"].long(),
            readBytes = proc["read_bytes"].long(),
            writeBytes = proc["write_bytes"].long(),
            heapLiveBytes = proc["heap_live_bytes"].long(),
            goMemoryBytes = proc["go_memory_bytes"].long(),
            goroutines = proc["goroutines"].long(),
            containerWorkingSetBytes = cgm["working_set_bytes"].long(),
            containerChargedBytes = cgm["current_bytes"].long(),
            swapBytes = cgm["swap_bytes"].long(),
            oomKills = cgm["oom_kills"].long(),
            cpuThrottledSeconds = cgc["throttled_seconds"].num(),
            cpuPressurePct = cgc["pressure_some_pct"].num(),
            containerCpuSeconds = cgc["usage_seconds"].num(),
            ffmpegCount = kids["sampled"].long()?.toInt() ?: 0,
            ffmpegRssBytes = kids["resident_bytes"].long() ?: 0,
            ffmpegCpuSeconds = kids["cpu_seconds"].num(),
            startedAt = status["started_at"].str()?.let { runCatching { Instant.parse(it) }.getOrNull() },
            restartRequired = status["restart_required"].bool() ?: false,
            build = build["display"].str().orEmpty(),
            builtAt = build["built_at"].str()?.let { runCatching { Instant.parse(it) }.getOrNull() },
            hwAccel = hw["detected_backends"].arr().orEmpty().filter { it["verified"].bool() == true }.mapNotNull { it["backend"].str()?.uppercase() },
            gpuName = hw["render_device_details"].arr()?.firstOrNull()?.get("description").str(),
            gpuDevice = hw["render_device_details"].arr()?.firstOrNull()?.get("path").str()?.substringAfterLast('/'),
            gpuSessions = gpu0["sessions"].long()?.toInt() ?: 0,
        )
    }

    /** Starts a scan of every library. */
    suspend fun scanLibraries(): Boolean = http.siloPost("/api/v1/scan")

    suspend fun warningLogs(): List<LogLine> {
        val a = siloJson("/api/v1/admin/logs/app?limit=80&level=warn")
        return a["entries"].arr().orEmpty().mapNotNull { e ->
            LogLine(
                time = e["timestamp"].str()?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH,
                level = e["level"].str().orEmpty(),
                component = e["component"].str().orEmpty(),
                message = e["message"].str() ?: return@mapNotNull null,
            )
        }
    }

    suspend fun requests(): List<RequestRow> {
        val a = siloJson("/api/v1/admin/requests?limit=30")
        return a["requests"].arr().orEmpty().map { r ->
            val targets = r["targets"].arr().orEmpty()
            val detail = targets.joinToString(" · ") { t ->
                listOfNotNull(t["instance_name"].str(), t["quality"].str(), t["status"].str()).joinToString(" ")
            }
            RequestRow(
                title = r["title"].str().orEmpty(),
                year = r["year"].long()?.toInt(),
                mediaType = r["media_type"].str().orEmpty(),
                status = r["status"].str().orEmpty(),
                outcome = r["outcome"].str().orEmpty(),
                backdropUrl = r["backdrop_path"].str()?.let { "https://image.tmdb.org/t/p/w780$it" },
                requestedAt = r["created_at"].str()?.let { runCatching { Instant.parse(it) }.getOrNull() },
                detail = detail,
            )
        }
    }

    // ---- Radarr / Sonarr ---------------------------------------------------------------------

    private suspend fun arr(c: AutomationConnection, path: String): JsonElement? =
        http.get("${c.baseUrl}$path", mapOf("X-Api-Key" to c.apiKey))
            ?.let { runCatching { LooseJson.parseToJsonElement(it) }.getOrNull() }

    suspend fun arrCommand(c: AutomationConnection, name: String): Boolean =
        http.post("${c.baseUrl}/api/v3/command", """{"name":"$name"}""", mapOf("X-Api-Key" to c.apiKey))

    private fun fanart(images: JsonElement?): String? {
        val list = images.arr().orEmpty()
        fun pick(type: String) = list.firstOrNull { it["coverType"].str() == type }?.let { it["remoteUrl"].str() ?: it["url"].str() }
        return pick("fanart") ?: pick("screenshot") ?: pick("poster")
    }

    private fun parseTime(s: String?): Instant? = s?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun historyTone(eventType: String?): Tone = when (eventType) {
        "downloadFolderImported", "episodeFileImported", "movieFileImported" -> Tone.Good
        "grabbed" -> Tone.Info
        "downloadFailed", "importFailed", "downloadIgnored" -> Tone.Bad
        else -> Tone.Info
    }

    private fun historyVerb(eventType: String?): String = when (eventType) {
        "downloadFolderImported", "episodeFileImported", "movieFileImported" -> "Imported"
        "grabbed" -> "Grabbed"
        "downloadFailed" -> "Download failed:"
        "importFailed" -> "Import failed:"
        "movieFileDeleted", "episodeFileDeleted" -> "Deleted"
        "movieFileRenamed", "episodeFileRenamed" -> "Renamed"
        "downloadIgnored" -> "Ignored"
        else -> eventType ?: "Event"
    }

    private fun queueItems(q: JsonElement?, label: (JsonElement) -> String): List<ArrItem> =
        q["records"].arr().orEmpty().map { r ->
            val size = r["size"].num() ?: 0.0
            val left = r["sizeleft"].num() ?: 0.0
            val pct = if (size > 0) ((size - left) / size).toFloat() else 0f
            val quality = r["quality"]["quality"]["name"].str()
            ArrItem(
                title = label(r),
                subtitle = listOfNotNull(quality, gb(size), r["indexer"].str(), r["downloadClient"].str()).joinToString(" · "),
                right = r["timeleft"].str()?.let { tl -> formatTimeLeft(tl) } ?: (r["trackedDownloadState"].str() ?: r["status"].str() ?: ""),
                progress = pct,
                imageUrl = fanart(r["movie"]["images"] ?: r["series"]["images"]),
            )
        }

    private fun healthEvents(h: JsonElement?): List<ArrEvent> = h.arr().orEmpty().map {
        ArrEvent(
            text = it["message"].str() ?: it["source"].str().orEmpty(),
            time = null,
            tone = when (it["type"].str()) { "error" -> Tone.Bad; "warning" -> Tone.Warn; else -> Tone.Info },
        )
    }

    suspend fun radarr(c: AutomationConnection): ArrDashboard? = coroutineScope {
        val status = async { arr(c, "/api/v3/system/status") }
        val movies = async { arr(c, "/api/v3/movie") }
        val queue = async { arr(c, "/api/v3/queue?pageSize=20&includeMovie=true") }
        val history = async { arr(c, "/api/v3/history?pageSize=12&sortKey=date&sortDirection=descending&includeMovie=true") }
        val missing = async { arr(c, "/api/v3/wanted/missing?pageSize=1&monitored=true") }
        val cutoff = async { arr(c, "/api/v3/wanted/cutoff?pageSize=1") }
        val today = LocalDate.now()
        val cal = async { arr(c, "/api/v3/calendar?start=$today&end=${today.plusDays(30)}&unmonitored=false") }
        val health = async { arr(c, "/api/v3/health") }
        val s = status.await() ?: return@coroutineScope null
        val all = movies.await().arr().orEmpty()
        val calendar = cal.await().arr().orEmpty()
        ArrDashboard(
            app = AutomationApp.Radarr,
            version = s["version"].str().orEmpty(),
            total = all.size.toLong(),
            totalLabel = "${all.count { it["hasFile"].bool() == true }} on disk",
            missing = missing.await()["totalRecords"].long(),
            cutoffUnmet = cutoff.await()["totalRecords"].long(),
            upcoming = calendar.size.toLong(),
            queue = queueItems(queue.await()) { r -> r["movie"]["title"].str()?.let { t -> r["movie"]["year"].long()?.let { "$t ($it)" } ?: t } ?: r["title"].str().orEmpty() },
            activity = history.await()["records"].arr().orEmpty().map { h ->
                val t = h["movie"]["title"].str() ?: h["sourceTitle"].str().orEmpty()
                val q = h["quality"]["quality"]["name"].str()
                ArrEvent("${historyVerb(h["eventType"].str())} $t${q?.let { " · $it" } ?: ""}", parseTime(h["date"].str()), historyTone(h["eventType"].str()))
            },
            calendar = calendar.mapNotNull { m ->
                val date = (m["digitalRelease"].str() ?: m["physicalRelease"].str() ?: m["inCinemas"].str())
                    ?.let { parseTime(it)?.atZone(ZoneId.systemDefault())?.toLocalDate() } ?: return@mapNotNull null
                date to ArrItem(m["title"].str().orEmpty(), "", "", null, fanart(m["images"]))
            }.groupBy({ it.first }, { it.second }).toSortedMap().map { CalendarDay(it.key, it.value) },
            health = healthEvents(health.await()),
        )
    }

    suspend fun sonarr(c: AutomationConnection): ArrDashboard? = coroutineScope {
        val status = async { arr(c, "/api/v3/system/status") }
        val queue = async { arr(c, "/api/v3/queue?pageSize=20&includeSeries=true&includeEpisode=true") }
        val history = async { arr(c, "/api/v3/history?pageSize=12&sortKey=date&sortDirection=descending&includeSeries=true&includeEpisode=true") }
        val missing = async { arr(c, "/api/v3/wanted/missing?pageSize=1&monitored=true") }
        val cutoff = async { arr(c, "/api/v3/wanted/cutoff?pageSize=1") }
        val monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        val cal = async { arr(c, "/api/v3/calendar?start=$monday&end=${monday.plusDays(7)}&includeSeries=true&unmonitored=false") }
        val health = async { arr(c, "/api/v3/health") }
        val s = status.await() ?: return@coroutineScope null
        val week = cal.await().arr().orEmpty()
        val byDay = week.mapNotNull { e ->
            val date = parseTime(e["airDateUtc"].str())?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: return@mapNotNull null
            val se = e["seasonNumber"].long(); val en = e["episodeNumber"].long()
            date to ArrItem(
                title = e["series"]["title"].str().orEmpty(),
                subtitle = "S${se}E${en}" + (e["title"].str()?.let { " · $it" } ?: ""),
                right = if (e["hasFile"].bool() == true) "on disk" else "",
                imageUrl = fanart(e["series"]["images"]),
            )
        }.groupBy({ it.first }, { it.second })
        ArrDashboard(
            app = AutomationApp.Sonarr,
            version = s["version"].str().orEmpty(),
            total = null,
            totalLabel = "",
            missing = missing.await()["totalRecords"].long(),
            cutoffUnmet = cutoff.await()["totalRecords"].long(),
            upcoming = week.size.toLong(),
            queue = queueItems(queue.await()) { r ->
                val t = r["series"]["title"].str() ?: r["title"].str().orEmpty()
                val se = r["episode"]["seasonNumber"].long(); val en = r["episode"]["episodeNumber"].long()
                if (se != null && en != null) "$t · S%02dE%02d".format(se, en) else t
            },
            activity = history.await()["records"].arr().orEmpty().map { h ->
                val t = h["series"]["title"].str() ?: h["sourceTitle"].str().orEmpty()
                val se = h["episode"]["seasonNumber"].long(); val en = h["episode"]["episodeNumber"].long()
                val ep = if (se != null && en != null) " S%02dE%02d".format(se, en) else ""
                ArrEvent("${historyVerb(h["eventType"].str())} $t$ep", parseTime(h["date"].str()), historyTone(h["eventType"].str()))
            },
            calendar = (0..6).map { d -> monday.plusDays(d.toLong()).let { CalendarDay(it, byDay[it].orEmpty()) } },
            health = healthEvents(health.await()),
        )
    }

    // ---- SABnzbd ----------------------------------------------------------------------------

    private suspend fun sab(c: AutomationConnection, mode: String): JsonElement? =
        http.get("${c.baseUrl}/api?mode=$mode&output=json&apikey=${c.apiKey}")
            ?.let { runCatching { LooseJson.parseToJsonElement(it) }.getOrNull() }

    suspend fun sabAction(c: AutomationConnection, mode: String): Boolean = sab(c, mode) != null

    suspend fun sabnzbd(c: AutomationConnection): SabDashboard? = coroutineScope {
        val queueD = async { sab(c, "queue&limit=20") }
        val historyD = async { sab(c, "history&limit=10") }
        val statusD = async { sab(c, "status&skip_dashboard=1") }
        val versionD = async { sab(c, "version") }
        val q = queueD.await()?.get("queue") ?: return@coroutineScope null
        SabDashboard(
            version = versionD.await()["version"].str().orEmpty(),
            paused = q["paused"].bool() ?: (q["status"].str() == "Paused"),
            speedMBs = (q["kbpersec"].num() ?: 0.0) / 1024.0,
            speedLimit = q["speedlimit"].str()?.takeIf { it.isNotBlank() && it != "100" }?.let { "$it%" } ?: "none",
            sizeLeft = q["sizeleft"].str().orEmpty(),
            timeLeft = q["timeleft"].str().orEmpty(),
            diskFreeGb = q["diskspace1"].num(),
            diskTotalGb = q["diskspacetotal1"].num(),
            queue = q["slots"].arr().orEmpty().map { s ->
                ArrItem(
                    title = s["filename"].str().orEmpty(),
                    subtitle = listOfNotNull(s["size"].str(), s["percentage"].str()?.let { "$it%" }, s["cat"].str()?.let { "category $it" }).joinToString(" · "),
                    right = if (s["status"].str() == "Downloading") s["timeleft"].str().orEmpty() else s["status"].str().orEmpty().lowercase(),
                    progress = (s["percentage"].num() ?: 0.0).toFloat() / 100f,
                )
            },
            history = historyD.await()["history"]["slots"].arr().orEmpty().map { h ->
                val st = h["status"].str().orEmpty()
                ArrEvent(
                    text = "${h["name"].str().orEmpty()} · ${st.lowercase()}" + (h["fail_message"].str()?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""),
                    time = h["completed"].long()?.let { Instant.ofEpochSecond(it) },
                    tone = when (st) { "Completed" -> Tone.Good; "Failed" -> Tone.Bad; else -> Tone.Info },
                )
            },
            servers = statusD.await()["status"]["servers"].arr().orEmpty().map { s ->
                val conns = s["serveractiveconn"].long()?.toInt()
                val total = s["servertotalconn"].long()?.toInt()
                SabServer(
                    name = s["servername"].str().orEmpty(),
                    activeConnections = conns,
                    maxConnections = total,
                    detail = listOfNotNull(
                        if (s["serveractive"].bool() == false) "disabled" else null,
                        s["serverpriority"].long()?.let { if (it > 0) "backup" else "primary" },
                        s["serveroptional"].bool()?.takeIf { it }?.let { "optional" },
                    ).joinToString(" · "),
                )
            },
            warnings = q["have_warnings"].str()?.toIntOrNull() ?: 0,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Formatting.
// ---------------------------------------------------------------------------------------------

internal fun gb(bytes: Double): String = when {
    bytes >= 1e12 -> "%.1f TB".format(bytes / 1e12)
    bytes >= 1e9 -> "%.1f GB".format(bytes / 1e9)
    bytes > 0 -> "%.0f MB".format(bytes / 1e6)
    else -> ""
}.takeIf { it.isNotEmpty() } ?: "0 MB"

internal fun formatTimeLeft(tl: String): String {
    // "00:06:12" or "1.02:03:04"
    val parts = tl.split(':')
    if (parts.size < 3) return tl
    val dh = parts[0]
    val days = if ('.' in dh) dh.substringBefore('.').toIntOrNull() ?: 0 else 0
    val hours = (if ('.' in dh) dh.substringAfter('.') else dh).toIntOrNull() ?: 0
    val mins = parts[1].toIntOrNull() ?: 0
    return when {
        days > 0 -> "${days}d ${hours}h left"
        hours > 0 -> "${hours}h ${mins}m left"
        else -> "${mins.coerceAtLeast(1)} min left"
    }
}

internal fun storageTb(gb: Double): String = if (gb >= 1000) "%.1f TB".format(gb / 1000) else "%.0f GB".format(gb)
