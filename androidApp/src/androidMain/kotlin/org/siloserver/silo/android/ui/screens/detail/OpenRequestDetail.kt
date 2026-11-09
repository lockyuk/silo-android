package org.siloserver.silo.android.ui.screens.detail

import androidx.compose.runtime.staticCompositionLocalOf
import org.siloserver.silo.model.catalog.PluginCredit
import org.siloserver.silo.model.request.RequestMediaType

/**
 * Opens the request page (a library-style page for a title the library lacks)
 * for a media type and TMDB id. Provided by the app's navigation; null where
 * there is no navigation, such as previews.
 */
val LocalOpenRequestDetail = staticCompositionLocalOf<((mediaType: String, tmdbId: Int) -> Unit)?> { null }

internal fun PluginCredit.requestMediaType(): String = if (isSeries) RequestMediaType.Series else RequestMediaType.Movie
