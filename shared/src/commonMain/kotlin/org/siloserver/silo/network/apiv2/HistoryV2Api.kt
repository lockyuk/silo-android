package org.siloserver.silo.network.apiv2

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import org.siloserver.silo.network.*

/** One server history page per request; empty pages can still have continuation. */
class HistoryV2Api(
    private val client: HttpClient,
    private val gate: ApiV2Gate,
    private val tokenManager: TokenManager? = null,
) {
    suspend fun page(limit: Int = 40, imageSize: String? = null,
        continuation: HistoryContinuationV2? = null): ApiResult<HistoryPageV2> {
        if (limit !in 1..200) return ApiResult.Error(0, "validation_failed", "History page size must be between 1 and 200.")
        if (continuation != null && (continuation.limit != limit || continuation.imageSize != imageSize))
            return invalidCursor("The history request changed. Reload history.")
        val scope = continuation?.scope ?: tokenManager?.snapshotCurrentScope()
        val result = ownedV2Call<HistoryPageWireV2, HistoryPageWireV2>(gate, tokenManager, scope, OwnerPolicy.IDENTITY, null, { owner ->
            client.get("/api/v2/history") {
                owner?.let { authScope(it) }
                parameter("limit", limit)
                imageSize?.let { parameter("image_size", it) }
                continuation?.let { parameter("cursor", it.cursor) }
            }
        }) { it }
        return when (result) {
            is ApiResult.Success -> {
                val page = result.data.page
                val next = page.nextCursor
                if (page.hasMore && (next.isNullOrBlank() || next in continuation?.seenCursors.orEmpty()) ||
                    !page.hasMore && !next.isNullOrBlank()) {
                    return invalidCursor("The server returned invalid history pagination. Reload history.")
                }
                val seen = continuation?.seenContentIds.orEmpty().toMutableSet()
                val items = result.data.items.filter { seen.add(it.item.contentId) }
                val following = if (page.hasMore) HistoryContinuationV2(next!!, scope, limit, imageSize,
                    continuation?.seenCursors.orEmpty() + next, seen) else null
                ApiResult.Success(HistoryPageV2(items, following))
            }
            is ApiResult.Error -> if (result.error == "invalid_cursor")
                result.copy(message = "History changed or expired. Reload history.") else result
            is ApiResult.NetworkError -> result
        }
    }

    private fun invalidCursor(message: String) = ApiResult.Error(0, "invalid_cursor", message)

    /** Every watch from the last [days] days, newest first (fork endpoint). */
    suspend fun watches(days: Int): ApiResult<List<HistoryWatchRowV2>> {
        val scope = tokenManager?.snapshotCurrentScope()
        return ownedV2Call<HistoryWatchesWireV2, List<HistoryWatchRowV2>>(gate, tokenManager, scope, OwnerPolicy.IDENTITY, null, { owner ->
            client.get("/api/v2/history/watches") {
                owner?.let { authScope(it) }
                parameter("days", days.coerceIn(1, 90))
            }
        }) { it.items }
    }
}

/** One watch in a watch list: the episode or film, its show, and when. */
@kotlinx.serialization.Serializable
data class HistoryWatchRowV2(
    @kotlinx.serialization.SerialName("content_id") val contentId: String,
    val type: String = "",
    val title: String = "",
    @kotlinx.serialization.SerialName("series_id") val seriesId: String? = null,
    @kotlinx.serialization.SerialName("series_title") val seriesTitle: String? = null,
    @kotlinx.serialization.SerialName("season_number") val seasonNumber: Int? = null,
    @kotlinx.serialization.SerialName("episode_number") val episodeNumber: Int? = null,
    @kotlinx.serialization.SerialName("poster_url") val posterUrl: String? = null,
    @kotlinx.serialization.SerialName("watched_at") val watchedAt: String,
    val completed: Boolean = false,
)

@kotlinx.serialization.Serializable
internal data class HistoryWatchesWireV2(val items: List<HistoryWatchRowV2> = emptyList())
