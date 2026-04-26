package com.veritas.app.debug.testing

import android.net.Uri

object Phase4TestHarness {
    var visualUri: Uri? = null
    var audioUri: Uri? = null
    var initialPasteLink: String? = null

    fun reset() {
        visualUri = null
        audioUri = null
        initialPasteLink = null
    }
}
