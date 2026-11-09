package org.siloserver.silo.model.catalog

/** One title an actor worked on, as listed by the server's Filmography plugin. */
data class PluginCredit(
    val tmdbId: Int,
    val isSeries: Boolean,
    val title: String,
    val date: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val voteAverage: Double?,
    val roles: List<String>,
    val isActing: Boolean,
    /** An appearance as themselves, such as a talk-show guest spot. */
    val isSelf: Boolean,
    /** The library item, when a library the profile can see has the title. */
    val itemId: String?,
) {
    val inLibrary: Boolean get() = !itemId.isNullOrBlank()
    val year: Int? get() = date?.take(4)?.toIntOrNull()
    val yearLabel: String get() = year?.toString() ?: "Upcoming"
    val roleSummary: String get() = roles.joinToString(" · ")
}

/** Who the person is, from the same plugin answer, so a page can show details for someone not in the library. */
data class PluginPersonProfile(
    val tmdbId: String,
    val name: String,
    val biography: String?,
    val birthday: String?,
    val deathday: String?,
    val birthplace: String?,
    val photoUrl: String?,
    val alsoKnownAs: List<String>,
    /** The person's id in Silo, when the library has them. */
    val siloPersonId: String?,
) {
    /** A [Person] for the shared person-page layouts. The id is 0 because there is no library record. */
    fun toPerson(): Person = Person(
        id = siloPersonId?.toLongOrNull() ?: 0L,
        name = name,
        bio = biography,
        birthDate = birthday,
        deathDate = deathday,
        birthplace = birthplace,
        photoUrl = photoUrl,
        tmdbId = tmdbId,
    )
}

/** Everything the plugin knows an actor worked on, newest first. */
data class PluginFilmography(val credits: List<PluginCredit>, val profile: PluginPersonProfile? = null)

enum class PluginLibraryFilter(val title: String) { All("Everything"), InLibrary("In library"), NotInLibrary("Not in library") }
enum class PluginTypeFilter(val title: String) { All("Any type"), Movies("Movies"), Series("TV") }

fun List<PluginCredit>.filterCredits(type: PluginTypeFilter, library: PluginLibraryFilter, showSelf: Boolean): List<PluginCredit> =
    filter { c ->
        (type == PluginTypeFilter.All || (type == PluginTypeFilter.Series) == c.isSeries) &&
            (library == PluginLibraryFilter.All || (library == PluginLibraryFilter.InLibrary) == c.inLibrary) &&
            (showSelf || !c.isSelf)
    }

/** One cast member on a title's preview page. */
data class PluginCast(val name: String, val tmdbId: String?, val character: String?, val photoUrl: String?)

/** Extra detail for a title the library lacks, from the Filmography plugin. */
data class PluginTitle(
    val tagline: String?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val rating: String?,
    val directors: List<String>,
    val cast: List<PluginCast>,
    val trailerKey: String?,
    val seasons: Int?,
    val episodes: Int?,
    val network: String?,
    val status: String?,
    /** IMDb / TMDB / Rotten Tomatoes scores, when the plugin has an MDBList key. */
    val ratings: List<DisplayRating> = emptyList(),
    /** The title's clear logo, when the plugin returns one. */
    val logoUrl: String? = null,
    /** YouTube trailers, teasers, featurettes and clips, trailers first. */
    val videos: List<ItemVideo> = emptyList(),
    val budget: Long? = null,
    val revenue: Long? = null,
    val companies: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
) {
    val trailerUrl: String? get() = trailerKey?.let { "https://www.youtube.com/watch?v=$it" }
}
