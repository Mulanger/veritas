@file:Suppress("FunctionName")

package com.veritas.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.core.design.BrandMark
import com.veritas.core.design.VeritasColors
import com.veritas.core.design.VeritasType

const val SETTINGS_SCREEN_ROOT_TAG = "screen_settings"

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(VeritasColors.bg),
    ) {
        SettingsTopBar()
        Column(
            modifier =
                Modifier
                    .testTag(SETTINGS_SCREEN_ROOT_TAG)
                    .fillMaxWidth()
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Settings",
                style = VeritasType.headingLg.copy(color = VeritasColors.ink),
            )
            Text(
                text = "TODO - full settings tree arrives in Phase 11.",
                style = VeritasType.bodyMd.copy(color = VeritasColors.inkDim),
            )
        }
    }
}

@Composable
private fun SettingsTopBar() {
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
            text = "SETTINGS",
            style = VeritasType.monoXs.copy(color = VeritasColors.inkMute),
        )
    }
    HorizontalDivider(color = VeritasColors.line)
}
