package com.animewave.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PlaybackSourceDialog(
    candidates: List<ResolvedPlaybackCandidate>,
    onSelect: (ResolvedPlaybackCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Choose playback source") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                candidates.forEachIndexed { index, candidate ->
                    val quality = candidate.stream.quality ?: "Quality not specified"
                    val subtitleCount = candidate.stream.subtitles.size
                    TextButton(
                        onClick = { onSelect(candidate) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("${index + 1}. ${candidate.stream.providerName}")
                            Text(
                                "$quality · ${if (subtitleCount == 1) "1 subtitle track" else "$subtitleCount subtitle tracks"}",
                                color = Muted,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
