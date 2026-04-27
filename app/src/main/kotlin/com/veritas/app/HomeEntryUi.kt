@file:Suppress("FunctionName", "LongMethod", "LongParameterList", "MagicNumber")

package com.veritas.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.core.design.VeritasButton
import com.veritas.core.design.VeritasButtonVariant
import com.veritas.core.design.VeritasColors
import com.veritas.core.design.VeritasType
import com.veritas.data.detection.HistoryItem
import com.veritas.feature.home.HomeRecentMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val MEDIA_PICKER_SHEET_TAG = "media_picker_sheet"
const val MEDIA_PICKER_VISUAL_TAG = "media_picker_visual"
const val MEDIA_PICKER_AUDIO_TAG = "media_picker_audio"
const val PASTE_LINK_SHEET_TAG = "paste_link_sheet"
const val PASTE_LINK_INPUT_TAG = "paste_link_input"
const val PASTE_LINK_SUBMIT_TAG = "paste_link_submit"
const val PASTE_LINK_ERROR_TAG = "paste_link_error"
const val PASTE_LINK_FETCH_ERROR_TAG = "paste_link_fetch_error"
const val MEDIA_PROCESSING_TAG = "media_processing_overlay"
private const val FAKE_FETCH_DELAY_MS = 700L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeritasHomeEntryHost(
    initialRecentMode: HomeRecentMode,
    enableHomeDevMenu: Boolean,
    onPickVisualMedia: () -> Unit,
    onPickAudio: () -> Unit,
    initialPasteLink: String? = null,
    isProcessingSelection: Boolean,
    historyItems: List<HistoryItem> = emptyList(),
    onDeleteHistoryItem: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
    settings: VeritasSettings = VeritasSettings(),
    onSetOverlayEnabled: (Boolean) -> Unit = {},
    onSetBubbleHaptics: (Boolean) -> Unit = {},
    onSetModelAutoUpdate: (Boolean) -> Unit = {},
    onSetModelWifiOnly: (Boolean) -> Unit = {},
    onSetTelemetryOptIn: (Boolean) -> Unit = {},
    onCreateDiagnosticExport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showPickerSheet by rememberSaveable { mutableStateOf(false) }
    var showPasteLinkSheet by rememberSaveable { mutableStateOf(false) }
    var pasteLinkText by rememberSaveable { mutableStateOf(initialPasteLink.orEmpty()) }
    var pasteLinkInvalid by rememberSaveable { mutableStateOf(false) }
    var pasteLinkFetchFailed by rememberSaveable { mutableStateOf(false) }
    var pasteLinkFetching by rememberSaveable { mutableStateOf(false) }
    val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pasteLinkSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialPasteLink) {
        if (!initialPasteLink.isNullOrBlank()) {
            pasteLinkText = initialPasteLink
            showPasteLinkSheet = true
            pasteLinkInvalid = false
            pasteLinkFetchFailed = false
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(VeritasColors.bg),
    ) {
        VeritasApp(
            initialRecentMode = initialRecentMode,
            enableHomeDevMenu = enableHomeDevMenu,
            onPickFile = { showPickerSheet = true },
            onPasteLink = { showPasteLinkSheet = true },
            historyItems = historyItems,
            onDeleteHistoryItem = onDeleteHistoryItem,
            onClearHistory = onClearHistory,
            settings = settings,
            onSetOverlayEnabled = onSetOverlayEnabled,
            onSetBubbleHaptics = onSetBubbleHaptics,
            onSetModelAutoUpdate = onSetModelAutoUpdate,
            onSetModelWifiOnly = onSetModelWifiOnly,
            onSetTelemetryOptIn = onSetTelemetryOptIn,
            onCreateDiagnosticExport = onCreateDiagnosticExport,
        )

        if (showPickerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showPickerSheet = false },
                sheetState = pickerSheetState,
                containerColor = VeritasColors.panel,
                dragHandle = null,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(MEDIA_PICKER_SHEET_TAG)
                            .padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Pick media",
                        style = VeritasType.headingMd.copy(color = VeritasColors.ink),
                    )
                    Text(
                        text = "Choose a photo, video, or audio file to copy into Veritas for scanning.",
                        style = VeritasType.bodySm.copy(color = VeritasColors.inkDim),
                    )
                    HorizontalDivider(color = VeritasColors.line)
                    PickerOption(
                        title = "Photos or videos",
                        body = "Uses the system visual picker.",
                        testTag = MEDIA_PICKER_VISUAL_TAG,
                        onClick = {
                            showPickerSheet = false
                            onPickVisualMedia()
                        },
                    )
                    PickerOption(
                        title = "Audio",
                        body = "Opens the document picker for MP3, AAC, WAV, or M4A files.",
                        testTag = MEDIA_PICKER_AUDIO_TAG,
                        onClick = {
                            showPickerSheet = false
                            onPickAudio()
                        },
                    )
                }
            }
        }

        if (showPasteLinkSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showPasteLinkSheet = false
                    pasteLinkInvalid = false
                    pasteLinkFetchFailed = false
                    pasteLinkFetching = false
                },
                sheetState = pasteLinkSheetState,
                containerColor = VeritasColors.panel,
                dragHandle = null,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(PASTE_LINK_SHEET_TAG)
                            .padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Paste a link",
                        style = VeritasType.headingMd.copy(color = VeritasColors.ink),
                    )
                    Text(
                        text = "We'll fetch the video and check it on your device.",
                        style = VeritasType.bodySm.copy(color = VeritasColors.inkDim),
                    )
                    OutlinedTextField(
                        value = pasteLinkText,
                        onValueChange = { value ->
                            pasteLinkText = value
                            pasteLinkInvalid = false
                            pasteLinkFetchFailed = false
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag(PASTE_LINK_INPUT_TAG),
                        singleLine = false,
                        minLines = 3,
                        textStyle = VeritasType.bodyMd.copy(color = VeritasColors.ink),
                        placeholder = {
                            Text(
                                text = "https://...",
                                style = VeritasType.bodyMd.copy(color = VeritasColors.inkMute),
                            )
                        },
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = VeritasColors.ink,
                                unfocusedTextColor = VeritasColors.ink,
                                focusedBorderColor = VeritasColors.accent,
                                unfocusedBorderColor = VeritasColors.line2,
                                focusedPlaceholderColor = VeritasColors.inkMute,
                                unfocusedPlaceholderColor = VeritasColors.inkMute,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = VeritasColors.accent,
                            ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        supportingText = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Supports TikTok, Instagram Reels, YouTube Shorts, Twitter/X posts.",
                                    style = VeritasType.bodySm.copy(color = VeritasColors.inkMute),
                                )
                                if (pasteLinkInvalid) {
                                    Text(
                                        text = "That doesn't look like a link we can open.",
                                        modifier = Modifier.testTag(PASTE_LINK_ERROR_TAG),
                                        style = VeritasType.bodySm.copy(color = VeritasColors.warn),
                                    )
                                }
                            }
                        },
                    )

                    if (pasteLinkFetchFailed) {
                        ErrorBanner(
                            text = "Couldn't fetch that link. The post might be private or deleted.",
                            testTag = PASTE_LINK_FETCH_ERROR_TAG,
                        )
                    }

                    VeritasButton(
                        text = if (pasteLinkFetching) "Fetching..." else "Check this",
                        onClick = {
                            val candidate = pasteLinkText.trim()
                            if (!candidate.isSupportedUrl()) {
                                pasteLinkInvalid = true
                                return@VeritasButton
                            }
                            pasteLinkInvalid = false
                            pasteLinkFetchFailed = false
                            pasteLinkFetching = true
                            scope.launch {
                                delay(FAKE_FETCH_DELAY_MS)
                                pasteLinkFetching = false
                                pasteLinkFetchFailed = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !pasteLinkFetching && pasteLinkText.trim().isNotEmpty(),
                        testTag = PASTE_LINK_SUBMIT_TAG,
                    )
                }
            }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }

        if (isProcessingSelection) {
            ProcessingSelectionOverlay()
        }
    }
}

@Composable
private fun PickerOption(
    title: String,
    body: String,
    testTag: String,
    onClick: () -> Unit,
) {
    VeritasButton(
        text = title,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        variant = VeritasButtonVariant.Ghost,
        testTag = testTag,
    )
    Text(
        text = body,
        modifier = Modifier.padding(start = 2.dp),
        style = VeritasType.bodySm.copy(color = VeritasColors.inkMute),
    )
}

@Composable
private fun ProcessingSelectionOverlay() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(VeritasColors.bg.copy(alpha = 0.82f))
                .testTag(MEDIA_PROCESSING_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(220.dp)
                    .background(
                        color = VeritasColors.panel,
                        shape = RoundedCornerShape(18.dp),
                    ).padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(58.dp)
                        .background(
                            brush =
                                Brush.radialGradient(
                                    colors =
                                        listOf(
                                            VeritasColors.accent.copy(alpha = 0.18f),
                                            Color.Transparent,
                                        ),
                                ),
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = VeritasColors.accent,
                    trackColor = VeritasColors.line2,
                )
            }
            Text(
                text = "Preparing media",
                style = VeritasType.headingSm.copy(color = VeritasColors.ink),
            )
            Text(
                text = "Copying into private storage and checking the file.",
                style = VeritasType.bodySm.copy(color = VeritasColors.inkDim),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun ErrorBanner(
    text: String,
    testTag: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = VeritasColors.badDim.copy(alpha = 0.38f),
                    shape = RoundedCornerShape(14.dp),
                ).testTag(testTag)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(VeritasColors.bad, CircleShape),
        )
        Text(
            text = text,
            style = VeritasType.bodySm.copy(color = VeritasColors.ink),
        )
    }
}

private fun String.isSupportedUrl(): Boolean {
    val uri = android.net.Uri.parse(this)
    return !uri.scheme.isNullOrBlank() &&
        (uri.scheme == "http" || uri.scheme == "https") &&
        !uri.host.isNullOrBlank()
}
