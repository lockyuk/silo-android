package org.siloserver.silo.model.catalog

/** One way a person worked on a title. */
data class FilmographyRole(
    val department: String,
    val character: String? = null,
    val job: String? = null,
    val episodeCount: Int = 0,
    /** An appearance as themselves, such as a talk-show guest spot. */
    val self: Boolean = false,
)

/** One title a person worked on, with every role they had on it. */
data class FilmographyCredit(
    val key: String,
    val itemType: String,
    val title: String,
    val year: Int? = null,
    val releaseDate: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val roles: List<FilmographyRole> = emptyList(),
    val tmdbId: String? = null,
    val voteAverage: Double? = null,
    /** The library item, when a library the profile can see has the title. */
    val itemId: String? = null,
    /** Opens the title on the server's request server, for titles the library lacks. */
    val requestUrl: String? = null,
) {
    val inLibrary: Boolean get() = !itemId.isNullOrBlank()
    val isSeries: Boolean get() = itemType == "series"
    val isActing: Boolean get() = roles.any { it.department == "Acting" }
    val isOnlySelf: Boolean get() = roles.isNotEmpty() && roles.all { it.self }

    /** How the person worked on the title, e.g. "as Tyler Durden · Producer". */
    val roleSummary: String
        get() = roles.joinToString(" · ") { role ->
            if (role.department == "Acting") {
                val name = role.character?.takeIf { it.isNotBlank() }?.let { "as $it" } ?: "Actor"
                when (role.episodeCount) {
                    0 -> name
                    1 -> "$name (1 episode)"
                    else -> "$name (${role.episodeCount} episodes)"
                }
            } else {
                role.job?.takeIf { it.isNotBlank() } ?: role.department
            }
        }

    /** The heading this title sits under: its year, or "Upcoming" without one. */
    val yearLabel: String get() = year?.toString() ?: "Upcoming"
}

/** Everything a person worked on, newest first. */
data class Filmography(
    /** False when no metadata provider could list the person's credits. */
    val available: Boolean,
    val credits: List<FilmographyCredit>,
)

enum class FilmographyTypeFilter(val title: String) { All("All"), Movies("Movies"), Series("TV") }

enum class FilmographyLibraryFilter(val title: String) { All("Everything"), InLibrary("In library"), NotInLibrary("Not in library") }

/** Filters a filmography the way its chips describe. Self appearances stay hidden unless asked for. */
fun List<FilmographyCredit>.filterFilmography(
    type: FilmographyTypeFilter,
    library: FilmographyLibraryFilter,
    showSelf: Boolean,
): List<FilmographyCredit> = filter { credit ->
    (type == FilmographyTypeFilter.All || (type == FilmographyTypeFilter.Series) == credit.isSeries) &&
        (library == FilmographyLibraryFilter.All || (library == FilmographyLibraryFilter.InLibrary) == credit.inLibrary) &&
        (showSelf || !credit.isOnlySelf)
}

/** Credits grouped under their year headings, in list order. */
fun List<FilmographyCredit>.groupByYear(): List<Pair<String, List<FilmographyCredit>>> {
    val out = mutableListOf<Pair<String, MutableList<FilmographyCredit>>>()
    for (credit in this) {
        val last = out.lastOrNull()
        if (last?.first == credit.yearLabel) last.second += credit else out += credit.yearLabel to mutableListOf(credit)
    }
    return out
}
