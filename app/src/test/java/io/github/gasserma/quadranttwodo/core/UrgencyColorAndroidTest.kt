package io.github.gasserma.quadranttwodo.core

import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The pure-Kotlin HSV conversion (used by the widget too) agrees with Android's own. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UrgencyColorAndroidTest {

    @Test fun matchesAndroidHsvToColor() {
        for (hue in 0 until 360 step 5) for (s in listOf(0f, 0.3f, 0.55f, 1f)) for (v in listOf(0.2f, 0.6f, 0.93f, 1f)) {
            assertThat(hsvToArgb(hue.toFloat(), s, v)).isEqualTo(Color.HSVToColor(floatArrayOf(hue.toFloat(), s, v)))
        }
    }
}
