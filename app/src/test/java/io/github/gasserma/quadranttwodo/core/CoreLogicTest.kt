package io.github.gasserma.quadranttwodo.core

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CoreLogicTest {

    private fun d(s: String): LocalDate = LocalDate.parse(s)
    private fun task(
        name: String = "t",
        period: Int = 7,
        firstDue: String? = null,
        last: String? = null,
        created: String? = "2026-01-01",
        id: Long = 0,
    ) = Task(id, name, period, firstDue?.let(::d), last?.let(::d), created?.let(::d))

    // nextDue

    @Test fun nextDue_prefersLastPerformedPlusPeriod() {
        assertThat(task(last = "2026-03-01", firstDue = "2026-05-05").nextDue())
            .isEqualTo(d("2026-03-08"))
    }

    @Test fun nextDue_thenFirstDue() {
        assertThat(task(firstDue = "2026-05-05").nextDue()).isEqualTo(d("2026-05-05"))
    }

    @Test fun nextDue_thenCreatedPlusPeriod() {
        assertThat(task(created = "2026-01-01").nextDue()).isEqualTo(d("2026-01-08"))
    }

    @Test fun nextDue_nullOnlyWithoutAnyDate() {
        assertThat(task(created = null).nextDue()).isNull()
    }

    @Test fun periodMustBeAtLeastOneDay() {
        val e = runCatching { task(period = 0) }.exceptionOrNull()
        assertThat(e).isInstanceOf(IllegalArgumentException::class.java)
    }

    // urgency

    @Test fun urgency_zeroAtSixOnDueDay() {
        val t = task(last = "2026-03-01") // due 2026-03-08
        assertThat(t.urgency(LocalDateTime.parse("2026-03-08T06:00"))).isWithin(1e-12).of(0.0)
    }

    @Test fun urgency_minusOneJustDone_plusOneAPeriodLate() {
        val t = task(last = "2026-03-01")
        assertThat(t.urgency(LocalDateTime.parse("2026-03-01T06:00"))).isWithin(1e-12).of(-1.0)
        assertThat(t.urgency(LocalDateTime.parse("2026-03-15T06:00"))).isWithin(1e-12).of(1.0)
    }

    @Test fun urgency_isFractionalThroughTheDay() {
        val t = task(period = 1, last = "2026-03-01") // due 03-02 06:00
        assertThat(t.urgency(LocalDateTime.parse("2026-03-02T18:00"))).isWithin(1e-12).of(0.5)
    }

    @Test fun urgency_nullWithoutDueDate() {
        assertThat(task(created = null).urgency(LocalDateTime.now())).isNull()
    }

    // sorting

    private val now = LocalDateTime.parse("2026-06-01T12:00")

    @Test fun sort_mostUrgentFirst_noDueDateLast() {
        val a = task("A", period = 10, last = "2026-05-20") // due 05-30, u ≈ 0.225
        val b = task("B", period = 2, last = "2026-05-29")  // due 05-31, u ≈ 0.625
        val c = task("C", period = 100, last = "2026-05-01") // due 08-09, u < 0
        val none = task("None", created = null)             // legacy task with no dates
        assertThat(rankTasks(listOf(none, c, a, b), now).map { it.task.name })
            .containsExactly("B", "A", "C", "None").inOrder()
    }

    @Test fun sort_urgencyTie_brokenByEarlierDue_thenName() {
        // Both exactly one period overdue (u = 1), different due dates.
        val n = LocalDateTime.parse("2026-06-01T06:00")
        val x = task("X", period = 1, last = "2026-05-30") // due 05-31
        val y = task("Y", period = 2, last = "2026-05-28") // due 05-30
        assertThat(rankTasks(listOf(x, y), n).map { it.task.name }).containsExactly("Y", "X").inOrder()
        val p = task("b-task", period = 1, last = "2026-05-30")
        val q = task("A-task", period = 1, last = "2026-05-30")
        assertThat(rankTasks(listOf(p, q), n).map { it.task.name }).containsExactly("A-task", "b-task").inOrder()
    }

    @Test fun sort_namesAreCaseInsensitive() {
        val tasks = listOf("Zebra", "apple", "Mango").map { task(it, last = "2026-05-01") }
        assertThat(rankTasks(tasks, now).map { it.task.name }).containsExactly("apple", "Mango", "Zebra").inOrder()
    }

    // colours and the quadrant mark

    @Test fun colour_coolToWarmSweep() {
        val dark = Palette.Dark
        val dusk = UrgencyScheme.Dusk
        assertThat(urgencyColor(-1.0, dark, dusk)).isEqualTo(0xFF6BEDD7.toInt()) // just done: teal
        assertThat(urgencyColor(0.0, dark, dusk)).isEqualTo(0xFFEDC26B.toInt())  // due: amber
        assertThat(urgencyColor(1.0, dark, dusk)).isEqualTo(0xFFED766B.toInt())  // a period late: coral
        assertThat(urgencyColor(5.0, dark, dusk)).isEqualTo(urgencyColor(1.0, dark, dusk))   // clamped
        assertThat(urgencyColor(-3.0, dark, dusk)).isEqualTo(urgencyColor(-1.0, dark, dusk)) // clamped
    }

    @Test fun colour_lightThemeIsDarkerThanDark() {
        for (scheme in UrgencyScheme.entries) {
            for (u in listOf(-1.0, 0.0, 1.0)) {
                assertWithMessage("%s at %s", scheme, u)
                    .that(luminance(urgencyColor(u, Palette.Light, scheme)))
                    .isLessThan(luminance(urgencyColor(u, Palette.Dark, scheme)))
            }
        }
    }

    @Test fun colour_greyEndFadesInsteadOfChangingHue() {
        // Blue and orange: "due" is grey, and halfway to it the colour is still a (paler) blue.
        val dark = Palette.Dark
        val due = urgencyColor(0.0, dark, UrgencyScheme.BlueOrange)
        assertThat(channels(due).let { it.max() - it.min() }).isAtMost(2)
        val (r, g, b) = channels(urgencyColor(-0.5, dark, UrgencyScheme.BlueOrange))
        assertThat(b).isGreaterThan(g)
        assertThat(g).isGreaterThan(r)
    }

    @Test fun colour_noDueDateIsMuted() {
        for (scheme in UrgencyScheme.entries) {
            assertThat(urgencyColor(null, Palette.Dark, scheme)).isEqualTo(Palette.Dark.textMuted)
            assertThat(urgencyColor(null, Palette.Light, scheme)).isEqualTo(Palette.Light.textMuted)
        }
    }

    /** Every scheme's urgency colours, and the muted text and accent, must read as text: WCAG AA. */
    @Test fun colour_everySchemeReadableOnItsBackgroundInBothThemes() {
        for (palette in listOf(Palette.Dark, Palette.Light)) {
            for (scheme in UrgencyScheme.entries) {
                val samples = (-20..20).map { urgencyColor(it / 20.0, palette, scheme) } + palette.textMuted + palette.accent
                for (c in samples) {
                    assertWithMessage("%s: %s on %s", scheme, Integer.toHexString(c), Integer.toHexString(palette.background))
                        .that(contrast(c, palette.background)).isAtLeast(4.5)
                }
            }
        }
    }

    @Test fun schemes_haveDistinctEndsAndStableIds() {
        for (scheme in UrgencyScheme.entries) {
            assertWithMessage("%s", scheme)
                .that(urgencyColor(-1.0, Palette.Dark, scheme)).isNotEqualTo(urgencyColor(1.0, Palette.Dark, scheme))
            assertThat(UrgencyScheme.fromId(scheme.id)).isEqualTo(scheme)
        }
        assertThat(UrgencyScheme.entries.map { it.id }.toSet()).hasSize(UrgencyScheme.entries.size)
        assertThat(UrgencyScheme.fromId("no-such-scheme")).isEqualTo(UrgencyScheme.Default)
        assertThat(UrgencyScheme.fromId(null)).isEqualTo(UrgencyScheme.Default)
    }

    @Test fun themeMode_resolvesAgainstTheSystem() {
        assertThat(ThemeMode.System.isDark(systemDark = true)).isTrue()
        assertThat(ThemeMode.System.isDark(systemDark = false)).isFalse()
        assertThat(ThemeMode.Light.isDark(systemDark = true)).isFalse()
        assertThat(ThemeMode.Dark.isDark(systemDark = false)).isTrue()
        assertThat(ThemeMode.fromId("dark")).isEqualTo(ThemeMode.Dark)
        assertThat(ThemeMode.fromId("garbage")).isEqualTo(ThemeMode.System)
    }

    private fun channels(argb: Int) = listOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    // An independent implementation of the WCAG formulas, so the test doesn't just trust core/Contrast.kt.
    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    @Test fun hsvToArgb_primaries() {
        assertThat(hsvToArgb(0f, 1f, 1f)).isEqualTo(0xFFFF0000.toInt())
        assertThat(hsvToArgb(120f, 1f, 1f)).isEqualTo(0xFF00FF00.toInt())
        assertThat(hsvToArgb(240f, 1f, 1f)).isEqualTo(0xFF0000FF.toInt())
        assertThat(hsvToArgb(0f, 0f, 0.5f)).isEqualTo(0xFF808080.toInt())
    }

    @Test fun quadrants_fillOnePerQuarterPeriod() {
        assertThat(filledQuadrants(-1.0)).isEqualTo(0)  // just done: empty
        assertThat(filledQuadrants(-0.8)).isEqualTo(0)
        assertThat(filledQuadrants(-0.75)).isEqualTo(1) // a quarter of the way
        assertThat(filledQuadrants(-0.5)).isEqualTo(2)
        assertThat(filledQuadrants(-0.01)).isEqualTo(3)
        assertThat(filledQuadrants(0.0)).isEqualTo(4)   // due: all four, quadrant two last
        assertThat(filledQuadrants(3.0)).isEqualTo(4)   // overdue stays full
        assertThat(filledQuadrants(-5.0)).isEqualTo(0)  // logged ahead
        assertThat(filledQuadrants(null)).isEqualTo(0)
    }

    // wording

    @Test fun dayCounts_showAsYearsWeeksDays() {
        assertThat(formatDays(401)).isEqualTo("1y 5w 1d")
        assertThat(formatDays(365)).isEqualTo("1y")
        assertThat(formatDays(366)).isEqualTo("1y 1d")
        assertThat(formatDays(372)).isEqualTo("1y 1w")
        assertThat(formatDays(322)).isEqualTo("46w")
        assertThat(formatDays(9)).isEqualTo("1w 2d")
        assertThat(formatDays(3)).isEqualTo("3d")
        assertThat(formatDays(0)).isEqualTo("0d")
        assertThat(formatDays(1095)).isEqualTo("3y")
    }

    @Test fun yearsWeeksDays_roundTrips() {
        assertThat(YearsWeeksDays.of(401)).isEqualTo(YearsWeeksDays(1, 5, 1))
        for (n in 0..2000) assertThat(YearsWeeksDays.of(n).totalDays).isEqualTo(n)
        assertThat(YearsWeeksDays(0, 0, 10).totalDays).isEqualTo(10) // boxes needn't be normalised
    }

    @Test fun dueLabel_wording() {
        val today = d("2026-06-01")
        fun due(last: String, period: Int = 1) = task(period = period, last = last).dueLabel(today)
        assertThat(due("2026-05-31")).isEqualTo("due today")
        assertThat(due("2026-06-01")).isEqualTo("due tomorrow")
        assertThat(due("2026-05-30")).isEqualTo("due yesterday")
        assertThat(due("2026-06-01", period = 9)).isEqualTo("due in 1w 2d")
        assertThat(due("2026-05-26")).isEqualTo("due 5d ago")
        assertThat(due("2025-04-25")).isEqualTo("due 1y 5w 1d ago") // due 2025-04-26: 401 days overdue
        // Never done: due one period after creation.
        assertThat(task(period = 7, created = "2026-05-30").dueLabel(today)).isEqualTo("due in 5d")
        // No due date at all (legacy data): no label.
        assertThat(task(created = null).dueLabel(today)).isEmpty()
    }

    @Test fun logTiming_regular() {
        val dates = listOf("2026-01-15", "2026-01-01", "2026-01-08", "2026-01-24").map(::d)
        assertThat(logTimings(dates, 7).map { it.format() })
            .containsExactly("on schedule", "baseline", "on schedule", "2d late").inOrder()
        assertThat(logTimings(listOf(d("2026-01-01"), d("2026-01-05")), 7).map { it.format() })
            .containsExactly("baseline", "3d early").inOrder()
        assertThat(logTimings(listOf(d("2026-01-01"), d("2026-03-01")), 7).map { it.format() })
            .containsExactly("baseline", "7w 3d late").inOrder()
    }

    @Test fun logTiming_sameDayEntriesAreEarly() {
        val dates = listOf("2026-01-01", "2026-01-01").map(::d)
        assertThat(logTimings(dates, 1).map { it.format() })
            .containsExactly("baseline", "1d early").inOrder()
    }

    @Test fun logTiming_undatedEntriesAreSkipped() {
        // Imported entries can have a blank date; they get no timing and don't break the others.
        val dates = listOf(d("2026-01-08"), null, d("2026-01-01"))
        assertThat(logTimings(dates, 7).map { it.format() })
            .containsExactly("on schedule", "", "baseline").inOrder()
    }

    @Test fun longDate() {
        assertThat(d("2026-11-18").longFormat()).isEqualTo("Wednesday, November 18th 2026")
        assertThat(d("2026-11-01").longFormat()).isEqualTo("Sunday, November 1st 2026")
        assertThat(d("2026-11-12").longFormat()).isEqualTo("Thursday, November 12th 2026")
    }
}
