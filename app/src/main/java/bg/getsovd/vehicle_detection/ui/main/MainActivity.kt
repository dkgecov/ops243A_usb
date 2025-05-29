package bg.getsovd.vehicle_detection.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.controls.ControlsProviderService.TAG
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import bg.getsovd.vehicle_detection.R
import bg.getsovd.vehicle_detection.camera.CameraServiceImpl
import bg.getsovd.vehicle_detection.databinding.ActivityMainBinding
import bg.getsovd.vehicle_detection.model.SpeedUnit
import bg.getsovd.vehicle_detection.processing.SensorDataHandlerImpl
import bg.getsovd.vehicle_detection.ui.options.TriggeringSpeedActivity
import bg.getsovd.vehicle_detection.usb.SerialPortListenerManager
import bg.getsovd.vehicle_detection.usb.UsbDeviceInitializer
import bg.getsovd.vehicle_detection.usb.UsbSerialPortService
import bg.getsovd.vehicle_detection.model.BoundingBoxOverlay
import bg.getsovd.vehicle_detection.model.InferenceResult

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val captureInterval = 5000

private const val defaultTriggerSpeed = 60f

class MainActivity : ComponentActivity() {
    private var triggerSpeed = defaultTriggerSpeed
    private lateinit var optionsLauncher: ActivityResultLauncher<Intent>
    private var lastCaptureTime = 0L
    private val uiHandler: Handler = Handler(Looper.getMainLooper())
    private lateinit var cameraServiceImpl: CameraServiceImpl
    private  val CAMERA_PERMISSION_REQUEST_CODE = 1001
    private val RECORD_AUDIO_REQUEST_CODE = 101
    @Volatile
    private lateinit var binding: ActivityMainBinding// TODO volatile?
    private lateinit var resultTextView: TextView
    private lateinit var speedTextView: TextView
    private lateinit var previewView: PreviewView
    private lateinit var infoTextView: TextView
    private lateinit var boundingBoxOverlay: BoundingBoxOverlay
    private var ACTION_USB_PERMISSION: String = "bg.getsovd.vehicle_detection.USB_PERMISSION"
    private var currentUnit: SpeedUnit = SpeedUnit.KPH

    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager

            when (action) {
                ACTION_USB_PERMISSION -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    if (permissionGranted && device != null) {
                        uiHandler.postDelayed({infoTextView.visibility=View.GONE},1000)
                        onPermission(manager, device)
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
                    infoTextView.visibility=View.VISIBLE
                    infoTextView.text="USB device attached"
                    infoTextView.setTextColor(Color.GREEN)
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    infoTextView.visibility=View.VISIBLE
                    infoTextView.text="USB device detached"
                    infoTextView.setTextColor(Color.YELLOW)
                    Log.d(TAG, "USB device detached")
                    // Optional: Clean up resources here
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        resultTextView = findViewById(R.id.resultTextView) // Result TextView
        previewView = findViewById(R.id.previewView) // Camera preview view
        boundingBoxOverlay = findViewById(R.id.boundingBoxOverlay) // overlay for boxes / visual ad
        speedTextView = findViewById(R.id.speed_field)
        infoTextView=findViewById(R.id.errrorTextView)
        infoTextView.visibility=View.GONE
        val optionsButton = findViewById<Button>(R.id.optionsButton)
        val sharedPref = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        triggerSpeed = sharedPref.getFloat("TRIGGER_SPEED", defaultTriggerSpeed)


        // register launcher
        optionsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val optionType = data?.getStringExtra(TriggeringSpeedActivity.OPTION_TYPE)

                when (optionType) {
                    TriggeringSpeedActivity.OPTION_TRIGGER_SPEED -> {
                        val selectedSpeed = data.getFloatExtra(TriggeringSpeedActivity.SELECTED_TRIGGER_SPEED, 70f)
                        triggerSpeed = selectedSpeed
                    }
                    TriggeringSpeedActivity.OPTION_UNITS -> {
                        val newUnits = data.getStringExtra(TriggeringSpeedActivity.RESULT_UNITS)
                        // handle units
                    }
                    // add more cases if needed
                }
            }
        }

// set options button listener
        optionsButton.setOnClickListener { //TODO extract somewhere as build meny or kind of
            val popupMenu = PopupMenu(this, optionsButton, Gravity.END)
            popupMenu.menuInflater.inflate(R.menu.options_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.option_1 -> {
                        val intent = Intent(this, TriggeringSpeedActivity::class.java)
                        intent.putExtra(TriggeringSpeedActivity.OPTION_TYPE, TriggeringSpeedActivity.OPTION_TRIGGER_SPEED)
                        intent.putExtra(TriggeringSpeedActivity.DEFAULT_TRIGGER_SPEED, triggerSpeed)
                        optionsLauncher.launch(intent)
                        true
                    }
                    R.id.option_2 -> {
                        // Handle units
                        val intent = Intent(this, TriggeringSpeedActivity::class.java)
                        intent.putExtra(TriggeringSpeedActivity.OPTION_TYPE, TriggeringSpeedActivity.OPTION_UNITS)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }

        // Set overlay size to match PreviewView
        previewView.post {// TODO not needed for now as AI model is disabled
            boundingBoxOverlay.layoutParams = boundingBoxOverlay.layoutParams.apply {
                width = previewView.width
                height = previewView.height
            }
        }

        cameraServiceImpl = CameraServiceImpl(
            context = this,             // MainActivity is a Context
            lifecycleOwner = this       // MainActivity is also a LifecycleOwner
        )
        if (!hasCameraPermission()) {
            requestCameraPermission()
        } else {
            // check if audio permission is still not granted after camera was granted
            if(!hasAudioPermission()){
                requestAudioPermission()
            }
            // Start camera
            cameraServiceImpl.startCamera(previewView)
        }

        val usbDeviceInitializer = UsbDeviceInitializer(
            context = this,
            usbReceiver = usbReceiver,
        )
        usbDeviceInitializer.registerReceivers(ACTION_USB_PERMISSION)
        checkConnectedDevices();
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {//TODO can this method be used for other permission granted?
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cameraServiceImpl.startCamera(previewView)
                requestAudioPermission()
            } else {
                infoTextView.text = "Camera permission required!"//TODO use only info textVIew
                infoTextView.visibility=View.VISIBLE
                infoTextView.setTextColor(Color.YELLOW)//TODO extract function for set tetx and gone
                uiHandler.postDelayed({infoTextView.visibility=View.GONE},2000)
            }
        }
        else if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                // All good
            } else {
                infoTextView.visibility=View.VISIBLE
                infoTextView.text="No audio recording"
                infoTextView.setTextColor(Color.YELLOW)//TODO extract function for set tetx and gone
                uiHandler.postDelayed({infoTextView.visibility=View.GONE},2000)
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
    //TODO check with original inference project
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
//TODO
    // Load the native library T
    init {
      //  System.loadLibrary("test_camera")
    }

    @SuppressLint("MissingPermission")
    private fun onPermission(usbManager:UsbManager, usbDevice: UsbDevice){
        Thread {
            try {
                val sensorHandler = SensorDataHandlerImpl(
                    uiHandler = Handler(Looper.getMainLooper()),
                    onSpeedUpdate = { speedTextView.text = it },
                    shouldCapture = { speed ->
                        abs(speed) > triggerSpeed && System.currentTimeMillis() - lastCaptureTime > captureInterval
                    },
                    onCapture = { speed ->
                        lastCaptureTime = System.currentTimeMillis()
                        cameraServiceImpl.startRecording(hasAudioPermission())}

                )

                val usbSerialPortService = UsbSerialPortService(usbManager)
                val port = usbSerialPortService.initializePort(usbDevice) // now safely on background thread
                val listenerManager = SerialPortListenerManager(port, sensorHandler)

                listenerManager.start()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing port or starting listener", e)
            }
        }.start()
    }
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        cameraServiceImpl.close() // ✅ Clean up executor
    }
    private fun requestAudioPermission() {
        if (!hasAudioPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_REQUEST_CODE
            )
        }
    }
    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkConnectedDevices() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            infoTextView.visibility = View.VISIBLE
            infoTextView.text = "USB device not found"
        }
    }
}