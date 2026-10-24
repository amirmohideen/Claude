package com.amirmohideen.foldfx

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import com.amirmohideen.foldfx.core.audio.AudioSource
import kotlin.math.max

/**
 * Plays an [AudioSource] through a low-latency audio stream, pulling one small buffer at a time
 * so sound follows the hinge within a few milliseconds. Call [play] and [stop] from the main thread.
 */
class AudioEngine(context: Context) {
    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)

    @Volatile
    private var source: AudioSource? = null

    @Volatile
    private var running = false
    private var thread: Thread? = null

    /** Starts playing [source], or switches to it if already playing. */
    fun play(source: AudioSource) {
        this.source = source
        if (thread == null) {
            running = true
            thread = Thread(::loop, "foldfx-audio").apply { start() }
        }
    }

    fun stop() {
        source = null
        running = false
        thread?.join(STOP_TIMEOUT_MS)
        thread = null
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val sampleRate = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48_000
        val frames = (audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256)
            .coerceIn(64, 1024)
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(max(minBytes, frames * BUFFERS_QUEUED * Float.SIZE_BYTES))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        val buffer = FloatArray(frames)
        var current: AudioSource? = null
        try {
            track.play()
            while (running) {
                val wanted = source
                if (wanted !== current) {
                    wanted?.prepare(sampleRate)
                    current = wanted
                }
                if (current != null) current.render(buffer, frames) else buffer.fill(0f)
                track.write(buffer, 0, frames, AudioTrack.WRITE_BLOCKING)
            }
            track.stop()
        } finally {
            track.release()
        }
    }

    private companion object {
        const val BUFFERS_QUEUED = 4
        const val STOP_TIMEOUT_MS = 500L
    }
}
