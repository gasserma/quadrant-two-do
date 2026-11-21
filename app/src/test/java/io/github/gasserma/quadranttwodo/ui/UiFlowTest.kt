package io.github.gasserma.quadranttwodo.ui

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import io.github.gasserma.quadranttwodo.AppContainer
import io.github.gasserma.quadranttwodo.TestSupport
import io.github.gasserma.quadranttwodo.core.ThemeMode
import io.github.gasserma.quadranttwodo.core.UrgencyScheme
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp")
class UiFlowTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var container: AppContainer
    private val repo get() = container.repository
    private val today = LocalDate.parse("2026-11-18")

    @Before fun setUp() {
        container = TestSupport.installContainer()
    }

    private fun seed(): Map<String, Long> = runBlocking {
        val a = repo.createTask("Alpha", 1, null, "")
        repo.addLogEntry(a, LocalDate.parse("2026-11-16")) // due 11-17 → overdue
        val b = repo.createTask("Bravo", 7, null, "")
        repo.addLogEntry(b, today) // due in 7 days
        val c = repo.createTask("Charlie", 30, null, "") // never done: due 30 days after creation
        mapOf("Alpha" to a, "Bravo" to b, "Charlie" to c)
    }

    private fun start() = rule.setContent { QuadrantTwoDoRoot(container) }

    private fun waitFor(tag: String): SemanticsNodeInteraction {
        rule.waitUntilAtLeastOneExists(hasTestTag(tag), 5_000)
        return rule.onNodeWithTag(tag)
    }

    private fun waitForText(text: String) {
        rule.waitUntilAtLeastOneExists(hasText(text), 5_000)
    }

    private fun y(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    private fun lastPerformed(id: Long) = runBlocking { repo.observeTasks().first().first { it.id == id }.lastPerformed }

    @Test fun listShowsSortedRowsWithDueText() {
        seed()
        start()
        waitFor("task:Alpha")
        rule.onNodeWithText("due yesterday").assertIsDisplayed()
        rule.onNodeWithText("due in 7 days").assertIsDisplayed()
        rule.onNodeWithText("due in 30 days").assertIsDisplayed()
        assertThat(y("task:Alpha")).isLessThan(y("task:Bravo"))
        assertThat(y("task:Bravo")).isLessThan(y("task:Charlie"))
    }

    @Test fun doneButtonLogsAfterConfirmation() {
        val ids = seed()
        start()
        waitFor("done:Alpha").performClick()
        waitFor("confirm").performClick()
        waitForText("due tomorrow")
        assertThat(lastPerformed(ids.getValue("Alpha"))).isEqualTo(today)
    }

    @Test fun cancellingTheConfirmationLogsNothing() {
        val ids = seed()
        start()
        waitFor("done:Charlie").performClick()
        rule.onNodeWithText("Log 1 task as done today?").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()
        assertThat(rule.onAllNodes(hasTestTag("confirm")).fetchSemanticsNodes()).isEmpty()
        assertThat(lastPerformed(ids.getValue("Charlie"))).isNull()
    }

    @Test fun longPressSelectsAndLogsSeveral() {
        val ids = seed()
        start()
        waitFor("task:Alpha").performTouchInput { longClick() }
        rule.onNodeWithTag("task:Charlie").performClick() // adds to selection
        rule.onNodeWithText("2 tasks selected").assertIsDisplayed()
        rule.onNodeWithTag("logSelected").performClick()
        waitFor("confirm").performClick()
        rule.waitUntil(5_000) { lastPerformed(ids.getValue("Charlie")) == today }
        assertThat(lastPerformed(ids.getValue("Alpha"))).isEqualTo(today)
        assertThat(lastPerformed(ids.getValue("Bravo"))).isEqualTo(today) // already today
    }

    @Test fun longPressSelectsAndDeletes() {
        seed()
        start()
        waitFor("task:Bravo").performTouchInput { longClick() }
        rule.onNodeWithTag("deleteSelected").performClick()
        waitFor("confirm").performClick()
        rule.waitUntil(5_000) { runBlocking { repo.observeTasks().first().none { it.name == "Bravo" } } }
    }

    @Test fun newTaskIsSavedOnBack() {
        start()
        waitFor("newTask").performClick()
        waitFor("nameField").performTextInput("Water plants")
        // A new task has no delete (trash can) button; back is the way out.
        assertThat(rule.onAllNodes(hasTestTag("deleteTask")).fetchSemanticsNodes()).isEmpty()
        // ...and no history yet: no Last done, no Log.
        assertThat(rule.onAllNodes(hasTestTag("lastDoneField")).fetchSemanticsNodes()).isEmpty()
        assertThat(rule.onAllNodes(hasTestTag("addLog")).fetchSemanticsNodes()).isEmpty()
        // The default period is 7 days, shown in days.
        rule.onNodeWithTag("periodField").assertTextContains("7 days", substring = true)

        rule.onNodeWithTag("back").performClick()
        waitFor("task:Water plants")
        val t = runBlocking { repo.observeTasks().first().single() }
        assertThat(t.name).isEqualTo("Water plants")
        assertThat(t.period).isEqualTo(7)
        assertThat(t.created).isEqualTo(today)
    }

    @Test fun existingTaskHasDeleteButton() {
        seed()
        start()
        waitFor("task:Bravo").performClick()
        waitFor("deleteTask").assertIsDisplayed()
    }

    @Test fun editScreenAddsAndDeletesLogEntries() {
        val ids = seed()
        start()
        waitFor("task:Charlie").performClick()
        waitFor("addLog").performClick()
        waitFor("addLogToday").performClick()
        waitFor("log:$today")
        assertThat(lastPerformed(ids.getValue("Charlie"))).isEqualTo(today)

        rule.onNodeWithTag("log:$today").performTouchInput { longClick() }
        rule.onNodeWithText("1 log entry selected").assertIsDisplayed()
        rule.onNodeWithContentDescription("Delete log entries").performClick()
        waitFor("confirm").performClick()
        waitForText("No entries yet")
        assertThat(lastPerformed(ids.getValue("Charlie"))).isNull()
    }

    /** Tags from an imported backup stay hidden: no tabs, no tag UI. */
    @Test fun importedTagsAreNotShown() {
        runBlocking { importBackupWithTag(taskName = "Tagged", tagName = "Home") }
        start()
        waitFor("task:Tagged")
        assertThat(rule.onAllNodes(hasText("Home")).fetchSemanticsNodes()).isEmpty()
        assertThat(rule.onAllNodes(hasContentDescription("Manage tags")).fetchSemanticsNodes()).isEmpty()
        rule.onNodeWithTag("task:Tagged").performClick()
        waitFor("nameField")
        assertThat(rule.onAllNodes(hasText("TAGS")).fetchSemanticsNodes()).isEmpty()
    }

    /** Writes a minimal backup file with one tagged task and imports it. */
    private suspend fun importBackupWithTag(taskName: String, tagName: String) {
        val file = File.createTempFile("tagged", ".db").also { it.delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE tasks (_id integer primary key autoincrement, name text not null, " +
                    "period integer not null, firstdue text, lastperformed text, lastnotified text, " +
                    "created text not null default '', details text not null, notifications_enabled integer not null, " +
                    "notifications_time text, notifications_period integer)",
            )
            db.execSQL("CREATE TABLE log (_id integer primary key autoincrement, taskid integer not null, entrydate text not null default '', note text not null)")
            db.execSQL("CREATE TABLE tags (_id integer primary key autoincrement, tagname text not null, position integer not null)")
            db.execSQL("CREATE TABLE tasktags (_id integer primary key autoincrement, taskid integer not null, tagid integer not null)")
            db.execSQL("INSERT INTO tasks (_id, name, period, created, details, notifications_enabled) VALUES (1, ?, 7, '2026-11-01', '', 0)", arrayOf(taskName))
            db.execSQL("INSERT INTO tags (_id, tagname, position) VALUES (1, ?, 0)", arrayOf(tagName))
            db.execSQL("INSERT INTO tasktags (_id, taskid, tagid) VALUES (1, 1, 1)")
            db.version = 8
        }
        repo.importBackup(file)
    }

    @Test fun settingsScreenSavesChoicesAndReturns() {
        seed()
        start()
        waitFor("overflow").performClick()
        waitFor("settings").performClick()
        waitFor("theme:dark").performClick()
        waitFor("scheme:berry").performClick()
        // Saving is asynchronous; the screen shows the choice once it's stored.
        rule.waitUntilAtLeastOneExists(hasTestTag("theme:dark") and isSelected(), 5_000)
        rule.waitUntilAtLeastOneExists(hasTestTag("scheme:berry") and isSelected(), 5_000)
        val saved = runBlocking { container.settings.settings.first() }
        assertThat(saved.theme).isEqualTo(ThemeMode.Dark)
        assertThat(saved.scheme).isEqualTo(UrgencyScheme.Berry)
        waitFor("back").performClick()
        waitFor("task:Alpha").assertIsDisplayed()
    }
}
