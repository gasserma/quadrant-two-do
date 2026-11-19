package io.github.gasserma.quadranttwodo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * The home-screen widget: a scrolling list of every task, sorted like the app, with the due text
 * coloured by urgency. Tapping anywhere opens the app.
 *
 * The widget renders a snapshot; [WidgetRefresher] re-renders it on every data change,
 * every 4 hours and just after 06:00 (when tasks become due).
 */
class QuadrantTwoDoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val tasks = container.repository.observeTasks().first()
        val rows = buildWidgetRows(tasks, LocalDateTime.now(container.clock))
        val empty = context.getString(R.string.list_empty)
        provideContent { WidgetContent(rows, empty) }
    }
}

class QuadrantTwoDoWidgetReceiverSmall : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuadrantTwoDoWidget()
}

class QuadrantTwoDoWidgetReceiverMedium : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuadrantTwoDoWidget()
}

private val Ink = ColorProvider(Color(Palette.INK))

/** The app's slate background at 50%, behind the list. */
private val PanelBackground = Color(Palette.SLATE).copy(alpha = 0.5f)

/**
 * Layout: an 8dp outer frame, a translucent slate panel, and a list with 8dp padding. Rows have
 * 4dp vertical padding, a medium sans-serif-light name and a small due text 6dp to its right.
 */
@Composable
fun WidgetContent(rows: List<WidgetRow>, emptyText: String) {
    val open = actionStartActivity<MainActivity>()
    Box(GlanceModifier.fillMaxSize().padding(8.dp)) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(PanelBackground)
                .clickable(open),
        ) {
            if (rows.isEmpty()) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(emptyText, style = TextStyle(color = Ink, fontSize = 18.sp))
                }
            } else {
                val family = FontFamily("sans-serif-light")
                val nameSize = 18.sp
                LazyColumn(GlanceModifier.fillMaxSize().padding(8.dp)) {
                    items(rows, itemId = { it.id }) { row ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(open)
                                .semantics { testTag = "row:${row.name}" },
                        ) {
                            Text(
                                text = row.name,
                                maxLines = 1,
                                style = TextStyle(color = Ink, fontSize = nameSize, fontFamily = family),
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            Text(
                                text = row.dueText,
                                maxLines = 1,
                                style = TextStyle(
                                    color = ColorProvider(Color(row.dueColor)),
                                    fontSize = 14.sp,
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
}
