package org.siloserver.silo.tv.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.siloserver.silo.model.catalog.BrowseItem

class TvLetterJumpTest {
    private fun item(title: String) = BrowseItem(contentId = title, type = "series", title = title)

    private val items = listOf(
        item("24"),
        item("Archer"),
        item("Breaking Bad"),
        item("The Office"),
        item("Zoo"),
    )

    @Test
    fun letterLandsOnTheFirstTitleFiledUnderIt() {
        assertEquals(1, letterJumpIndex(items, "A", descending = false))
        assertEquals(3, letterJumpIndex(items, "O", descending = false))
    }

    @Test
    fun aLetterWithNoTitlesLandsOnTheNextOne() {
        assertEquals(3, letterJumpIndex(items, "C", descending = false))
    }

    @Test
    fun symbolsAndAllLandAtTheStart() {
        assertEquals(0, letterJumpIndex(items, "#", descending = false))
        assertEquals(0, letterJumpIndex(items, null, descending = false))
    }

    @Test
    fun aLetterPastEverythingLoadedWaitsForMore() {
        assertNull(letterJumpIndex(items.take(3), "Z", descending = false))
    }

    @Test
    fun descendingOrderLandsOnTheFirstTitleAtOrBeforeTheLetter() {
        assertEquals(1, letterJumpIndex(items.reversed(), "O", descending = true))
    }
}
