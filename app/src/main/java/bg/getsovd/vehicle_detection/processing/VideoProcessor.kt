package bg.getsovd.vehicle_detection.processing

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
import bg.getsovd.vehicle_detection.utils.StorageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import java.io.File
import java.util.concurrent.ExecutorService

private const val videoDuration = 5000L

class VideoProcessor (private val videoCapture: VideoCapture<Recorder>,
                      private val sharedExecutor: ExecutorService, private val context: Context
){
    @Volatile private var isRecording = false
    private val outputDir = StorageUtils.getOutputDirectory(context)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startVideoRecording(includeAudio: Boolean) {
        if (isRecording) return
        isRecording = true

        try {
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
                    is VideoRecordEvent.Start -> {
                        Log.d("CameraX", "Video recording started")

                        // Launch a coroutine to stop after 5 seconds
                        CoroutineScope(Dispatchers.Default).launch {
                            delay(videoDuration)
                            recording?.stop()
                        }
                    }

                    is VideoRecordEvent.Finalize -> {
                        Log.d("CameraX", "Video saved: ${file.absolutePath}")
                        isRecording = false // ✅ Release the flag here
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("myLog", "recording failed: ", e)
            isRecording = false
            throw e
        }
    }


}