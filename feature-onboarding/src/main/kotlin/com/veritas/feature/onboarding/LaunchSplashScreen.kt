@file:Suppress("FunctionNaming")

package com.veritas.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.veritas.core.design.BrandMark
import com.veritas.core.design.VeritasColors
import com.veritas.core.design.VeritasType

@Composable
fun LaunchSplashScreen(
    showIndicator: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(VeritasColors.bg)
                .testTag(LAUNCH_SPLASH_TAG),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(size = 18.dp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "VERITAS",
            style =
                VeritasType.monoSm.copy(
                    color = VeritasColors.ink,
                    fontWeight = FontWeight.W700,
                    fontSize = 12.sp,
                    letterSpacing = 0.18.em,
                ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Forensic media verification",
            style = VeritasType.bodySm.copy(color = VeritasColors.inkMute),
        )
        if (showIndicator) {
            Spacer(modifier = Modifier.height(28.dp))
            CircularProgressIndicator(
                color = VeritasColors.accent,
                trackColor = VeritasColors.line,
                strokeCap = StrokeCap.Round,
                strokeWidth = 2.dp,
            )
        }
    }
}
