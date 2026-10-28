package org.siloserver.silo.common.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.util.Collections
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.siloserver.silo.model.section.SectionItem

/** How many items at the start of each row have their artwork fetched ahead. */
private const val PrefetchItemsPerRow = 15

/** Lets the cards on screen load first. */
private const val PrefetchDelayMillis = 1_500L

/** Fetches at a time, so the prefetch never crowds out a visible card. */
private const val PrefetchParallelism = 3

/** Artwork already fetched by this process; a Home refresh skips it. */
private val prefetchedUrls: MutableSet<String> = Collections.synchronizedSet(HashSet())

/**
 * Fetches the artwork of the first items in every row into the disk cache
 * shortly after the rows arrive, so cards scrolled to later draw from disk
 * instead of waiting on the network. Only the disk cache is filled: the small
 * decode each request needs is not kept in memory, where the cards' own sizes
 * belong. Each picture is fetched once per run of the app, a few at a time.
 */
@Composable
fun PrefetchSectionArtwork(rows: List<List<SectionItem>>) {
    val context = LocalContext.current
    LaunchedEffect(rows) {
        val urls = buildList {
            for (index in 0 until PrefetchItemsPerRow) {
                for (row in rows) {
                    val item = row.getOrNull(index) ?: continue
                    listOf(item.posterUrl, item.backdropUrl, item.logoUrl).forEach { url ->
                        if (!url.isNullOrBlank() && url !in prefetchedUrls && url !in this) add(url)
                    }
                }
            }
        }
        if (urls.isEmpty()) return@LaunchedEffect
        delay(PrefetchDelayMillis)
        val loader = context.imageLoader
        val gate = Semaphore(PrefetchParallelism)
        coroutineScope {
            urls.forEach { url ->
                launch {
                    gate.withPermit {
                        val result = loader.execute(
                            ImageRequest.Builder(context)
                                .data(url)
                                .size(64, 64)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .build(),
                        )
                        if (result is SuccessResult) prefetchedUrls.add(url)
                    }
                }
            }
        }
    }
}
