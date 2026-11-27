package com.animewave.app.stream

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Some anime hosts disguise each video segment as an image: a small fake PNG sits in front of the
 * real MPEG-TS data. The player can't recognise that, so this cuts the fake header off and hands
 * the player plain video. Everything else passes through untouched.
 */
@UnstableApi
class UnwrapDataSource(private val upstream: DataSource) : DataSource {
    companion object {
        /** Bytes of fake image before the video in [buf] (first [n] bytes of a file), or 0. */
        fun fakeHeaderLength(buf: ByteArray, n: Int): Int {
            val png = n >= 8 && buf[0] == 0x89.toByte() && buf[1] == 'P'.code.toByte() && buf[2] == 'N'.code.toByte() && buf[3] == 'G'.code.toByte()
            if (!png) return 0
            // MPEG-TS packets are 188 bytes, each starting with 0x47: start where three line up.
            return (0 until n).firstOrNull { i -> (0 until 3).all { k -> val p = i + 188 * k; p < n && buf[p] == 0x47.toByte() } } ?: 0
        }
    }

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = UnwrapDataSource(upstream.createDataSource())
    }

    private var head = ByteArray(0)
    private var headPos = 0

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val length = upstream.open(dataSpec)
        head = ByteArray(0); headPos = 0
        if (dataSpec.position != 0L) return length
        // Read the first few KB and look for the PNG signature.
        val buf = ByteArray(4096)
        var n = 0
        while (n < buf.size) {
            val r = upstream.read(buf, n, buf.size - n)
            if (r == C.RESULT_END_OF_INPUT) break
            n += r
        }
        val start = fakeHeaderLength(buf, n)
        head = buf.copyOfRange(start, n)
        return when {
            start == 0 -> length
            length == C.LENGTH_UNSET.toLong() -> C.LENGTH_UNSET.toLong()
            else -> length - start
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (headPos < head.size) {
            val c = minOf(length, head.size - headPos)
            System.arraycopy(head, headPos, buffer, offset, c)
            headPos += c
            return c
        }
        return upstream.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() { head = ByteArray(0); headPos = 0; upstream.close() }
}
