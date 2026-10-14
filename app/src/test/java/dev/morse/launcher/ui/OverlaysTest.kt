package dev.morse.launcher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.morse.core.apps.AppInfo
import dev.morse.core.apps.AppKey
import dev.morse.core.apps.Library
import dev.morse.core.layout.FolderItem
import dev.morse.core.search.SettingsShortcuts
import dev.morse.core.settings.LauncherSettings
import dev.morse.launcher.TestData
import dev.morse.launcher.TestTheme
import dev.morse.launcher.ui.overlays.ActionSheet
import dev.morse.launcher.ui.overlays.FolderOverlay
import dev.morse.launcher.ui.overlays.LibraryScreen
import dev.morse.launcher.ui.overlays.MenuAction
import dev.morse.launcher.ui.overlays.SearchActions
import dev.morse.launcher.ui.overlays.SearchResults
import dev.morse.launcher.ui.overlays.SearchScreen
import dev.morse.launcher.ui.overlays.SettingsAction
import dev.morse.launcher.ui.overlays.SettingsInfo
import dev.morse.launcher.ui.overlays.SettingsScreen
import dev.morse.core.glyph.Glyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OverlaysTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun folderShowsItsAppsAndLaunchesThem() {
        var launched: AppKey? = null
        var closed = false
        val folder = TestData.layout.pages[0].items.filterIsInstance<FolderItem>().single()
        compose.setContent {
            TestTheme {
                FolderOverlay(
                    folder = folder,
                    state = TestData.homeState(),
                    onLaunch = { app, _ -> launched = app },
                    onAppMenu = { _, _ -> },
                    onRename = {},
                    onClose = { closed = true },
                )
            }
        }
        compose.onNodeWithText("TOOLS").assertExists()
        compose.onNodeWithContentDescription("Calculator").performClick()
        assertEquals(TestData.calc.key, launched)
        compose.onNodeWithTag("folder").performTouchInput { click(Offset(8f, 8f)) }
        assertTrue(closed)
    }

    @Test
    fun searchTypingAndLaunching() {
        var typed = ""
        var launched: AppInfo? = null
        var web: String? = null
        val actions = object : SearchActions {
            override fun onQueryChange(query: String) {
                typed = query
            }
            override fun onLaunch(app: AppInfo, bounds: Rect?) {
                launched = app
            }
            override fun onWebSearch(query: String) {
                web = query
            }
        }
        compose.setContent {
            TestTheme {
                SearchScreen(
                    query = "cam",
                    results = SearchResults(apps = listOf(TestData.camera), settings = listOf(SettingsShortcuts.all.first())),
                    suggestions = emptyList(),
                    contactsAllowed = true,
                    state = TestData.homeState(),
                    actions = actions,
                )
            }
        }
        compose.onNodeWithTag("search-field").performTextInput("e")
        assertTrue(typed.contains("e"))
        compose.onNodeWithContentDescription("Camera").performClick()
        assertEquals(TestData.camera, launched)
        compose.onNodeWithText("Wi-Fi").assertExists()
        compose.onNodeWithContentDescription("Search the web for cam").performClick()
        assertEquals("cam", web)
    }

    @Test
    fun libraryGroupsAndAlphabet() {
        var launched: AppInfo? = null
        val groups = Library.groups(TestData.allApps, emptyMap(), 0L)
        val sections = Library.alphabetical(TestData.allApps)
        compose.setContent {
            TestTheme {
                LibraryScreen(
                    groups = groups,
                    sections = sections,
                    state = TestData.homeState(),
                    appCount = TestData.allApps.size,
                    onLaunch = { app, _ -> launched = app },
                    onAppMenu = { _, _ -> },
                    onSearch = {},
                    onClose = {},
                )
            }
        }
        compose.onNodeWithText("5 APPS").assertExists()
        compose.onNodeWithText("Creativity").assertExists()
        compose.onNodeWithContentDescription("Camera").performClick()
        assertEquals(TestData.camera, launched)
        compose.onNodeWithText("A – Z").performClick()
        compose.onNodeWithTag("library-az").assertExists()
        compose.onNodeWithText("Calculator").assertExists()
        // Zine is below the fold on the small test screen; the letter rail scrolls to it.
        compose.onNodeWithContentDescription("Jump to Z").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Zine").assertExists()
    }

    @Test
    fun settingsSwitchesUpdateSettings() {
        var settings = LauncherSettings()
        var action: SettingsAction? = null
        compose.setContent {
            TestTheme {
                SettingsScreen(
                    settings = settings,
                    info = SettingsInfo(isDefaultLauncher = false),
                    onUpdate = { transform -> settings = transform(settings) },
                    onAction = { action = it },
                    onSetCity = {},
                    onClose = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Icon labels").performClick()
        assertFalse(settings.showLabels)
        compose.onNodeWithText("Make Morse your home screen").performClick()
        assertEquals(SettingsAction.SET_DEFAULT_LAUNCHER, action)
    }

    @Test
    fun actionSheetRunsActions() {
        var ran = false
        var dismissed = false
        compose.setContent {
            TestTheme {
                ActionSheet(
                    title = "Camera",
                    subtitle = null,
                    actions = listOf(MenuAction("App info", Glyph.INFO) { ran = true }),
                    onDismiss = { dismissed = true },
                )
            }
        }
        compose.onNode(hasText("App info") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(ran)
        compose.onNodeWithTag("menu").performTouchInput { click(Offset(8f, 8f)) }
        assertTrue(dismissed)
    }
}
