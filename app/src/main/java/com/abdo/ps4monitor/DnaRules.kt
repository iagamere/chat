package com.abdo.ps4monitor

/**
 * The readability contract of a [DnaScheme]. Every foreground role is checked against every background it is actually drawn on:
 * text roles need 4.5:1, essential graphics 3:1. Some rules use a tinted background (e.g. primary text on a 20 % primary highlight,
 * as in the selected tab) because that is what the screen really shows. [repair] fixes violations by moving the foreground only.
 */
object DnaRules {
    const val TEXT_MIN = 4.5
    const val GRAPHIC_MIN = 3.0

    /** [tint] > 0: the background is [role] mixed with the foreground itself by that fraction (translucent highlight). */
    class Bg(val role: Role, val tint: Float = 0f)
    class Rule(val fg: Role, val bgs: List<Bg>, val min: Double, val text: Boolean)

    private fun b(vararg r: Role) = r.map { Bg(it) }
    private val SURFACES = b(Role.BG, Role.BG2, Role.LOW, Role.CARD, Role.HIGH)

    val RULES: List<Rule> = listOf(
        Rule(Role.PRIMARY, b(Role.BG, Role.BG2, Role.CARD, Role.HIGH) + listOf(Bg(Role.CARD, 0.20f), Bg(Role.BG2, 0.18f)), TEXT_MIN, true),
        Rule(Role.SECONDARY, b(Role.BG, Role.BG2, Role.CARD, Role.HIGH), TEXT_MIN, true),
        Rule(Role.TERTIARY, b(Role.BG, Role.CARD), TEXT_MIN, true),
        Rule(Role.ERROR, b(Role.BG, Role.CARD, Role.HIGH) + listOf(Bg(Role.BG, 0.16f), Bg(Role.CARD, 0.16f)), TEXT_MIN, true),
        Rule(Role.SUCCESS, b(Role.BG, Role.CARD) + listOf(Bg(Role.BG, 0.16f), Bg(Role.CARD, 0.16f)), TEXT_MIN, true),
        Rule(Role.WARNING, b(Role.BG, Role.CARD) + listOf(Bg(Role.BG, 0.16f), Bg(Role.CARD, 0.16f)), TEXT_MIN, true),
        Rule(Role.TEXT, SURFACES, TEXT_MIN, true),
        Rule(Role.MUTED, SURFACES, TEXT_MIN, true),
        Rule(Role.OUTLINE, b(Role.BG, Role.CARD), GRAPHIC_MIN, false),
        Rule(Role.PROGRESS, b(Role.BG, Role.CARD, Role.HIGH), GRAPHIC_MIN, false),
        // text that sits on a coloured fill
        Rule(Role.ON_PRIMARY, b(Role.PRIMARY), TEXT_MIN, true),
        Rule(Role.ON_SECONDARY, b(Role.SECONDARY), TEXT_MIN, true),
        Rule(Role.ON_TERTIARY, b(Role.TERTIARY), TEXT_MIN, true),
        Rule(Role.ON_ERROR, b(Role.ERROR), TEXT_MIN, true),
        Rule(Role.ON_PRIMARY_CONTAINER, b(Role.PRIMARY_CONTAINER), TEXT_MIN, true),
        Rule(Role.ON_SECONDARY_CONTAINER, b(Role.SECONDARY_CONTAINER), TEXT_MIN, true),
        Rule(Role.ON_TERTIARY_CONTAINER, b(Role.TERTIARY_CONTAINER), TEXT_MIN, true),
        Rule(Role.ON_ERROR_CONTAINER, b(Role.ERROR_CONTAINER), TEXT_MIN, true),
    )

    private fun backgrounds(s: DnaScheme, r: Rule, fg: Int): List<Int> = r.bgs.map { if (it.tint > 0f) ColorOps.mix(s[it.role], fg, it.tint) else s[it.role] }
    fun contrastOf(s: DnaScheme, r: Rule): Double = ColorOps.worst(s[r.fg], backgrounds(s, r, s[r.fg]))

    /** Human-readable list of broken rules ("TEXT 3.2 < 4.5"); empty when the scheme is fully readable. */
    fun check(s: DnaScheme): List<String> = RULES.mapNotNull { r ->
        val c = contrastOf(s, r)
        if (c + 1e-9 < r.min) "${r.fg.key} %.2f < %.1f".format(c, r.min) else null
    }

    fun report(s: DnaScheme): ContrastReport {
        var t = Double.MAX_VALUE; var g = Double.MAX_VALUE
        for (r in RULES) { val c = contrastOf(s, r); if (r.text) t = minOf(t, c) else g = minOf(g, c) }
        return ContrastReport(t, g)
    }

    /** Returns a scheme that satisfies every rule: only foreground roles are moved, backgrounds stay as designed. */
    fun repair(s: DnaScheme): DnaScheme {
        var cur = s
        for (pass in 0 until 3) {
            var changed = false
            for (r in RULES) {
                var fg = cur[r.fg]
                var n = 0
                while (n++ < 6) {
                    val bgs = backgrounds(cur, r, fg)
                    if (ColorOps.worst(fg, bgs) >= r.min) break
                    fg = ColorOps.ensure(fg, bgs, r.min)
                }
                if (fg != cur[r.fg]) { cur = cur.with(r.fg to fg); changed = true }
            }
            if (!changed) break
        }
        return cur
    }
}
