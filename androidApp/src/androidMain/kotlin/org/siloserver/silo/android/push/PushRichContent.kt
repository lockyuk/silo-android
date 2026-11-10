package org.siloserver.silo.android.push

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.siloserver.silo.model.notifications.NotificationPush
import org.siloserver.silo.model.notifications.NotificationRow
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.CatalogRepository

/**
 * What a push notification can show beyond the row's title lines: the synopsis
 * and the poster. Both come from the user's own Silo server after the wake;
 * neither travels through the push relay or FCM.
 */
internal data class PushRichContent(val synopsis: String?, val poster: Bitmap?)

internal fun interface PushRichContentSource {
    /** Never throws; a part that cannot be loaded in time is simply null. */
    suspend fun load(row: NotificationRow): PushRichContent
}

/**
 * Loads the synopsis through the catalog and the poster through Coil's shared
 * loader (so it hits the same disk cache as the rest of the app). Each part
 * has its own short budget and fails on its own: FCM gives a wake only a few
 * seconds, and a missing synopsis or poster must never delay or lose the
 * notification itself.
 */
internal class CatalogPushRichContentSource(
    private val context: Context,
    private val catalog: CatalogRepository,
    private val partBudgetMs: Long = RICH_PART_BUDGET_MS,
) : PushRichContentSource {
    override suspend fun load(row: NotificationRow): PushRichContent = coroutineScope {
        val styled = row.push
        if (styled != null) {
            // The admin's push styling decides the synopsis and the image.
            val image = async { part { styledImageFor(styled) } }
            return@coroutineScope PushRichContent(
                synopsis = trimSynopsis(styled.synopsis),
                poster = image.await(),
            )
        }
        val synopsis = async { part { synopsisFor(row) } }
        val poster = async { part { posterFor(row) } }
        PushRichContent(synopsis = synopsis.await(), poster = poster.await())
    }

    private suspend fun styledImageFor(styled: NotificationPush): Bitmap? {
        val url = styled.imageUrl?.takeIf { it.isNotBlank() && styled.image != "none" } ?: return null
        val backdrop = styled.image == "backdrop"
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(if (backdrop) BACKDROP_WIDTH_PX else POSTER_WIDTH_PX, if (backdrop) BACKDROP_HEIGHT_PX else POSTER_HEIGHT_PX)
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        return result.image.toBitmap()
    }

    private suspend fun <T> part(block: suspend () -> T?): T? =
        withTimeoutOrNull(partBudgetMs) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
        }

    private suspend fun synopsisFor(row: NotificationRow): String? {
        val id = row.episodeId?.takeIf { it.isNotBlank() } ?: row.seriesId?.takeIf { it.isNotBlank() } ?: return null
        val detail = (catalog.getItemDetail(id) as? ApiResult.Success)?.data ?: return null
        return trimSynopsis(detail.overview)
    }

    private suspend fun posterFor(row: NotificationRow): Bitmap? {
        val url = row.posterUrl.takeIf { it.isNotBlank() } ?: return null
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(POSTER_WIDTH_PX, POSTER_HEIGHT_PX)
            // A notification bitmap crosses a process boundary; hardware
            // bitmaps cannot.
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        return result.image.toBitmap()
    }

    companion object {
        const val RICH_PART_BUDGET_MS = 3_000L
        private const val POSTER_WIDTH_PX = 256
        private const val POSTER_HEIGHT_PX = 384
        private const val BACKDROP_WIDTH_PX = 640
        private const val BACKDROP_HEIGHT_PX = 360
    }
}

private const val MAX_SYNOPSIS_CHARS = 400

/** Collapses whitespace and cuts at a word boundary; null for nothing to show. */
internal fun trimSynopsis(overview: String?): String? {
    val text = overview?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    if (text.isEmpty()) return null
    if (text.length <= MAX_SYNOPSIS_CHARS) return text
    val cut = text.take(MAX_SYNOPSIS_CHARS)
    return cut.substringBeforeLast(' ', cut).trimEnd(',', ';', ':', '-', ' ') + "…"
}
