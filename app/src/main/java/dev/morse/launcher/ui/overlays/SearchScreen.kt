package dev.morse.launcher.ui.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.morse.core.apps.AppInfo
import dev.morse.core.glyph.Glyph
import dev.morse.core.search.SettingShortcut
import dev.morse.launcher.data.ContactHit
import dev.morse.launcher.ui.components.AppIcon
import dev.morse.launcher.ui.components.FullScreenLayer
import dev.morse.launcher.ui.components.GlyphIcon
import dev.morse.launcher.ui.components.ListRow
import dev.morse.launcher.ui.components.MText
import dev.morse.launcher.ui.components.MorseIconButton
import dev.morse.launcher.ui.components.SectionLabel
import dev.morse.launcher.ui.components.morseSurface
import dev.morse.launcher.ui.components.tapOrHold
import dev.morse.launcher.ui.home.BoundsHolder
import dev.morse.launcher.ui.home.HomeUiState
import dev.morse.launcher.ui.theme.Morse

data class SearchResults(
    val apps: List<AppInfo> = emptyList(),
    val contacts: List<ContactHit> = emptyList(),
    val settings: List<SettingShortcut> = emptyList(),
)

interface SearchActions {
    fun onQueryChange(query: String) {}
    fun onLaunch(app: AppInfo, bounds: Rect?) {}
    fun onAppMenu(app: AppInfo, bounds: Rect?) {}
    fun onContact(contact: ContactHit) {}
    fun onCall(contact: ContactHit) {}
    fun onMessage(contact: ContactHit) {}
    fun onSetting(setting: SettingShortcut) {}
    fun onWebSearch(query: String) {}
    fun onAllowContacts() {}
    fun onClose() {}
}

/** Search across apps, contacts and system settings; the keyboard opens right away. */
@Composable
fun SearchScreen(
    query: String,
    results: SearchResults,
    suggestions: List<AppInfo>,
    contactsAllowed: Boolean,
    state: HomeUiState,
    actions: SearchActions,
) {
    val c = Morse.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    FullScreenLayer("search") {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .morseSurface(RoundedCornerShape(50), line = c.text)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.SEARCH, 18.dp, c.text2)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = actions::onQueryChange,
                    singleLine = true,
                    textStyle = Morse.type.ui(16.sp, color = c.text),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        val first = results.apps.firstOrNull()
                        if (first != null) actions.onLaunch(first, null) else if (query.isNotBlank()) actions.onWebSearch(query)
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus)
                        .semantics { contentDescription = "Search apps, contacts and settings" }
                        .testTag("search-field"),
                    decorationBox = { inner ->
                        if (query.isEmpty()) MText("Apps, contacts, settings", Morse.type.ui(16.sp, weight = FontWeight.Normal), color = c.text3)
                        inner()
                    },
                )
                if (query.isNotEmpty()) {
                    MorseIconButton(Glyph.CLOSE, "Clear", { actions.onQueryChange("") }, size = 36.dp, glyphSize = 14.dp, background = c.hairline)
                }
            }
            Spacer(Modifier.width(8.dp))
            MText(
                "Cancel",
                Morse.type.ui(15.sp),
                color = c.text2,
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .tapOrHold("Cancel", onTap = actions::onClose, onHold = actions::onClose),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            val apps = if (query.isBlank()) suggestions else results.apps
            if (apps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel(if (query.isBlank()) "Suggested" else "Apps")
                    apps.take(8).chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { app -> SearchApp(app, state, actions, Modifier.weight(1f)) }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (query.isNotBlank()) {
                if (results.contacts.isNotEmpty()) {
                    Column {
                        SectionLabel("Contacts")
                        results.contacts.forEach { contact -> ContactRow(contact, actions) }
                    }
                } else if (!contactsAllowed && query.length >= 2) {
                    ListRow("Search your contacts too", glyph = Glyph.CONTACTS, detail = "Allow contacts access", onClick = actions::onAllowContacts)
                }
                if (results.settings.isNotEmpty()) {
                    Column {
                        SectionLabel("Settings")
                        results.settings.forEach { s ->
                            ListRow(s.title, glyph = Glyph.SETTINGS, onClick = { actions.onSetting(s) }, trailing = {
                                MText(s.section, Morse.type.ui(12.sp, weight = FontWeight.Normal), color = c.text3)
                            })
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .morseSurface(RoundedCornerShape(50))
                        .tapOrHold("Search the web for $query", onTap = { actions.onWebSearch(query) }, onHold = { actions.onWebSearch(query) })
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(Glyph.BROWSER, 19.dp, c.text)
                    Spacer(Modifier.width(12.dp))
                    MText("Search the web for “$query”", Morse.type.ui(15.sp, weight = FontWeight.Normal), modifier = Modifier.weight(1f), maxLines = 1)
                    GlyphIcon(Glyph.ARROW_UP_RIGHT, 16.dp, c.text3)
                }
            }
        }
    }
}

@Composable
private fun SearchApp(app: AppInfo, state: HomeUiState, actions: SearchActions, modifier: Modifier) {
    val bounds = remember { BoundsHolder() }
    Column(
        modifier
            .onGloballyPositioned { bounds.rect = it.boundsInWindow() }
            .tapOrHold(app.label, onTap = { actions.onLaunch(app, bounds.rect) }, onHold = { actions.onAppMenu(app, bounds.rect) }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app, state.isOriginal(app.key), badge = state.hasBadge(app.key))
        Spacer(Modifier.height(7.dp))
        MText(app.label, Morse.type.ui(11.5.sp), color = Morse.colors.text.copy(alpha = 0.86f), maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@Composable
private fun ContactRow(contact: ContactHit, actions: SearchActions) {
    val c = Morse.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .tapOrHold(contact.name, onTap = { actions.onContact(contact) }, onHold = { actions.onContact(contact) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .size(44.dp)
                    .morseSurface(CircleShape, color = c.raised),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MText(contact.initials, Morse.type.mono(14.sp, weight = FontWeight.SemiBold, tracking = 0f))
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                MText(contact.name, Morse.type.ui(15.sp), maxLines = 1)
                if (contact.phone != null) MText(contact.phone, Morse.type.ui(12.sp, weight = FontWeight.Normal), color = c.text3, maxLines = 1)
            }
        }
        if (contact.phone != null) {
            MorseIconButton(Glyph.MESSAGES, "Message ${contact.name}", { actions.onMessage(contact) }, background = c.raised, tint = c.text)
            Spacer(Modifier.width(8.dp))
            MorseIconButton(Glyph.PHONE, "Call ${contact.name}", { actions.onCall(contact) }, background = c.inverse, tint = c.onInverse)
        }
    }
}
