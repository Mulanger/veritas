@file:Suppress("FunctionName")

package com.veritas.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.core.design.BrandMark
import com.veritas.core.design.VeritasButton
import com.veritas.core.design.VeritasColors
import com.veritas.core.design.VeritasType

const val HISTORY_SCREEN_ROOT_TAG = "screen_history"

@Composable
fun HistoryScreen(
    onCheckSomething: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(VeritasColors.bg),
    ) {
        HistoryTopBar()
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().testTag(HISTORY_SCREEN_ROOT_TAG),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HistoryEmptyIllustration()
                Text(
                    text = "No checks yet.",
                    style = VeritasType.headingLg.copy(color = VeritasColors.ink),
                )
                Text(
                    text = "Verdicts you run appear here. Everything stays on your phone.",
                    style = VeritasType.bodyMd.copy(color = VeritasColors.inkDim),
                    textAlign = TextAlign.Center,
                )
                VeritasButton(
                    text = "Check something",
                    onClick = onCheckSomething,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HistoryTopBar() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
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
            text = "HISTORY",
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
