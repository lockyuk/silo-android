package org.siloserver.silo.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.koin.core.context.GlobalContext
import org.siloserver.silo.model.catalog.PluginCredit
import org.siloserver.silo.network.apiv2.FilmographyPluginApi

/**
 * The credit with its library id filled in when the plugin missed a title the server does have,
 * so a row says "In library" exactly when the title page will offer "Open in library".
 */
@Composable
fun rememberLibraryResolvedCredit(credit: PluginCredit): PluginCredit {
    if (credit.inLibrary) return credit
    val api: FilmographyPluginApi = remember { GlobalContext.get().get() }
    val resolved: State<String?> = produceState<String?>(null, credit.tmdbId, credit.isSeries) {
        value = runCatching { api.libraryContentIdFor(credit.isSeries, credit.tmdbId) }.getOrNull()
    }
    val id = resolved.value
    return if (id != null) credit.copy(itemId = id) else credit
}
