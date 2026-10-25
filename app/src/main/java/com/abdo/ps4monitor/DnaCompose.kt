package com.abdo.ps4monitor

import android.content.Context
import android.graphics.Typeface
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * Compose side of the design system: turns a resolved [Look] into Material colours, shapes and typography, and exposes the look
 * (and the effects level) to every composable through [LocalLook] / [LocalFx]. Screens never branch on a game or theme name; they read
 * these locals (directly or through the helpers in DnaDecor.kt).
 */

/** The tokens the UI needs beyond what Material has a slot for (kept from the previous theme engine so existing screens still compile). */
@Immutable
data class Tokens(
    val bg: Color, val bg2: Color, val surface: Color, val accent: Color, val accent2: Color,
    val text: Color, val text2: Color, val muted: Color, val border: Color, val progress: Color,
    val success: Color, val warning: Color, val error: Color, val onAccent: Color,
)

private val FALLBACK_LOOK: Look = LookResolver.forApp(ThemeMode.DEFAULT, ThemeCore.DEFAULT, true)
val LocalLook = staticCompositionLocalOf { FALLBACK_LOOK }
val LocalTokens = compositionLocalOf { tokensOf(FALLBACK_LOOK.scheme) }
/** Effects level: 0 = no decoration, 1 = decoration without motion, 2 = decoration with motion (the default). */
val LocalFx = compositionLocalOf { 2 }

private fun c(i: Int) = Color(i)

fun tokensOf(s: DnaScheme) = Tokens(c(s.bg), c(s.bg2), c(s.card), c(s.primary), c(s.secondary), c(s.text), c(s.muted),
    c(ColorOps.mix(s.muted, s.bg, 0.35f)), c(s.outlineVariant), c(s[Role.PROGRESS]), c(s[Role.SUCCESS]), c(s[Role.WARNING]), c(s[Role.ERROR]), c(s[Role.ON_PRIMARY]))

/** Tokens of an arbitrary Material scheme (used for the phone's own Material You colours); status colours come from [fallback]. */
fun tokensOf(cs: ColorScheme, fallback: DnaScheme) = Tokens(cs.surface, cs.surfaceContainer, cs.surfaceContainerHigh, cs.primary, cs.secondary, cs.onSurface, cs.onSurfaceVariant,
    cs.onSurfaceVariant.copy(alpha = 0.75f), cs.outlineVariant, cs.primary, c(fallback[Role.SUCCESS]), c(fallback[Role.WARNING]), c(fallback[Role.ERROR]), cs.onPrimary)

fun colorSchemeOf(s: DnaScheme): ColorScheme =
    if (s.dark) darkColorScheme(
        primary = c(s.primary), onPrimary = c(s[Role.ON_PRIMARY]), primaryContainer = c(s[Role.PRIMARY_CONTAINER]), onPrimaryContainer = c(s[Role.ON_PRIMARY_CONTAINER]),
        secondary = c(s.secondary), onSecondary = c(s[Role.ON_SECONDARY]), secondaryContainer = c(s[Role.SECONDARY_CONTAINER]), onSecondaryContainer = c(s[Role.ON_SECONDARY_CONTAINER]),
        tertiary = c(s.tertiary), onTertiary = c(s[Role.ON_TERTIARY]), tertiaryContainer = c(s[Role.TERTIARY_CONTAINER]), onTertiaryContainer = c(s[Role.ON_TERTIARY_CONTAINER]),
        background = c(s.bg), onBackground = c(s.text), surface = c(s.bg), onSurface = c(s.text), surfaceVariant = c(s.card), onSurfaceVariant = c(s.muted), surfaceTint = c(s.primary),
        inverseSurface = c(s.text), inverseOnSurface = c(s.bg), error = c(s[Role.ERROR]), onError = c(s[Role.ON_ERROR]), errorContainer = c(s[Role.ERROR_CONTAINER]), onErrorContainer = c(s[Role.ON_ERROR_CONTAINER]),
        outline = c(s.outline), outlineVariant = c(s.outlineVariant),
        surfaceContainerLowest = c(s.bg), surfaceContainerLow = c(s.low), surfaceContainer = c(s.bg2), surfaceContainerHigh = c(s.card), surfaceContainerHighest = c(s.high),
    ) else lightColorScheme(
        primary = c(s.primary), onPrimary = c(s[Role.ON_PRIMARY]), primaryContainer = c(s[Role.PRIMARY_CONTAINER]), onPrimaryContainer = c(s[Role.ON_PRIMARY_CONTAINER]),
        secondary = c(s.secondary), onSecondary = c(s[Role.ON_SECONDARY]), secondaryContainer = c(s[Role.SECONDARY_CONTAINER]), onSecondaryContainer = c(s[Role.ON_SECONDARY_CONTAINER]),
        tertiary = c(s.tertiary), onTertiary = c(s[Role.ON_TERTIARY]), tertiaryContainer = c(s[Role.TERTIARY_CONTAINER]), onTertiaryContainer = c(s[Role.ON_TERTIARY_CONTAINER]),
        background = c(s.bg), onBackground = c(s.text), surface = c(s.bg), onSurface = c(s.text), surfaceVariant = c(s.card), onSurfaceVariant = c(s.muted), surfaceTint = c(s.primary),
        inverseSurface = c(s.text), inverseOnSurface = c(s.bg), error = c(s[Role.ERROR]), onError = c(s[Role.ON_ERROR]), errorContainer = c(s[Role.ERROR_CONTAINER]), onErrorContainer = c(s[Role.ON_ERROR_CONTAINER]),
        outline = c(s.outline), outlineVariant = c(s.outlineVariant),
        surfaceContainerLowest = c(s.bg), surfaceContainerLow = c(s.low), surfaceContainer = c(s.bg2), surfaceContainerHigh = c(s.card), surfaceContainerHighest = c(s.high),
    )

// ---------------------------------------------------------------- shapes
/** A corner shape of the DNA's family. [dp] is the radius a classic (soft) design would use; the family scales it. Direction-aware (RTL safe). */
fun shapeFor(sp: ShapeSpec, dp: Float): CornerBasedShape {
    val k = dp / 18f
    return when (sp.family) {
        ShapeFamily.SOFT, ShapeFamily.CRISP -> RoundedCornerShape((sp.radius * k).dp)
        ShapeFamily.ORGANIC -> { val r = sp.radius * k; RoundedCornerShape(topStart = (r * 1.35f).dp, topEnd = (r * 0.55f).dp, bottomEnd = (r * 1.35f).dp, bottomStart = (r * 0.55f).dp) }
        ShapeFamily.ANGULAR -> { val cut = (sp.cut * k).coerceAtLeast(2f); CutCornerShape(topStart = cut.dp, topEnd = 0.dp, bottomEnd = cut.dp, bottomStart = 0.dp) }
    }
}
fun shapesOf(sp: ShapeSpec) = Shapes(extraSmall = shapeFor(sp, 6f), small = shapeFor(sp, 10f), medium = shapeFor(sp, 14f), large = shapeFor(sp, 18f), extraLarge = shapeFor(sp, 24f))

/** The shape a card with a classic corner radius of [dp] has in the current identity. */
@Composable fun dnaShape(dp: Float): Shape { val sp = LocalLook.current.style.shape; return remember(sp, dp) { shapeFor(sp, dp) } }

// ---------------------------------------------------------------- typography
private val condensed: FontFamily? by lazy { runCatching { FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL)) }.getOrNull() }

/** Headings take the identity's face and tracking. Arabic keeps zero tracking: letter spacing would break the joined letters. */
fun typographyOf(base: Typography, t: TypeSpec, arabic: Boolean): Typography {
    val fam: FontFamily? = when (t.face) { DisplayFace.SANS -> null; DisplayFace.SERIF -> FontFamily.Serif; DisplayFace.CONDENSED -> condensed }
    if (fam == null && (t.tracking == 0f || arabic)) return base
    fun TextStyle.d(): TextStyle {
        var s = this
        if (fam != null) s = s.copy(fontFamily = fam)
        if (!arabic && t.tracking != 0f) s = s.copy(letterSpacing = t.tracking.em)
        return s
    }
    return base.copy(displayLarge = base.displayLarge.d(), displayMedium = base.displayMedium.d(), displaySmall = base.displaySmall.d(),
        headlineLarge = base.headlineLarge.d(), headlineMedium = base.headlineMedium.d(), headlineSmall = base.headlineSmall.d(),
        titleLarge = base.titleLarge.d(), titleMedium = base.titleMedium.d(), titleSmall = base.titleSmall.d())
}

// ---------------------------------------------------------------- the provider
/**
 * Applies [look] to [content]: Material colour scheme, shapes, typography, [Tokens], [LocalLook] and [LocalFx].
 * [phoneScheme]: the phone's own (Material You) colours, used instead of the generated scheme when the user chose them.
 */
@Composable fun DnaTheme(look: Look, fx: Int, phoneScheme: ColorScheme? = null, content: @Composable () -> Unit) {
    val arabic = Lang.isAr
    val cs = phoneScheme ?: remember(look.scheme) { colorSchemeOf(look.scheme) }
    val shapes = remember(look.style.shape) { shapesOf(look.style.shape) }
    val typo = remember(look.style.type, arabic) { typographyOf(Typography(), look.style.type, arabic) }
    val tokens = remember(look.scheme, phoneScheme) { if (phoneScheme != null) tokensOf(phoneScheme, look.scheme) else tokensOf(look.scheme) }
    CompositionLocalProvider(LocalLook provides look, LocalTokens provides tokens, LocalFx provides fx) {
        MaterialTheme(colorScheme = cs, shapes = shapes, typography = typo, content = content)
    }
}

/** Motion is allowed only when the system has animations on and the phone is not in battery-saver mode. */
@Composable fun rememberMotionAllowed(): Boolean {
    val ctx = LocalContext.current
    return remember { motionAllowed(ctx) }
}
private fun motionAllowed(ctx: Context): Boolean {
    val scale = runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
    val saver = runCatching { (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true }.getOrDefault(false)
    return scale > 0f && !saver
}
