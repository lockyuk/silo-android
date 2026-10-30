package org.siloserver.silo.common.images

import coil3.ImageLoader
import coil3.EventListener
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.decode.Decoder
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import org.siloserver.silo.network.SiloOkHttp
import org.siloserver.silo.common.diagnostics.DiagnosticsImageHealth
import org.siloserver.silo.common.diagnostics.DiagnosticsImageSource
import org.siloserver.silo.common.diagnostics.DiagnosticsImageStage
import java.io.File

/**
 * The one Coil image loader configuration for both the phone and TV apps
 * (each Application's `SingletonImageLoader.Factory` delegates here so the
 * two can't drift):
 *
 * - A generous (2GB) on-disk artwork cache so posters/backdrops survive
 *   between sessions (Coil's default disk cap is small — 2% of free space,
 *   capped at 250MB).
 * - An explicitly registered OkHttp fetcher on [SiloOkHttp.imageClient] so
 *   artwork shares the API clients' warm connection pool instead of a
 *   service-loader-built default client.
 * - Concurrent requests for the same URL share one download
 *   ([SharedDownloadFetcherFactory]).
 * - Signed artwork is cached under its URL without the signature
 *   ([StableArtworkCacheKeys]), so the cache outlives the daily re-signing.
 * - Memory cache stays at Coil's heap-proportional default.
 *
 * API decoding resolves artwork against its supplying response origin before
 * it reaches Coil. This covers direct requests and media-session loading, and
 * keeps cache keys tied to that server when the active session changes.
 * The image client carries no Silo login or profile credentials.
 */
fun buildSiloImageLoader(context: PlatformContext, cacheDir: File): ImageLoader =
    ImageLoader.Builder(context)
        .crossfade(true)
        .eventListenerFactory { DiagnosticsImageEventListener() }
        .components {
            add(StableArtworkCacheKeys)
            add(SharedDownloadFetcherFactory(OkHttpNetworkFetcherFactory(callFactory = { SiloOkHttp.imageClient })))
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache").toOkioPath())
                .maxSizeBytes(2048L * 1024 * 1024)
                .build()
        }
        .build()

/**
 * Silo re-signs artwork URLs every day (`exp` and `sig` change), so caching
 * by the full URL threw the disk cache away daily. The path already names the
 * picture's content (`w500.<sha256>.webp`), so the URL without those two
 * parameters is a stable disk cache key. Requests that set their own key keep
 * it.
 */
internal object StableArtworkCacheKeys : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        if (request.diskCacheKey != null) return chain.proceed()
        val key = stableArtworkKey(request.data.toString()) ?: return chain.proceed()
        return chain.withRequest(request.newBuilder().diskCacheKey(key).build()).proceed()
    }
}

/** [url] without its artwork signature, or null when it carries none. */
internal fun stableArtworkKey(url: String): String? {
    val query = url.indexOf('?').takeIf { it >= 0 } ?: return null
    if (!url.regionMatches(0, "http", 0, 4) || !url.contains("/artwork/")) return null
    val kept = url.substring(query + 1).split('&').filterNot { param ->
        param.startsWith("sig=") || param.startsWith("exp=")
    }
    if (kept.size == url.substring(query + 1).split('&').size) return null
    val base = url.substring(0, query)
    return if (kept.isEmpty()) base else base + "?" + kept.joinToString("&")
}

private class DiagnosticsImageEventListener : EventListener() {
    private var stage = DiagnosticsImageStage.UNKNOWN

    override fun fetchStart(request: ImageRequest, fetcher: Fetcher, options: Options) {
        stage = DiagnosticsImageStage.FETCH
    }

    override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) {
        stage = DiagnosticsImageStage.DECODE
    }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        val source = when (result.dataSource) {
            DataSource.MEMORY_CACHE, DataSource.MEMORY -> DiagnosticsImageSource.MEMORY
            DataSource.DISK -> DiagnosticsImageSource.DISK
            DataSource.NETWORK -> DiagnosticsImageSource.NETWORK
        }
        DiagnosticsImageHealth.success(source)
    }

    override fun onError(request: ImageRequest, result: ErrorResult) {
        DiagnosticsImageHealth.failure(stage, result.throwable)
    }
}
