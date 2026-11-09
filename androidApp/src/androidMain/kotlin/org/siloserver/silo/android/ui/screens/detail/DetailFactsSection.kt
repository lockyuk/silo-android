package org.siloserver.silo.android.ui.screens.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.siloserver.silo.common.ui.DetailFactsBlock
import org.siloserver.silo.model.catalog.DetailFacts
import org.siloserver.silo.model.catalog.ItemDetail
import org.siloserver.silo.model.catalog.PluginTitle
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.network.apiv2.FilmographyPluginApi

/**
 * The Details block (release, runtime, budget, revenue, studios, countries,
 * language, status) under a title's synopsis. Budget and revenue come from TMDB
 * through the Filmography plugin; everything else comes from the title, or from
 * the plugin when the title lacks it. [preloaded] is the plugin answer a page
 * already has, to skip a second request.
 */
@Composable
fun DetailFactsSection(detail: ItemDetail, preloaded: PluginTitle? = null) {
    val api: FilmographyPluginApi = koinInject()
    val tokens: TokenManager = koinInject()
    val extra by produceState(preloaded, detail.tmdbId, detail.type, preloaded) {
        if (preloaded != null) {
            value = preloaded
            return@produceState
        }
        val tmdb = detail.tmdbId?.toIntOrNull() ?: return@produceState
        val owner = tokens.snapshotCurrentScope() ?: return@produceState
        value = (api.title(detail.type == "series", tmdb, owner) as? ApiResult.Success)?.data
    }
    val facts = remember(detail, extra) { DetailFacts.from(detail, extra) }
    DetailFactsBlock(facts, modifier = Modifier.padding(horizontal = 16.dp))
}
