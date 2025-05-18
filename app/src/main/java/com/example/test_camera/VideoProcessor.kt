package com.example.test_camera

import android.Manifest
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VideoProcessor (private val videoCapture: VideoCapture<Recorder>,private val context: Context,
                      private val sharedExecutor: ExecutorService
){
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
     fun startVideoRecording(outputDir: File) {
        val name = "VID_${System.currentTimeMillis()}.mp4"
        val file = File(outputDir, name)

        val outputOptions = FileOutputOptions.Builder(file).build()

        val recording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled() // Optional
            .start( sharedExecutor) { recordEvent ->
                if (recordEvent is VideoRecordEvent.Start) {
                    Log.d("CameraX", "Video recording started")
                } else if (recordEvent is VideoRecordEvent.Finalize) {
                    Log.d("CameraX", "Video saved: ${file.absolutePath}")
                }
            }

        // Stop after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({//TODO is it ok, main UI thread?
            recording.stop()
        }, 5000)
    }
}