package org.siloserver.silo.common.images

import coil3.ImageLoader
import coil3.EventListener
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.decode.Decoder
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.crossfade
import coil3.intercept.Interceptor
import coil3.request.ImageResult
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
 * - A generous on-disk artwork cache so posters/backdrops survive between
 *   sessions (Coil's default disk cap is small — 2% of free space, capped at
 *   250MB).
 * - An explicitly registered OkHttp fetcher on [SiloOkHttp.imageClient] so
 *   artwork shares the API clients' warm connection pool instead of a
 *   service-loader-built default client.
 * - Concurrent requests for the same URL share one download
 *   ([SharedDownloadFetcherFactory]).
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
            add(StableArtworkKeyInterceptor)
            add(SharedDownloadFetcherFactory(OkHttpNetworkFetcherFactory(callFactory = { SiloOkHttp.imageClient })))
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache").toOkioPath())
                .maxSizeBytes(512L * 1024 * 1024)
                .build()
        }
        .build()

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

/**
 * Artwork URLs are signed per API response (`exp` and `sig` change every
 * time the same poster is issued), so keying the caches on the full URL made
 * every refresh a miss. Key memory and disk caches on the URL without the
 * signature so a poster already seen is served from cache.
 */
private object StableArtworkKeyInterceptor : Interceptor {
    private val volatileParams = setOf("exp", "sig", "signature", "expires")

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val raw = request.data as? String ?: (request.data as? coil3.Uri)?.toString()
        if (raw == null || !raw.startsWith("http") || !raw.contains('?')) return chain.proceed()
        val base = raw.substringBefore('?')
        val kept = raw.substringAfter('?').split('&').filter { it.isNotEmpty() && it.substringBefore('=') !in volatileParams }
        val key = if (kept.isEmpty()) base else base + "?" + kept.joinToString("&")
        val next = request.newBuilder()
            .apply {
                if (request.memoryCacheKey == null) memoryCacheKey(key)
                if (request.diskCacheKey == null) diskCacheKey(key)
            }
            .build()
        return chain.withRequest(next).proceed()
    }
}
