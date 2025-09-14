package bg.getsovd.vehicle_detection.processing

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.net.toUri
import bg.getsovd.vehicle_detection.config.AppConfig
import bg.getsovd.vehicle_detection.config.AppConfig.VIDEO_DURATION_MS
import bg.getsovd.vehicle_detection.utils.FirebaseAuthManager
import bg.getsovd.vehicle_detection.utils.FirebaseUploader
import bg.getsovd.vehicle_detection.utils.OverlayUtils
import bg.getsovd.vehicle_detection.utils.StorageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.gms.tasks.Tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.Queue
import java.util.concurrent.ExecutorService

class VideoProcessor (private val videoCapture: VideoCapture<Recorder>,
                      private val sharedExecutor: ExecutorService, private val context: Context
){
    @Volatile private var isRecording = false
    private val outputDir = StorageUtils.getOutputDirectory(context)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startVideoRecording(includeAudio: Boolean, recentSpeeds:Queue<Float>) {
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
                            delay(VIDEO_DURATION_MS)
                            recording?.stop()
                        }
                    }

                    is VideoRecordEvent.Finalize -> {
                        val snapshotSpeeds = synchronized(recentSpeeds) {
                            ArrayList(recentSpeeds)
                        }
                        Log.d("CameraX", "Video saved: ${file.absolutePath}")
                        isRecording = false // ✅ Release the flag here
                        Log.d("MyLog","recent speeds:"+recentSpeeds.toString())
                        Log.d("capturetimeEnd", System.nanoTime().toString())

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val maxSpeed = snapshotSpeeds.maxByOrNull { kotlin.math.abs(it)}
                                val overlayOutputFile = File(outputDir, "Speeder_${maxSpeed}_${System.nanoTime()/1000}.mp4")
                                val textToBurn = OverlayUtils.buildOverlay(maxSpeed)
                                val lines = textToBurn.split("\n")
                                Log.d("FFmpeg","Element after split:"+lines.get(0)+","+lines.get(1)+","+lines.get(2))
                                val safeLines=escapeLinesForFfmpegDrawtext(lines)
                                burnOverlayToVideo(//TODO make callback instead of wait 1000
                                    inputFile = file,
                                    outputFile = overlayOutputFile,
                                    fontPath =  copyFontFromAssets(context, "Roboto-Regular.ttf"),
                                    lines = safeLines
                                )
                                Log.d("FFmpeg", "Overlay processing started.")
                                delay(1000)
                                FirebaseAuthManager.signInAnonymously { success, error ->
                                    if (success) {
                                        FirebaseUploader.uploadVideo(overlayOutputFile.toUri(), { downloadUrl ->
                                            if (downloadUrl != null) {
                                                // Upload succeeded, you have the download URL
                                                Log.d("Upload", "Video uploaded successfully: $downloadUrl")
                                                // e.g., update UI, save URL to database, share link, notify user, etc.
                                            } else {
                                                // Upload failed
                                                Log.e("Upload", "Video upload failed")
                                                // e.g., show error message to user
                                            }
                                        })
                                    } else {
                                        Log.e("Auth", "auth failed",error)
                                        // handle auth error
                                    }
                                }

                            } catch (e: Exception) {
                                Log.e("FFmpeg", "Failed to burn overlay", e)
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("myLog", "recording failed: ", e)
            isRecording = false
            throw e
        }
    }
    private fun buildFfmpegOverlayCommand(
        inputPath: String,
        outputPath: String,
        fontPath: String,
        lines: List<String>
    ): Array<String> {
        Log.d("FFmpeg","just before building the command:"+lines.toString())
        return arrayOf(
            "-i", inputPath,
            "-vf", "drawtext=fontfile='$fontPath':text='${lines[0]}':x=10:y=10:fontsize=24:fontcolor=white:box=1:boxcolor=0x00000099," +
                    "drawtext=fontfile='$fontPath':text='${lines[1]}':x=10:y=35:fontsize=24:fontcolor=white:box=1:boxcolor=0x00000099," +
                    "drawtext=fontfile='$fontPath':text='${lines[2]}':x=10:y=60:fontsize=24:fontcolor=white:box=1:boxcolor=0x00000099",
            "-c:v", "hevc_mediacodec",
            "-b:v", "10M",        // Increase bitrate for better quality
            "-maxrate", "10M",
            "-bufsize", "20M",
            "-c:a", "copy",
            outputPath
        )
    }
    private fun burnOverlayToVideo(
        inputFile: File,
        outputFile: File,
        fontPath: String,
        lines: List<String>
    ) {
        val command = buildFfmpegOverlayCommand(
            inputPath = inputFile.absolutePath,
            outputPath = outputFile.absolutePath,
            fontPath = fontPath,
            lines = lines
        )

        FFmpegKit.executeAsync(command.joinToString(" ")) { session ->
            val returnCode = session.returnCode
            if (ReturnCode.isSuccess(returnCode)) {
                Log.d("FFmpeg", "Overlay written successfully: ${outputFile.absolutePath}")
            } else {
               Log.d("FFmpeg", "Overlay failed")
            }
        }
    }
    private fun copyFontFromAssets(context: Context, assetFileName: String): String {
        val file = File(context.filesDir, assetFileName)
        if (!file.exists()) {
            context.assets.open(assetFileName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return file.absolutePath
    }


    private fun escapeForFfmpegDrawtext(text: String): String {
        return text
            .replace(":", "\\\\:")
            .replace(",", "\\,")
    }

    private fun escapeLinesForFfmpegDrawtext(lines: List<String>): List<String> {
        return lines.map { line -> escapeForFfmpegDrawtext(line) }
    }
}