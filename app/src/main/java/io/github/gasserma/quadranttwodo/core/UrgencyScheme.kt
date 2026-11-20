package io.github.gasserma.quadranttwodo.core

/**
 * One colour of a scheme: a hue in degrees and a saturation, 0..1. Brightness isn't part of it:
 * that comes from the theme (see Palette) and is adjusted for contrast.
 */
data class Hue(val degrees: Float, val saturation: Float)

/**
 * The colour schemes for urgency, chosen in Settings. Each goes from [justDone] (u = -1) through
 * [due] (u = 0) to [late] (u ≥ 1, a whole period overdue). [id] is what Settings stores, so it
 * must never change; the order here is the order in Settings.
 */
enum class UrgencyScheme(val id: String, val justDone: Hue, val due: Hue, val late: Hue) {
    /** The original: teal → amber → coral. */
    Dusk("dusk", Hue(170f, 0.55f), Hue(40f, 0.55f), Hue(5f, 0.55f)),

    /** Green → amber → red. */
    TrafficLight("traffic_light", Hue(125f, 0.60f), Hue(45f, 0.65f), Hue(0f, 0.65f)),

    /**
     * Blue → grey → orange. Blue and orange stay distinct for the common kinds of colour
     * blindness, and the grey middle avoids a hue that could be confused with either.
     */
    BlueOrange("blue_orange", Hue(212f, 0.60f), Hue(212f, 0f), Hue(28f, 0.70f)),

    /** Gold → orange → magenta. */
    Sunset("sunset", Hue(48f, 0.60f), Hue(22f, 0.65f), Hue(330f, 0.55f)),

    /** Green → yellow-green → rust. */
    Forest("forest", Hue(145f, 0.50f), Hue(75f, 0.55f), Hue(18f, 0.60f)),

    /** Aqua → blue → indigo: calm, no alarm colours. */
    Ocean("ocean", Hue(180f, 0.50f), Hue(220f, 0.55f), Hue(260f, 0.50f)),

    /** Violet → pink → crimson. */
    Berry("berry", Hue(265f, 0.45f), Hue(320f, 0.50f), Hue(350f, 0.65f)),

    /** Yellow → orange → red. */
    Fire("fire", Hue(55f, 0.60f), Hue(30f, 0.70f), Hue(5f, 0.75f)),

    /** Teal only, getting stronger: urgency shown by intensity rather than hue. */
    Monochrome("monochrome", Hue(172f, 0.08f), Hue(172f, 0.45f), Hue(172f, 0.85f)),
    ;

    companion object {
        val Default = Dusk

        fun fromId(id: String?): UrgencyScheme = entries.firstOrNull { it.id == id } ?: Default
    }
}
