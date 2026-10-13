package dev.morse.launcher.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.morse.core.glyph.Glyph
import dev.morse.core.power.RefreshProfile
import dev.morse.core.settings.Accent
import dev.morse.core.settings.IconStyle
import dev.morse.core.settings.LauncherSettings
import dev.morse.core.settings.ThemeMode
import dev.morse.core.settings.WallpaperMode
import dev.morse.core.weather.TemperatureUnit
import dev.morse.launcher.ui.components.Divider
import dev.morse.launcher.ui.components.FullScreenLayer
import dev.morse.launcher.ui.components.GroupCard
import dev.morse.launcher.ui.components.ListRow
import dev.morse.launcher.ui.components.MText
import dev.morse.launcher.ui.components.MorseIconButton
import dev.morse.launcher.ui.components.MorseSwitch
import dev.morse.launcher.ui.components.PillButton
import dev.morse.launcher.ui.components.SectionLabel
import dev.morse.launcher.ui.components.Segmented
import dev.morse.launcher.ui.theme.Morse

/** What the settings screen shows about the system (permissions, default launcher…). */
data class SettingsInfo(
    val isDefaultLauncher: Boolean = false,
    val locationGranted: Boolean = false,
    val calendarGranted: Boolean = false,
    val contactsGranted: Boolean = false,
    val notificationAccess: Boolean = false,
    val versionName: String = "",
    val placeStatus: String? = null,
)

enum class SettingsAction {
    SET_DEFAULT_LAUNCHER,
    GRANT_LOCATION,
    GRANT_CALENDAR,
    GRANT_CONTACTS,
    GRANT_NOTIFICATIONS,
    PICK_SYSTEM_WALLPAPER,
    APP_PERMISSIONS,
    RESET_LAYOUT,
    SHOW_HIDDEN_APPS,
}

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    info: SettingsInfo,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onAction: (SettingsAction) -> Unit,
    onSetCity: (String) -> Unit,
    onClose: () -> Unit,
) {
    val c = Morse.colors
    FullScreenLayer("settings") {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MorseIconButton(Glyph.BACK, "Back", onClose, background = Color.Transparent)
                Spacer(Modifier.weight(1f))
                MText("MORSE ${info.versionName}".trim(), Morse.type.mono(11.sp), color = c.text3)
            }
            MText("LAUNCHER", Morse.type.dot(44.sp), modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))

            if (!info.isDefaultLauncher) {
                GroupCard {
                    ListRow(
                        "Make Morse your home screen",
                        glyph = Glyph.LAYERS,
                        detail = "Pressing Home will open Morse.",
                        onClick = { onAction(SettingsAction.SET_DEFAULT_LAUNCHER) },
                        trailing = { PillButton("Set", { onAction(SettingsAction.SET_DEFAULT_LAUNCHER) }, filled = true, height = 36.dp) },
                    )
                }
            }

            SectionLabel("Layout", Modifier.padding(start = 6.dp, top = 6.dp))
            GroupCard {
                SettingRow("Home grid") {
                    Segmented(listOf(4, 5), settings.columns, { "$it × 6" }, { cols -> onUpdate { it.copy(columns = cols) } }, mono = true)
                }
                Divider()
                SwitchRow("Icon labels", settings.showLabels) { v -> onUpdate { it.copy(showLabels = v) } }
                Divider()
                SwitchRow("Search pill", settings.showSearchPill) { v -> onUpdate { it.copy(showSearchPill = v) } }
                Divider()
                SwitchRow("Notification dots", settings.notificationDots, detail = if (info.notificationAccess) null else "Needs notification access") { v ->
                    onUpdate { it.copy(notificationDots = v) }
                    if (v && !info.notificationAccess) onAction(SettingsAction.GRANT_NOTIFICATIONS)
                }
            }

            SectionLabel("Appearance", Modifier.padding(start = 6.dp, top = 14.dp))
            GroupCard {
                SettingRow("Theme") {
                    Segmented(ThemeMode.entries, settings.theme, { it.title }, { t -> onUpdate { it.copy(theme = t) } })
                }
                Divider()
                SettingRow("Accent") {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Accent.entries.forEach { a ->
                            val on = a == settings.accent
                            val colour = Color(if (c.isDark) a.darkArgb else a.lightArgb)
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clickable(role = Role.RadioButton) { onUpdate { it.copy(accent = a) } }
                                    .semantics {
                                        contentDescription = a.title
                                        selected = on
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(if (on) 28.dp else 22.dp)
                                        .then(if (on) Modifier.border(2.dp, c.text, CircleShape).padding(4.dp) else Modifier)
                                        .clip(CircleShape)
                                        .background(colour),
                                )
                            }
                        }
                    }
                }
                Divider()
                SettingRow("Icons") {
                    Segmented(IconStyle.entries, settings.iconStyle, { it.title }, { s -> onUpdate { it.copy(iconStyle = s) } })
                }
                Divider()
                SettingRow("Wallpaper") {
                    Segmented(WallpaperMode.entries, settings.wallpaper, { it.title }, { w ->
                        onUpdate { it.copy(wallpaper = w) }
                    })
                }
                if (settings.wallpaper == WallpaperMode.SYSTEM) {
                    Divider()
                    ListRow("Choose wallpaper", glyph = Glyph.PHOTOS, detail = "A still image keeps the battery cost at zero.", onClick = { onAction(SettingsAction.PICK_SYSTEM_WALLPAPER) })
                }
            }

            SectionLabel("Power", Modifier.padding(start = 6.dp, top = 14.dp))
            GroupCard {
                SwitchRow("Low-power motion", settings.lowPowerMotion, detail = "Short fades, no scaling, home capped at 60 Hz") { v ->
                    onUpdate { it.copy(lowPowerMotion = v) }
                }
                Divider()
                SwitchRow("Pause widgets below 20%", settings.pauseWidgetsOnLowBattery, detail = "Freeze refresh until charging") { v ->
                    onUpdate { it.copy(pauseWidgetsOnLowBattery = v) }
                }
                Divider()
                SettingRow("Widget refresh", detail = "Weather every ${settings.refresh.weatherMinutes} min · clock every minute") {
                    Segmented(RefreshProfile.entries, settings.refresh, { it.title }, { p -> onUpdate { it.copy(refresh = p) } })
                }
                Divider()
                SwitchRow("Haptics", settings.haptics, detail = "A light tick on long-press") { v -> onUpdate { it.copy(haptics = v) } }
            }

            SectionLabel("Weather", Modifier.padding(start = 6.dp, top = 14.dp))
            GroupCard {
                SettingRow("Units") {
                    Segmented(TemperatureUnit.entries, settings.temperatureUnit, { if (it == TemperatureUnit.CELSIUS) "°C" else "°F" }, { u ->
                        onUpdate { it.copy(temperatureUnit = u) }
                    }, mono = true)
                }
                Divider()
                if (!info.locationGranted) {
                    ListRow("Use my location", glyph = Glyph.LOCATION, detail = "Uses the last known position; never starts GPS.", onClick = { onAction(SettingsAction.GRANT_LOCATION) })
                    Divider()
                }
                CityRow(settings.manualPlaceName, info.placeStatus, onSetCity)
            }

            SectionLabel("Permissions", Modifier.padding(start = 6.dp, top = 14.dp))
            GroupCard {
                PermissionRow("Calendar", "Next events and the month widget", info.calendarGranted) { onAction(SettingsAction.GRANT_CALENDAR) }
                Divider()
                PermissionRow("Contacts", "People in search", info.contactsGranted) { onAction(SettingsAction.GRANT_CONTACTS) }
                Divider()
                PermissionRow("Notification access", "Notification dots and the music widget", info.notificationAccess) { onAction(SettingsAction.GRANT_NOTIFICATIONS) }
                Divider()
                ListRow("All app permissions", glyph = Glyph.INFO, onClick = { onAction(SettingsAction.APP_PERMISSIONS) })
            }

            SectionLabel("Home", Modifier.padding(start = 6.dp, top = 14.dp))
            GroupCard {
                ListRow("Hidden apps", glyph = Glyph.HIDE, detail = "${settings.hiddenApps.size} hidden", onClick = { onAction(SettingsAction.SHOW_HIDDEN_APPS) })
                Divider()
                ListRow("Reset home screen", glyph = Glyph.TRASH, detail = "Back to the default pages and widgets", titleColor = c.accent, onClick = { onAction(SettingsAction.RESET_LAYOUT) })
            }

            MText(
                "Fonts: Doto, Geist and Geist Mono, under the SIL Open Font License 1.1. Weather by Open-Meteo.com (CC BY 4.0).",
                Morse.type.ui(12.sp, weight = FontWeight.Normal),
                color = c.text3,
                modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 32.dp, end = 6.dp),
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, detail: String? = null, control: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            MText(title, Morse.type.ui(15.sp))
            if (detail != null) MText(detail, Morse.type.ui(12.5.sp, weight = FontWeight.Normal), color = Morse.colors.text3)
        }
        Spacer(Modifier.width(12.dp))
        control()
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, detail: String? = null, onChange: (Boolean) -> Unit) {
    SettingRow(title, detail) { MorseSwitch(checked, onChange, title) }
}

@Composable
private fun PermissionRow(title: String, detail: String, granted: Boolean, onGrant: () -> Unit) {
    SettingRow(title, detail) {
        if (granted) {
            MText("ALLOWED", Morse.type.mono(11.sp), color = Morse.colors.text3)
        } else {
            PillButton("Allow", onGrant, height = 36.dp)
        }
    }
}

@Composable
private fun CityRow(current: String, status: String?, onSetCity: (String) -> Unit) {
    val c = Morse.colors
    var draft by remember(current) { mutableStateOf(current) }
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MText("City", Morse.type.ui(15.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.take(60) },
                singleLine = true,
                textStyle = Morse.type.ui(15.sp, weight = FontWeight.Normal, color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSetCity(draft) }),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .border(1.dp, c.hairline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .semantics { contentDescription = "Weather city" },
                decorationBox = { inner ->
                    if (draft.isEmpty()) MText("e.g. São Paulo", Morse.type.ui(15.sp, weight = FontWeight.Normal), color = c.text3)
                    inner()
                },
            )
            Spacer(Modifier.width(8.dp))
            PillButton("Set", { onSetCity(draft) }, height = 40.dp)
        }
        MText(
            status ?: "Used when location is off.",
            Morse.type.ui(12.5.sp, weight = FontWeight.Normal),
            color = c.text3,
        )
    }
}
