package com.animewave.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class ParsingTest {
    private val page = """
        {"data":{"r":{"media":[{
          "id":1,"title":{"romaji":"Kimi no Sora","english":null},
          "coverImage":{"large":"https://img/c.jpg","extraLarge":"https://img/xl.jpg","color":"#e4a15d"},"bannerImage":null,
          "genres":["Action","Drama"],"seasonYear":2024,"episodes":12,"format":"TV","averageScore":81,
          "description":"Hi<br><br><br>there <i>friend</i>",
          "externalLinks":[
            {"site":"Crunchyroll","url":"https://cr/show","type":"STREAMING"},
            {"site":"Twitter","url":"https://x/show","type":"SOCIAL"},
            {"site":"Hulu","url":"http://hulu/show","type":"STREAMING"},
            {"site":"Weird","url":"ftp://x","type":"STREAMING"}],
          "streamingEpisodes":[{"title":"Episode 1","thumbnail":null,"url":"https://cr/e1","site":"Crunchyroll"}],
          "status":"RELEASING","nextAiringEpisode":{"episode":7,"airingAt":1000000}
        }]}}}
    """

    @Test fun parsesAniListPage() {
        val a = parseResponse(page).getValue("r").single()
        assertEquals("Kimi no Sora", a.title) // falls back to romaji when english is null
        assertEquals(0xFFE4A15D, a.accent)
        assertNull(a.banner)
        assertEquals("https://img/xl.jpg", a.coverXL)
        assertEquals("Hi\n\nthere friend", a.synopsis)
        assertEquals(listOf("Crunchyroll" to "https://cr/show", "Hulu" to "https://hulu/show"), a.watchLinks) // streaming only, http upgraded
        assertEquals(listOf(Episode("Episode 1", null, "https://cr/e1", "Crunchyroll")), a.streamingEpisodes)
        assertEquals("RELEASING", a.status)
        assertEquals(7, a.nextEpisode)
        assertEquals(1_000_000L, a.nextAiringAt)
    }

    @Test fun airingLabelsAndNewEpisodeBadge() {
        val a = parseResponse(page).getValue("r").single()
        assertEquals("Ep 7 in 2d 3h", airingLabel(a, nowSeconds = 1_000_000 - (2 * 86_400 + 3 * 3_600)))
        assertEquals("Ep 7 in 5h", airingLabel(a, nowSeconds = 1_000_000 - 5 * 3_600))
        assertEquals("Ep 7 in 6d", airingLabel(a, nowSeconds = 1_000_000 - 6 * 86_400))
        assertNull(airingLabel(a, nowSeconds = 1_000_001))
        // Episode 6 aired a week before episode 7: new for three days after that.
        assertEquals(true, hasNewEpisode(a, nowSeconds = 1_000_000 - 6 * 86_400))
        assertEquals(false, hasNewEpisode(a, nowSeconds = 1_000_000 - 2 * 86_400))
    }

    @Test(expected = IOException::class) fun errorResponseThrows() {
        parseResponse("""{"data":null,"errors":[{"message":"Too Many Requests"}]}""")
    }

    @Test fun parsesUpdateInfo() {
        val u = parseUpdate("""{"versionCode":14,"versionName":"1.0.14","notes":"Redesign"}""")
        assertEquals(UpdateInfo(14, "1.0.14", "Redesign"), u)
    }

    @Test fun ordersEpisodesAndMatchesSubtitles() {
        val names = listOf("Show E10.mkv", "Show E2.mkv", "Show E2.srt", "Show E10.en.vtt", "notes.txt", "Show E1.mp4")
        val eps = pairEpisodes(names.map { LocalFile(it, it) })
        assertEquals(listOf("Show E1.mp4", "Show E2.mkv", "Show E10.mkv"), eps.map { it.video.name })
        assertEquals(listOf(null, "Show E2.srt", "Show E10.en.vtt"), eps.map { it.subtitle?.name })
    }

    @Test fun pairsLoneVideoWithLoneSubtitle() {
        val eps = pairEpisodes(listOf(LocalFile(1, "movie.mkv"), LocalFile(2, "English.srt")))
        assertEquals("English.srt", eps.single().subtitle?.name)
    }
}
