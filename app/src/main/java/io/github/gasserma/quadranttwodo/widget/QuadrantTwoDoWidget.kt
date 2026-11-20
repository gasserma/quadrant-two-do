package io.github.gasserma.quadranttwodo.widget

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.gasserma.quadranttwodo.MainActivity
import io.github.gasserma.quadranttwodo.R
import io.github.gasserma.quadranttwodo.appContainer
import io.github.gasserma.quadranttwodo.core.Palette
import io.github.gasserma.quadranttwodo.core.Settings
import io.github.gasserma.quadranttwodo.core.ThemeMode
import io.github.gasserma.quadranttwodo.core.urgencyColor
import java.time.LocalDateTime
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * The home-screen widget: a scrolling list of every task, sorted like the app, with the due text
 * coloured by urgency. Tapping anywhere opens the app.
 *
 * While a widget session is running, it redraws on every change to the tasks or the settings,
 * because it collects them inside its content. [WidgetRefresher] starts new sessions: on data
 * changes, every 4 hours and just after 06:00 (when tasks become due).
 */
class QuadrantTwoDoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val empty = context.getString(R.string.list_empty)
        val snapshots = combine(container.repository.observeTasks(), container.settings.settings) { tasks, settings ->
            WidgetSnapshot(buildWidgetRows(tasks, LocalDateTime.now(container.clock)), WidgetStyle(settings))
        }
        // Read once up front so the first frame already has the tasks.
        val initial = snapshots.first()
        provideContent {
            val snapshot by snapshots.collectAsState(initial)
            WidgetContent(snapshot.rows, empty, snapshot.style)
        }
    }
}

/** What one frame of the widget shows. */
private class WidgetSnapshot(val rows: List<WidgetRow>, val style: WidgetStyle)

class QuadrantTwoDoWidgetReceiverSmall : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuadrantTwoDoWidget()
}

class QuadrantTwoDoWidgetReceiverMedium : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuadrantTwoDoWidget()
}

/**
 * The widget's colours for the current [settings]. With the theme set to follow the system, each
 * colour has a light and a dark version and the launcher picks one; otherwise it's fixed.
 */
class WidgetStyle(private val settings: Settings = Settings()) {
    private fun themed(pick: (Palette) -> Color): ColorProvider = when (settings.theme) {
        ThemeMode.System -> DayNightColorProvider(day = pick(Palette.Light), night = pick(Palette.Dark))
        ThemeMode.Light -> ColorProvider(pick(Palette.Light))
        ThemeMode.Dark -> ColorProvider(pick(Palette.Dark))
    }

    /** Main text. */
    val text: ColorProvider = themed { Color(it.text) }

    /** The app's background behind the list, at the chosen opacity. */
    val panel: ColorProvider = themed { Color(it.background).copy(alpha = settings.widgetOpacity / 100f) }

    /** A due text's urgency colour in the chosen scheme. */
    fun due(urgency: Double?): ColorProvider = themed { Color(urgencyColor(urgency, it, settings.scheme)) }
}

/**
 * Layout. Like the system's own widgets (Calendar, Gmail), the panel (see [WidgetStyle]) fills the
 * whole space the launcher gives the widget, with the system's widget corner radius (Android 12+),
 * so it lines up with widgets next to it. Inside, the list has 14dp/10dp padding, enough to keep
 * text clear of the rounded corners. Rows are compact so a 2-row widget fits about seven tasks:
 * 3dp vertical padding, a 16sp sans-serif-light name and a 13sp due text 6dp to its right.
 */
// The system corner radius exists only on Android 12+; Glance ignores cornerRadius below that,
// so older phones simply get square corners.
@SuppressLint("InlinedApi")
@Composable
fun WidgetContent(rows: List<WidgetRow>, emptyText: String, style: WidgetStyle = WidgetStyle()) {
    val open = actionStartActivity<MainActivity>()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .background(style.panel)
            .clickable(open),
    ) {
        if (rows.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyText, style = TextStyle(color = style.text, fontSize = 18.sp))
            }
        } else {
            val family = FontFamily("sans-serif-light")
            LazyColumn(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
                items(rows, itemId = { it.id }) { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable(open)
                            .semantics { testTag = "row:${row.name}" },
                    ) {
                        Text(
                            text = row.name,
                            maxLines = 1,
                            style = TextStyle(color = style.text, fontSize = 16.sp, fontFamily = family),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Text(
                            text = row.dueText,
                            maxLines = 1,
                            style = TextStyle(
                                color = style.due(row.urgency),
                                fontSize = 13.sp,
                                fontFamily = family,
                            ),
                            modifier = GlanceModifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
