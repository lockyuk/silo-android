package org.siloserver.silo.common.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import org.siloserver.silo.model.section.SectionItem

/** How many items at the start of each row have their artwork fetched ahead. */
private const val PrefetchItemsPerRow = 15

/**
 * Fetches the artwork of the first items in every row into the disk cache as
 * soon as the rows arrive, so cards scrolled to later draw from disk instead of
 * waiting on the network. Only the disk cache is filled: the small decode each
 * request needs is not kept in memory, where the cards' own sizes belong.
 */
@Composable
fun PrefetchSectionArtwork(rows: List<List<SectionItem>>) {
    val context = LocalContext.current
    LaunchedEffect(rows) {
        val loader = context.imageLoader
        val seen = HashSet<String>()
        for (index in 0 until PrefetchItemsPerRow) {
            for (row in rows) {
                val item = row.getOrNull(index) ?: continue
                listOf(item.posterUrl, item.backdropUrl, item.logoUrl).forEach { url ->
                    if (url.isNullOrBlank() || !seen.add(url)) return@forEach
                    loader.enqueue(
                        ImageRequest.Builder(context)
                            .data(url)
                            .size(64, 64)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .build(),
                    )
                }
            }
        }
    }
}
