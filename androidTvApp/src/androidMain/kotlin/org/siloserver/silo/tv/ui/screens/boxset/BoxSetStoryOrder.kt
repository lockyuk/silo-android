package org.siloserver.silo.tv.ui.screens.boxset

import org.siloserver.silo.model.catalog.BrowseItem

/**
 * Story (in-universe) order for box sets whose films were not made in the order
 * they are set. TMDB lists a collection in release order only, so this is a
 * hand-kept list: box set (TMDB collection id) to TMDB movie ids in story order.
 * A box set that is not listed has no story order and shows no switch; films
 * missing from a list follow the listed ones in release order.
 */
internal object BoxSetStoryOrder {
    private val orders: Map<String, List<Int>> = mapOf(
        // Transformers
        "8650" to listOf(424783, 667538, 1858, 8373, 38356, 91314, 335988),
        // Star Wars
        "10" to listOf(1893, 1894, 1895, 348350, 330459, 11, 1891, 1892, 140607, 181808, 181812),
        // Fast & Furious
        "9485" to listOf(9799, 584, 13804, 51497, 82992, 9615, 168259, 337339, 384018, 385128, 385687),
    )

    /** [items] in story order, or null when this box set has no story order to offer. */
    fun apply(boxSetId: String, items: List<BrowseItem>): List<BrowseItem>? {
        val order = orders[boxSetId] ?: return null
        val rank = order.withIndex().associate { (i, id) -> id to i }
        fun rankOf(item: BrowseItem): Int =
            item.contentId.removePrefix("movie-tmdb-").toIntOrNull()?.let { rank[it] } ?: Int.MAX_VALUE
        if (items.count { rankOf(it) != Int.MAX_VALUE } < 2) return null
        // sortedBy is stable, so films not listed keep their release order at the end.
        return items.sortedBy { rankOf(it) }
    }
}
