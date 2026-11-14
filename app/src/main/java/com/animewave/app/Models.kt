package com.animewave.app

import org.json.JSONArray
import org.json.JSONObject

/** Wire models for the authorized playback gateway API. */
internal data class GatewaySourceRequest(
    val url: String,
    val quality: String,
    val isM3U8: Boolean,
) {
    fun toJson() = JSONObject()
        .put("url", url)
        .put("quality", quality)
        .put("isM3U8", isM3U8)
}

internal data class GatewaySubtitleRequest(
    val url: String,
    val language: String,
    val label: String,
) {
    fun toJson() = JSONObject()
        .put("url", url)
        .put("lang", language)
        .put("label", label)
}

internal data class GatewaySessionRequest(
    val title: String,
    val episodeId: String,
    val sources: List<GatewaySourceRequest>,
    val subtitles: List<GatewaySubtitleRequest>,
) {
    fun toJson() = JSONObject()
        .put("title", title)
        .put("episodeId", episodeId)
        .put("sources", JSONArray().apply { sources.forEach { put(it.toJson()) } })
        .put("subtitles", JSONArray().apply { subtitles.forEach { put(it.toJson()) } })
}

internal data class GatewaySourceResponse(
    val url: String,
    val quality: String?,
    val isM3U8: Boolean,
)

internal data class GatewaySubtitleResponse(
    val url: String,
    val language: String?,
    val label: String?,
)

internal data class GatewaySessionResponse(
    val id: String,
    val expiresAt: String?,
    val sources: List<GatewaySourceResponse>,
    val subtitles: List<GatewaySubtitleResponse>,
) {
    companion object {
        fun parse(json: JSONObject): GatewaySessionResponse {
            val sourceArray = json.optJSONArray("sources") ?: JSONArray()
            val subtitleArray = json.optJSONArray("subtitles") ?: JSONArray()
            val sources = (0 until sourceArray.length()).mapNotNull { index ->
                val item = sourceArray.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url").takeIf(String::isNotBlank) ?: return@mapNotNull null
                GatewaySourceResponse(
                    url = url,
                    quality = item.optString("quality").takeIf(String::isNotBlank),
                    isM3U8 = item.optBoolean("isM3U8"),
                )
            }
            val subtitles = (0 until subtitleArray.length()).mapNotNull { index ->
                val item = subtitleArray.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url").takeIf(String::isNotBlank) ?: return@mapNotNull null
                GatewaySubtitleResponse(
                    url = url,
                    language = item.optString("lang").takeIf(String::isNotBlank),
                    label = item.optString("label").takeIf(String::isNotBlank),
                )
            }
            return GatewaySessionResponse(
                id = json.optString("id"),
                expiresAt = json.optString("expiresAt").takeIf(String::isNotBlank),
                sources = sources,
                subtitles = subtitles,
            )
        }
    }
}
