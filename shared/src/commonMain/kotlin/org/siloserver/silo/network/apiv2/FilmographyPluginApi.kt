package org.siloserver.silo.network.apiv2

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.siloserver.silo.model.catalog.PluginCredit
import org.siloserver.silo.model.catalog.PluginCast
import org.siloserver.silo.model.catalog.PluginFilmography
import org.siloserver.silo.model.catalog.PluginPersonProfile
import org.siloserver.silo.model.catalog.PluginTitle
import org.siloserver.silo.network.*

private const val PluginId = "community.filmography"
private const val TmdbImages = "https://image.tmdb.org/t/p/w342"
private const val TmdbBackdrops = "https://image.tmdb.org/t/p/w780"

@Serializable
internal data class PluginListWire(val items: List<PluginInstallWire> = emptyList())

@Serializable
internal data class PluginInstallWire(val id: String = "", @SerialName("plugin_id") val pluginId: String = "")

@Serializable
internal data class PluginRoleWire(val crew: Boolean = false, val text: String = "")

@Serializable
internal data class PluginCreditWire(
    val id: Int = 0,
    val media: String = "",
    val title: String = "",
    val date: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val overview: String? = null,
    val vote: Double? = null,
    val acting: Boolean = false,
    val self: Boolean = false,
    val roles: List<PluginRoleWire> = emptyList(),
    @SerialName("content_id") val contentId: String? = null,
)

@Serializable
internal data class PluginPersonWire(
    val id: Int = 0,
    val name: String = "",
    val biography: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    val birthplace: String? = null,
    val photo: String? = null,
    @SerialName("also_known_as") val alsoKnownAs: List<String> = emptyList(),
    @SerialName("silo_person_id") val siloPersonId: String? = null,
    val credits: List<PluginCreditWire> = emptyList(),
)

@Serializable
internal data class PluginSearchResultWire(val id: Int = 0, val name: String = "", val photo: String? = null)

@Serializable
internal data class PluginSearchWire(val results: List<PluginSearchResultWire> = emptyList())

@Serializable
internal data class PluginCastWire(val id: Long = 0, val name: String = "", val character: String? = null, val photo: String? = null)

@Serializable
internal data class PluginVideoWire(val key: String = "", val name: String = "", val type: String = "", val official: Boolean = false)

@Serializable
internal data class PluginRatingWire(
    val source: String = "",
    val name: String = "",
    val score: Double? = null,
    val display: String = "",
)

@Serializable
internal data class PluginTitleWire(
    val tagline: String? = null,
    val runtime: Int = 0,
    val genres: List<String> = emptyList(),
    val rating: String? = null,
    val directors: List<String> = emptyList(),
    val cast: List<PluginCastWire> = emptyList(),
    val trailer: String? = null,
    val seasons: Int = 0,
    val episodes: Int = 0,
    val network: String? = null,
    val status: String? = null,
    val ratings: List<PluginRatingWire> = emptyList(),
    val logo: String? = null,
    val videos: List<PluginVideoWire> = emptyList(),
    val budget: Long = 0,
    val revenue: Long = 0,
    val companies: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
)

/**
 * Reads an actor's credits from the Filmography plugin (`community.filmography`)
 * on the server. The plugin is optional: when it is not installed the result is
 * [ApiResult.Error] and the caller shows a short explanation.
 */
class FilmographyPluginApi(
    private val client: HttpClient,
    private val tokens: TokenManager,
    private val gate: ApiV2Gate,
    private val requests: org.siloserver.silo.network.api.RequestsApi? = null,
) {
    private val libraryIds = HashMap<String, String?>() // "series:456" to library content id, null when not in the library

    /**
     * The plugin's library match can miss a title the server does know (for example a show matched
     * by TVDB id only). Asking the requests API, which the title page uses, gives the same answer
     * the page will show when the row is opened.
     */
    suspend fun libraryContentIdFor(isSeries: Boolean, tmdbId: Int): String? {
        val api = requests ?: return null
        val key = (if (isSeries) "series:" else "movie:") + tmdbId
        synchronized(libraryIds) { if (libraryIds.containsKey(key)) return libraryIds[key] }
        val r = api.detail(if (isSeries) "series" else "movie", tmdbId)
        if (r !is ApiResult.Success) return null
        val id = r.data.libraryContentId?.takeIf { it.isNotBlank() }
        synchronized(libraryIds) { libraryIds[key] = id }
        return id
    }

    private var installation: Pair<String, String>? = null // server url to installation id

    private suspend fun installationId(owner: AuthScopeSnapshot): ApiResult<String> {
        installation?.let { if (it.first == owner.serverUrl) return ApiResult.Success(it.second) }
        return ownedV2Call<PluginListWire, String>(gate, tokens, owner, OwnerPolicy.IDENTITY, HttpStatusCode.OK, { scope ->
            client.get("/api/v2/settings/plugins") { authScope(scope!!); requireSiloAuth() }
        }) { list ->
            val id = list.items.firstOrNull { it.pluginId == PluginId }?.id
            require(!id.isNullOrBlank()) { "The Filmography plugin is not installed on this server." }
            id
        }.also { if (it is ApiResult.Success) installation = owner.serverUrl to it.data }
    }

    private class Cached<T>(val value: T, val at: kotlin.time.TimeSource.Monotonic.ValueTimeMark)
    private val personCache = HashMap<String, Cached<PluginFilmography>>()
    private val titleCache = HashMap<String, Cached<PluginTitle>>()
    private val cacheLife = kotlin.time.Duration.parse("5m")

    /** A person's credits. Answers are kept for five minutes so reopening a page, or the cast sheet then the page, is instant. */
    suspend fun person(tmdbId: String, owner: AuthScopeSnapshot): ApiResult<PluginFilmography> {
        val key = owner.serverUrl + "|" + tmdbId
        synchronized(personCache) { personCache[key]?.takeIf { it.at.elapsedNow() < cacheLife }?.let { return ApiResult.Success(it.value) } }
        val r = personUncached(tmdbId, owner)
        if (r is ApiResult.Success) synchronized(personCache) { personCache[key] = Cached(r.data, kotlin.time.TimeSource.Monotonic.markNow()) }
        return r
    }

    private suspend fun personUncached(tmdbId: String, owner: AuthScopeSnapshot): ApiResult<PluginFilmography> {
        if (tmdbId.isBlank() || tmdbId.any { !it.isDigit() }) return ApiResult.Error(422, "validation_failed", "This person has no TMDB id.")
        val id = when (val r = installationId(owner)) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> return r
            is ApiResult.NetworkError -> return r
        }
        return ownedV2Call<PluginPersonWire, PluginFilmography>(gate, tokens, owner, OwnerPolicy.IDENTITY, HttpStatusCode.OK, { scope ->
            client.get("/api/v2/plugin-content/plugins/$id/api/person/$tmdbId") { authScope(scope!!); requireSiloAuth() }
        }) { wire ->
            PluginFilmography(
                wire.credits
                    .filter { it.media == "movie" || it.media == "tv" }
                    .map { c ->
                        PluginCredit(
                            tmdbId = c.id,
                            isSeries = c.media == "tv",
                            title = c.title,
                            date = c.date?.takeIf { it.isNotBlank() },
                            posterUrl = c.poster?.takeIf { it.isNotBlank() }?.let { TmdbImages + it },
                            backdropUrl = c.backdrop?.takeIf { it.isNotBlank() }?.let { TmdbBackdrops + it },
                            overview = c.overview?.takeIf { it.isNotBlank() },
                            voteAverage = c.vote?.takeIf { it > 0 },
                            roles = c.roles.map { it.text }.filter { it.isNotBlank() },
                            isActing = c.acting,
                            isSelf = c.self,
                            itemId = c.contentId?.takeIf { it.isNotBlank() },
                        )
                    }
                    .sortedByDescending { it.date ?: "9999" },
                profile = PluginPersonProfile(
                    tmdbId = tmdbId,
                    name = wire.name,
                    biography = wire.biography?.takeIf { it.isNotBlank() },
                    birthday = wire.birthday?.takeIf { it.isNotBlank() },
                    deathday = wire.deathday?.takeIf { it.isNotBlank() },
                    birthplace = wire.birthplace?.takeIf { it.isNotBlank() },
                    photoUrl = wire.photo?.takeIf { it.isNotBlank() }?.let { TmdbImages + it },
                    alsoKnownAs = wire.alsoKnownAs,
                    siloPersonId = wire.siloPersonId?.takeIf { it.isNotBlank() },
                ),
            )
        }
    }

    /** Finds a person's TMDB id from their name, for cast lists that carry only a name. */
    suspend fun findPersonTmdbId(name: String, owner: AuthScopeSnapshot): String? {
        if (name.isBlank()) return null
        val id = (installationId(owner) as? ApiResult.Success)?.data ?: return null
        val result = ownedV2Call<PluginSearchWire, String?>(gate, tokens, owner, OwnerPolicy.IDENTITY, HttpStatusCode.OK, { scope ->
            client.get("/api/v2/plugin-content/plugins/$id/api/search") {
                authScope(scope!!); requireSiloAuth()
                parameter("q", name)
            }
        }) { w ->
            (w.results.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: w.results.firstOrNull())?.id?.takeIf { it > 0 }?.toString()
        }
        return (result as? ApiResult.Success)?.data
    }

    /** Genres, runtime, cast and trailer for a movie or series, for the preview page. */
    /** A title's TMDB extras (ratings, logo, budget...), kept for five minutes. */
    suspend fun title(isSeries: Boolean, tmdbId: Int, owner: AuthScopeSnapshot): ApiResult<PluginTitle> {
        val key = owner.serverUrl + "|" + isSeries + tmdbId
        synchronized(titleCache) { titleCache[key]?.takeIf { it.at.elapsedNow() < cacheLife }?.let { return ApiResult.Success(it.value) } }
        val r = titleUncached(isSeries, tmdbId, owner)
        if (r is ApiResult.Success) synchronized(titleCache) { titleCache[key] = Cached(r.data, kotlin.time.TimeSource.Monotonic.markNow()) }
        return r
    }

    private suspend fun titleUncached(isSeries: Boolean, tmdbId: Int, owner: AuthScopeSnapshot): ApiResult<PluginTitle> {
        val id = when (val r = installationId(owner)) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> return r
            is ApiResult.NetworkError -> return r
        }
        val media = if (isSeries) "tv" else "movie"
        return ownedV2Call<PluginTitleWire, PluginTitle>(gate, tokens, owner, OwnerPolicy.IDENTITY, HttpStatusCode.OK, { scope ->
            client.get("/api/v2/plugin-content/plugins/$id/api/title/$media/$tmdbId") { authScope(scope!!); requireSiloAuth() }
        }) { w ->
            PluginTitle(
                tagline = w.tagline?.takeIf { it.isNotBlank() },
                runtimeMinutes = w.runtime.takeIf { it > 0 },
                genres = w.genres,
                rating = w.rating?.takeIf { it.isNotBlank() },
                directors = w.directors,
                cast = w.cast.filter { it.name.isNotBlank() }.map {
                    PluginCast(it.name, it.id.takeIf { id -> id > 0 }?.toString(), it.character?.takeIf { c -> c.isNotBlank() }, it.photo?.takeIf { p -> p.isNotBlank() }?.let { p -> "https://image.tmdb.org/t/p/w185$p" })
                },
                trailerKey = w.trailer?.takeIf { it.isNotBlank() },
                seasons = w.seasons.takeIf { it > 0 },
                episodes = w.episodes.takeIf { it > 0 },
                network = w.network?.takeIf { it.isNotBlank() },
                status = w.status?.takeIf { it.isNotBlank() },
                logoUrl = w.logo?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500" + it },
                videos = w.videos.filter { it.key.isNotBlank() }.map {
                    org.siloserver.silo.model.catalog.ItemVideo(
                        kind = it.type.lowercase().replace(' ', '_'),
                        site = "YouTube",
                        siteKey = it.key,
                        name = it.name.takeIf { n -> n.isNotBlank() },
                        isOfficial = it.official,
                    )
                },
                budget = w.budget.takeIf { it > 0 },
                revenue = w.revenue.takeIf { it > 0 },
                companies = w.companies,
                countries = w.countries,
                languages = w.languages,
                ratings = w.ratings.filter { it.source.isNotBlank() && it.display.isNotBlank() }
                    .map { org.siloserver.silo.model.catalog.DisplayRating(it.source, it.name.ifBlank { it.source }, it.score, it.display) },
            )
        }
    }
}
