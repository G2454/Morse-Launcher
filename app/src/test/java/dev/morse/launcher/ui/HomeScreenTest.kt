package dev.morse.launcher.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.morse.core.apps.AppKey
import dev.morse.core.layout.HomeItem
import dev.morse.core.settings.LauncherSettings
import dev.morse.launcher.TestData
import dev.morse.launcher.TestTheme
import dev.morse.launcher.ui.home.HomeCallbacks
import dev.morse.launcher.ui.home.HomeScreen
import dev.morse.launcher.ui.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(state: HomeUiState, callbacks: HomeCallbacks) {
        compose.setContent {
            TestTheme {
                val pager: PagerState = rememberPagerState { state.layout.pages.size }
                HomeScreen(state, pager, callbacks)
            }
        }
    }

    @Test
    fun tappingAnAppLaunchesIt() {
        var launched: AppKey? = null
        show(TestData.homeState(), HomeCallbacks(onLaunch = { app, _ -> launched = app }))
        compose.onNodeWithContentDescription("Camera").assertIsDisplayed().performClick()
        assertEquals(TestData.camera.key, launched)
    }

    @Test
    fun appsWithoutAGlyphStillShowTheirLabel() {
        show(TestData.homeState(), HomeCallbacks())
        compose.onNodeWithText("Zine").assertIsDisplayed()
    }

    @Test
    fun tappingAFolderOpensIt() {
        var opened: String? = null
        show(TestData.homeState(), HomeCallbacks(onOpenFolder = { _, id, _ -> opened = id }))
        compose.onNodeWithContentDescription("Folder Tools").performClick()
        assertEquals("tools", opened)
    }

    @Test
    fun dockAppsLaunch() {
        var launched: AppKey? = null
        show(TestData.homeState(), HomeCallbacks(onLaunch = { app, _ -> launched = app }))
        compose.onNodeWithContentDescription("Phone").performClick()
        assertEquals(TestData.phone.key, launched)
    }

    @Test
    fun searchPillAndLibraryButton() {
        var search = false
        var library = false
        show(TestData.homeState(), HomeCallbacks(onOpenSearch = { search = true }, onOpenLibrary = { library = true }))
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithContentDescription("App library").performClick()
        assertTrue(search)
        assertTrue(library)
    }

    @Test
    fun labelsCanBeHidden() {
        show(TestData.homeState(settings = LauncherSettings(showLabels = false)), HomeCallbacks())
        compose.onNodeWithText("Zine").assertDoesNotExist()
        // Still reachable by screen readers.
        compose.onNodeWithContentDescription("Zine").assertExists()
    }

    @Test
    fun longPressOnAnAppOpensItsMenu() {
        var menuFor: HomeItem? = null
        show(TestData.homeState(), HomeCallbacks(onItemMenu = { _, item, _ -> menuFor = item }))
        compose.onNodeWithContentDescription("Camera").performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals("cam", menuFor?.id)
    }

    @Test
    fun editModeShowsDoneAndRemoveButtons() {
        var done = false
        var removed: String? = null
        show(
            TestData.homeState(editMode = true),
            HomeCallbacks(onExitEdit = { done = true }, onRemoveItem = { _, id -> removed = id }),
        )
        compose.onNodeWithText("EDITING · PAGE 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove Camera").performClick()
        assertEquals("cam", removed)
        compose.onNodeWithText("Done").performClick()
        assertTrue(done)
    }

    @Test
    fun editModeDoesNotLaunchApps() {
        var launched = false
        show(TestData.homeState(editMode = true), HomeCallbacks(onLaunch = { _, _ -> launched = true }))
        compose.onNodeWithContentDescription("Camera").performClick()
        assertTrue(!launched)
    }

    @Test
    fun homeAndDockAreTagged() {
        show(TestData.homeState(), HomeCallbacks())
        compose.onNodeWithTag("home").assertExists()
        compose.onNodeWithTag("dock").assertExists()
    }
}
