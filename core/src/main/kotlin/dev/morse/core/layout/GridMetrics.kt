package dev.morse.core.layout

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Geometry shared by the home grid, drag-and-drop and widget sizing. */
object GridMetrics {

    /**
     * Grid span for another app's widget. Android 12+ providers can state a target
     * cell size; older ones only give a minimum size in dp, which is rounded up to
     * whole cells.
     */
    fun hostedSpan(
        minWidthDp: Float,
        minHeightDp: Float,
        targetColumns: Int,
        targetRows: Int,
        cellWidthDp: Float,
        cellHeightDp: Float,
        columns: Int,
        rows: Int,
    ): GridSpan {
        val c = if (targetColumns > 0) targetColumns else ceil(minWidthDp / cellWidthDp).toInt()
        val r = if (targetRows > 0) targetRows else ceil(minHeightDp / cellHeightDp).toInt()
        return GridSpan(c.coerceIn(1, columns), r.coerceIn(1, rows))
    }

    /**
     * The cell under a dragged item's top-left corner, given its offset from the
     * grid origin in pixels. The result always keeps the item inside the grid.
     */
    fun snapToCell(
        x: Float,
        y: Float,
        cellWidth: Float,
        cellHeight: Float,
        span: GridSpan,
        columns: Int,
        rows: Int,
    ): Cell {
        val col = (x / cellWidth).roundToInt().coerceIn(0, (columns - span.columns).coerceAtLeast(0))
        val row = (y / cellHeight).roundToInt().coerceIn(0, (rows - span.rows).coerceAtLeast(0))
        return Cell(col, row)
    }

    /** Dock slot under a horizontal position, or -1 when outside the dock. */
    fun dockSlotAt(x: Float, dockLeft: Float, dockWidth: Float, slots: Int = HomeLayout.DOCK_SIZE): Int {
        if (x < dockLeft || x > dockLeft + dockWidth || dockWidth <= 0f) return -1
        return ((x - dockLeft) / (dockWidth / slots)).toInt().coerceIn(0, slots - 1)
    }
}
