package com.example.test_camera

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.test_camera.interfaces.CameraService
import java.io.Closeable
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class CameraServiceImpl(
        private val context: Context,
        private val lifecycleOwner: LifecycleOwner,

    ) : CameraService, Closeable{
        private lateinit var imageCapture: ImageCapture
        private lateinit var videoCapture: VideoCapture<Recorder>
        private val analyzeImageExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var videoProcessor: VideoProcessor
    private val sharedExecutor = Executors.newSingleThreadExecutor()

         override fun startCamera(previewView: PreviewView) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // ✅ Unbind all previously bound use cases
                cameraProvider.unbindAll()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Preview use case
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // Image capture use case
                imageCapture = ImageCapture.Builder().build()

                // Video capture use case
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                // Optional image analysis use case
                val imageAnalysis = ImageAnalysis.Builder().build().also {
                    it.setAnalyzer(analyzeImageExecutor) { imageProxy ->
                      //  processImage(imageProxy)//TODO
                    }
                }

                // Bind all use cases to lifecycle
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture,
                    imageAnalysis
                )
                imageProcessor = ImageProcessor(imageCapture,sharedExecutor,context)
                videoProcessor = VideoProcessor(videoCapture,sharedExecutor,context)
                Log.d("CameraManager", "Camera initialized successfully")

            }, ContextCompat.getMainExecutor(context))
        }

    override fun takePhoto(speed:Float) {
        check(::imageProcessor.isInitialized) { "Camera not started. Call startCamera() first." }
        imageProcessor.takePhoto(speed)
    }


    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun startRecording(recordAudio:Boolean) {
        check(::videoProcessor.isInitialized) { "Camera not started. Call startCamera() first." }
        videoProcessor.startVideoRecording(recordAudio)
    }

    override fun stopRecording() {
       // videoProcessor.stopRecording()
    }

    override fun close() {
       sharedExecutor.shutdown()
    }
}
