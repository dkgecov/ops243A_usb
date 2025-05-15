package com.example.test_camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.controls.ControlsProviderService.TAG
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.test_camera.databinding.ActivityMainBinding
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

data class InferenceResult(
    val classification: Map<String, Float>?,   // Classification labels and values
    val objectDetections: List<BoundingBox>?,  // Object detection results
    val visualAnomalyGridCells: List<BoundingBox>?, //TODO not used ? Visual anomaly grid
    val anomalyResult: Map<String, Float>?, // Anomaly values
    val timing: Timing  // Timing information
)

data class BoundingBox(
    val label: String,
    val confidence: Float,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class Timing(
    val sampling: Int,
    val dsp: Int,
    val classification: Int,
    val anomaly: Int,
    val dsp_us: Long,
    val classification_us: Long,
    val anomaly_us: Long
)

private const val CAMERA_PERMISSION_REQUEST_CODE = 1001

class BoundingBoxOverlay(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
    }

    private val anomalyPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        alpha = 60 // Adjust transparency
    }

    var boundingBoxes: List<BoundingBox> = emptyList()
        set(value) {
            field = value
            invalidate() // Redraw when new bounding boxes are set
        }

    @SuppressLint("DefaultLocale")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.TRANSPARENT) // Ensure transparency

        boundingBoxes.forEach { box ->
            val rect = Rect(box.x, box.y, box.x + box.width, box.y + box.height)

            if (box.label == "anomaly") {
                // Fill the box with transparent red
                canvas.drawRect(rect, anomalyPaint)

                // Display anomaly score in the center
                val scoreText = String.format("%.2f", box.confidence)
                val textX = rect.centerX().toFloat()
                val textY = rect.centerY().toFloat()

                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(scoreText, textX, textY, textPaint)
            } else {
                // Standard object detection box
                canvas.drawRect(rect, paint)
                canvas.drawText("${box.label} (${(box.confidence * 100).toInt()}%)", box.x.toFloat(), (box.y - 10).toFloat(), textPaint)
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    private var lastCaptureTime = 0L
    private val uiHandler: Handler = Handler(Looper.getMainLooper())
    private lateinit var imageCapture: ImageCapture
    private lateinit var videoCapture: VideoCapture<Recorder>
     val updateIntervalMs = 150L // n updates/second
    @Volatile
    private var updateScheduled = false
    private lateinit var binding: ActivityMainBinding
    private lateinit var resultTextView: TextView
    private lateinit var speedTextView: TextView
    private lateinit var previewView: PreviewView
    private lateinit var errorTextView: TextView
    private lateinit var boundingBoxOverlay: BoundingBoxOverlay
    private var ACTION_USB_PERMISSION: String = "com.example.test_camera.USB_PERMISSION"
    val executor = Executors.newSingleThreadExecutor()

    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager

            when (action) {
                ACTION_USB_PERMISSION -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    if (permissionGranted && device != null) {
                        uiHandler.postDelayed({errorTextView.visibility=View.GONE},1500)
                        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                        val connection = manager.openDevice(device)
                        if (driver != null && connection != null) {
                            onPermission(driver, connection)
                        } else {
                            Log.e(TAG, "Driver or connection is null after permission")
                        }
                    } else {
                        Log.d(TAG, "Permission denied for device: $device")
                    }
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null && !manager.hasPermission(device)) {
                        val permissionIntent = PendingIntent.getBroadcast(
                            context, 0,
                            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                            PendingIntent.FLAG_MUTABLE
                        )
                        manager.requestPermission(device, permissionIntent)
                    }
                    errorTextView.visibility=View.VISIBLE
                    errorTextView.text="USB device attached"
                    //uiHandler.postDelayed({errorTextView.visibility=View.GONE},1500)
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    errorTextView.visibility=View.VISIBLE
                    errorTextView.text="USB device detached"
                    Log.d(TAG, "USB device detached")
                    // Optional: Clean up resources here
                }
            }
        }
    }

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        resultTextView = findViewById(R.id.resultTextView) // Result TextView
        previewView = findViewById(R.id.previewView) // Camera preview view
        boundingBoxOverlay = findViewById(R.id.boundingBoxOverlay) // overlay for boxes / visual ad
        this.speedTextView = findViewById<TextView>(R.id.speed_field)
        errorTextView=findViewById(R.id.errrorTextView)
        errorTextView.visibility=View.GONE

        // Set overlay size to match PreviewView
        previewView.post {
            boundingBoxOverlay.layoutParams = boundingBoxOverlay.layoutParams.apply {
                width = previewView.width
                height = previewView.height
            }
        }

        if (!hasCameraPermission()) {
            requestCameraPermission()
        } else {
            startCamera()
        }
        prepareUsbDeviceListeners()// should be after camera started because it can trigger image capture if
    // the sensor permission is granted and it detects speeder

    }
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startVideoRecording() {
        val name = "VID_${System.currentTimeMillis()}.mp4"
        val file = File(getOutputDirectory(), name)

        val outputOptions = FileOutputOptions.Builder(file).build()

        val recording = videoCapture.output
            .prepareRecording(this, outputOptions)
            .withAudioEnabled() // Optional
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                if (recordEvent is VideoRecordEvent.Start) {
                    Log.d("CameraX", "Video recording started")
                } else if (recordEvent is VideoRecordEvent.Finalize) {
                    Log.d("CameraX", "Video saved: ${file.absolutePath}")
                }
            }

        // Stop after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            recording.stop()
        }, 5000)
    }
    private fun takePhoto(speed: Float) {
        val photoFile = File(getOutputDirectory(), "IMG_${System.currentTimeMillis()}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            executor, // ✅ Runs on a background thread
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        // Stamp the photo (heavy work is safe here)
                        val stampedFile = stampImageWithText(photoFile, speed.toString())
                        Log.d("Stamping", "Stamped image saved to: ${stampedFile.absolutePath}")

                        // Optional: show success on UI
                       // Handler(Looper.getMainLooper()).post {
                        //    Toast.makeText(this@YourActivityName, "Photo stamped!", Toast.LENGTH_SHORT).show()
                       // }

                    } catch (e: Exception) {
                        Log.e("Stamping", "Failed to stamp image: ${e.message}", e)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraX", "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }
    fun stampImageWithText(
        originalFile: File,
        speed: String
    ): File {
        // Load original image
        val originalBitmap = BitmapFactory.decodeFile(originalFile.absolutePath)
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.RED
            textSize = 128f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(2f, 2f, 2f, Color.BLACK)
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val text = "Speed: $speed km/h\n$timestamp"

        // Draw text on bottom left
        canvas.drawText(text, 40f, mutableBitmap.height - 80f, paint)

        // Overwrite the original file or create a new one
        val stampedFile = File(originalFile.parent, "STAMPED_${originalFile.name}")
        FileOutputStream(stampedFile).use { out ->
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }

        return stampedFile
    }
    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            imageCapture = ImageCapture.Builder().build() // Add this for image capture
            val imageAnalysis = ImageAnalysis.Builder().build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImage(imageProxy)
            }

            cameraProvider.bindToLifecycle(this, cameraSelector, preview,imageCapture, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                resultTextView.text = "Camera permission required!"
            }
        }
    }

    // Process the captured image
    private fun processImage(imageProxy: ImageProxy) {
        // Convert ImageProxy to Bitmap
        val bitmap = imageProxy.toBitmap()

        // Resize the Bitmap to Edge Impulse model size
        // val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        // resizing is done in C++ code

        // Convert the resized bitmap to ByteArray
        val byteArray = getByteArrayFromBitmap(bitmap)

        // Close the imageProxy after processing
        imageProxy.close()

        // Pass to C++ for Edge Impulse inference
        lifecycleScope.launch(Dispatchers.IO) {
            val result = passToCpp(byteArray)
            runOnUiThread {
                displayResults(result)
            }
        }
    }

    // Convert ImageProxy to Bitmap
    private fun ImageProxy.toBitmap(): Bitmap {
        val planes = this.planes
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // Convert Bitmap to ByteArray (RGB888 format)
    private fun getByteArrayFromBitmap(bitmap: Bitmap): ByteArray {

        // Rotate the bitmap by 90 degrees
        val matrix = Matrix()
        matrix.postRotate(90f)

        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        val width = rotatedBitmap.width
        val height = rotatedBitmap.height

        val pixels = IntArray(width * height) // Holds ARGB pixels
        val rgbByteArray = ByteArray(width * height * 3) // Holds RGB888 data

        rotatedBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Convert ARGB to RGB888
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            rgbByteArray[i * 3] = r.toByte()
            rgbByteArray[i * 3 + 1] = g.toByte()
            rgbByteArray[i * 3 + 2] = b.toByte()
        }

        return rgbByteArray
    }

    // Call the C++ function to process the image and return results
    private external fun passToCpp(imageData: ByteArray): InferenceResult?

    // Display results in UI
    @SuppressLint("SetTextI18n")
    private fun displayResults(result: InferenceResult?) {
        resultTextView.visibility = View.GONE
        boundingBoxOverlay.visibility = View.GONE

        if (result == null) {
            resultTextView.text = "Error running inference"
        } else
        {
            val combinedText = StringBuilder()
            if (result.classification != null) {
                // Display classification results
                val classificationText = result.classification.entries.joinToString("\n") {
                    "${it.key}: ${it.value}"
                }
                combinedText.append("Classification:\n$classificationText\n\n")
            }
            if (result.objectDetections != null) {
                // Display object detection results
//                val objectDetectionText = result.objectDetections.joinToString("\n") {
//                    "${it.label}: ${it.confidence}, ${it.x}, ${it.y}, ${it.width}, ${it.height}"
//                }
                // Update bounding boxes on the overlay
                boundingBoxOverlay.visibility = View.VISIBLE
                boundingBoxOverlay.boundingBoxes = result.objectDetections
                //combinedText.append("Object detection:\n$objectDetectionText\n\n")
            }
            if (result.visualAnomalyGridCells != null) {
                // Display visual anomaly grid cells
//                val visualAnomalyGridText = result.visualAnomalyGridCells.joinToString("\n") {
//                    "${it.label}: ${it.confidence}, ${it.x}, ${it.y}, ${it.width}, ${it.height}"
//                }
                val visualAnomalyMax = result.anomalyResult?.getValue("max")
                val visualAnomalyMean = result.anomalyResult?.getValue("mean")
                boundingBoxOverlay.visibility = View.VISIBLE
                boundingBoxOverlay.boundingBoxes = result.visualAnomalyGridCells
                resultTextView.visibility = View.VISIBLE
                combinedText.append("Visual anomaly values:\nMean: ${visualAnomalyMean}\nMax: ${visualAnomalyMax}")
                //combinedText.append("Visual anomalies:\n$visualAnomalyGridText\n\nVisual anomaly values:\nMean: ${visualAnomalyMean}\nMax: ${visualAnomalyMax}\n\n")
            }
            if (result.anomalyResult?.get("anomaly") != null) {
                // Display anomaly detection score
                val anomalyScore = result.anomalyResult.get("anomaly")
                combinedText.append("Anomaly score:\n${anomalyScore}")
            }
            // print the result
            val textToDisplay = combinedText.toString()
            //Log.d("MainActivity", "Result: $textToDisplay")
            resultTextView.text = textToDisplay
        }
    }

    // Load the native library
    init {
        System.loadLibrary("test_camera")
    }

    private fun prepareUsbDeviceListeners(){

        val intent = Intent(ACTION_USB_PERMISSION)
        intent.setPackage(packageName)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter,RECEIVER_EXPORTED)

        val attachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        val detachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbReceiver, attachFilter, RECEIVER_EXPORTED)
        registerReceiver(usbReceiver, detachFilter, RECEIVER_EXPORTED)

        //end of register receiver
        val manager = getSystemService(USB_SERVICE) as UsbManager

        val deviceList = manager.deviceList
        if (deviceList.isEmpty()) {
            errorTextView.visibility=View.VISIBLE
            errorTextView.text = "No USB device found"
            return
        }
    }

    private fun onPermission (driver: UsbSerialDriver, connection: UsbDeviceConnection){
        val port: UsbSerialPort =
        driver.getPorts().get(0) // Most devices have just one port (port 0)

        try {
            port.open(connection)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        try {
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        val listener: SerialInputOutputManager.Listener = object :SerialInputOutputManager.Listener {
            override fun onNewData(newData: ByteArray?) {
                if (!updateScheduled) {
                    val latestValue = newData!!.toString(Charsets.UTF_8)

                    if (!isDigitOrMinus(latestValue.first())) {
                        return
                    }

                    updateScheduled = true//TODO check again timing

                    uiHandler.post({
                        Log.d("Throttler post delayed", latestValue)

                        speedTextView.text = latestValue

                        val speed = latestValue.toFloatOrNull()
                        val now = System.currentTimeMillis()

                        if (speed != null && abs(speed) > 5f && now - lastCaptureTime > 5000) {
                            lastCaptureTime = now
                            takePhoto(speed)
                        }

                        updateScheduled = false
                    })
                }
            }
            override fun onRunError(e: Exception?) {
            }
            fun isDigitOrMinus(char: Char): Boolean {
                return char.isDigit() || char == '-'
            }
        }
        val usbIoManager = SerialInputOutputManager(port, listener)
        usbIoManager.start()
    }
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }
}