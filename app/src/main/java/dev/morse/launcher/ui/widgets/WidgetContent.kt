package dev.morse.launcher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.morse.core.layout.WidgetItem
import dev.morse.core.layout.WidgetKind
import dev.morse.launcher.ui.components.MText
import dev.morse.launcher.ui.components.morseSurface
import dev.morse.launcher.ui.theme.Morse

/** Creates views for other apps' widgets. Null in tests and previews. */
interface HostedWidgetViews {
    fun create(context: Context, hostedId: Int): AppWidgetHostView?
    fun reportSize(view: AppWidgetHostView, widthDp: Float, heightDp: Float)
}

val LocalHostedWidgetViews = staticCompositionLocalOf<HostedWidgetViews?> { null }

/** Draws the right widget for a home item. */
@Composable
fun WidgetContent(item: WidgetItem, editMode: Boolean, modifier: Modifier = Modifier) {
    val wide = item.span.columns >= 3
    val tall = item.span.rows >= 3
    when (item.kind) {
        WidgetKind.GLANCE -> GlanceWidget(modifier)
        WidgetKind.DOT_CLOCK -> DotClockWidget(modifier)
        WidgetKind.DIAL_CLOCK -> DialClockWidget(modifier)
        WidgetKind.WEATHER -> if (wide) WeatherHourlyWidget(modifier) else WeatherSmallWidget(modifier)
        WidgetKind.BATTERY -> BatteryWidget(modifier)
        WidgetKind.CALENDAR -> when {
            tall -> MonthWidget(modifier)
            wide -> AgendaWidget(modifier)
            else -> CalendarSmallWidget(modifier)
        }
        WidgetKind.MUSIC -> MusicWidget(modifier)
        WidgetKind.TOGGLES -> TogglesWidget(modifier)
        WidgetKind.REMINDERS -> RemindersWidget(modifier, large = wide)
        WidgetKind.HOSTED -> HostedWidget(item.hostedId, editMode, modifier)
    }
}

/**
 * Another app's widget. While editing, a transparent layer sits on top so drags
 * reach the launcher instead of the widget.
 */
@Composable
fun HostedWidget(hostedId: Int, editMode: Boolean, modifier: Modifier = Modifier) {
    val views = LocalHostedWidgetViews.current
    BoxWithConstraints(modifier.fillMaxSize().clip(WidgetShape)) {
        if (views == null) {
            Box(Modifier.fillMaxSize().morseSurface(WidgetShape), contentAlignment = Alignment.Center) {
                MText("App widget", Morse.type.ui(13.sp, color = Morse.colors.text2))
            }
            return@BoxWithConstraints
        }
        val w = maxWidth.value
        val h = maxHeight.value
        AndroidView(
            factory = { ctx -> views.create(ctx, hostedId) ?: FrameLayout(ctx) },
            update = { v -> if (v is AppWidgetHostView) views.reportSize(v, w, h) },
            modifier = Modifier.fillMaxSize(),
        )
        if (editMode) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial)
                        }
                    },
            )
        }
    }
}
