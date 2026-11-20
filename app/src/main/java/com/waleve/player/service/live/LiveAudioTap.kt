package com.waleve.player.service.live

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Copies what Olive's player is playing (decoded PCM) into a small ring buffer, so the
 * Listen Together host can stream it live. Does nothing unless [active] is on.
 *
 * Stored as 48 kHz mono 16-bit; [read] resamples to whatever WebRTC asks for.
 */
@OptIn(UnstableApi::class)
object LiveAudioTap {

    @Volatile var active = false
        set(value) { field = value; if (!value) clear() }

    private const val RATE = 48_000
    private val ring = ShortArray(RATE * 3)          // 3 s
    private var writePos = 0L
    private var readPos = 0L
    private val lock = Any()

    // Source format (from the player)
    @Volatile private var srcRate = 44_100
    @Volatile private var srcChannels = 2
    @Volatile private var srcEncoding = C.ENCODING_PCM_16BIT
    private var resamplePhase = 0.0

    val sink = object : TeeAudioProcessor.AudioBufferSink {
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            srcRate = sampleRateHz; srcChannels = channelCount.coerceAtLeast(1); srcEncoding = encoding
            clear()
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            if (!active) return
            val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val ch = srcChannels
            val step = srcRate.toDouble() / RATE
            synchronized(lock) {
                when (srcEncoding) {
                    C.ENCODING_PCM_16BIT -> {
                        val frames = b.remaining() / (2 * ch)
                        val start = b.position()
                        // Linear-ish resample to 48 kHz while downmixing to mono
                        var pos = resamplePhase
                        while (pos < frames) {
                            val i = pos.toInt()
                            var sum = 0
                            for (c in 0 until ch) sum += b.getShort(start + (i * ch + c) * 2).toInt()
                            push((sum / ch).toShort())
                            pos += step
                        }
                        resamplePhase = pos - frames
                    }
                    C.ENCODING_PCM_FLOAT -> {
                        val frames = b.remaining() / (4 * ch)
                        val start = b.position()
                        var pos = resamplePhase
                        while (pos < frames) {
                            val i = pos.toInt()
                            var sum = 0f
                            for (c in 0 until ch) sum += b.getFloat(start + (i * ch + c) * 4)
                            push(((sum / ch).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
                            pos += step
                        }
                        resamplePhase = pos - frames
                    }
                    else -> Unit
                }
            }
        }
    }

    /** The processor to add to ExoPlayer's audio sink. */
    val processor: TeeAudioProcessor by lazy { TeeAudioProcessor(sink) }

    private fun push(s: Short) {
        ring[(writePos % ring.size).toInt()] = s
        writePos++
        if (writePos - readPos > ring.size) readPos = writePos - ring.size   // overflow: drop the oldest
    }

    fun clear() = synchronized(lock) { readPos = writePos; resamplePhase = 0.0 }

    /**
     * Fill [out] (16-bit PCM, [channels] interleaved, [rate] Hz) with the latest audio.
     * Missing audio (paused / buffering) becomes silence.
     */
    fun read(out: ByteBuffer, bytes: Int, channels: Int, rate: Int) {
        val o = out.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        o.position(0)
        val frames = bytes / (2 * channels)
        val step = RATE.toDouble() / rate
        synchronized(lock) {
            // Keep latency low: if we're more than ~400 ms behind, skip ahead
            val maxLag = RATE * 4 / 10
            if (writePos - readPos > maxLag) readPos = writePos - RATE / 10
            var pos = 0.0
            for (f in 0 until frames) {
                val idx = readPos + pos.toLong()
                val v: Short = if (idx < writePos) ring[(idx % ring.size).toInt()] else 0
                for (c in 0 until channels) o.putShort(v)
                pos += step
            }
            readPos = minOf(writePos, readPos + pos.toLong())
        }
    }
}
