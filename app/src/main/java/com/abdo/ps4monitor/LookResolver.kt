package com.abdo.ps4monitor

/**
 * Decides what the interface looks like. The two inputs are independent:
 *   appearance (system / light / dark)  - the user's choice, applies to EVERY theme and game identity;
 *   identity   (app default / preset / custom profile / the game's own DNA) - what the artwork or theme says.
 * Pure Kotlin: the same function runs in the app and in unit tests.
 */
object LookResolver {
    /** [pref]: 0 = follow the system, 1 = light, 2 = dark (the value stored by the existing Appearance setting). */
    fun isDark(pref: Int, systemDark: Boolean) = when (pref) { 1 -> false; 2 -> true; else -> systemDark }

    private fun lookOf(t: AppTheme, dark: Boolean) = Look(dark, ThemeCore.schemeOf(t, dark), t.style, DnaHash.seed(t.id), null)

    /** The look of every ordinary screen. In Game-theme mode only game pages follow the game, the rest uses the app default. */
    fun forApp(mode: ThemeMode, base: AppTheme, dark: Boolean): Look = when (mode) {
        ThemeMode.PRESET, ThemeMode.CUSTOM -> lookOf(base, dark)
        else -> lookOf(ThemeCore.DEFAULT, dark)
    }

    /**
     * The look of one game's pages. [game] may be null (cover still loading / unreadable): the page then simply uses the app look, and
     * switches when the identity is ready.
     */
    fun forGame(mode: ThemeMode, base: AppTheme, applyGameAccents: Boolean, game: DesignDna?, dark: Boolean): Look {
        if (game == null) return forApp(mode, base, dark)
        return when {
            mode == ThemeMode.DYNAMIC -> Look(dark, game.scheme(dark), game.style, game.seed, game)
            (mode == ThemeMode.PRESET || mode == ThemeMode.CUSTOM) && applyGameAccents -> {
                val gs = game.scheme(dark)
                val t = ThemeCore.forAppearance(base, dark).copy(accent = gs.primary, accent2 = gs.secondary, progress = gs[Role.PROGRESS])
                // the game's accents were made readable on the game's surfaces; schemeOf re-checks them on this theme's surfaces
                lookOf(t, dark)
            }
            else -> forApp(mode, base, dark)
        }
    }
}
