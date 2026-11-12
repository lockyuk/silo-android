package org.siloserver.silo.tv.ui.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What shows under each picture on the TV, chosen in Settings. Device-local
 * (SharedPreferences), never synced; every option starts on, which is how
 * cards looked before the options existed.
 */
object TvCardText {
    private const val PREFS = "tv_card_text"
    private var loaded = false

    /** Master switch: off hides all the text under pictures. */
    var showText by mutableStateOf(true)
        private set
    /** The show name for an episode, the title for a film. */
    var showName by mutableStateOf(true)
        private set
    var showEpisodeNumber by mutableStateOf(true)
        private set
    var showEpisodeTitle by mutableStateOf(true)
        private set
    var showYear by mutableStateOf(true)
        private set

    /** Reads the saved choices once per process. */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        showText = p.getBoolean("showText", true)
        showName = p.getBoolean("showName", true)
        showEpisodeNumber = p.getBoolean("showEpisodeNumber", true)
        showEpisodeTitle = p.getBoolean("showEpisodeTitle", true)
        showYear = p.getBoolean("showYear", true)
    }

    fun set(context: Context, key: String, value: Boolean) {
        ensureLoaded(context)
        when (key) {
            "showText" -> showText = value
            "showName" -> showName = value
            "showEpisodeNumber" -> showEpisodeNumber = value
            "showEpisodeTitle" -> showEpisodeTitle = value
            "showYear" -> showYear = value
            else -> return
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(key, value).apply()
    }
}
