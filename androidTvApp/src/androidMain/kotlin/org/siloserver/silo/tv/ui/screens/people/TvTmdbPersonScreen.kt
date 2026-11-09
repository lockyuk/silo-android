package org.siloserver.silo.tv.ui.screens.people

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import org.koin.compose.koinInject
import org.siloserver.silo.model.catalog.Person
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.network.apiv2.FilmographyPluginApi
import org.siloserver.silo.tv.ui.components.TvErrorScreen
import org.siloserver.silo.tv.ui.components.TvLoadingScreen

/** A person page for someone the library does not have, built from the Filmography plugin's TMDB data. */
@Composable
fun TvTmdbPersonScreen(
    tmdbId: String,
    onOpenItemDetail: (contentId: String) -> Unit,
    onOpenRequest: (mediaType: String, tmdbId: Int) -> Unit,
    onBack: () -> Unit,
) {
    val api: FilmographyPluginApi = koinInject()
    val tokens: TokenManager = koinInject()
    val result by produceState<Result<Person>?>(null, tmdbId) {
        val owner = tokens.snapshotCurrentScope()
        value = if (owner == null) Result.failure(IllegalStateException("Sign in to see this person."))
        else when (val r = api.person(tmdbId, owner)) {
            is ApiResult.Success -> Result.success(
                r.data.profile?.toPerson() ?: Person(id = 0L, name = "Unknown", tmdbId = tmdbId),
            )
            is ApiResult.Error -> Result.failure(IllegalStateException("Couldn't load this person. ${r.message}"))
            is ApiResult.NetworkError -> Result.failure(IllegalStateException("Network error. Check your connection."))
        }
    }
    BackHandler(enabled = true) { onBack() }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val r = result
        when {
            r == null -> TvLoadingScreen(modifier = Modifier.background(MaterialTheme.colorScheme.background))
            r.isFailure -> TvErrorScreen(
                message = r.exceptionOrNull()?.message ?: "Something went wrong",
                onRetry = onBack,
                modifier = Modifier.background(MaterialTheme.colorScheme.background),
            )
            else -> TvPersonDetailContent(
                person = r.getOrThrow(),
                onOpenItemDetail = onOpenItemDetail,
                onOpenRequest = onOpenRequest,
            )
        }
    }
}
