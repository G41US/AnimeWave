package com.animewave.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumetClientHeadersTest {
    @Test
    fun parsesOnlyNonBlankHeaderValues() {
        val client = ConsumetClient("https://api.example.org")
        val headers = client.parseHeaders(JSONObject("""{"Referer":"https://watch.example.org/","User-Agent":"AnimeWave test","Empty":"","Null":null}"""))

        assertEquals("https://watch.example.org/", headers["Referer"])
        assertEquals("AnimeWave test", headers["User-Agent"])
        assertTrue("Empty values should be ignored", "Empty" !in headers)
        assertTrue("JSON null values should be ignored", "Null" !in headers)
    }

    @Test
    fun missingHeaderObjectProducesEmptyMap() {
        val client = ConsumetClient("https://api.example.org")
        assertEquals(emptyMap<String, String>(), client.parseHeaders(null))
    }
}
