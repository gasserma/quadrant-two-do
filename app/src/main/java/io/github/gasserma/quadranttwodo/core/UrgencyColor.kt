package io.github.gasserma.quadranttwodo.core

import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Urgency is shown with a cool-to-warm sweep: teal when just done, amber when due, coral once a
 * whole period late. Colours are plain ARGB ints so the widget (RemoteViews) can use them too.
 */

/** Hue for each anchor of the sweep, in degrees. */
private const val HUE_JUST_DONE = 170f // teal
private const val HUE_DUE = 40f // amber
private const val HUE_LATE = 5f // coral

private const val SATURATION = 0.55f
private const val VALUE = 0.93f

/** Colour for text and marks when a task has no due date (legacy data only). */
const val NO_URGENCY_COLOR: Int = Palette.INK_MUTED

/** The urgency colour: u = -1 (just done) teal, 0 (due) amber, ≥ 1 (a period late) coral. */
fun urgencyColor(u: Double?): Int {
    if (u == null) return NO_URGENCY_COLOR
    val t = u.coerceIn(-1.0, 1.0).toFloat()
    val hue = if (t <= 0f) lerp(HUE_DUE, HUE_JUST_DONE, -t) else lerp(HUE_DUE, HUE_LATE, t)
    return hsvToArgb(hue, SATURATION, VALUE)
}

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
