package io.github.gasserma.quadranttwodo.core

import kotlin.math.pow

/** The WCAG AA minimum contrast for normal-size text. */
const val MIN_TEXT_CONTRAST = 4.5

/** Relative luminance of an opaque ARGB colour, 0 (black) to 1 (white), as defined by WCAG. */
fun luminance(argb: Int): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

/** WCAG contrast ratio between two opaque colours, from 1 (identical) to 21 (black on white). */
fun contrastRatio(a: Int, b: Int): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}
