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

/**
 * How full the progress ring is: 0 just after doing the task, 1 when it falls due.
 * Overdue tasks ([isOverdue]) are drawn as a solid dot instead.
 */
fun urgencyProgress(u: Double?): Float = if (u == null) 0f else (u + 1).coerceIn(0.0, 1.0).toFloat()

fun isOverdue(u: Double?): Boolean = u != null && u > 0

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
