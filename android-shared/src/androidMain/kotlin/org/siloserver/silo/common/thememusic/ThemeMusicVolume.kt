package org.siloserver.silo.common.thememusic

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How loud theme music plays on this device, 10–100 percent of the app's
 * volume. Device-local: a TV across the room and a phone in the hand want
 * different levels.
 */
class ThemeMusicVolume(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _percent = MutableStateFlow(prefs.getInt(KEY, DEFAULT).coerceIn(MIN, MAX))
    val percent: StateFlow<Int> = _percent.asStateFlow()

    /** The gain theme playback is multiplied by. */
    val gain: Float get() = _percent.value / 100f

    fun set(percent: Int) {
        val value = percent.coerceIn(MIN, MAX)
        prefs.edit().putInt(KEY, value).apply()
        _percent.value = value
    }

    /** The next step up, wrapping from 100 back to 10. */
    fun step() = set(if (_percent.value >= MAX) MIN else _percent.value + STEP)

    companion object {
        const val MIN = 10
        const val MAX = 100
        const val STEP = 10
        const val DEFAULT = 100
        private const val PREFS = "theme_music"
        private const val KEY = "volume_percent"
    }
}
