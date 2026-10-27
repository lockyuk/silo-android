package org.siloserver.silo.network.apiv2

import org.siloserver.silo.model.catalog.FilmographyLibraryFilter
import org.siloserver.silo.model.catalog.FilmographyTypeFilter
import org.siloserver.silo.model.catalog.filterFilmography
import org.siloserver.silo.model.catalog.groupByYear
import org.siloserver.silo.network.SiloJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilmographyReadV2Test {
    private val body = """
        {"person_id":"7","available":true,"credits":[
          {"key":"movie:tmdb:9","item_type":"movie","title":"Next","roles":[{"department":"Acting","character":"Lead"}],"provider_ids":{"tmdb":"9"}},
          {"key":"movie:tmdb:550","item_type":"movie","title":"Fight Club","year":1999,"release_date":"1999-10-15",
           "roles":[{"department":"Acting","character":"Tyler"},{"department":"Production","job":"Producer"}],
           "provider_ids":{"tmdb":"550"},"item_id":"movie-550","poster_url":"https://img/fc.jpg"},
          {"key":"series:tmdb:1399","item_type":"series","title":"Friends","year":1994,
           "roles":[{"department":"Acting","character":"Will","episode_count":1}],"provider_ids":{"tmdb":"1399"},
           "request_url":"http://seerr/tv/1399","overview":""},
          {"key":"series:tmdb:2","item_type":"series","title":"Late Show","year":1994,
           "roles":[{"department":"Acting","character":"Self","self":true}],"provider_ids":{"tmdb":"2"}},
          {"key":"book:x:1","item_type":"book","title":"Skipped","roles":[],"provider_ids":{}}
        ],"unknown_field":1}
    """.trimIndent()

    @Test
    fun mapsTheServerFilmography() {
        val f = SiloJson.decodeFromString<FilmographyReadV2>(body).toDomain()
        assertTrue(f.available)
        assertEquals(listOf("Next", "Fight Club", "Friends", "Late Show"), f.credits.map { it.title })
        val fightClub = f.credits[1]
        assertTrue(fightClub.inLibrary)
        assertEquals("as Tyler · Producer", fightClub.roleSummary)
        assertEquals("550", fightClub.tmdbId)
        val friends = f.credits[2]
        assertFalse(friends.inLibrary)
        assertEquals("as Will (1 episode)", friends.roleSummary)
        assertEquals("http://seerr/tv/1399", friends.requestUrl)
        assertEquals(null, friends.overview)
        assertTrue(f.credits[3].isOnlySelf)
    }

    @Test
    fun filtersAndGroupsByYear() {
        val credits = SiloJson.decodeFromString<FilmographyReadV2>(body).toDomain().credits
        val all = credits.filterFilmography(FilmographyTypeFilter.All, FilmographyLibraryFilter.All, showSelf = false)
        assertEquals(listOf("Upcoming", "1999", "1994"), all.groupByYear().map { it.first })
        assertFalse(all.any { it.title == "Late Show" })
        assertEquals(
            listOf("Friends", "Late Show"),
            credits.filterFilmography(FilmographyTypeFilter.Series, FilmographyLibraryFilter.All, showSelf = true).map { it.title },
        )
        assertEquals(
            listOf("Fight Club"),
            credits.filterFilmography(FilmographyTypeFilter.All, FilmographyLibraryFilter.InLibrary, showSelf = true).map { it.title },
        )
        assertEquals(
            listOf("Next", "Friends"),
            credits.filterFilmography(FilmographyTypeFilter.All, FilmographyLibraryFilter.NotInLibrary, showSelf = false).map { it.title },
        )
    }
}
