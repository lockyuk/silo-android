package org.siloserver.silo.network.apiv2

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.siloserver.silo.model.catalog.Filmography
import org.siloserver.silo.model.catalog.FilmographyCredit
import org.siloserver.silo.model.catalog.FilmographyRole

@Serializable
internal data class FilmographyRoleReadV2(
    val department: String = "",
    val character: String? = null,
    val job: String? = null,
    @SerialName("episode_count") val episodeCount: Int = 0,
    val self: Boolean = false,
)

@Serializable
internal data class FilmographyCreditReadV2(
    val key: String,
    @SerialName("item_type") val itemType: String,
    val title: String,
    val year: Int? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val overview: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    val roles: List<FilmographyRoleReadV2> = emptyList(),
    @SerialName("provider_ids") val providerIds: Map<String, String> = emptyMap(),
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("request_url") val requestUrl: String? = null,
)

@Serializable
internal data class FilmographyReadV2(
    val available: Boolean = false,
    val credits: List<FilmographyCreditReadV2> = emptyList(),
) {
    fun toDomain() = Filmography(
        available = available,
        credits = credits
            // Only titles the clients can show: unknown item types are skipped.
            .filter { it.itemType == "movie" || it.itemType == "series" }
            .map { c ->
                FilmographyCredit(
                    key = c.key,
                    itemType = c.itemType,
                    title = c.title,
                    year = c.year,
                    releaseDate = c.releaseDate,
                    overview = c.overview?.takeIf { it.isNotBlank() },
                    posterUrl = c.posterUrl?.takeIf { it.isNotBlank() },
                    backdropUrl = c.backdropUrl?.takeIf { it.isNotBlank() },
                    roles = c.roles.map { FilmographyRole(it.department, it.character, it.job, it.episodeCount, it.self) },
                    tmdbId = c.providerIds["tmdb"],
                    voteAverage = c.voteAverage,
                    itemId = c.itemId?.takeIf { it.isNotBlank() },
                    requestUrl = c.requestUrl?.takeIf { it.isNotBlank() },
                )
            },
    )
}
