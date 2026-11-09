package org.siloserver.silo.model.catalog

/**
 * The facts block under a title's synopsis: release, runtime, budget, revenue,
 * studios, countries, language and status. It is the same for a title in the
 * library and one that is not; each source fills what it has.
 */
data class DetailFacts(
    val isSeries: Boolean,
    /** ISO date (`1986-08-08`), or null. */
    val releaseDate: String?,
    val year: Int?,
    val runtimeMinutes: Int?,
    val budget: Long?,
    val revenue: Long?,
    val studios: List<String>,
    val countries: List<String>,
    val language: String?,
    val status: String?,
    val networks: List<String>,
    val seasons: Int?,
) {
    val isEmpty: Boolean
        get() = releaseDate == null && year == null && runtimeMinutes == null && budget == null && revenue == null &&
            studios.isEmpty() && countries.isEmpty() && language == null && status == null && networks.isEmpty() && seasons == null

    companion object {
        /** From a library title, with budget and revenue and any gaps filled from the Filmography plugin's TMDB data. */
        fun from(detail: ItemDetail, extra: PluginTitle?): DetailFacts {
            val series = detail.type == "series"
            return DetailFacts(
                isSeries = series,
                releaseDate = (if (series) detail.firstAirDate else detail.releaseDate)?.takeIf { it.isNotBlank() },
                year = detail.year.takeIf { it > 0 },
                runtimeMinutes = detail.runtime.takeIf { it > 0 } ?: extra?.runtimeMinutes,
                budget = extra?.budget,
                revenue = extra?.revenue,
                studios = detail.studios.ifEmpty { extra?.companies.orEmpty() },
                countries = detail.countries.ifEmpty { extra?.countries.orEmpty() },
                language = (detail.originalLanguage?.takeIf { it.isNotBlank() }?.let(::languageName)) ?: extra?.languages?.firstOrNull(),
                status = (detail.showStatus ?: extra?.status)?.takeIf { it.isNotBlank() },
                networks = detail.networks.ifEmpty { listOfNotNull(extra?.network) },
                seasons = detail.seasonCount ?: extra?.seasons,
            )
        }

        /** From a title that is not in the library: TMDB's detail from the request, plus the plugin's extras. */
        fun fromRequest(
            isSeries: Boolean,
            releaseDate: String?,
            year: Int?,
            runtimeMinutes: Int?,
            studios: List<String>,
            networks: List<String>,
            seasons: Int?,
            extra: PluginTitle?,
        ): DetailFacts = DetailFacts(
            isSeries = isSeries,
            releaseDate = releaseDate?.takeIf { it.isNotBlank() },
            year = year?.takeIf { it > 0 },
            runtimeMinutes = runtimeMinutes?.takeIf { it > 0 } ?: extra?.runtimeMinutes,
            budget = extra?.budget,
            revenue = extra?.revenue,
            studios = studios.ifEmpty { extra?.companies.orEmpty() },
            countries = extra?.countries.orEmpty(),
            language = extra?.languages?.firstOrNull(),
            status = extra?.status?.takeIf { it.isNotBlank() },
            networks = networks.ifEmpty { listOfNotNull(extra?.network) },
            seasons = seasons ?: extra?.seasons,
        )

        private fun languageName(code: String): String = when (code.lowercase()) {
            "en" -> "English"; "fr" -> "French"; "de" -> "German"; "es" -> "Spanish"; "it" -> "Italian"
            "ja" -> "Japanese"; "ko" -> "Korean"; "zh", "cn" -> "Chinese"; "pt" -> "Portuguese"; "ru" -> "Russian"
            "hi" -> "Hindi"; "sv" -> "Swedish"; "da" -> "Danish"; "no", "nb" -> "Norwegian"; "nl" -> "Dutch"
            "pl" -> "Polish"; "tr" -> "Turkish"; "ar" -> "Arabic"; "th" -> "Thai"
            else -> code
        }
    }
}

/** `$8.0M`, `$225.9M`, `$1.2B`, or `$850K`. */
fun formatMoney(amount: Long): String = when {
    amount >= 1_000_000_000 -> "$" + oneDecimal(amount / 1_000_000_000.0) + "B"
    amount >= 1_000_000 -> "$" + oneDecimal(amount / 1_000_000.0) + "M"
    amount >= 1_000 -> "$" + (amount / 1_000) + "K"
    else -> "$$amount"
}

private fun oneDecimal(value: Double): String = ExternalRatings.formatOneDecimal(value)

/** `6.5` for revenue over budget, or null when either is missing. */
fun revenueMultiple(budget: Long?, revenue: Long?): Double? {
    if (budget == null || revenue == null || budget <= 0 || revenue <= 0) return null
    return revenue.toDouble() / budget
}
