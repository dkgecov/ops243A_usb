package com.example.test_camera.interfaces

import androidx.camera.view.PreviewView
import java.io.File

interface CameraService {
    fun startCamera(previewView: PreviewView)
    fun takePhoto(speed:Float)
    fun startRecording(outputDir: File)
    fun stopRecording()
}