package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.media.AudioFormat
import androidx.test.core.app.ApplicationProvider
import com.example.data.audio.VoiceAudioStreamer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceAudioStreamerTest {

    @Test
    fun `audio streamer constants match Gemini Live PCM specification`() {
        assertEquals(16_000, VoiceAudioStreamer.INPUT_SAMPLE_RATE)
        assertEquals(24_000, VoiceAudioStreamer.OUTPUT_SAMPLE_RATE)
        assertEquals(AudioFormat.CHANNEL_IN_MONO, VoiceAudioStreamer.INPUT_CHANNEL_CONFIG)
        assertEquals(AudioFormat.CHANNEL_OUT_MONO, VoiceAudioStreamer.OUTPUT_CHANNEL_CONFIG)
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, VoiceAudioStreamer.AUDIO_FORMAT)
        assertEquals("audio/pcm", VoiceAudioStreamer.INPUT_MIME_TYPE)
        assertEquals(1280, VoiceAudioStreamer.CAPTURE_FRAME_BYTES)
    }

    @Test
    fun `computeNormalizedRms returns zero for silence and clamped positive for speech`() {
        val silence = ByteArray(128)
        assertEquals(0f, VoiceAudioStreamer.computeNormalizedRms(silence), 0.0001f)

        // Full-scale 16-bit PCM alternating +32767 / -32768 (little-endian)
        val loud = ByteArray(4).apply {
            this[0] = 0xFF.toByte()
            this[1] = 0x7F.toByte()
            this[2] = 0x00.toByte()
            this[3] = 0x80.toByte()
        }
        assertEquals(1.0f, VoiceAudioStreamer.computeNormalizedRms(loud), 0.0001f)
    }

    @Test
    fun `hasRecordPermission reflects runtime permission grant and mute resets micLevel`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)
        val streamer = VoiceAudioStreamer(app as Context)

        shadowApp.denyPermissions(Manifest.permission.RECORD_AUDIO)
        assertFalse(streamer.hasRecordPermission())

        shadowApp.grantPermissions(Manifest.permission.RECORD_AUDIO)
        assertTrue(streamer.hasRecordPermission())

        assertFalse(streamer.isMuted.value)
        assertTrue(streamer.toggleMute())
        assertTrue(streamer.isMuted.value)
        assertEquals(0f, streamer.micLevel.value, 0.0001f)
    }
}
