package com.veritas.app.debug.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.veritas.app.BuildConfig
import com.veritas.core.design.GalleryScreen
import com.veritas.core.design.VeritasTheme

class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!BuildConfig.ENABLE_DESIGN_GALLERY) {
            finish()
            return
        }

        setContent {
            VeritasTheme {
                GalleryScreen(onClose = ::finish)
            }
        }
    }
}
