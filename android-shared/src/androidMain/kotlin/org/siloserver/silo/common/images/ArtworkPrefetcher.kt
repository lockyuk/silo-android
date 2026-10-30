package org.siloserver.silo.common.images

import android.content.Context
import android.graphics.Canvas
import android.os.Process
import coil3.Image
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import java.util.Collections
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Lets the pictures on screen load first. */
private const val PrefetchDelayMillis = 1_500L

/** Downloads at a time, so the prefetch never crowds out a visible card. */
private const val PrefetchParallelism = 2

/**
 * Fills Coil's disk cache with artwork the user is likely to scroll to, on
 * background-priority threads, so it never competes with what is on screen:
 *
 * - Runs on its own low-priority threads, waits a moment after each new list
 *   so the visible cards load first, and fetches two at a time.
 * - Downloads only: nothing is decoded and nothing goes into the memory
 *   cache, so it costs no main-thread time, bitmap memory or GC.
 * - Waits while playback is short of media ([mediaActive]: starting,
 *   buffering or low on buffer), so it only uses bandwidth the stream has
 *   to spare.
 * - Each URL is fetched once per run of the app; a new list replaces the
 *   unfinished rest of the old one.
 * - Home's artwork ([prefetch]) goes first; the whole library's
 *   ([prefetchBacklog]) follows once it is done.
 *
 * The cards then find the file on disk and only decode at their own size.
 */
class ArtworkPrefetcher(
    private val context: Context,
    private val mediaActive: StateFlow<Boolean>,
    private val loader: () -> ImageLoader = { SingletonImageLoader.get(context) },
) {
    private val dispatcher = Executors.newFixedThreadPool(PrefetchParallelism) { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "silo-artwork-prefetch").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val wanted = MutableStateFlow<List<String>>(emptyList())
    private val backlog = MutableStateFlow<List<String>>(emptyList())
    private val fetched: MutableSet<String> = Collections.synchronizedSet(HashSet())

    init {
        scope.launch {
            combine(wanted, backlog) { first, rest -> (first + rest).distinct() }.collectLatest { urls ->
                val todo = urls.filterNot { it in fetched }
                if (todo.isEmpty()) return@collectLatest
                delay(PrefetchDelayMillis)
                val gate = Semaphore(PrefetchParallelism)
                coroutineScope {
                    todo.forEach { url ->
                        launch {
                            gate.withPermit {
                                mediaActive.first { !it }
                                if (url !in fetched && download(url)) fetched.add(url)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Asks for these URLs, most wanted first, in place of any earlier list. */
    fun prefetch(urls: List<String>) {
        wanted.value = urls.filter { it.isNotBlank() }.distinct()
    }

    /** Asks for these URLs after [prefetch]'s, in place of any earlier backlog. */
    fun prefetchBacklog(urls: List<String>) {
        backlog.value = urls.filter { it.isNotBlank() }.distinct()
    }

    private suspend fun download(url: String): Boolean {
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .decoderFactory(DownloadOnlyDecoder)
            .interceptorCoroutineContext(dispatcher)
            .fetcherCoroutineContext(dispatcher)
            .decoderCoroutineContext(dispatcher)
            .build()
        return runCatching { loader().execute(request) is SuccessResult }.getOrDefault(false)
    }
}

/**
 * Ends a prefetch once the file is in the disk cache: Coil's network fetcher
 * writes the download to disk before a decoder sees it, so there is nothing
 * left to do but close the source.
 */
private object DownloadOnlyDecoder : Decoder.Factory {
    override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder =
        Decoder {
            result.source.close()
            DecodeResult(image = NoImage, isSampled = false)
        }
}

private object NoImage : Image {
    override val size: Long = 0
    override val width: Int = 1
    override val height: Int = 1
    override val shareable: Boolean = true
    override fun draw(canvas: Canvas) = Unit
}
