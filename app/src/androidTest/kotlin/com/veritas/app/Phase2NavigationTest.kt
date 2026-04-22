package com.veritas.app

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veritas.app.debug.testing.Phase2TestActivity
import com.veritas.app.debug.testing.Phase2TestHarness
import com.veritas.feature.home.HOME_PICK_FILE_TAG
import com.veritas.feature.home.HOME_RECENT_EMPTY_TAG
import com.veritas.feature.home.HOME_RECENT_LIST_TAG
import com.veritas.feature.home.HOME_SCREEN_TAG
import com.veritas.feature.home.HomeRecentMode
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase2NavigationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<Phase2TestActivity>
    private val filePickerLaunches = AtomicInteger(0)

    @Before
    fun setUp() {
        filePickerLaunches.set(0)
        Phase2TestHarness.reset()
        Phase2TestHarness.initialRecentMode = HomeRecentMode.Empty
        Phase2TestHarness.onPickFile = { filePickerLaunches.incrementAndGet() }
        scenario = ActivityScenario.launch(Phase2TestActivity::class.java)
        composeRule.waitForIdle()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
        Phase2TestHarness.reset()
    }

    @Test
    fun bottomNavNavigatesBetweenTopLevelDestinations() {
        assertTagCount(HOME_SCREEN_TAG, 1)

        composeRule.onNodeWithTag(HISTORY_NAV_TAG).performClick()
        assertTagCount(HISTORY_SCREEN_TAG, 1)

        composeRule.onNodeWithTag(ABOUT_NAV_TAG).performClick()
        assertTagCount(SETTINGS_SCREEN_TAG, 1)

        composeRule.onNodeWithTag(VERIFY_NAV_TAG).performClick()
        assertTagCount(HOME_SCREEN_TAG, 1)
    }

    @Test
    fun bottomNavSelectionSurvivesActivityRecreation() {
        composeRule.onNodeWithTag(ABOUT_NAV_TAG).performClick()
        composeRule.onNodeWithTag(ABOUT_NAV_TAG).assertIsSelected()
        assertTagCount(SETTINGS_SCREEN_TAG, 1)

        scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ABOUT_NAV_TAG).assertIsSelected()
        assertTagCount(SETTINGS_SCREEN_TAG, 1)
    }

    @Test
    fun pickFileUsesInjectedLauncher() {
        composeRule.onNodeWithTag(HOME_PICK_FILE_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals(1, filePickerLaunches.get())
        }
    }

    @Test
    fun homeSupportsEmptyAndPopulatedRecentStates() {
        assertTagCount(HOME_RECENT_EMPTY_TAG, 1)

        scenario.close()
        Phase2TestHarness.initialRecentMode = HomeRecentMode.Populated
        scenario = ActivityScenario.launch(Phase2TestActivity::class.java)
        composeRule.waitForIdle()

        assertTagCount(HOME_RECENT_LIST_TAG, 1)
        assertTextPresent("Shared from TikTok")
    }

    private fun assertTagCount(
        tag: String,
        expected: Int,
    ) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size == expected
        }
        assertEquals(
            expected,
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    private fun assertTextPresent(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
