package io.github.gasserma.quadranttwodo.core

import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Urgency colours sweep through a scheme's three hues (see UrgencyScheme): "just done" at u = -1,
 * "due" at 0, "late" at 1 and beyond. The theme (see Palette) sets the starting brightness, and each
 * colour is then made just dark or bright enough to read as text on the theme's background.
 * Colours are plain ARGB ints so the widget (RemoteViews) can use them too.
 */

/** A little above the WCAG minimum, so rounding never lands a colour just under it. */
private const val TARGET_CONTRAST = MIN_TEXT_CONTRAST + 0.1

/**
 * The urgency colour for [u] in [scheme], readable on [palette]'s background. Tasks with no due
 * date (legacy data only) use the palette's muted text colour.
 */
fun urgencyColor(u: Double?, palette: Palette, scheme: UrgencyScheme): Int {
    if (u == null) return palette.textMuted
    val t = u.coerceIn(-1.0, 1.0).toFloat()
    val hue = if (t <= 0f) blend(scheme.due, scheme.justDone, -t) else blend(scheme.due, scheme.late, t)
    return readable(hue, palette)
}

/**
 * Blends two scheme colours, turning the shorter way round the colour wheel. A grey end has no
 * real hue, so it takes the other end's: blue → grey fades the blue rather than passing other hues.
 */
private fun blend(a: Hue, b: Hue, t: Float): Hue {
    val fromDegrees = if (a.saturation < GREY) b.degrees else a.degrees
    val toDegrees = if (b.saturation < GREY) a.degrees else b.degrees
    var delta = (toDegrees - fromDegrees) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return Hue(fromDegrees + delta * t, lerp(a.saturation, b.saturation, t))
}

private const val GREY = 0.1f

/**
 * [hue] at the palette's brightness, then adjusted until it reaches [TARGET_CONTRAST] on the
 * background: darker on a light background; brighter, then less saturated, on a dark one.
 */
private fun readable(hue: Hue, palette: Palette): Int {
    val background = palette.background
    var s = (hue.saturation * palette.urgencySaturationScale).coerceAtMost(1f)
    var v = palette.urgencyValue
    var color = hsvToArgb(hue.degrees, s, v)
    fun short() = contrastRatio(color, background) < TARGET_CONTRAST
    if (palette.isDark) {
        while (short() && v < 1f) { v = (v + STEP).coerceAtMost(1f); color = hsvToArgb(hue.degrees, s, v) }
        while (short() && s > 0f) { s = (s - STEP).coerceAtLeast(0f); color = hsvToArgb(hue.degrees, s, v) }
    } else {
        while (short() && v > 0f) { v = (v - STEP).coerceAtLeast(0f); color = hsvToArgb(hue.degrees, s, v) }
    }
    return color
}

private const val STEP = 0.01f

/** The urgency mark is a 2×2 grid, after the Eisenhower matrix. */
const val QUADRANTS = 4

/**
 * How many cells of the urgency mark are filled: one more for each quarter of the period since
 * the task was last done, so all four when it falls due. Overdue tasks stay full (the urgency
 * colour shows how late). 0 when there is no due date.
 */
fun filledQuadrants(u: Double?): Int =
    if (u == null) 0 else floor((u + 1) * QUADRANTS).toInt().coerceIn(0, QUADRANTS)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** HSV (hue in degrees, saturation and value in 0..1) to opaque ARGB. */
fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val h = ((hue % 360f) + 360f) % 360f / 60f
    val sector = floor(h).toInt()
    val f = h - sector
    val p = v * (1 - s)
    val q = v * (1 - s * f)
    val t = v * (1 - s * (1 - f))
    val (r, g, b) = when (sector) {
        0 -> Triple(v, t, p)
        1 -> Triple(q, v, p)
        2 -> Triple(p, v, t)
        3 -> Triple(p, q, v)
        4 -> Triple(t, p, v)
        else -> Triple(v, p, q)
    }
    fun byte(x: Float) = (x * 255f).roundToInt()
    return (0xFF shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
}
