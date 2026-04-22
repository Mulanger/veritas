package com.veritas.app.debug.testing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.veritas.app.BuildConfig
import com.veritas.app.VeritasApp
import com.veritas.core.design.VeritasTheme

class Phase2TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VeritasTheme {
                VeritasApp(
                    initialRecentMode = Phase2TestHarness.initialRecentMode,
                    enableHomeDevMenu = BuildConfig.ENABLE_HOME_DEV_MENU,
                    onPickFile = { Phase2TestHarness.onPickFile.invoke() },
                    onPasteLink = { Phase2TestHarness.onPasteLink.invoke() },
                )
            }
        }
    }
}
