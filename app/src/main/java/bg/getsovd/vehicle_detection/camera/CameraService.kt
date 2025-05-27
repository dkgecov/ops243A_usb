package bg.getsovd.vehicle_detection.camera

import androidx.camera.view.PreviewView

interface CameraService {
    fun startCamera(previewView: PreviewView)
    fun takePhoto(speed:Float)
    fun startRecording(recordAudio:Boolean)
    fun stopRecording()
}