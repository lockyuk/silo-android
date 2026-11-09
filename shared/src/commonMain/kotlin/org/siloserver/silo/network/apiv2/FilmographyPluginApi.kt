package org.siloserver.silo.network.apiv2

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.siloserver.silo.model.catalog.PluginCredit
import org.siloserver.silo.model.catalog.PluginCast
import org.siloserver.silo.model.catalog.PluginFilmography
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
internal data class PluginPersonWire(val credits: List<PluginCreditWire> = emptyList())

@Serializable
internal data class PluginCastWire(val id: Long = 0, val name: String = "", val character: String? = null, val photo: String? = null)

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
) {
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

    suspend fun person(tmdbId: String, owner: AuthScopeSnapshot): ApiResult<PluginFilmography> {
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
            )
        }
    }

    /** Genres, runtime, cast and trailer for a movie or series, for the preview page. */
    suspend fun title(isSeries: Boolean, tmdbId: Int, owner: AuthScopeSnapshot): ApiResult<PluginTitle> {
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
            )
        }
    }
}
