@file:Suppress("FunctionName", "LongMethod", "LongParameterList", "MagicNumber", "MaxLineLength")

package com.veritas.feature.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.veritas.core.design.BrandMark
import com.veritas.core.design.VerdictPill
import com.veritas.core.design.VeritasButton
import com.veritas.core.design.VeritasColors
import com.veritas.core.design.VeritasType
import com.veritas.domain.detection.MediaType
import com.veritas.domain.detection.VerdictOutcome
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val HISTORY_SCREEN_ROOT_TAG = "screen_history"
const val HISTORY_EMPTY_STATE_TAG = "history_empty_state"
const val HISTORY_LIST_TAG = "history_list"
const val HISTORY_ITEM_TAG_PREFIX = "history_item_"
const val HISTORY_CONTEXT_MENU_TAG = "history_context_menu"

data class HistoryListItemUi(
    val id: String,
    val mediaType: MediaType,
    val durationMs: Long?,
    val sourceLabel: String,
    val thumbnailPath: String,
    val verdictOutcome: VerdictOutcome,
    val scannedAtEpochMs: Long,
)

@Composable
fun HistoryScreen(
    items: List<HistoryListItemUi>,
    onCheckSomething: () -> Unit,
    onOpenItem: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onExportDiagnostic: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(VeritasColors.bg),
    ) {
        HistoryTopBar()
        if (items.isEmpty()) {
            HistoryEmptyState(onCheckSomething = onCheckSomething)
        } else {
            HistoryList(
                items = items,
                onOpenItem = onOpenItem,
                onDeleteItem = onDeleteItem,
                onExportDiagnostic = onExportDiagnostic,
            )
        }
    }
}

@Composable
private fun HistoryEmptyState(onCheckSomething: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().testTag(HISTORY_EMPTY_STATE_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HistoryEmptyIllustration()
            Text(
                text = stringResource(R.string.history_empty_title),
                style = VeritasType.headingLg.copy(color = VeritasColors.ink),
            )
            Text(
                text = stringResource(R.string.history_empty_body),
                style = VeritasType.bodyMd.copy(color = VeritasColors.inkDim),
                textAlign = TextAlign.Center,
            )
            VeritasButton(
                text = stringResource(R.string.history_check_something),
                onClick = onCheckSomething,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HistoryList(
    items: List<HistoryListItemUi>,
    onOpenItem: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onExportDiagnostic: (String) -> Unit,
) {
    val groups = remember(items) { items.groupBy(::historyGroup) }
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(HISTORY_LIST_TAG)
                .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(6.dp)) }
        groups.forEach { (group, groupItems) ->
            item(key = group.name) {
                Text(
                    text = stringResource(group.labelRes).uppercase(),
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    style = VeritasType.monoXs.copy(color = VeritasColors.inkMute, fontWeight = FontWeight.W700),
                )
            }
            items(
                items = groupItems,
                key = { it.id },
            ) { item ->
                HistoryItemRow(
                    item = item,
                    onOpen = { onOpenItem(item.id) },
                    onDelete = { onDeleteItem(item.id) },
                    onExportDiagnostic = { onExportDiagnostic(item.id) },
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItemRow(
    item: HistoryListItemUi,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onExportDiagnostic: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = onOpen,
                        onLongClick = { menuExpanded = true },
                    )
                    .testTag("$HISTORY_ITEM_TAG_PREFIX${item.id}")
                    .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = File(item.thumbnailPath),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VeritasColors.panel)
                        .border(1.dp, VeritasColors.line, RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.sourceLabel,
                    style = VeritasType.bodyMd.copy(color = VeritasColors.ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.history_media_meta, item.mediaType.name.lowercase(), formatTimestamp(item.scannedAtEpochMs)),
                    style = VeritasType.bodySm.copy(color = VeritasColors.inkMute),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            VerdictPill(
                text = pillText(item.verdictOutcome),
                tone =
                    when (item.verdictOutcome) {
                        VerdictOutcome.VERIFIED_AUTHENTIC,
                        VerdictOutcome.LIKELY_AUTHENTIC,
                        -> com.veritas.core.design.VerdictTone.Ok
                        VerdictOutcome.UNCERTAIN -> com.veritas.core.design.VerdictTone.Warn
                        VerdictOutcome.LIKELY_SYNTHETIC -> com.veritas.core.design.VerdictTone.Bad
                    },
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.testTag(HISTORY_CONTEXT_MENU_TAG),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_export_diagnostic)) },
                onClick = {
                    menuExpanded = false
                    onExportDiagnostic()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_delete)) },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
    HorizontalDivider(color = VeritasColors.line)
}

@Composable
private fun HistoryTopBar() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = 22.dp,
                    end = 22.dp,
                    top = 18.dp,
                    bottom = 14.dp,
                ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark()
            Text(
                text = stringResource(R.string.history_brand),
                style =
                    VeritasType.monoSm.copy(
                        color = VeritasColors.ink,
                        fontSize = 12.sp,
                    ),
            )
        }
        Text(
            text = stringResource(R.string.history_title),
            style =
                VeritasType.monoXs.copy(
                    color = VeritasColors.inkMute,
                    fontWeight = FontWeight.W700,
                ),
        )
    }
    HorizontalDivider(color = VeritasColors.line)
}

@Composable
private fun HistoryEmptyIllustration() {
    Box(
        modifier =
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(1.dp, VeritasColors.line2, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, VeritasColors.inkMute, RoundedCornerShape(6.dp)),
        )
        Spacer(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(VeritasColors.accent),
        )
    }
}

private fun historyGroup(item: HistoryListItemUi): HistoryGroup {
    val itemDate = Instant.ofEpochMilli(item.scannedAtEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when {
        itemDate == today -> HistoryGroup.Today
        itemDate == today.minusDays(1) -> HistoryGroup.Yesterday
        itemDate.isAfter(today.minusDays(7)) -> HistoryGroup.ThisWeek
        else -> HistoryGroup.Earlier
    }
}

private fun formatTimestamp(epochMs: Long): String =
    DateTimeFormatter.ofPattern("MMM d, HH:mm").format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

@Composable
private fun pillText(outcome: VerdictOutcome): String =
    when (outcome) {
        VerdictOutcome.VERIFIED_AUTHENTIC -> stringResource(R.string.history_pill_verified)
        VerdictOutcome.LIKELY_AUTHENTIC -> stringResource(R.string.history_pill_authentic)
        VerdictOutcome.UNCERTAIN -> stringResource(R.string.history_pill_uncertain)
        VerdictOutcome.LIKELY_SYNTHETIC -> stringResource(R.string.history_pill_synthetic)
    }

private enum class HistoryGroup(
    val labelRes: Int,
) {
    Today(R.string.history_group_today),
    Yesterday(R.string.history_group_yesterday),
    ThisWeek(R.string.history_group_this_week),
    Earlier(R.string.history_group_earlier),
}
