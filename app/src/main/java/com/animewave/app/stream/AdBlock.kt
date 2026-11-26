package com.animewave.app.stream

import android.content.Context
import java.util.zip.GZIPInputStream

/**
 * Network-level ad blocking: ~120k ad, popunder and tracker domains (StevenBlack + anudeepND lists,
 * plus popunder networks common on streaming sites). A request to a listed domain, or any
 * subdomain of one, never leaves the phone.
 */
object AdBlock {
    @Volatile private var domains: Set<String> = emptySet()
    @Volatile var ready = false; private set
    val size get() = domains.size

    fun warm(context: Context) {
        if (ready) return
        val app = context.applicationContext
        Thread {
            try {
                val set = HashSet<String>(140_000)
                GZIPInputStream(app.assets.open("blocklist.txt.gz")).bufferedReader().useLines { lines ->
                    lines.forEach { if (it.isNotBlank()) set.add(it.trim()) }
                }
                domains = set
            } catch (_: Exception) { }
            ready = true
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    fun blocks(host: String?): Boolean {
        var h = host?.lowercase() ?: return false
        val set = domains
        while (true) {
            if (h in set) return true
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false
            h = h.substring(dot + 1)
        }
    }
}
