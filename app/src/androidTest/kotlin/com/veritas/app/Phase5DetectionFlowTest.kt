package com.veritas.app

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veritas.domain.detection.MediaSource
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.ScannedMedia
import java.io.File
import java.util.UUID
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase5DetectionFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<ScanActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun authenticFilenameShowsLooksAuthenticVerdict() {
        scenario = launchScan("phase5_authentic_sample.mp4")

        assertTagPresent(VERDICT_SCREEN_TAG)
        assertTextPresent("Looks\nauthentic.")
    }

    @Test
    fun authenticC2paFilenameShowsVerifiedAuthenticVerdict() {
        scenario = launchScan("phase5_authentic_c2pa_sample.mp4")

        assertTagPresent(VERDICT_SCREEN_TAG)
        assertTextPresent("Verified\nauthentic.")
    }

    @Test
    fun uncertainFilenameShowsUncertainVerdict() {
        scenario = launchScan("phase5_uncertain_sample.mp4")

        assertTagPresent(VERDICT_SCREEN_TAG)
        assertTextPresent("Uncertain.")
        composeRule.onNodeWithTag(VERDICT_PRIMARY_ACTION_TAG).performClick()
        assertTagPresent(FIND_ORIGINAL_SHEET_TAG)
    }

    @Test
    fun syntheticFlowShowsForensicViewAndReasonSheet() {
        scenario = launchScan("phase5_synthetic_sample.mp4")

        assertTagPresent(VERDICT_SCREEN_TAG)
        assertTextPresent("Likely\nsynthetic.")

        composeRule.onNodeWithTag(VERDICT_PRIMARY_ACTION_TAG).performClick()
        assertTagPresent(FORENSIC_SCREEN_TAG)
        assertTextPresent("Heatmap")

        composeRule.onNodeWithTag("${FORENSIC_REASON_TAG_PREFIX}0").performClick()
        assertTagPresent(REASON_DETAIL_SHEET_TAG)

        composeRule.onNodeWithTag(REASON_DETAIL_CLOSE_TAG).performClick()
        assertTagAbsent(REASON_DETAIL_SHEET_TAG)
        assertTagPresent(FORENSIC_SCREEN_TAG)

        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertTagPresent(VERDICT_SCREEN_TAG)
    }

    @Test
    fun cancelDuringScanFinishesActivity() {
        scenario = launchScan("phase5_cancel_sample.mp4")

        assertTagPresent(SCAN_SCREEN_TAG)
        composeRule.onNodeWithTag(SCAN_CLOSE_BUTTON_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            scenario?.state == Lifecycle.State.DESTROYED
        }
        assertTrue(scenario?.state == Lifecycle.State.DESTROYED)
    }

    private fun launchScan(fileName: String): ActivityScenario<ScanActivity> {
        val media = createMedia(fileName)
        val intent = targetContext().buildScanIntent(media)
        return ActivityScenario.launch(intent)
    }

    private fun createMedia(fileName: String): ScannedMedia {
        val file =
            File(targetContext().cacheDir, "phase5-media\\$fileName").apply {
                parentFile?.mkdirs()
                writeBytes(UUID.randomUUID().toString().toByteArray())
            }

        return ScannedMedia(
            id = UUID.randomUUID().toString(),
            uri = Uri.fromFile(file).toString(),
            mediaType = MediaType.VIDEO,
            mimeType = "video/mp4",
            sizeBytes = file.length(),
            durationMs = 23_000,
            widthPx = 1920,
            heightPx = 1080,
            source = MediaSource.FilePicker,
            ingestedAt = Clock.System.now(),
        )
    }

    private fun assertTagPresent(tag: String) {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertTextPresent(text: String) {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertTagAbsent(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun targetContext(): Context = ApplicationProvider.getApplicationContext()
}
