@file:Suppress("FunctionName", "FunctionNaming", "LongParameterList", "TooManyFunctions", "MagicNumber")

package com.veritas.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.veritas.core.design.BrandMark
import com.veritas.core.design.VerdictPill
import com.veritas.core.design.VerdictTone
import com.veritas.core.design.VeritasButton
import com.veritas.core.design.VeritasButtonVariant
import com.veritas.core.design.VeritasColors
import com.veritas.core.design.VeritasTag
import com.veritas.core.design.VeritasType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

const val HOME_SCREEN_TAG = "screen_home"
const val HOME_PICK_FILE_TAG = "home_pick_file"
const val HOME_PASTE_LINK_TAG = "home_paste_link"
const val HOME_RECENT_EMPTY_TAG = "home_recent_empty"
const val HOME_RECENT_LIST_TAG = "home_recent_list"

enum class HomeRecentMode {
    Empty,
    Populated,
}

enum class RecentMediaType {
    Video,
    Audio,
    Image,
}

data class HistoryItem(
    val id: String,
    val source: String,
    val timestamp: String,
    val verdictLabel: String,
    val verdictTone: VerdictTone,
    val mediaType: RecentMediaType,
)

data class HomeUiState(
    val recentItems: List<HistoryItem>,
)

@Composable
fun HomeRoute(
    initialRecentMode: HomeRecentMode,
    enableDebugMenu: Boolean,
    onPickFile: () -> Unit,
    onPasteLink: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(initialRecentMode),
    )
    val uiState by homeViewModel.uiState.collectAsState()

    HomeScreen(
        uiState = uiState,
        enableDebugMenu = enableDebugMenu,
        onPickFile = onPickFile,
        onPasteLink = onPasteLink,
        onOpenSettings = onOpenSettings,
        onOpenRecent = onOpenRecent,
        onSetRecentMode = homeViewModel::setRecentMode,
        modifier = modifier,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    enableDebugMenu: Boolean,
    onPickFile: () -> Unit,
    onPasteLink: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecent: () -> Unit,
    onSetRecentMode: (HomeRecentMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(VeritasColors.bg)
                .testTag(HOME_SCREEN_TAG),
    ) {
        HomeTopBar(onOpenSettings = onOpenSettings)
        HomeHero(
            enableDebugMenu = enableDebugMenu,
            onPickFile = onPickFile,
            onPasteLink = onPasteLink,
            onSetRecentMode = onSetRecentMode,
        )
        HomeRecentSection(
            recentItems = uiState.recentItems,
            onOpenRecent = onOpenRecent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeTopBar(onOpenSettings: () -> Unit) {
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
                text = "VERITAS",
                style =
                    VeritasType.monoSm.copy(
                        color = VeritasColors.ink,
                        fontSize = 12.sp,
                    ),
            )
        }
        Text(
            text = "SETTINGS",
            modifier = Modifier.clickable(onClick = onOpenSettings),
            style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
        )
    }
    HorizontalDivider(color = VeritasColors.line)
}

@Composable
private fun HomeHero(
    enableDebugMenu: Boolean,
    onPickFile: () -> Unit,
    onPasteLink: () -> Unit,
    onSetRecentMode: (HomeRecentMode) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(VeritasColors.bg),
    ) {
        HeroGlow()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 22.dp,
                        end = 22.dp,
                        top = 18.dp,
                        bottom = 22.dp,
                    ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DebugReadyTag(
                enableDebugMenu = enableDebugMenu,
                onSetRecentMode = onSetRecentMode,
            )
            Text(
                text = homeTitle(),
                style =
                    VeritasType.displayMd.copy(
                        color = VeritasColors.ink,
                        lineHeight = 30.sp,
                    ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VeritasButton(
                    text = "Pick a file",
                    onClick = onPickFile,
                    modifier = Modifier.weight(1f).testTag(HOME_PICK_FILE_TAG),
                    variant = VeritasButtonVariant.Primary,
                )
                VeritasButton(
                    text = "Paste link",
                    onClick = onPasteLink,
                    modifier = Modifier.weight(1f).testTag(HOME_PASTE_LINK_TAG),
                    variant = VeritasButtonVariant.Ghost,
                )
            }
        }
    }
    HorizontalDivider(color = VeritasColors.line)
}

@Composable
private fun BoxScope.HeroGlow() {
    Box(
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-92).dp)
                .size(240.dp)
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
    )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    VeritasColors.accent.copy(alpha = 0.05f),
                                    Color.Transparent,
                                ),
                        ),
                ),
    )
}

@Composable
private fun DebugReadyTag(
    enableDebugMenu: Boolean,
    onSetRecentMode: (HomeRecentMode) -> Unit,
) {
    if (!enableDebugMenu) {
        VeritasTag(text = "READY")
        return
    }

    var showDebugMenu by rememberSaveable { mutableStateOf(false) }

    Box {
        VeritasTag(
            text = "READY",
            modifier =
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { showDebugMenu = true },
                ),
        )
        DropdownMenu(
            expanded = showDebugMenu,
            onDismissRequest = { showDebugMenu = false },
            modifier =
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(VeritasColors.panel),
        ) {
            DropdownMenuItem(
                text = { Text("Empty history", style = VeritasType.bodySm) },
                onClick = {
                    onSetRecentMode(HomeRecentMode.Empty)
                    showDebugMenu = false
                },
            )
            DropdownMenuItem(
                text = { Text("Mock history", style = VeritasType.bodySm) },
                onClick = {
                    onSetRecentMode(HomeRecentMode.Populated)
                    showDebugMenu = false
                },
            )
        }
    }
}

@Composable
private fun HomeRecentSection(
    recentItems: List<HistoryItem>,
    onOpenRecent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = 22.dp,
                    end = 22.dp,
                    top = 18.dp,
                ),
    ) {
        Text(
            text = recentLabel(recentItems),
            style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (recentItems.isEmpty()) {
            EmptyRecentCard(modifier = Modifier.fillMaxWidth())
        } else {
            Column(
                modifier = Modifier.testTag(HOME_RECENT_LIST_TAG),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                recentItems.take(3).forEachIndexed { index, item ->
                    RecentHistoryRow(
                        item = item,
                        onClick = onOpenRecent,
                    )
                    if (index < recentItems.lastIndex.coerceAtMost(2)) {
                        HorizontalDivider(color = VeritasColors.line)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRecentCard(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .testTag(HOME_RECENT_EMPTY_TAG)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = 1.dp,
                    color = VeritasColors.line2,
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(
                    horizontal = 20.dp,
                    vertical = 28.dp,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, VeritasColors.line2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .border(1.dp, VeritasColors.inkMute, RoundedCornerShape(3.dp)),
            )
        }
        Text(
            text = "No checks yet.",
            style = VeritasType.headingSm.copy(color = VeritasColors.inkDim),
        )
        Text(
            text = "Share a file to get started.",
            style = VeritasType.bodySm.copy(color = VeritasColors.inkMute),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RecentHistoryRow(
    item: HistoryItem,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecentThumbnail(mediaType = item.mediaType)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.source,
                style = VeritasType.bodySm.copy(color = VeritasColors.inkDim),
            )
            Text(
                text = item.timestamp,
                style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
            )
        }
        VerdictPill(
            text = item.verdictLabel,
            tone = item.verdictTone,
        )
    }
}

@Composable
private fun RecentThumbnail(mediaType: RecentMediaType) {
    val backgroundBrush =
        when (mediaType) {
            RecentMediaType.Video ->
                Brush.linearGradient(
                    colors =
                        listOf(
                            VeritasColors.line2,
                            VeritasColors.panel,
                        ),
                )
            RecentMediaType.Audio ->
                Brush.linearGradient(
                    colors =
                        listOf(
                            VeritasColors.panel2,
                            VeritasColors.bg,
                        ),
                )
            RecentMediaType.Image ->
                Brush.linearGradient(
                    colors =
                        listOf(
                            VeritasColors.accent.copy(alpha = 0.24f),
                            VeritasColors.ok.copy(alpha = 0.14f),
                        ),
                )
        }

    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(backgroundBrush),
        contentAlignment = Alignment.Center,
    ) {
        when (mediaType) {
            RecentMediaType.Video -> VideoThumbnailHighlight()
            RecentMediaType.Audio -> AudioThumbnailBars()
            RecentMediaType.Image -> ImageThumbnailPlane()
        }
    }
}

@Composable
private fun VideoThumbnailHighlight() {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.radialGradient(
                        colors =
                            listOf(
                                VeritasColors.accent.copy(alpha = 0.16f),
                                Color.Transparent,
                            ),
                    ),
                ),
    )
}

@Composable
private fun AudioThumbnailBars() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(6) { index ->
            Box(
                modifier =
                    Modifier
                        .width(2.dp)
                        .height((10 + (index % 3) * 4).dp)
                        .background(VeritasColors.inkMute, RectangleShape),
            )
        }
    }
}

@Composable
private fun ImageThumbnailPlane() {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                VeritasColors.accent.copy(alpha = 0.24f),
                                VeritasColors.ok.copy(alpha = 0.18f),
                            ),
                    ),
                ),
    )
}

private fun homeTitle(): AnnotatedString =
    buildAnnotatedString {
        append("Verify ")
        pushStyle(SpanStyle(fontWeight = FontWeight.W700))
        append("anything")
        pop()
        append("\non your screen.")
    }

private fun recentLabel(recentItems: List<HistoryItem>): String =
    if (recentItems.isEmpty()) {
        "RECENT"
    } else {
        "RECENT / ${recentItems.size.coerceAtMost(3)}"
    }

private fun mockHistoryItems(): List<HistoryItem> =
    listOf(
        HistoryItem(
            id = "recent-1",
            source = "Shared from TikTok",
            timestamp = "Today / 09:12",
            verdictLabel = "SYNTHETIC",
            verdictTone = VerdictTone.Bad,
            mediaType = RecentMediaType.Video,
        ),
        HistoryItem(
            id = "recent-2",
            source = "Imported voice note",
            timestamp = "Today / 08:03",
            verdictLabel = "UNCERTAIN",
            verdictTone = VerdictTone.Warn,
            mediaType = RecentMediaType.Audio,
        ),
        HistoryItem(
            id = "recent-3",
            source = "Saved from gallery",
            timestamp = "Yesterday / 20:41",
            verdictLabel = "AUTHENTIC",
            verdictTone = VerdictTone.Ok,
            mediaType = RecentMediaType.Image,
        ),
    )

private class HomeViewModel(
    initialRecentMode: HomeRecentMode,
) : ViewModel() {
    private val recentItems =
        MutableStateFlow(
            recentItemsFor(initialRecentMode),
        )

    val uiState =
        recentItems
            .map(::HomeUiState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = HomeUiState(recentItems.value),
            )

    fun setRecentMode(mode: HomeRecentMode) {
        recentItems.update { recentItemsFor(mode) }
    }

    companion object {
        fun factory(initialRecentMode: HomeRecentMode): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(initialRecentMode) as T
                }
            }
    }
}

private fun recentItemsFor(mode: HomeRecentMode): List<HistoryItem> =
    when (mode) {
        HomeRecentMode.Empty -> emptyList()
        HomeRecentMode.Populated -> mockHistoryItems()
    }
