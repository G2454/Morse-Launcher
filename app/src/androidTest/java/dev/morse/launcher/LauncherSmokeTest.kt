package dev.morse.launcher

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a real device or emulator (CI uses an emulator). Starts the actual
 * launcher with bundled fonts, real repositories and real system services.
 */
@RunWith(AndroidJUnit4::class)
class LauncherSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForHome() {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag("home").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun homeScreenAppears() {
        waitForHome()
        compose.onNodeWithTag("dock").assertExists()
    }

    @Test
    fun searchOpensAndCloses() {
        waitForHome()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithTag("search-field").assertExists()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("search-field").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun libraryOpens() {
        waitForHome()
        compose.onNodeWithContentDescription("App library").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("library").fetchSemanticsNodes().isNotEmpty() }
    }
}
