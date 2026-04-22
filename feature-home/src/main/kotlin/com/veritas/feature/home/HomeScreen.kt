package com.veritas.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.veritas.core.design.VeritasTheme

@Suppress("FunctionName")
@Composable
fun HomeScreen(
    statusLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Veritas is building",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Suppress("FunctionName", "UnusedPrivateMember")
@Preview
@Composable
private fun HomeScreenPreview() {
    VeritasTheme {
        HomeScreen(statusLabel = "Phase 0 - Scaffold stub")
    }
}
