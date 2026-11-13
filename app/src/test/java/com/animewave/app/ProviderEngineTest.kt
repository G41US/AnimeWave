package com.animewave.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderEngineTest {
    @Test fun extractsEpisodeNumbers() {
        assertEquals(2, EpisodeMatcher.episodeNumber("Episode 02"))
        assertEquals(10, EpisodeMatcher.episodeNumber("Show - Ep 10 [1080p]"))
        assertEquals(3, EpisodeMatcher.episodeNumber("Show 1x03"))
        assertNull(EpisodeMatcher.episodeNumber("Special OVA"))
    }

    @Test fun normalizesEpisodeTitles() {
        assertEquals("show", EpisodeMatcher.normalize("Show Episode 02"))
        assertEquals("show", EpisodeMatcher.normalize("Show - Ep 2"))
    }

    @Test fun progressIsBounded() {
        val record = PlaybackRecord("content://media/1", "Episode 1", 150, 100, 1)
        assertEquals(1f, record.progress)
    }
}
