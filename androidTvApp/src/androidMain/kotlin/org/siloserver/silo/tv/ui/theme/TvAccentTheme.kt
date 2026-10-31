package org.siloserver.silo.tv.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The app's chrome colours: the menu bar capsule and its tabs, dropdown
 * panels, the focus fill of buttons and pills, card outlines, progress bars,
 * the hero's controls and the profile circle. Default is the grey-and-white
 * tvOS look; the others tint all of those at once. Body text never changes
 * colour, and text on a [focusFill] always uses [focusContent], a pair chosen
 * to stay readable (WCAG AA).
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
    /** Progress bars, the hero's eyebrow tick: a mark on dark art, never behind text. */
    val accent: Color,
    /** The running slideshow dot and other small "you are here" marks. */
    val indicator: Color,
    /** Resting round controls (hero arrows) and the profile circle. */
    val restFill: Color,
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
        accent = Color(0xFFE50914),
        indicator = Color.White,
        restFill = Color.White.copy(alpha = 0.18f),
    ),
    Purple(
        label = "Silo Purple",
        // White on #7C3AED is 5.7:1 (WCAG AA for normal text).
        focusFill = Color(0xFF7C3AED),
        focusContent = Color.White,
        barFill = Color(0xFF2B1F4A).copy(alpha = 0.80f),
        outline = Color(0xFFA78BFA).copy(alpha = 0.35f),
        selectedFill = Color(0xFF8B5CF6).copy(alpha = 0.30f),
        cardFocus = Color(0xFFA78BFA),
        cardGlow = Color(0xFF8B5CF6).copy(alpha = 0.45f),
        panelTop = Color(0xFF261C40),
        panelBottom = Color(0xFF1A1430),
        accent = Color(0xFFA78BFA),
        indicator = Color(0xFFC4B5FD),
        restFill = Color(0xFF7C3AED).copy(alpha = 0.45f),
    ),
    Blue(
        label = "Light Blue",
        // A light sky blue, so text on it is black: 13:1 (WCAG AAA).
        focusFill = Color(0xFF7DD3FC),
        focusContent = Color.Black,
        barFill = Color(0xFF14273D).copy(alpha = 0.80f),
        outline = Color(0xFF7DD3FC).copy(alpha = 0.40f),
        selectedFill = Color(0xFF7DD3FC).copy(alpha = 0.28f),
        cardFocus = Color(0xFF7DD3FC),
        cardGlow = Color(0xFF38BDF8).copy(alpha = 0.45f),
        panelTop = Color(0xFF16283D),
        panelBottom = Color(0xFF0F1C2C),
        accent = Color(0xFF7DD3FC),
        indicator = Color(0xFFBAE6FD),
        restFill = Color(0xFF38BDF8).copy(alpha = 0.35f),
    ),
    Orange(
        label = "Orange",
        // Black on #FB923C is 8.6:1 (WCAG AAA).
        focusFill = Color(0xFFFB923C),
        focusContent = Color.Black,
        barFill = Color(0xFF3A2414).copy(alpha = 0.80f),
        outline = Color(0xFFFDBA74).copy(alpha = 0.40f),
        selectedFill = Color(0xFFFB923C).copy(alpha = 0.28f),
        cardFocus = Color(0xFFFDBA74),
        cardGlow = Color(0xFFF97316).copy(alpha = 0.45f),
        panelTop = Color(0xFF33200F),
        panelBottom = Color(0xFF22160B),
        accent = Color(0xFFFB923C),
        indicator = Color(0xFFFED7AA),
        restFill = Color(0xFFF97316).copy(alpha = 0.35f),
    ),
    Green(
        label = "Green",
        // Black on #4ADE80 is 11.4:1 (WCAG AAA).
        focusFill = Color(0xFF4ADE80),
        focusContent = Color.Black,
        barFill = Color(0xFF143323).copy(alpha = 0.80f),
        outline = Color(0xFF86EFAC).copy(alpha = 0.40f),
        selectedFill = Color(0xFF4ADE80).copy(alpha = 0.28f),
        cardFocus = Color(0xFF86EFAC),
        cardGlow = Color(0xFF22C55E).copy(alpha = 0.45f),
        panelTop = Color(0xFF132B1E),
        panelBottom = Color(0xFF0C1D14),
        accent = Color(0xFF4ADE80),
        indicator = Color(0xFFBBF7D0),
        restFill = Color(0xFF22C55E).copy(alpha = 0.35f),
    ),
    Red(
        label = "Red",
        // White on #DC2626 is 4.8:1 (WCAG AA).
        focusFill = Color(0xFFDC2626),
        focusContent = Color.White,
        barFill = Color(0xFF3F1A1A).copy(alpha = 0.80f),
        outline = Color(0xFFF87171).copy(alpha = 0.40f),
        selectedFill = Color(0xFFDC2626).copy(alpha = 0.28f),
        cardFocus = Color(0xFFF87171),
        cardGlow = Color(0xFFEF4444).copy(alpha = 0.45f),
        panelTop = Color(0xFF361717),
        panelBottom = Color(0xFF241010),
        accent = Color(0xFFF87171),
        indicator = Color(0xFFFCA5A5),
        restFill = Color(0xFFEF4444).copy(alpha = 0.35f),
    ),
    ;

    fun next(): TvAccentTheme = entries[(ordinal + 1) % entries.size]

    /**
     * A control at rest (not focused): [default] on the Default theme, so it
     * looks as it always has, else the theme's tinted fill. White text stays
     * readable on every theme's rest fill.
     */
    fun rest(default: Color): Color = if (this == Default) default else restFill

    /** A resting outline: [default] on the Default theme, else the theme's outline. */
    fun restOutline(default: Color): Color = if (this == Default) default else outline

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
