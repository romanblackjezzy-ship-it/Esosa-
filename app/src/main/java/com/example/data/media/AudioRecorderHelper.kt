package com.example.data.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorderHelper {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var isCurrentlyRecording = false

    val isRecording: Boolean
        get() = isCurrentlyRecording

    fun startRecording(context: Context): File? {
        if (isCurrentlyRecording) {
            cancelRecording()
        }
        return try {
            val audioDir = File(context.cacheDir, "voice_notes").apply {
                if (!exists()) mkdirs()
            }
            val tempFile = File.createTempFile("voice_", ".m4a", audioDir)
            currentOutputFile = tempFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder = recorder

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96000)
                setAudioSamplingRate(44100)
                setOutputFile(tempFile.absolutePath)
                prepare()
                start()
            }

            isCurrentlyRecording = true
            Log.i(TAG, "Audio recording started -> ${tempFile.absolutePath}")
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording: ${e.message}", e)
            cleanup()
            null
        }
    }

    fun stopRecording(): File? {
        if (!isCurrentlyRecording) return currentOutputFile
        val recordedFile = currentOutputFile
        try {
            mediaRecorder?.let { recorder ->
                runCatching { recorder.stop() }
                    .onFailure { Log.w(TAG, "MediaRecorder.stop failed: ${it.message}") }
                runCatching { recorder.release() }
            }
            Log.i(TAG, "Audio recording stopped successfully. File size: ${recordedFile?.length()} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping media recorder: ${e.message}", e)
        } finally {
            mediaRecorder = null
            isCurrentlyRecording = false
        }
        return if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
            recordedFile
        } else {
            null
        }
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.let { recorder ->
                if (isCurrentlyRecording) {
                    runCatching { recorder.stop() }
                        .onFailure { Log.w(TAG, "MediaRecorder.stop failed during cancel: ${it.message}") }
                }
                runCatching { recorder.release() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception cancelling media recorder: ${e.message}")
        } finally {
            cleanup()
        }
    }

    private fun cleanup() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        isCurrentlyRecording = false
        currentOutputFile?.let {
            if (it.exists()) it.delete()
        }
        currentOutputFile = null
    }

    companion object {
        private const val TAG = "AudioRecorderHelper"
    }
}
