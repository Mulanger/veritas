package com.veritas.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veritas.app.debug.testing.Phase3TestActivity
import com.veritas.app.debug.testing.Phase3TestHarness
import com.veritas.feature.home.HOME_SCREEN_TAG
import com.veritas.feature.onboarding.ONBOARDING_CAPABILITIES_PRIMARY_TAG
import com.veritas.feature.onboarding.ONBOARDING_HOW_IT_WORKS_PRIMARY_TAG
import com.veritas.feature.onboarding.ONBOARDING_MEDIA_PROJECTION_TAG
import com.veritas.feature.onboarding.ONBOARDING_MEDIA_PROJECTION_PRIMARY_TAG
import com.veritas.feature.onboarding.ONBOARDING_NOTIFICATIONS_ALLOW_TAG
import com.veritas.feature.onboarding.ONBOARDING_OVERLAY_INTRO_TAG
import com.veritas.feature.onboarding.ONBOARDING_OVERLAY_CONTINUE_TAG
import com.veritas.feature.onboarding.ONBOARDING_OVERLAY_ENABLE_TAG
import com.veritas.feature.onboarding.ONBOARDING_NOTIFICATIONS_TAG
import com.veritas.feature.onboarding.ONBOARDING_OVERLAY_PERMISSION_TAG
import com.veritas.feature.onboarding.ONBOARDING_OVERLAY_SETTINGS_TAG
import com.veritas.feature.onboarding.ONBOARDING_READY_TAG
import com.veritas.feature.onboarding.ONBOARDING_READY_PRIMARY_TAG
import com.veritas.feature.onboarding.ONBOARDING_SHARE_SETUP_PRIMARY_TAG
import com.veritas.feature.onboarding.ONBOARDING_WELCOME_TAG
import com.veritas.feature.onboarding.ONBOARDING_WELCOME_PRIMARY_TAG
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class Phase3OnboardingTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<Phase3TestActivity>

    @Before
    fun setUp() {
        Phase3TestHarness.reset()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
        Phase3TestHarness.reset()
    }

    @Test
    fun happyPathCompletesOnboardingAndShowsHome() {
        Phase3TestHarness.supportsNotificationsPermission = true
        Phase3TestHarness.notificationPermissionGrantedAtLaunch = false
        Phase3TestHarness.notificationGrantResult = true
        Phase3TestHarness.overlayGrantResult = true

        launchScenario()

        progressThroughSharedSetup(enableBubble = true)
        assertTagCount(ONBOARDING_OVERLAY_PERMISSION_TAG, 1)
        clickNodeWithTag(ONBOARDING_OVERLAY_SETTINGS_TAG)
        assertTagCount(ONBOARDING_MEDIA_PROJECTION_TAG, 1)
        clickNodeWithTag(ONBOARDING_MEDIA_PROJECTION_PRIMARY_TAG)
        assertTagCount(ONBOARDING_NOTIFICATIONS_TAG, 1)
        clickNodeWithTag(ONBOARDING_NOTIFICATIONS_ALLOW_TAG)
        assertTagCount(ONBOARDING_READY_TAG, 1)
        clickNodeWithTag(ONBOARDING_READY_PRIMARY_TAG)

        assertTagCount(HOME_SCREEN_TAG, 1)
        composeRule.runOnIdle {
            assertEquals(1, Phase3TestHarness.completionCount.get())
            assertTrue(Phase3TestHarness.hasCompletedOnboarding())
        }
    }

    @Test
    fun overlayDeniedCanContinueWithoutBubbleAndShowHome() {
        Phase3TestHarness.supportsNotificationsPermission = false
        Phase3TestHarness.notificationPermissionGrantedAtLaunch = true
        Phase3TestHarness.overlayGrantResult = false

        launchScenario()

        progressThroughSharedSetup(enableBubble = true)
        assertTagCount(ONBOARDING_OVERLAY_PERMISSION_TAG, 1)
        clickNodeWithTag(ONBOARDING_OVERLAY_SETTINGS_TAG)
        assertTagCount(ONBOARDING_OVERLAY_PERMISSION_TAG, 1)
        clickNodeWithTag(ONBOARDING_OVERLAY_CONTINUE_TAG)
        assertTagCount(ONBOARDING_READY_TAG, 1)
        clickNodeWithTag(ONBOARDING_READY_PRIMARY_TAG)

        assertTagCount(HOME_SCREEN_TAG, 1)
        composeRule.runOnIdle {
            assertEquals(1, Phase3TestHarness.completionCount.get())
            assertTrue(Phase3TestHarness.hasCompletedOnboarding())
        }
    }

    @Test
    fun returnFromSettingsFlowAdvancesToEducationAndHome() {
        Phase3TestHarness.supportsNotificationsPermission = false
        Phase3TestHarness.notificationPermissionGrantedAtLaunch = true
        Phase3TestHarness.overlayGrantResult = true

        launchScenario()

        progressThroughSharedSetup(enableBubble = true)
        assertTagCount(ONBOARDING_OVERLAY_PERMISSION_TAG, 1)
        clickNodeWithTag(ONBOARDING_OVERLAY_SETTINGS_TAG)
        assertTagCount(ONBOARDING_MEDIA_PROJECTION_TAG, 1)
        clickNodeWithTag(ONBOARDING_MEDIA_PROJECTION_PRIMARY_TAG)
        assertTagCount(ONBOARDING_READY_TAG, 1)
        clickNodeWithTag(ONBOARDING_READY_PRIMARY_TAG)

        assertTagCount(HOME_SCREEN_TAG, 1)
        composeRule.runOnIdle {
            assertEquals(1, Phase3TestHarness.completionCount.get())
            assertTrue(Phase3TestHarness.hasCompletedOnboarding())
        }
    }

    @Test
    fun completedOnboardingSkipsFlowOnNextLaunch() {
        runBlocking {
            Phase3TestHarness.statusStore.setHasCompletedOnboarding(true)
        }

        scenario = ActivityScenario.launch(Phase3TestActivity::class.java)
        assertTagCount(HOME_SCREEN_TAG, 1)
        assertTagCount(ONBOARDING_WELCOME_TAG, 0)
    }

    private fun launchScenario() {
        scenario = ActivityScenario.launch(Phase3TestActivity::class.java)
        assertTagCount(ONBOARDING_WELCOME_TAG, 1)
    }

    private fun progressThroughSharedSetup(enableBubble: Boolean) {
        clickNodeWithTag(ONBOARDING_WELCOME_PRIMARY_TAG)
        clickNodeWithTag(ONBOARDING_CAPABILITIES_PRIMARY_TAG)
        clickNodeWithTag(ONBOARDING_HOW_IT_WORKS_PRIMARY_TAG)
        clickNodeWithTag(ONBOARDING_SHARE_SETUP_PRIMARY_TAG)
        assertTagCount(ONBOARDING_OVERLAY_INTRO_TAG, 1)
        if (enableBubble) {
            clickNodeWithTag(ONBOARDING_OVERLAY_ENABLE_TAG)
        } else {
            error("Skip path is not used in Phase 3 tests.")
        }
    }

    private fun clickNodeWithTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule
            .onAllNodesWithTag(tag, useUnmergedTree = true)
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
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

}
