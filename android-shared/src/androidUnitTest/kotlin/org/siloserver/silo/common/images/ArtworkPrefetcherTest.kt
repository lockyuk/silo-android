package org.siloserver.silo.common.images

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArtworkPrefetcherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val server = MockWebServer()
    private val mediaActive = MutableStateFlow(false)
    private lateinit var loader: ImageLoader
    private lateinit var prefetcher: ArtworkPrefetcher

    @Before
    fun setUp() {
        server.start()
        loader = buildSiloImageLoader(context, java.nio.file.Files.createTempDirectory("artwork-prefetch").toFile())
        prefetcher = ArtworkPrefetcher(context, mediaActive) { loader }
    }

    @After
    fun tearDown() {
        loader.shutdown()
        server.shutdown()
    }

    @Test
    fun prefetchedArtworkLoadsFromDiskWithoutMemory() = runBlocking<Unit> {
        server.enqueue(png())
        val url = server.url("/poster.png").toString()

        prefetcher.prefetch(listOf(url))
        withTimeout(10_000) { while (!loader.isOnDisk(url)) delay(20) }

        assertTrue(loader.memoryCache!!.keys.isEmpty())
        val card = loader.execute(ImageRequest.Builder(context).data(url).size(2).build())
        assertIs<SuccessResult>(card)
        assertEquals(DataSource.DISK, card.dataSource)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun waitsWhileThePlayerIsMovingMedia() = runBlocking<Unit> {
        server.enqueue(png())
        val url = server.url("/poster.png").toString()
        mediaActive.value = true

        prefetcher.prefetch(listOf(url))
        delay(2_500)
        assertEquals(0, server.requestCount)

        mediaActive.value = false
        withTimeout(10_000) { while (server.requestCount == 0) delay(20) }
    }

    @Test
    fun fetchesEachUrlOncePerRun() = runBlocking<Unit> {
        server.enqueue(png())
        server.enqueue(png())
        val url = server.url("/poster.png").toString()
        val added = server.url("/added.png").toString()

        prefetcher.prefetch(listOf(url))
        withTimeout(10_000) { while (!loader.isOnDisk(url)) delay(20) }
        // A Home refresh hands over the old picture again, beside a new one.
        prefetcher.prefetch(listOf(url, added, url))
        withTimeout(10_000) { while (!loader.isOnDisk(added)) delay(20) }

        assertEquals(listOf("/poster.png", "/added.png"), List(2) { server.takeRequest().path })
        assertEquals(2, server.requestCount)
    }

    @Test
    fun reSignedArtworkLoadsFromDisk() = runBlocking<Unit> {
        server.enqueue(png())
        val today = server.url("/api/v2/artwork/poster/w500.abc.webp?exp=1&sig=a").toString()
        val tomorrow = server.url("/api/v2/artwork/poster/w500.abc.webp?exp=2&sig=b").toString()

        assertIs<SuccessResult>(loader.execute(ImageRequest.Builder(context).data(today).size(2).build()))
        loader.memoryCache!!.clear()
        val next = loader.execute(ImageRequest.Builder(context).data(tomorrow).size(2).build())

        assertIs<SuccessResult>(next)
        assertEquals(DataSource.DISK, next.dataSource)
        assertEquals(1, server.requestCount)
    }

    private fun ImageLoader.isOnDisk(url: String): Boolean =
        diskCache!!.openSnapshot(stableArtworkKey(url) ?: url)?.also { it.close() } != null

    private fun png(): MockResponse {
        val png = ByteArrayOutputStream().apply {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, this)
        }.toByteArray()
        return MockResponse()
            .setHeader("Content-Type", "image/png")
            .setBody(Buffer().write(png))
    }
}
