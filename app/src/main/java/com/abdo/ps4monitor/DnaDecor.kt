package com.abdo.ps4monitor

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Decorative primitives of the design system. All of them read the current [Look] (colours + [StyleSpec]) and the effects level
 * ([LocalFx]); none knows a game's name. Geometry comes from [MotifGeometry] (deterministic per seed) and is built once per size inside
 * drawWithCache, so scrolling only replays recorded draw commands. Directional parts (edge bars, tapers, corner marks, rays) mirror in RTL.
 */

// ---------------------------------------------------------------- surfaces
/**
 * A card / panel surface of the current identity: clip to [shape], fill, optional motif + texture behind the content, then the border style
 * (hairline, double rule, strong, accent edge) and corner marks above it. [level] 0 = recessed, 1 = card, 2 = raised.
 * [container] overrides the fill colour (e.g. a tinted download card) while keeping the identity's border and ornaments.
 */
@Composable fun Modifier.dnaSurface(shape: Shape, level: Int = 1, container: Color? = null): Modifier {
    val look = LocalLook.current; val fx = LocalFx.current; val cs = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val st = look.style; val orn = st.ornament
    val fill = container ?: when (level) { 0 -> cs.surfaceContainerLow; 2 -> cs.surfaceContainerHighest; else -> cs.surfaceContainerHigh }
    val line = cs.outlineVariant; val strong = cs.outline; val accent = cs.primary; val ink = cs.onSurface
    val seed = look.seed
    val decorate = fx > 0
    return this.clip(shape).background(fill).drawWithCache {
        val d = density
        val w = size.width; val h = size.height
        var motif: Path? = null; var motifFill = false
        if (decorate && orn.motif != MotifKind.NONE && w > 60f * d && h > 40f * d) {
            val p = Path(); motifFill = buildMotif(p, orn.motif, w, h, d, seed, rtl)
            motif = p
        }
        var specks: Path? = null
        if (decorate && st.texture.kind != TextureKind.NONE && w > 0f && h > 0f) {
            val count = ((w / d) * (h / d) / 260f).toInt().coerceIn(0, 140)
            val len = if (st.texture.kind == TextureKind.PAPER) 6f * d else 1.2f * d
            specks = specksPath(MotifGeometry.specks(w, h, count, len, seed xor 0x5DEECE66DL))
        }
        val motifBrush = Brush.verticalGradient(listOf(accent.copy(alpha = orn.motifAlpha * 1.4f), accent.copy(alpha = 0f)), 0f, h * 0.9f)
        onDrawWithContent {
            motif?.let { if (motifFill) drawPath(it, motifBrush) else drawPath(it, accent.copy(alpha = orn.motifAlpha * 1.6f), style = Stroke(1f * d)) }
            specks?.let { drawPath(it, ink.copy(alpha = st.texture.strength), style = Stroke(0.7f * d, cap = StrokeCap.Round)) }
            drawContent()
            val bw = orn.borderWidth * d
            when (orn.border) {
                BorderStyle.NONE -> {}
                BorderStyle.HAIRLINE -> strokeShape(shape, line, max(1f * d, bw), 0f)
                BorderStyle.DOUBLE -> { strokeShape(shape, strong.copy(alpha = 0.55f), max(1f * d, bw), 0f); strokeShape(shape, line, 1f * d, 4f * d) }
                BorderStyle.STRONG -> strokeShape(shape, strong.copy(alpha = 0.8f), max(1.5f * d, bw), 0f)
                BorderStyle.ACCENT_EDGE -> {
                    strokeShape(shape, line, 1f * d, 0f)
                    val bar = max(2f * d, bw) * 1.5f
                    drawRect(accent, topLeft = Offset(if (rtl) w - bar else 0f, 0f), size = Size(bar, h))
                }
            }
            if (orn.cornerMark && w > 80f * d && h > 60f * d) cornerMarks(accent.copy(alpha = 0.75f), d, rtl)
        }
    }
}

private fun DrawScope.strokeShape(shape: Shape, color: Color, width: Float, inset: Float) {
    inset(inset + width / 2f) {
        drawOutline(shape.createOutline(size, layoutDirection, this), color, style = Stroke(width))
    }
}

private fun DrawScope.cornerMarks(color: Color, d: Float, rtl: Boolean) {
    val m = 7f * d; val l = 11f * d; val sw = 1.5f * d
    val w = size.width; val h = size.height
    val tx = if (rtl) m else w - m; val dir = if (rtl) 1f else -1f      // top END corner
    drawLine(color, Offset(tx, m), Offset(tx + dir * l, m), sw, StrokeCap.Round); drawLine(color, Offset(tx, m), Offset(tx, m + l), sw, StrokeCap.Round)
    val bx = if (rtl) w - m else m; val dir2 = if (rtl) -1f else 1f      // bottom START corner
    drawLine(color, Offset(bx, h - m), Offset(bx + dir2 * l, h - m), sw, StrokeCap.Round); drawLine(color, Offset(bx, h - m), Offset(bx, h - m - l), sw, StrokeCap.Round)
}

private fun specksPath(a: FloatArray): Path {
    val p = Path()
    var i = 0
    while (i + 3 < a.size) {
        val x = a[i]; val y = a[i + 1]; val len = a[i + 2]; val ang = a[i + 3]
        p.moveTo(x, y); p.lineTo(x + kotlin.math.cos(ang) * len, y + sin(ang) * len)
        i += 4
    }
    return p
}

/** Builds the motif into [p]; returns true when it must be filled (rays, slashes, dots) and false when stroked (waves, vines). Anchored to the top END. */
private fun buildMotif(p: Path, kind: MotifKind, w: Float, h: Float, d: Float, seed: Long, rtl: Boolean): Boolean {
    fun mx(x: Float) = if (rtl) w - x else x
    when (kind) {
        MotifKind.WAVES -> {
            val a = MotifGeometry.waves(w, h, 22f * d, 56f * d)
            var i = 0
            while (i + 2 < a.size) {
                val r = a[i + 2]
                p.arcTo(Rect(mx(a[i]) - r, a[i + 1] - r, mx(a[i]) + r, a[i + 1] + r), 180f, 180f, true)
                i += 3
            }
            return false
        }
        MotifKind.RAYS -> {
            val a = MotifGeometry.rays(w, min(h, 160f * d), 110f, 5, seed)
            val hh = min(h, 160f * d)
            var i = 0
            while (i + 3 < a.size) {
                p.moveTo(mx(a[i]), 0f); p.lineTo(mx(a[i + 1]), 0f); p.lineTo(mx(a[i + 2]), hh); p.lineTo(mx(a[i + 3]), hh); p.close()
                i += 4
            }
            return true
        }
        MotifKind.SLASHES -> {
            val rw = min(w, 150f * d); val rh = min(h, 90f * d)
            val a = MotifGeometry.slashes(rw, rh, 4, seed)
            var i = 0
            while (i + 3 < a.size) {
                val x = a[i] + (w - rw); val y = a[i + 1]; val len = a[i + 2]; val t = a[i + 3] * d
                val dx = len * 0.5f; val dy = len * 0.87f
                p.moveTo(mx(x), y); p.lineTo(mx(x + t), y); p.lineTo(mx(x + t + dx), y + dy); p.lineTo(mx(x + dx), y + dy); p.close()
                i += 4
            }
            return true
        }
        MotifKind.DOTS -> {
            val rw = min(w, 120f * d); val rh = min(h, 80f * d)
            val a = MotifGeometry.dots(rw, rh, 14f * d, seed)
            var i = 0
            while (i + 2 < a.size) { val x = a[i] + (w - rw); val r = a[i + 2] * d; p.addOval(Rect(mx(x) - r, a[i + 1] - r, mx(x) + r, a[i + 1] + r)); i += 3 }
            return true
        }
        MotifKind.VINES -> {
            val (c, leaves) = MotifGeometry.vines(w, h, seed)
            if (c.isNotEmpty()) p.moveTo(mx(c[0]), c[1])
            var i = 0
            while (i + 7 < c.size) { p.cubicTo(mx(c[i + 2]), c[i + 3], mx(c[i + 4]), c[i + 5], mx(c[i + 6]), c[i + 7]); i += 8 }
            i = 0
            while (i + 2 < leaves.size) { val r = leaves[i + 2] * d; p.addOval(Rect(mx(leaves[i]) - r, leaves[i + 1] - r, mx(leaves[i]) + r, leaves[i + 1] + r)); i += 3 }
            return false
        }
        MotifKind.NONE -> return false
    }
}

// ---------------------------------------------------------------- dividers
/** A separator drawn in the identity's style (plain rule, brush stroke, slash, mist, diamond, wave). Direction-aware. */
@Composable fun DnaDivider(modifier: Modifier = Modifier) {
    val look = LocalLook.current; val cs = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val style = look.style.ornament.divider
    val line = cs.outlineVariant; val strong = cs.outline; val acc = cs.primary; val seed = look.seed
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val d = density; val w = size.width; val cy = size.height / 2f
        fun x(v: Float) = if (rtl) w - v else v
        when (style) {
            DividerStyle.PLAIN -> drawLine(Brush.horizontalGradient(listOf(line, Color.Transparent), x(0f), x(w)), Offset(0f, cy), Offset(w, cy), 1f * d)
            DividerStyle.BRUSH -> {
                val n = 28; val rng = Rng(seed); val top = ArrayList<Offset>(); val bot = ArrayList<Offset>()
                for (i in 0..n) {
                    val t = i / n.toFloat(); val th = 4.2f * d * Math.pow((1f - t).toDouble(), 0.8).toFloat() * (0.75f + 0.25f * rng.nextFloat()) + 0.6f * d
                    top.add(Offset(x(t * w), cy - th / 2f)); bot.add(Offset(x(t * w), cy + th / 2f))
                }
                val p = Path(); p.moveTo(top[0].x, top[0].y); for (o in top.drop(1)) p.lineTo(o.x, o.y); for (o in bot.reversed()) p.lineTo(o.x, o.y); p.close()
                drawPath(p, strong.copy(alpha = 0.75f))
            }
            DividerStyle.SLASH -> {
                for (k in 0..1) { val x0 = (k * 7f + 2f) * d; val p = Path(); p.moveTo(x(x0 + 4f * d), cy - 5f * d); p.lineTo(x(x0 + 7f * d), cy - 5f * d); p.lineTo(x(x0 + 3f * d), cy + 5f * d); p.lineTo(x(x0), cy + 5f * d); p.close(); drawPath(p, if (k == 0) acc else acc.copy(alpha = 0.5f)) }
                drawLine(Brush.horizontalGradient(listOf(line, Color.Transparent), x(22f * d), x(w)), Offset(x(22f * d), cy), Offset(x(w), cy), 1f * d)
            }
            DividerStyle.MIST -> {
                val b = Brush.horizontalGradient(listOf(Color.Transparent, acc.copy(alpha = 0.55f), Color.Transparent))
                drawLine(b, Offset(0f, cy), Offset(w, cy), 1.5f * d); drawLine(b, Offset(w * 0.2f, cy + 3f * d), Offset(w * 0.8f, cy + 3f * d), 1f * d, alpha = 0.5f)
            }
            DividerStyle.DIAMOND -> {
                val c = w / 2f; val r = 4.5f * d
                drawLine(Brush.horizontalGradient(listOf(Color.Transparent, line), 0f, c - r * 2), Offset(0f, cy), Offset(c - r * 2, cy), 1f * d)
                drawLine(Brush.horizontalGradient(listOf(line, Color.Transparent), c + r * 2, w), Offset(c + r * 2, cy), Offset(w, cy), 1f * d)
                val p = Path(); p.moveTo(c, cy - r); p.lineTo(c + r, cy); p.lineTo(c, cy + r); p.lineTo(c - r, cy); p.close(); drawPath(p, acc)
            }
            DividerStyle.WAVE -> {
                val p = Path(); val wl = 28f * d; val amp = 2.5f * d; var px = 0f; p.moveTo(x(0f), cy)
                while (px < w) { px += 2f * d; p.lineTo(x(px), cy + amp * sin(px / wl * 2f * PI.toFloat())) }
                drawPath(p, strong.copy(alpha = 0.7f), style = Stroke(1.2f * d, cap = StrokeCap.Round))
            }
        }
    }
}

// ---------------------------------------------------------------- atmosphere (glow, light shafts, vignette, particles)
/**
 * Soft light and particles of the identity. Draws nothing for plain styles or when effects are off. Particles move only at effects level 2,
 * when the style is animated, and the system allows motion (animations on, no battery saver). The animation state is read only while drawing.
 */
@Composable fun DnaAtmosphere(modifier: Modifier = Modifier, strength: Float = 1f) {
    val look = LocalLook.current; val fx = LocalFx.current; val a = look.style.atmosphere
    if (fx <= 0 || (a.glow <= 0.01f && a.rays <= 0.01f && a.vignette <= 0.01f && a.particles == ParticleKind.NONE)) return
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val glow = Color(look.scheme[Role.GLOW])
    val seed = look.seed
    val animate = fx >= 2 && look.style.motion.animated && a.particles != ParticleKind.NONE && rememberMotionAllowed()
    val phase: State<Float>? = if (animate) rememberInfiniteTransition(label = "dna").animateFloat(0f, 1f,
        infiniteRepeatable(tween((26000 / look.style.motion.speed.coerceAtLeast(0.2f)).toInt(), easing = LinearEasing), RepeatMode.Restart), label = "dnaPhase") else null
    val parts = androidx.compose.runtime.remember(a.particleCount, seed) { MotifGeometry.particles(a.particleCount, seed xor 0x1234L) }
    Canvas(modifier) {
        val d = density; val w = size.width; val h = size.height
        if (a.glow > 0.01f) {
            val cx = if (rtl) w * 0.2f else w * 0.8f
            drawRect(Brush.radialGradient(listOf(glow.copy(alpha = 0.34f * a.glow * strength), Color.Transparent), Offset(cx, 0f), max(w, h) * 0.85f))
        }
        if (a.rays > 0.01f) {
            val hh = h * 0.9f
            val r = MotifGeometry.rays(w, hh, if (rtl) 180f - a.angle else a.angle, 5, seed)
            val b = Brush.verticalGradient(listOf(glow.copy(alpha = 0.16f * a.rays * strength), Color.Transparent), 0f, hh)
            var i = 0
            while (i + 3 < r.size) { val p = Path(); p.moveTo(r[i], 0f); p.lineTo(r[i + 1], 0f); p.lineTo(r[i + 2], hh); p.lineTo(r[i + 3], hh); p.close(); drawPath(p, b); i += 4 }
        }
        if (a.vignette > 0.01f) drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = a.vignette * strength)), Offset(w / 2f, h / 2f), max(w, h) * 0.75f))
        if (a.particles != ParticleKind.NONE && a.particleCount > 0) {
            val t = phase?.value ?: 0.35f
            val embers = a.particles == ParticleKind.EMBERS
            for (i in 0 until a.particleCount) {
                val (px, py, al) = MotifGeometry.particleAt(parts, i, t, if (embers) 1.0f else 0.35f)
                val size01 = parts[i * 5 + 2]
                val r = (if (embers) 1f + 1.2f * size01 else 1f + 1.6f * size01) * d
                drawCircle(glow.copy(alpha = (if (embers) 0.85f else 0.55f) * al * strength), r, Offset(px * w, (if (embers) 1f - py else py) * h))
            }
        }
    }
}

/** Page background of a decorated identity: a faint hero gradient, then the atmosphere. Nothing is drawn for plain styles or when effects are off. */
@Composable fun DnaBackdrop(modifier: Modifier = Modifier) {
    val look = LocalLook.current
    if (LocalFx.current <= 0 || look.style.isPlain) return
    val top = Color(look.scheme[Role.HERO_TOP])
    Box(modifier.background(Brush.verticalGradient(listOf(top.copy(alpha = 0.7f), Color.Transparent)))) {
        DnaAtmosphere(Modifier.matchParentSize(), 0.8f)
    }
}

/** True when the current identity draws anything beyond plain surfaces (used to decide whether the page background must be transparent). */
@Composable fun dnaDecorated(): Boolean = LocalFx.current > 0 && !LocalLook.current.style.isPlain

/** Horizontal padding / vertical gap scale of the identity's density: compact 0.85, comfortable 1, airy 1.2. */
@Composable fun dnaDensity(): Float = when (LocalLook.current.style.layout.density) { SpacingDensity.COMPACT -> 0.85f; SpacingDensity.COMFORTABLE -> 1f; SpacingDensity.AIRY -> 1.2f }
