package bg.getsovd.vehicle_detection.camera

import androidx.camera.view.PreviewView
import java.util.Queue

interface CameraService {
    fun startCamera(previewView: PreviewView)
    fun takePhoto(speed:Float)
    fun startRecording(recordAudio:Boolean,recentSpeeds: Queue<Float>)
    fun stopRecording()
}