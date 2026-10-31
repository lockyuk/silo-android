package org.siloserver.silo.tv.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The app's chrome colours: the menu bar capsule and its tabs, dropdown
 * panels, the focus fill of buttons and pills, and card outlines. Default is
 * the grey-and-white tvOS look; the others tint all of those at once.
 */
enum class TvAccentTheme(
    val label: String,
    /** Fill of a focused tab, pill or button. */
    val focusFill: Color,
    /** Text and icons on [focusFill]. */
    val focusContent: Color,
    /** The menu bar capsule behind the tabs. */
    val barFill: Color,
    /** Hairline around the bar capsule, panels and resting cards. */
    val outline: Color,
    /** A selected (not focused) tab or row. */
    val selectedFill: Color,
    /** Focused card outline and glow. */
    val cardFocus: Color,
    val cardGlow: Color,
    /** Dropdown panel gradient, top to bottom. */
    val panelTop: Color,
    val panelBottom: Color,
) {
    Default(
        label = "Default",
        focusFill = Color(0xFFEDEDED),
        focusContent = Color(0xFF000000),
        barFill = Color(0xFF2A2A2E).copy(alpha = 0.72f),
        outline = Color.White.copy(alpha = 0.14f),
        selectedFill = Color.White.copy(alpha = 0.14f),
        cardFocus = Color(0xFFEDEDED),
        cardGlow = Color.White.copy(alpha = 0.28f),
        panelTop = Color(0xFF1D1F24),
        panelBottom = Color(0xFF17181B),
    ),
    Purple(
        label = "Silo Purple",
        focusFill = Color(0xFF8B5CF6),
        focusContent = Color.White,
        barFill = Color(0xFF2B1F4A).copy(alpha = 0.80f),
        outline = Color(0xFFA78BFA).copy(alpha = 0.35f),
        selectedFill = Color(0xFF8B5CF6).copy(alpha = 0.30f),
        cardFocus = Color(0xFFA78BFA),
        cardGlow = Color(0xFF8B5CF6).copy(alpha = 0.45f),
        panelTop = Color(0xFF261C40),
        panelBottom = Color(0xFF1A1430),
    ),
    Blue(
        label = "Blue",
        focusFill = Color(0xFF3B82F6),
        focusContent = Color.White,
        barFill = Color(0xFF172A4A).copy(alpha = 0.80f),
        outline = Color(0xFF60A5FA).copy(alpha = 0.35f),
        selectedFill = Color(0xFF3B82F6).copy(alpha = 0.30f),
        cardFocus = Color(0xFF60A5FA),
        cardGlow = Color(0xFF3B82F6).copy(alpha = 0.45f),
        panelTop = Color(0xFF16263F),
        panelBottom = Color(0xFF101B2E),
    ),
    ;

    fun next(): TvAccentTheme = entries[(ordinal + 1) % entries.size]

    companion object {
        private const val PREFS = "tv_appearance"
        private const val KEY = "accent_theme"

        /**
         * The theme in use. Snapshot state, so every screen reading a colour
         * token below recomposes when it changes.
         */
        var current by mutableStateOf(Default)
            private set

        /** Loads this device's choice; call once at startup. */
        fun load(context: Context) {
            val saved = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null)
            current = entries.firstOrNull { it.name == saved } ?: Default
        }

        fun set(context: Context, theme: TvAccentTheme) {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, theme.name).apply()
            current = theme
        }
    }
}
