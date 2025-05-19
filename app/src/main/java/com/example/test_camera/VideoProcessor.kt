package com.example.test_camera

import android.Manifest
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import com.example.test_camera.util.StorageUtils
import java.io.File
import java.util.concurrent.ExecutorService

class VideoProcessor (private val videoCapture: VideoCapture<Recorder>,
                      private val sharedExecutor: ExecutorService,private val context: Context
){
    private val outputDir = StorageUtils.getOutputDirectory(context)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
     fun startVideoRecording( includeAudio:Boolean) {
        val name = "VID_${System.currentTimeMillis()}.mp4"
        val file = File(outputDir, name)

        val outputOptions = FileOutputOptions.Builder(file).build()

        var mediaRecorder = videoCapture.output
            .prepareRecording(context, outputOptions)

        if (includeAudio) {
            mediaRecorder = mediaRecorder.withAudioEnabled()
        }
        var recording: Recording? = null
         recording = mediaRecorder.start(sharedExecutor) { recordEvent ->
            when (recordEvent) {
                is VideoRecordEvent.Start ->{ Log.d("CameraX", "Video recording started")
                    Handler(Looper.getMainLooper()).postDelayed({
                        recording?.stop()
                    }, 5000)//TODO may not be in UI thread
                }
                is VideoRecordEvent.Finalize -> Log.d("CameraX", "Video saved: ${file.absolutePath}")
            }
        }


    }
}