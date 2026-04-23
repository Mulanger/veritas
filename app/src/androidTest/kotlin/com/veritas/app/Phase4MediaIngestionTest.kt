package com.veritas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.veritas.app.debug.testing.Phase4TestActivity
import com.veritas.app.debug.testing.Phase4TestHarness
import com.veritas.feature.home.HOME_PICK_FILE_TAG
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase4MediaIngestionTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<*>

    @Before
    fun setUp() {
        Phase4TestHarness.reset()
        clearIngestedMedia()
        initializeTestWorkManager()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
        Phase4TestHarness.reset()
        clearIngestedMedia()
    }

    @Test
    fun filePickerFlowCopiesVideoAndShowsStubScreen() {
        Phase4TestHarness.visualUri = copyAssetToTempFile("phase4_short.mp4").toUri()

        scenario = ActivityScenario.launch(Phase4TestActivity::class.java)
        composeRule.onNodeWithTag(HOME_PICK_FILE_TAG).performClick()
        composeRule.onNodeWithTag(MEDIA_PICKER_VISUAL_TAG).performClick()

        assertTagCount(SCAN_STUB_SCREEN_TAG, 1)
        composeRule.runOnIdle {
            assertTrue(ingestedFiles().single().exists())
        }
    }

    @Test
    fun shareIntentWithVideoRoutesToStubScreen() {
        val sharedUri = copyAssetToTempFile("phase4_short.mp4").toUri()
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                setClass(ApplicationProvider.getApplicationContext(), ShareTargetActivity::class.java)
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, sharedUri)
            }

        scenario = ActivityScenario.launch<ShareTargetActivity>(intent)

        assertTagCount(SCAN_STUB_SCREEN_TAG, 1)
        composeRule.runOnIdle {
            assertTrue(ingestedFiles().single().exists())
        }
    }

    @Test
    fun oversizedFileShowsTooLargeError() {
        val oversizedFile = createSparseFile("oversized.mp4", 201L * 1024L * 1024L)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                setClass(ApplicationProvider.getApplicationContext(), ShareTargetActivity::class.java)
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, oversizedFile.toUri())
            }

        scenario = ActivityScenario.launch<ShareTargetActivity>(intent)

        assertTagCount(INGESTION_ERROR_SCREEN_TAG, 1)
        assertTextPresent("This file is too large")
    }

    @Test
    fun wrongMimeShowsUnsupportedFormatError() {
        val bogusImage = writeTextFile("not-an-image.png", "plain text")
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                setClass(ApplicationProvider.getApplicationContext(), ShareTargetActivity::class.java)
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, bogusImage.toUri())
            }

        scenario = ActivityScenario.launch<ShareTargetActivity>(intent)

        assertTagCount(INGESTION_ERROR_SCREEN_TAG, 1)
        assertTextPresent("This format isn't supported")
    }

    @Test
    fun autoPurgeDeletesCopiedFileAfterDelay() {
        Phase4TestHarness.visualUri = copyAssetToTempFile("phase4_short.mp4").toUri()

        scenario = ActivityScenario.launch(Phase4TestActivity::class.java)
        composeRule.onNodeWithTag(HOME_PICK_FILE_TAG).performClick()
        composeRule.onNodeWithTag(MEDIA_PICKER_VISUAL_TAG).performClick()

        assertTagCount(SCAN_STUB_SCREEN_TAG, 1)
        val ingestedFile = ingestedFiles().single()
        val workManager = WorkManager.getInstance(targetContext())
        val workInfos =
            workManager
                .getWorkInfosForUniqueWork("purge-scanned-media-${ingestedFile.nameWithoutExtension}")
                .get(5, TimeUnit.SECONDS)
        val workId = workInfos.single().id

        WorkManagerTestInitHelper.getTestDriver(targetContext())?.setInitialDelayMet(workId)

        composeRule.waitUntil(timeoutMillis = 5_000) { !ingestedFile.exists() }
        assertTrue(!ingestedFile.exists())
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

    private fun copyAssetToTempFile(assetName: String): File {
        val assetFile = File(targetContext().cacheDir, "phase4-assets\\$assetName")
        assetFile.parentFile?.mkdirs()
        InstrumentationRegistry.getInstrumentation().context.assets.open(assetName).use { input ->
            assetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return assetFile
    }

    private fun writeTextFile(
        name: String,
        contents: String,
    ): File =
        File(targetContext().cacheDir, "phase4-assets\\$name").apply {
            parentFile?.mkdirs()
            writeText(contents)
        }

    private fun createSparseFile(
        name: String,
        length: Long,
    ): File =
        File(targetContext().cacheDir, "phase4-assets\\$name").apply {
            parentFile?.mkdirs()
            RandomAccessFile(this, "rw").use { file ->
                file.setLength(length)
            }
        }

    private fun clearIngestedMedia() {
        File(targetContext().cacheDir, "ingested_media").deleteRecursively()
    }

    private fun ingestedFiles(): List<File> =
        File(targetContext().cacheDir, "ingested_media")
            .listFiles()
            ?.filter(File::isFile)
            .orEmpty()

    private fun initializeTestWorkManager() {
        val configuration =
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .build()
        try {
            WorkManagerTestInitHelper.initializeTestWorkManager(targetContext(), configuration)
        } catch (_: IllegalStateException) {
        }
    }

    private fun File.toUri(): Uri = Uri.fromFile(this)

    private fun targetContext(): Context = ApplicationProvider.getApplicationContext()
}
