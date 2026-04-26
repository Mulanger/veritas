package com.veritas.app.debug.testing

import com.veritas.feature.home.HomeRecentMode

object Phase2TestHarness {
    var initialRecentMode: HomeRecentMode = HomeRecentMode.Empty
    var onPickFile: () -> Unit = {}
    var onPasteLink: () -> Unit = {}

    fun reset() {
        initialRecentMode = HomeRecentMode.Empty
        onPickFile = {}
        onPasteLink = {}
    }
}
