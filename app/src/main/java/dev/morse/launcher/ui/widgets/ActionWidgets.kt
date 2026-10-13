package dev.morse.launcher.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.morse.core.glyph.Glyph
import dev.morse.core.reminders.Reminders
import dev.morse.launcher.data.ToggleStates
import dev.morse.launcher.ui.components.DotMeter
import dev.morse.launcher.ui.components.GlyphIcon
import dev.morse.launcher.ui.components.MText
import dev.morse.launcher.ui.components.MorseIconButton
import dev.morse.launcher.ui.components.PillButton
import dev.morse.launcher.ui.theme.Morse
import kotlinx.coroutines.delay

/* ---------------- Music ---------------- */

private const val PROGRESS_DOTS = 28

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(java.util.Locale.ROOT, s / 60, s % 60)
}

/** M · now playing, a dotted progress line and transport controls. */
@Composable
fun MusicWidget(modifier: Modifier = Modifier) {
    val env = LocalWidgetEnvironment.current
    val c = Morse.colors
    val access by env.mediaAccess.collectAsStateWithLifecycle()
    val playing by env.nowPlaying.collectAsStateWithLifecycle()
    val paused by env.paused.collectAsStateWithLifecycle()

    // Progress advances one dot at a time, so it only needs to redraw 28 times per track.
    var tick by remember { mutableLongStateOf(0L) }
    val np = playing
    LaunchedEffect(np?.playing, np?.durationMs, paused) {
        if (np == null || !np.playing || paused || np.durationMs <= 0) return@LaunchedEffect
        val step = (np.durationMs / PROGRESS_DOTS).coerceAtLeast(1_000L)
        while (true) {
            delay(step)
            tick++
        }
    }

    WidgetCard(
        description = np?.let { "Now playing ${it.title}" } ?: "Music",
        modifier = modifier,
        padding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
    ) {
        if (!access) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.MUSIC, 22.dp, c.text2)
                    Spacer(Modifier.width(10.dp))
                    MText("See what's playing and control it from home.", Morse.type.ui(13.sp, color = c.text2), maxLines = 2)
                }
                PillButton("Allow notification access", env.actions::grantMediaAccess, height = 36.dp)
            }
            return@WidgetCard
        }
        val position = remember(tick, np) { np?.positionNow() ?: 0L }
        val duration = np?.durationMs ?: 0L
        val lit = if (duration > 0) ((position * PROGRESS_DOTS) / duration).toInt().coerceIn(0, PROGRESS_DOTS) else 0
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (c.isDark) c.raised else c.text)
                        .drawBehind {
                            val step = 8.dp.toPx()
                            var y = step / 2
                            while (y < size.height) {
                                var x = step / 2
                                while (x < size.width) {
                                    drawCircle(c.hairline, 1.4.dp.toPx(), Offset(x, y))
                                    x += step
                                }
                                y += step
                            }
                        }
                        .clickable { env.actions.openMusic(np?.packageName) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(c.accent))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MText(if (np?.playing == true) "NOW PLAYING" else "PAUSED", Morse.type.mono(11.sp), color = c.text2, maxLines = 1)
                    MText(np?.title ?: "Nothing playing", Morse.type.ui(16.sp, weight = FontWeight.SemiBold), maxLines = 1)
                    if (!np?.artist.isNullOrBlank()) MText(np!!.artist, Morse.type.ui(13.sp, weight = FontWeight.Normal), color = c.text2, maxLines = 1)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MText(formatMs(position), Morse.type.mono(11.sp, tracking = 0f), color = c.text2)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    DotMeter(
                        lit = lit,
                        total = PROGRESS_DOTS,
                        modifier = Modifier.fillMaxWidth(),
                        dotWidth = 4.dp,
                        dotHeight = 4.dp,
                        gap = 4.dp,
                        highlight = if (lit in 0 until PROGRESS_DOTS && np != null) lit else -1,
                    )
                }
                Spacer(Modifier.width(10.dp))
                MText(formatMs(duration), Morse.type.mono(11.sp, tracking = 0f), color = c.text2)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)) {
                MorseIconButton(Glyph.PREVIOUS, "Previous", env.actions::previous, background = androidx.compose.ui.graphics.Color.Transparent, tint = c.text, glyphSize = 20.dp)
                MorseIconButton(
                    if (np?.playing == true) Glyph.PAUSE else Glyph.PLAY,
                    if (np?.playing == true) "Pause" else "Play",
                    env.actions::playPause,
                    background = c.inverse,
                    tint = c.onInverse,
                    glyphSize = 20.dp,
                )
                MorseIconButton(Glyph.NEXT, "Next", env.actions::next, background = androidx.compose.ui.graphics.Color.Transparent, tint = c.text, glyphSize = 20.dp)
            }
        }
    }
}

/* ---------------- Toggles ---------------- */

/** S · Wi-Fi, Bluetooth, torch and Do Not Disturb as four round buttons. */
@Composable
fun TogglesWidget(modifier: Modifier = Modifier) {
    val env = LocalWidgetEnvironment.current
    val states by env.toggles.collectAsStateWithLifecycle(initialValue = ToggleStates())
    WidgetCard("Quick toggles", modifier, PaddingValues(14.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ToggleButton(Glyph.WIFI, "Wi-Fi", states.wifi, env.actions::toggleWifi)
                ToggleButton(Glyph.BLUETOOTH, "Bluetooth", states.bluetooth, env.actions::toggleBluetooth)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (env.hasTorch) ToggleButton(Glyph.TORCH, "Torch", states.torch, env.actions::toggleTorch)
                ToggleButton(Glyph.MOON, "Do not disturb", states.doNotDisturb, env.actions::toggleDoNotDisturb)
            }
        }
    }
}

@Composable
private fun ToggleButton(glyph: Glyph, label: String, on: Boolean, onClick: () -> Unit) {
    val c = Morse.colors
    Box(
        Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(if (on) c.inverse else c.hairline.copy(alpha = if (c.isDark) 0.9f else 0.5f))
            .clickable(role = Role.Switch, onClick = onClick)
            .semantics {
                contentDescription = label
                stateDescription = if (on) "On" else "Off"
            },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, 24.dp, if (on) c.onInverse else c.text)
    }
}

/* ---------------- Reminders ---------------- */

/** S/M · a short on-device checklist. Tap to tick, long lists show open items first. */
@Composable
fun RemindersWidget(modifier: Modifier = Modifier, large: Boolean = false) {
    val env = LocalWidgetEnvironment.current
    val c = Morse.colors
    val items by env.reminders.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val (done, total) = Reminders.progress(items)
    WidgetCard("Reminders, $done of $total done", modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MText("Today", Morse.type.ui(13.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                if (total > 0) MText("$done / $total", Morse.type.mono(11.sp, tracking = 0f), color = c.text2)
                Spacer(Modifier.width(4.dp))
                MorseIconButton(
                    if (adding) Glyph.CLOSE else Glyph.PLUS,
                    if (adding) "Cancel" else "Add reminder",
                    { adding = !adding; draft = "" },
                    size = 32.dp,
                    glyphSize = 16.dp,
                    background = androidx.compose.ui.graphics.Color.Transparent,
                    tint = c.text,
                )
            }
            if (adding) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(Reminders.MAX_TEXT) },
                    singleLine = true,
                    textStyle = Morse.type.ui(13.sp, weight = FontWeight.Normal, color = c.text),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (draft.isNotBlank()) env.actions.addReminder(draft)
                        draft = ""
                        adding = false
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .border(1.dp, c.hairline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            val shown = Reminders.forWidget(items, if (large) 6 else if (adding) 1 else 3)
            if (shown.isEmpty() && !adding) {
                MText("Nothing to do. Tap + to add.", Morse.type.ui(13.sp, weight = FontWeight.Normal), color = c.text2, maxLines = 2)
            }
            shown.forEach { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp)
                        .clickable(role = Role.Checkbox) { env.actions.toggleReminder(r.id) }
                        .semantics { stateDescription = if (r.done) "Done" else "Not done" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (r.done) c.text else androidx.compose.ui.graphics.Color.Transparent)
                            .border(1.5.dp, c.text, CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    MText(
                        r.text,
                        Morse.type.ui(13.sp, weight = FontWeight.Normal).copy(textDecoration = if (r.done) TextDecoration.LineThrough else null),
                        color = if (r.done) c.text3 else c.text,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
        }
    }
}
