package com.example.test_camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File



    class CameraManager(
        private val context: Context,
        private val lifecycleOwner: LifecycleOwner,

    ) {
        private lateinit var imageCapture: ImageCapture
        private lateinit var videoCapture: VideoCapture<Recorder>

        fun startCamera(previewView: PreviewView) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                cameraProvider.unbindAll()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder().build()

                val recorder = Recorder.Builder().build()
                videoCapture = VideoCapture.withOutput(recorder)

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
                )
            }, ContextCompat.getMainExecutor(context))
        }

        fun getImageProcessor(): ImageProcessor {
            if (!::imageCapture.isInitialized || !::videoCapture.isInitialized) {
                throw IllegalStateException("Camera must be started before getting ImageProcessor.")
            }
            return ImageProcessor(
                imageCapture = imageCapture,
                videoCapture = videoCapture,
            )
        }


    }
