package com.example.data.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class VoiceCallAudioController(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var previousAudioMode: Int? = null
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    fun configureCallAudio(isSpeakerOn: Boolean, isMuted: Boolean) {
        val manager = audioManager ?: return
        runCatching {
            if (previousAudioMode == null) {
                previousAudioMode = manager.mode
            }
            manager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            manager.isSpeakerphoneOn = isSpeakerOn
            manager.isMicrophoneMute = isMuted
        }
    }

    fun setSpeakerphone(enabled: Boolean) {
        val manager = audioManager ?: return
        runCatching {
            @Suppress("DEPRECATION")
            manager.isSpeakerphoneOn = enabled
        }
    }

    fun setMicrophoneMute(muted: Boolean) {
        val manager = audioManager ?: return
        runCatching {
            manager.isMicrophoneMute = muted
        }
    }

    @SuppressLint("MissingPermission")
    fun startMicrophoneCapture(
        scope: CoroutineScope,
        onLevelChanged: (Float) -> Unit
    ) {
        stopMicrophoneCapture()
        if (!hasMicrophonePermission(appContext)) {
            onLevelChanged(0f)
            return
        }

        captureJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16_000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuffer = runCatching {
                AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            }.getOrDefault(AudioRecord.ERROR_BAD_VALUE)

            if (minBuffer <= 0) {
                onLevelChanged(0f)
                return@launch
            }

            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    minBuffer.coerceAtLeast(2048)
                )
            }.getOrNull()

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                runCatching { recorder?.release() }
                onLevelChanged(0f)
                return@launch
            }

            audioRecord = recorder
            val buffer = ShortArray(minBuffer.coerceAtLeast(1024) / 2)
            try {
                recorder.startRecording()
                while (currentCoroutineContext().isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sumSquares = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i].toDouble() / Short.MAX_VALUE
                            sumSquares += sample * sample
                        }
                        val rms = sqrt(sumSquares / read).toFloat()
                        val normalized = (rms * 4.5f).coerceIn(0f, 1f)
                        onLevelChanged(normalized)
                    }
                }
            } catch (_: SecurityException) {
                onLevelChanged(0f)
            } catch (_: IllegalStateException) {
                onLevelChanged(0f)
            } finally {
                onLevelChanged(0f)
                runCatching {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        recorder.stop()
                    }
                }
                runCatching { recorder.release() }
                if (audioRecord === recorder) {
                    audioRecord = null
                }
            }
        }
    }

    fun stopMicrophoneCapture() {
        captureJob?.cancel()
        captureJob = null
        val recorder = audioRecord
        audioRecord = null
        if (recorder != null) {
            runCatching {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            }
            runCatching { recorder.release() }
        }
    }

    fun release() {
        stopMicrophoneCapture()
        val manager = audioManager ?: return
        runCatching {
            manager.isMicrophoneMute = false
            @Suppress("DEPRECATION")
            manager.isSpeakerphoneOn = false
            previousAudioMode?.let { manager.mode = it }
            previousAudioMode = null
        }
    }

    companion object {
        fun hasMicrophonePermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
