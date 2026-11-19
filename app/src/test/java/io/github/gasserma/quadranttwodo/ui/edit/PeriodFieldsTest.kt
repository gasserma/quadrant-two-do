package io.github.gasserma.quadranttwodo.ui.edit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.gasserma.quadranttwodo.ui.theme.QuadrantTwoDoTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The period dialog's contents: years / weeks / days boxes and the total in days, so the user
 * can check what they typed.
 * (Tested outside the dialog window; see [PeriodFields].)
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h914dp")
class PeriodFieldsTest {

    @get:Rule val rule = createComposeRule()

    private fun show(period: Int): PeriodInput {
        val input = PeriodInput.of(period)
        rule.setContent { QuadrantTwoDoTheme { PeriodFields(input) } }
        return input
    }

    @Test fun prefillsFromThePeriod() {
        show(401)
        rule.onNodeWithTag("periodYears").assertTextEquals("1")
        rule.onNodeWithTag("periodWeeks").assertTextEquals("5")
        rule.onNodeWithTag("periodDays").assertTextEquals("1")
        rule.onNodeWithText("= 401 days").assertIsDisplayed()
    }

    @Test fun zeroPartsStartEmpty() {
        val input = show(7)
        assertThat(input.years).isEmpty()
        assertThat(input.weeks).isEqualTo("1")
        assertThat(input.days).isEmpty()
    }

    @Test fun typingUpdatesTheTotal() {
        val input = show(7)
        rule.onNodeWithTag("periodYears").performTextInput("2")
        rule.onNodeWithTag("periodDays").performTextInput("3")
        rule.onNodeWithText("= 740 days").assertIsDisplayed()
        assertThat(input.totalDays).isEqualTo(2 * 365 + 7 + 3)
        assertThat(input.isValid).isTrue()
    }

    @Test fun boxesNeedNotBeNormalised() {
        val input = show(7)
        rule.onNodeWithTag("periodWeeks").performTextClearance()
        rule.onNodeWithTag("periodDays").performTextInput("10")
        rule.onNodeWithText("= 10 days").assertIsDisplayed()
        assertThat(input.totalDays).isEqualTo(10)
    }

    @Test fun onlyDigitsAreAccepted() {
        val input = show(7)
        rule.onNodeWithTag("periodDays").performTextInput("4x-")
        assertThat(input.days).isEqualTo("4")
    }

    @Test fun zeroIsRejected() {
        val input = show(7)
        rule.onNodeWithTag("periodWeeks").performTextClearance()
        rule.onNodeWithText("The period must be at least one day").assertIsDisplayed()
        assertThat(input.isValid).isFalse()
    }
}
