@file:Suppress("FunctionName", "LongMethod", "MagicNumber", "TooManyFunctions")

package com.veritas.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.veritas.core.design.VeritasButton
import com.veritas.core.design.VeritasButtonVariant
import com.veritas.core.design.VeritasColors
import com.veritas.core.design.VeritasScaffold
import com.veritas.core.design.VeritasTag
import com.veritas.core.design.VeritasType
import com.veritas.domain.detection.ScannedMedia
import java.text.DecimalFormat
import java.util.Locale

@Composable
fun ScanStubScreen(
    media: ScannedMedia,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VeritasScaffold(
        onClose = onClose,
        modifier = modifier.testTag(SCAN_STUB_SCREEN_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                VeritasTag(text = stubTag(media))
                Text(
                    text = "Ready to scan.",
                    style = VeritasType.displayMd.copy(color = VeritasColors.ink),
                )
                Text(
                    text =
                        "Veritas copied this file into private storage and queued it " +
                            "for the stub scan flow. The real scanning UI arrives in Phase 5.",
                    style = VeritasType.bodyLg.copy(color = VeritasColors.inkDim),
                    lineHeight = 24.sp,
                )
                StubPreviewCard(media = media)
                MetadataStrip(media = media)
            }
            VeritasButton(
                text = "Done",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                testTag = SCAN_STUB_DONE_TAG,
            )
        }
    }
}

@Composable
fun IngestionErrorScreen(
    error: IngestionErrorScreen,
    onDone: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = error.title()
    val body = error.body()
    val primaryText =
        if (error == IngestionErrorScreen.STORAGE_FULL) {
            "Open storage settings"
        } else {
            "Done"
        }
    val primaryAction =
        if (error == IngestionErrorScreen.STORAGE_FULL) {
            onOpenStorageSettings
        } else {
            onDone
        }
    val showSecondary = error == IngestionErrorScreen.STORAGE_FULL

    VeritasScaffold(
        onClose = onDone,
        modifier = modifier.testTag(INGESTION_ERROR_SCREEN_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                VeritasTag(text = "INGESTION ERROR")
                ErrorPreviewCard(error = error)
                Text(
                    text = title,
                    style = VeritasType.displayMd.copy(color = VeritasColors.ink),
                )
                Text(
                    text = body,
                    style = VeritasType.bodyLg.copy(color = VeritasColors.inkDim),
                    lineHeight = 24.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VeritasButton(
                    text = primaryText,
                    onClick = primaryAction,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = INGESTION_ERROR_PRIMARY_TAG,
                )
                if (showSecondary) {
                    VeritasButton(
                        text = "Done",
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                        variant = VeritasButtonVariant.Ghost,
                        testTag = INGESTION_ERROR_SECONDARY_TAG,
                    )
                }
            }
        }
    }
}

@Composable
private fun StubPreviewCard(media: ScannedMedia) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .border(1.dp, VeritasColors.line2, RoundedCornerShape(18.dp))
                .background(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    VeritasColors.panel2,
                                    VeritasColors.bg,
                                ),
                        ),
                    shape = RoundedCornerShape(18.dp),
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .size(18.dp)
                    .border(1.dp, VeritasColors.accent, RoundedCornerShape(4.dp)),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(18.dp)
                    .border(1.dp, VeritasColors.accent, RoundedCornerShape(4.dp)),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(84.dp)
                    .background(VeritasColors.panel, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = media.mediaType.name,
                style =
                    VeritasType.monoXs.copy(
                        color = VeritasColors.accent,
                        fontWeight = FontWeight.W700,
                        letterSpacing = 0.12.em,
                    ),
            )
        }
        Text(
            text = "Stub scan handoff",
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
            style = VeritasType.bodySm.copy(color = VeritasColors.inkMute),
        )
    }
}

@Composable
private fun MetadataStrip(media: ScannedMedia) {
    val durationMs = media.durationMs

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, VeritasColors.line, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetadataRow(label = "TYPE", value = media.mimeType.uppercase(Locale.US))
        HorizontalDivider(color = VeritasColors.line)
        MetadataRow(label = "SIZE", value = formatFileSize(media.sizeBytes))
        if (durationMs != null) {
            HorizontalDivider(color = VeritasColors.line)
            MetadataRow(label = "DURATION", value = formatDuration(durationMs))
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
        )
        Text(
            text = value,
            style = VeritasType.bodySm.copy(color = VeritasColors.ink),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ErrorPreviewCard(error: IngestionErrorScreen) {
    val tone =
        when (error) {
            IngestionErrorScreen.FILE_TOO_LARGE,
            IngestionErrorScreen.VIDEO_TOO_LONG,
            IngestionErrorScreen.AUDIO_TOO_LONG,
            -> VeritasColors.warn

            IngestionErrorScreen.UNSUPPORTED_FORMAT,
            IngestionErrorScreen.CORRUPTED_FILE,
            IngestionErrorScreen.STORAGE_FULL,
            -> VeritasColors.bad
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, VeritasColors.line2, RoundedCornerShape(18.dp))
                .background(VeritasColors.panel, RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(54.dp)
                    .background(tone.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .background(tone, RoundedCornerShape(4.dp)),
            )
        }
        Text(
            text = error.previewLabel(),
            style =
                VeritasType.monoXs.copy(
                    color = tone,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 0.12.em,
                ),
        )
    }
}

private fun stubTag(media: ScannedMedia): String {
    val duration =
        media.durationMs?.let(::formatDuration)
            ?: "${media.widthPx ?: 0}x${media.heightPx ?: 0}"
    return "STUB SCAN | ${media.mediaType.name} | $duration"
}

private fun IngestionErrorScreen.title(): String =
    when (this) {
        IngestionErrorScreen.FILE_TOO_LARGE -> "This file is too large"
        IngestionErrorScreen.VIDEO_TOO_LONG,
        IngestionErrorScreen.AUDIO_TOO_LONG,
        -> "This clip is too long"
        IngestionErrorScreen.UNSUPPORTED_FORMAT -> "This format isn't supported"
        IngestionErrorScreen.CORRUPTED_FILE -> "This file looks damaged"
        IngestionErrorScreen.STORAGE_FULL -> "Your phone is low on storage"
    }

private fun IngestionErrorScreen.body(): String =
    when (this) {
        IngestionErrorScreen.FILE_TOO_LARGE ->
            "Veritas can check videos up to 200 MB. Try a shorter clip or a lower-resolution copy."
        IngestionErrorScreen.VIDEO_TOO_LONG ->
            "Veritas v1 handles videos up to 60 seconds and audio up to 3 minutes. Longer support is coming."
        IngestionErrorScreen.AUDIO_TOO_LONG ->
            "Veritas v1 handles videos up to 60 seconds and audio up to 3 minutes. Longer support is coming."
        IngestionErrorScreen.UNSUPPORTED_FORMAT ->
            "Veritas doesn't recognize this file format. We support common formats: " +
                "MP4, WebM for video; MP3, AAC, WAV, M4A for audio; JPEG, PNG, WebP, HEIC for images."
        IngestionErrorScreen.CORRUPTED_FILE ->
            "This file didn't decode properly. It might be damaged or only partially downloaded."
        IngestionErrorScreen.STORAGE_FULL ->
            "Your phone is low on storage. Free up some space and try again."
    }

private fun IngestionErrorScreen.previewLabel(): String =
    when (this) {
        IngestionErrorScreen.FILE_TOO_LARGE -> "SIZE LIMIT"
        IngestionErrorScreen.VIDEO_TOO_LONG -> "VIDEO LIMIT"
        IngestionErrorScreen.AUDIO_TOO_LONG -> "AUDIO LIMIT"
        IngestionErrorScreen.UNSUPPORTED_FORMAT -> "FORMAT"
        IngestionErrorScreen.CORRUPTED_FILE -> "DECODE"
        IngestionErrorScreen.STORAGE_FULL -> "STORAGE"
    }

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(Locale.US, minutes, seconds)
}

private fun formatFileSize(sizeBytes: Long): String {
    val formatter = DecimalFormat("0.0")
    val megabytes = sizeBytes / (1024f * 1024f)
    return "${formatter.format(megabytes)} MB"
}

@Composable
fun ShareTargetLoadingScreen() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(VeritasColors.bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(54.dp)
                        .background(VeritasColors.panel, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .background(VeritasColors.accent, RoundedCornerShape(4.dp)),
                )
            }
            Text(
                text = "Preparing share",
                style = VeritasType.headingSm.copy(color = VeritasColors.ink),
            )
            Text(
                text = "Copying the file into private storage and validating it first.",
                style = VeritasType.bodySm.copy(color = VeritasColors.inkDim),
                textAlign = TextAlign.Center,
            )
        }
    }
}
