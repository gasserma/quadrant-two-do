package io.github.gasserma.quadranttwodo.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.gasserma.quadranttwodo.core.filledQuadrants

/** Cells of the mark as (column, row), in the order they fill. Quadrant two, top-right, is last. */
private val FILL_ORDER = listOf(0 to 0, 0 to 1, 1 to 1, 1 to 0)

/**
 * How far through its period a task is, as a 2×2 grid after the Eisenhower matrix. One cell fills
 * per quarter of the period: top-left, bottom-left, bottom-right, then top-right (quadrant two:
 * important, not urgent) when the task falls due. Overdue tasks stay full; the colour shows how late.
 */
@Composable
fun QuadrantMark(urgency: Double?, color: Color, modifier: Modifier = Modifier) {
    val filled = filledQuadrants(urgency)
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val cell = (size.minDimension - gap) / 2
        val corner = CornerRadius(cell * 0.2f)
        FILL_ORDER.forEachIndexed { index, (column, row) ->
            drawRoundRect(
                color = if (index < filled) color else color.copy(alpha = 0.25f),
                topLeft = Offset(column * (cell + gap), row * (cell + gap)),
                size = Size(cell, cell),
                cornerRadius = corner,
            )
        }
    }
}
