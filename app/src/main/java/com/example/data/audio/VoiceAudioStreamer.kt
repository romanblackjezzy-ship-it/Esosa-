package com.example.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Full-duplex PCM audio streaming boilerplate for Gemini Live voice conversations.
 *
 * - Captures 16 kHz mono 16-bit PCM microphone audio with hardware AEC/NS when supported.
 * - Streams 24 kHz mono 16-bit PCM server responses via [AudioTrack] in [AudioTrack.MODE_STREAM].
 * - Manages [AudioManager.MODE_IN_COMMUNICATION], transient audio focus, speaker routing, and mute.
 */
class VoiceAudioStreamer(
    private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val playbackLock = Any()
    private var audioTrack: AudioTrack? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var previousAudioMode: Int? = null
    private var hasAudioFocus = false

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _isAecEnabled = MutableStateFlow(false)
    val isAecEnabled: StateFlow<Boolean> = _isAecEnabled.asStateFlow()

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        if (muted) {
            _micLevel.value = 0f
        }
    }

    fun toggleMute(): Boolean {
        val next = !_isMuted.value
        setMuted(next)
        return next
    }

    fun setSpeakerphoneEnabled(enabled: Boolean) {
        _isSpeakerOn.value = enabled
        applySpeakerRouting(enabled)
    }

    fun toggleSpeakerphone(): Boolean {
        val next = !_isSpeakerOn.value
        setSpeakerphoneEnabled(next)
        return next
    }

    fun prepareAudioSession(speakerOn: Boolean = _isSpeakerOn.value) {
        if (previousAudioMode == null) {
            previousAudioMode = audioManager.mode
        }
        requestCommunicationAudioFocus()
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            Log.w(TAG, "Unable to switch AudioManager mode: ${e.message}")
        }
        _isSpeakerOn.value = speakerOn
        applySpeakerRouting(speakerOn)
        ensureAudioTrackInitialized()
    }

    @SuppressLint("MissingPermission")
    fun capturedAudioFlow(): Flow<ByteArray> = flow {
        if (!hasRecordPermission()) {
            throw SecurityException("RECORD_AUDIO permission is not granted.")
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            INPUT_CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        if (minBufferSize <= 0) {
            throw IllegalStateException("AudioRecord.getMinBufferSize failed with code $minBufferSize")
        }

        val bufferSizeInBytes = maxOf(minBufferSize * 2, CAPTURE_FRAME_BYTES * 4)
        val audioFormat = AudioFormat.Builder()
            .setSampleRate(INPUT_SAMPLE_RATE)
            .setChannelMask(INPUT_CHANNEL_CONFIG)
            .setEncoding(AUDIO_FORMAT)
            .build()

        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSizeInBytes)
            .build()

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("AudioRecord failed to initialize on this device.")
        }

        var echoCanceler: AcousticEchoCanceler? = null
        var noiseSuppressor: NoiseSuppressor? = null

        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                    enabled = true
                    _isAecEnabled.value = enabled
                }
            } else {
                _isAecEnabled.value = false
            }

            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(recorder.audioSessionId)?.apply {
                    enabled = true
                }
            }

            recorder.startRecording()
            val frameBuffer = ByteArray(CAPTURE_FRAME_BYTES)

            while (currentCoroutineContext().isActive) {
                val bytesRead = recorder.read(
                    frameBuffer,
                    0,
                    frameBuffer.size,
                    AudioRecord.READ_BLOCKING
                )
                if (bytesRead > 0) {
                    if (_isMuted.value) {
                        _micLevel.value = 0f
                    } else {
                        _micLevel.value = computeNormalizedRms(frameBuffer, bytesRead)
                        emit(frameBuffer.copyOf(bytesRead))
                    }
                } else if (bytesRead < 0) {
                    Log.w(TAG, "AudioRecord.read returned error code $bytesRead")
                    if (bytesRead == AudioRecord.ERROR_DEAD_OBJECT ||
                        bytesRead == AudioRecord.ERROR_INVALID_OPERATION
                    ) {
                        break
                    }
                }
            }
        } finally {
            _micLevel.value = 0f
            _isAecEnabled.value = false
            try {
                echoCanceler?.release()
            } catch (_: Exception) {
            }
            try {
                noiseSuppressor?.release()
            } catch (_: Exception) {
            }
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (_: Exception) {
            }
            try {
                recorder.release()
            } catch (_: Exception) {
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun enqueuePlayback(pcmBytes: ByteArray) {
        if (pcmBytes.isEmpty()) return
        withContext(Dispatchers.IO) {
            synchronized(playbackLock) {
                val track = ensureAudioTrackInitialized() ?: return@synchronized
                if (track.state != AudioTrack.STATE_INITIALIZED) return@synchronized

                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }

                var offset = 0
                while (offset < pcmBytes.size) {
                    val written = track.write(
                        pcmBytes,
                        offset,
                        pcmBytes.size - offset,
                        AudioTrack.WRITE_BLOCKING
                    )
                    if (written <= 0) {
                        if (written < 0) {
                            Log.w(TAG, "AudioTrack.write failed with code $written")
                        }
                        break
                    }
                    offset += written
                }
            }
        }
    }

    fun flushPlayback() {
        synchronized(playbackLock) {
            val track = audioTrack ?: return
            if (track.state != AudioTrack.STATE_INITIALIZED) return
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }
                track.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Error flushing AudioTrack: ${e.message}")
            }
        }
    }

    fun stopAndRelease() {
        synchronized(playbackLock) {
            val track = audioTrack
            audioTrack = null
            if (track != null) {
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING ||
                        track.playState == AudioTrack.PLAYSTATE_PAUSED
                    ) {
                        track.stop()
                    }
                } catch (_: Exception) {
                }
                try {
                    track.release()
                } catch (_: Exception) {
                }
            }
        }

        _micLevel.value = 0f
        _isAecEnabled.value = false
        clearSpeakerRouting()
        abandonCommunicationAudioFocus()

        previousAudioMode?.let { oldMode ->
            try {
                audioManager.mode = oldMode
            } catch (_: Exception) {
            }
            previousAudioMode = null
        }
    }

    private fun ensureAudioTrackInitialized(): AudioTrack? {
        synchronized(playbackLock) {
            val existing = audioTrack
            if (existing != null && existing.state == AudioTrack.STATE_INITIALIZED) {
                return existing
            }

            val minBufferSize = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                OUTPUT_CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            if (minBufferSize <= 0) {
                Log.e(TAG, "AudioTrack.getMinBufferSize failed with code $minBufferSize")
                return null
            }

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(OUTPUT_SAMPLE_RATE)
                .setChannelMask(OUTPUT_CHANNEL_CONFIG)
                .setEncoding(AUDIO_FORMAT)
                .build()

            val created = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBufferSize * 2, OUTPUT_BUFFER_MIN_BYTES))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (created.state != AudioTrack.STATE_INITIALIZED) {
                created.release()
                return null
            }

            audioTrack = created
            return created
        }
    }

    private fun requestCommunicationAudioFocus() {
        if (hasAudioFocus) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    ) {
                        flushPlayback()
                    }
                }
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonCommunicationAudioFocus() {
        if (!hasAudioFocus) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
        hasAudioFocus = false
    }

    private fun applySpeakerRouting(enabled: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (enabled) {
                    val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) {
                        audioManager.setCommunicationDevice(speaker)
                    }
                } else {
                    val earpiece = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    if (earpiece != null) {
                        audioManager.setCommunicationDevice(earpiece)
                    } else {
                        audioManager.clearCommunicationDevice()
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = enabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to update speaker routing: ${e.message}")
        }
    }

    private fun clearSpeakerRouting() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "VoiceAudioStreamer"

        const val INPUT_SAMPLE_RATE = 16_000
        const val OUTPUT_SAMPLE_RATE = 24_000
        const val INPUT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val OUTPUT_CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val INPUT_MIME_TYPE = "audio/pcm"

        // 40 ms frame at 16 kHz, 16-bit mono PCM (640 samples * 2 bytes)
        const val CAPTURE_FRAME_BYTES = 1280
        private const val OUTPUT_BUFFER_MIN_BYTES = 4800

        /**
         * Computes a normalized [0f..1f] RMS voice amplitude from little-endian 16-bit PCM bytes.
         */
        fun computeNormalizedRms(pcm16Le: ByteArray, byteCount: Int = pcm16Le.size): Float {
            val usableBytes = minOf(byteCount, pcm16Le.size) and 1.inv()
            if (usableBytes <= 0) return 0f

            var sumSquares = 0.0
            val sampleCount = usableBytes / 2
            var i = 0
            while (i < usableBytes) {
                val low = pcm16Le[i].toInt() and 0xFF
                val high = pcm16Le[i + 1].toInt()
                val sample = ((high shl 8) or low).toShort().toDouble()
                val normalized = sample / 32768.0
                sumSquares += normalized * normalized
                i += 2
            }
            val rms = sqrt(sumSquares / sampleCount)
            // Boost speech RMS gently into 0..1 visual meter range
            return (rms * 3.2).toFloat().coerceIn(0f, 1f)
        }
    }
}
