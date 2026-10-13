package dev.morse.launcher.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Watches a whole area for long presses before children see the touch, so one
 * detector can serve every icon and widget on a page (and other apps' widgets,
 * which would otherwise swallow the touch). [onLongPress] gets the press position;
 * returning true consumes the rest of the gesture so the child does not also click.
 * [onTap] runs for short taps (children still receive them too).
 */
fun Modifier.areaPress(
    key: Any?,
    onLongPress: (Offset) -> Boolean,
    onTap: (Offset) -> Unit = {},
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        var released = false
        var moved = false
        val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    released = true
                    break
                }
                if ((change.position - down.position).getDistance() > slop) {
                    moved = true
                    break
                }
            }
            true
        }
        if (finished == null) {
            if (onLongPress(down.position)) {
                // Swallow the rest of this gesture.
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        } else if (released && !moved) {
            onTap(down.position)
        }
    }
}

/** Swipe up for the app library, swipe down for search. */
fun Modifier.homeSwipes(enabled: Boolean, onUp: () -> Unit, onDown: () -> Unit): Modifier =
    if (!enabled) {
        this
    } else {
        this.pointerInput(Unit) {
            val threshold = 72.dp.toPx()
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    when {
                        total < -threshold -> onUp()
                        total > threshold -> onDown()
                    }
                },
                onVerticalDrag = { _, dy -> total += dy },
            )
        }
    }

/** Shared drag-and-drop state for edit mode. */
class DragState {
    var itemId by mutableStateOf<String?>(null)
    var dockSlot by mutableStateOf(-1)

    /** Read inside graphicsLayer only, so dragging redraws without recomposing. */
    var offset by mutableStateOf(Offset.Zero)

    var dockBounds: Rect? = null
    val gridOrigins = HashMap<Int, Offset>()
    var gridSizePx: Pair<Float, Float> = 0f to 0f

    val active: Boolean get() = itemId != null || dockSlot >= 0

    fun reset() {
        itemId = null
        dockSlot = -1
        offset = Offset.Zero
    }
}
