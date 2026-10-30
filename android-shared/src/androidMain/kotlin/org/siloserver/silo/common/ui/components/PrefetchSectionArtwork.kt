package org.siloserver.silo.common.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import org.koin.core.context.GlobalContext
import org.siloserver.silo.common.images.ArtworkPrefetcher
import org.siloserver.silo.model.section.SectionItem

/** How many items at the start of each row have their artwork fetched ahead. */
private const val PrefetchItemsPerRow = 15

/**
 * Hands the artwork of the first items in every row to the background
 * [ArtworkPrefetcher], so cards scrolled to later draw from the disk cache
 * instead of waiting on the network. The list goes column by column, so the
 * first few cards of every row come before the far end of any one row.
 */
@Composable
fun PrefetchSectionArtwork(rows: List<List<SectionItem>>) {
    val prefetcher = remember { GlobalContext.get().get<ArtworkPrefetcher>() }
    LaunchedEffect(rows) {
        prefetcher.prefetch(
            buildList {
                for (index in 0 until PrefetchItemsPerRow) {
                    for (row in rows) {
                        val item = row.getOrNull(index) ?: continue
                        listOfNotNull(item.posterUrl, item.backdropUrl, item.logoUrl).forEach(::add)
                    }
                }
            },
        )
    }
}
