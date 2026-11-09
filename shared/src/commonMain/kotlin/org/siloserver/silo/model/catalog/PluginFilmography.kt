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

/** Everything the plugin knows an actor worked on, newest first. */
data class PluginFilmography(val credits: List<PluginCredit>)

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
) {
    val trailerUrl: String? get() = trailerKey?.let { "https://www.youtube.com/watch?v=$it" }
}
