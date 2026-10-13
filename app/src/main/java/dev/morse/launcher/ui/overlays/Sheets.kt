package dev.morse.launcher.ui.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.morse.core.glyph.Glyph
import dev.morse.core.layout.GridSpan
import dev.morse.core.layout.WidgetItem
import dev.morse.core.layout.WidgetKind
import dev.morse.core.layout.WidgetSize
import dev.morse.launcher.data.ProviderEntry
import dev.morse.launcher.ui.components.BottomSheetLayer
import dev.morse.launcher.ui.components.Divider
import dev.morse.launcher.ui.components.ListRow
import dev.morse.launcher.ui.components.MText
import dev.morse.launcher.ui.components.MorseIconButton
import dev.morse.launcher.ui.components.PillButton
import dev.morse.launcher.ui.components.SectionLabel
import dev.morse.launcher.ui.components.Segmented
import dev.morse.launcher.ui.components.SheetHeader
import dev.morse.launcher.ui.theme.Morse
import dev.morse.launcher.ui.widgets.LocalHostedWidgetViews
import dev.morse.launcher.ui.widgets.WidgetContent

/* ---------------- Context menu ---------------- */

data class MenuAction(
    val label: String,
    val glyph: Glyph,
    val destructive: Boolean = false,
    val run: () -> Unit,
)

/** The long-press menu, as a short sheet: title, then actions. */
@Composable
fun ActionSheet(title: String, subtitle: String?, actions: List<MenuAction>, onDismiss: () -> Unit) {
    val c = Morse.colors
    BottomSheetLayer("menu", onDismiss) {
        MText(title, Morse.type.ui(18.sp, weight = FontWeight.SemiBold), maxLines = 1)
        if (subtitle != null) MText(subtitle, Morse.type.mono(11.sp), color = c.text3, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        actions.forEachIndexed { i, a ->
            if (i > 0) Divider()
            ListRow(a.label, glyph = a.glyph, titleColor = if (a.destructive) c.accent else c.text, onClick = a.run)
        }
    }
}

/* ---------------- Widget picker ---------------- */

/** Built-in kinds in the order the picker lists them; Glance and Dot clock share one entry. */
private val PICKER_KINDS = listOf(
    WidgetKind.DOT_CLOCK,
    WidgetKind.DIAL_CLOCK,
    WidgetKind.WEATHER,
    WidgetKind.CALENDAR,
    WidgetKind.BATTERY,
    WidgetKind.MUSIC,
    WidgetKind.TOGGLES,
    WidgetKind.REMINDERS,
)

/** Sizes offered for a picker entry (the dot clock's M size is the Glance widget). */
private fun pickerSizes(kind: WidgetKind): List<WidgetSize> =
    if (kind == WidgetKind.DOT_CLOCK) listOf(WidgetSize.S, WidgetSize.M) else kind.sizes

private fun resolveKind(kind: WidgetKind, size: WidgetSize): WidgetKind =
    if (kind == WidgetKind.DOT_CLOCK && size == WidgetSize.M) WidgetKind.GLANCE else kind

@Composable
fun WidgetPickerSheet(
    providers: List<ProviderEntry>,
    onAdd: (WidgetKind, GridSpan) -> Unit,
    onAddHosted: (ProviderEntry) -> Unit,
    onClose: () -> Unit,
) {
    val c = Morse.colors
    var kind by remember { mutableStateOf(WidgetKind.DOT_CLOCK) }
    var chosenSize by remember { mutableStateOf(WidgetSize.M) }
    val sizes = pickerSizes(kind)
    val size = if (chosenSize in sizes) chosenSize else sizes.first()
    val resolved = resolveKind(kind, size)

    BottomSheetLayer("widget-picker", onClose) {
        SheetHeader("Add widget", onClose)
        Column(
            Modifier
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Live preview at real size (S 174, M 364 wide).
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                val w = if (size == WidgetSize.S) 174.dp else 364.dp
                val h = if (size == WidgetSize.L) 364.dp else 174.dp
                Box(Modifier.size(w, h)) {
                    CompositionLocalProvider(LocalHostedWidgetViews provides null) {
                        WidgetContent(WidgetItem("preview", dev.morse.core.layout.Cell(0, 0), resolved, size.span), editMode = true)
                    }
                }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MText(if (resolved == WidgetKind.GLANCE) "Glance" else kind.title, Morse.type.ui(15.sp, weight = FontWeight.SemiBold))
                MText(resolved.refreshNote, Morse.type.ui(13.sp, weight = FontWeight.Normal), color = c.text2)
            }
            if (sizes.size > 1) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Segmented(sizes, size, { it.label }, { chosenSize = it }, mono = true)
                }
            }
            PillButton("Add to page", { onAdd(resolved, size.span) }, glyph = Glyph.PLUS, filled = true, modifier = Modifier.fillMaxWidth(), height = 48.dp)

            SectionLabel("Morse widgets", Modifier.padding(top = 8.dp))
            PICKER_KINDS.forEach { k ->
                ListRow(
                    k.title,
                    glyph = glyphFor(k),
                    detail = pickerSizes(k).joinToString(" · ") { it.label },
                    titleColor = if (k == kind) c.text else c.text2,
                    onClick = { kind = k },
                )
            }

            if (providers.isNotEmpty()) {
                SectionLabel("App widgets", Modifier.padding(top = 8.dp))
                providers.groupBy { it.appLabel }.forEach { (app, entries) ->
                    MText(app, Morse.type.ui(13.sp, weight = FontWeight.SemiBold), color = c.text2, modifier = Modifier.padding(top = 6.dp))
                    entries.forEach { e ->
                        val cols = if (e.targetColumns > 0) e.targetColumns else kotlin.math.ceil(e.minWidthDp / 90f).toInt().coerceIn(1, 4)
                        val rows = if (e.targetRows > 0) e.targetRows else kotlin.math.ceil(e.minHeightDp / 100f).toInt().coerceIn(1, 4)
                        ListRow(e.label, glyph = Glyph.WIDGET, detail = "$cols × $rows", onClick = { onAddHosted(e) })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun glyphFor(kind: WidgetKind): Glyph = when (kind) {
    WidgetKind.GLANCE, WidgetKind.DOT_CLOCK, WidgetKind.DIAL_CLOCK -> Glyph.CLOCK
    WidgetKind.WEATHER -> Glyph.WEATHER
    WidgetKind.CALENDAR -> Glyph.CALENDAR
    WidgetKind.BATTERY -> Glyph.BATTERY
    WidgetKind.MUSIC -> Glyph.MUSIC
    WidgetKind.TOGGLES -> Glyph.WIFI
    WidgetKind.REMINDERS -> Glyph.CHECK
    WidgetKind.HOSTED -> Glyph.WIDGET
}

/* ---------------- Pages ---------------- */

data class PageSummary(val id: String, val itemCount: Int)

@Composable
fun PagesSheet(
    pages: List<PageSummary>,
    current: Int,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onClose: () -> Unit,
) {
    val c = Morse.colors
    BottomSheetLayer("pages", onClose) {
        SheetHeader("Pages", onClose)
        pages.forEachIndexed { i, p ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                MText("${i + 1}", Morse.type.dot(30.sp), color = if (i == current) c.accent else c.text, modifier = Modifier.width(44.dp))
                Column(Modifier.weight(1f)) {
                    MText("Page ${i + 1}", Morse.type.ui(15.sp))
                    MText(if (p.itemCount == 0) "Empty" else "${p.itemCount} items", Morse.type.ui(12.5.sp, weight = FontWeight.Normal), color = c.text3)
                }
                MorseIconButton(Glyph.BACK, "Move page ${i + 1} earlier", { if (i > 0) onMove(i, i - 1) }, size = 40.dp, modifier = Modifier.rotate90())
                MorseIconButton(Glyph.CHEVRON_RIGHT, "Move page ${i + 1} later", { if (i < pages.lastIndex) onMove(i, i + 1) }, size = 40.dp, modifier = Modifier.rotate90())
                Spacer(Modifier.width(4.dp))
                MorseIconButton(Glyph.TRASH, "Remove page ${i + 1}", { onRemove(i) }, size = 40.dp, tint = c.accent)
            }
        }
        Spacer(Modifier.height(12.dp))
        PillButton("Add page", onAdd, glyph = Glyph.PLUS, modifier = Modifier.fillMaxWidth(), height = 48.dp)
    }
}

private fun Modifier.rotate90(): Modifier = this.rotate(90f)
