package org.siloserver.silo.common.images

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.siloserver.silo.network.apiv2.CatalogContinuationV2
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.CatalogRepository

/** Lets Home and its own artwork settle before the library walk starts. */
private const val LibraryPrefetchDelayMillis = 20_000L

/** The largest page the catalog read allows. */
private const val LibraryPageSize = 100

/** Media types whose grids the walk covers. */
private val LibraryMediaTypes = listOf("movie", "series")

/**
 * Fills the disk cache with the poster and backdrop of every movie and series,
 * once per run of the app, so library grids and focus backdrops draw from disk.
 * It reads the catalog the way the library grids do ([CatalogRepository.browse]
 * sizes its artwork for the grid), so the files are the ones the grids ask
 * for, and hands them to [ArtworkPrefetcher]'s backlog, which downloads them
 * on background threads after Home's artwork and never during playback.
 */
class LibraryArtworkPrefetch(
    private val catalog: CatalogRepository,
    private val prefetcher: ArtworkPrefetcher,
) {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Starts the walk the first time it is called; later calls do nothing. */
    fun startOnce() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            delay(LibraryPrefetchDelayMillis)
            val urls = buildList {
                for (type in LibraryMediaTypes) {
                    var continuation: CatalogContinuationV2? = null
                    do {
                        val page = catalog.browse(mediaType = type, limit = LibraryPageSize, continuation = continuation)
                        if (page !is ApiResult.Success) break
                        page.data.items.forEach { item -> listOfNotNull(item.posterUrl, item.backdropUrl).forEach(::add) }
                        continuation = page.data.continuation
                    } while (continuation != null)
                }
            }
            // A failed walk may try again on the next Home visit.
            if (urls.isEmpty()) started.set(false) else prefetcher.prefetchBacklog(urls)
        }
    }
}
