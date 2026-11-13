package com.animewave.app

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes

object SubtitleManager {
    fun configurations(tracks: List<ProviderSubtitle>): List<MediaItem.SubtitleConfiguration> =
        tracks.mapNotNull { track ->
            val uri = runCatching { Uri.parse(track.url) }.getOrNull() ?: return@mapNotNull null
            if (uri.scheme !in setOf("https", "content")) return@mapNotNull null
            val mime = track.mimeType ?: mimeType(track.url) ?: return@mapNotNull null
            MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(mime)
                .setLanguage(track.language)
                .setLabel(track.label)
                .setSelectionFlags(if (track.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }

    fun mimeType(url: String): String? = when (url.substringBefore('?').substringAfterLast('.', "").lowercase()) {
        "vtt" -> MimeTypes.TEXT_VTT
        "srt" -> MimeTypes.APPLICATION_SUBRIP
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        else -> null
    }
}
