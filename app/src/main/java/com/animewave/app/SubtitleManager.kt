package com.animewave.app

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import java.net.URI

object SubtitleManager {
    private val allowedMimeTypes = setOf(
        MimeTypes.TEXT_VTT,
        MimeTypes.APPLICATION_SUBRIP,
        MimeTypes.TEXT_SSA,
    )

    fun configurations(tracks: List<ProviderSubtitle>): List<MediaItem.SubtitleConfiguration> =
        tracks.mapNotNull { track ->
            if (!isAllowedUrl(track.url)) return@mapNotNull null
            val uri = runCatching { Uri.parse(track.url) }.getOrNull() ?: return@mapNotNull null
            val mime = if (track.mimeType != null) {
                track.mimeType.substringBefore(';').trim().lowercase()
                    .takeIf { it in allowedMimeTypes }
            } else {
                mimeType(track.url)
            } ?: return@mapNotNull null
            MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(mime)
                .setLanguage(track.language)
                .setLabel(track.label)
                .setSelectionFlags(if (track.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }

    fun isAllowedUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return when (uri.scheme?.lowercase()) {
            "https" -> !uri.host.isNullOrBlank() && uri.userInfo == null
            "content" -> !uri.authority.isNullOrBlank()
            else -> false
        }
    }

    fun mimeType(url: String): String? = when (url.substringBefore('?').substringAfterLast('.', "").lowercase()) {
        "vtt" -> MimeTypes.TEXT_VTT
        "srt" -> MimeTypes.APPLICATION_SUBRIP
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        else -> null
    }
}
