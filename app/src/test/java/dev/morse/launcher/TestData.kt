package dev.morse.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.morse.core.apps.AppCategory
import dev.morse.core.apps.AppInfo
import dev.morse.core.apps.AppKey
import dev.morse.core.glyph.Glyph
import dev.morse.core.layout.AppItem
import dev.morse.core.layout.Cell
import dev.morse.core.layout.FolderItem
import dev.morse.core.layout.HomeLayout
import dev.morse.core.layout.HomePage
import dev.morse.core.settings.LauncherSettings
import dev.morse.core.settings.ThemeMode
import dev.morse.launcher.ui.home.HomeUiState
import dev.morse.launcher.ui.theme.MorseFonts
import dev.morse.launcher.ui.theme.MorseTheme
import dev.morse.launcher.ui.widgets.LocalWidgetEnvironment
import dev.morse.launcher.ui.widgets.WidgetEnvironment

/** Shared fixtures for the app's JVM (Robolectric) tests. */
object TestData {
    val camera = AppInfo(AppKey("com.example.camera", "com.example.camera.Main"), "Camera", AppCategory.CREATIVITY, glyph = Glyph.CAMERA)
    val phone = AppInfo(AppKey("com.example.phone", "com.example.phone.Main"), "Phone", AppCategory.SOCIAL, glyph = Glyph.PHONE)
    val notes = AppInfo(AppKey("com.example.notes", "com.example.notes.Main"), "Notes", AppCategory.PRODUCTIVITY, glyph = Glyph.NOTES)
    val calc = AppInfo(AppKey("com.example.calc", "com.example.calc.Main"), "Calculator", AppCategory.UTILITIES, glyph = Glyph.CALCULATOR)
    val zine = AppInfo(AppKey("com.example.zine", "com.example.zine.Main"), "Zine", AppCategory.OTHER)

    val allApps = listOf(camera, phone, notes, calc, zine)

    val layout = HomeLayout(
        columns = 4,
        rows = 6,
        pages = listOf(
            HomePage(
                "p0",
                listOf(
                    AppItem("cam", Cell(0, 0), camera.key),
                    FolderItem("tools", Cell(1, 0), "Tools", listOf(calc.key, notes.key)),
                    AppItem("zine", Cell(2, 0), zine.key),
                ),
            ),
            HomePage("p1"),
        ),
        dock = listOf(phone.key, null, null, null),
    )

    fun homeState(
        settings: LauncherSettings = LauncherSettings(),
        editMode: Boolean = false,
        badges: Set<String> = emptySet(),
    ) = HomeUiState(
        layout = layout,
        apps = allApps.associateBy { it.key },
        badges = badges,
        settings = settings,
        editMode = editMode,
    )
}

/** Wraps test content in the Morse theme with platform fonts (bundled variable fonts are device-only). */
@Composable
fun TestTheme(
    environment: WidgetEnvironment = WidgetEnvironment.preview(),
    theme: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    MorseTheme(theme = theme, accent = dev.morse.core.settings.Accent.SIGNAL_RED, fonts = MorseFonts.System) {
        CompositionLocalProvider(LocalWidgetEnvironment provides environment, content = content)
    }
}
