package bg.getsovd.vehicle_detection.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
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
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
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
import bg.getsovd.vehicle_detection.config.AppConfig.CAMERA_STARTUP_LATENCY_MS
import bg.getsovd.vehicle_detection.config.AppConfig.DEFAULT_OBJECTS_ANGLE
import bg.getsovd.vehicle_detection.config.AppConfig.DEFAULT_TRIGGER_SPEED
import bg.getsovd.vehicle_detection.config.AppConfig.OVERLAY_UPDATES_INTERVAL_MS
import bg.getsovd.vehicle_detection.config.AppConfig.VIDEO_DURATION_MS
import bg.getsovd.vehicle_detection.databinding.ActivityMainBinding
import bg.getsovd.vehicle_detection.model.SpeedUnit
import bg.getsovd.vehicle_detection.processing.SpeedDataHandler
import bg.getsovd.vehicle_detection.ui.options.TriggeringSpeedActivity
import bg.getsovd.vehicle_detection.usb.UsbDeviceInitializer
import bg.getsovd.vehicle_detection.usb.UsbSerialPortService
import bg.getsovd.vehicle_detection.model.BoundingBoxOverlay
import bg.getsovd.vehicle_detection.model.InferenceResult
import bg.getsovd.vehicle_detection.model.MessageType
import bg.getsovd.vehicle_detection.ui.options.CosineError
import bg.getsovd.vehicle_detection.ui.options.SpeedUnitsActivity
import bg.getsovd.vehicle_detection.usb.UsbCommandManager
import bg.getsovd.vehicle_detection.usb.UsbDataDispatcher
import bg.getsovd.vehicle_detection.usb.exceptions.InvalidSpeedUnitException
import bg.getsovd.vehicle_detection.usb.exceptions.NoDeviceResponseException
import bg.getsovd.vehicle_detection.utils.AppConstants
import bg.getsovd.vehicle_detection.utils.AppConstants.COSINE_ERROR
import bg.getsovd.vehicle_detection.utils.AppConstants.OPTION_COSINE_ERROR
import bg.getsovd.vehicle_detection.utils.AppConstants.OPTION_TRIGGER_SPEED
import bg.getsovd.vehicle_detection.utils.AppConstants.OPTION_TYPE
import bg.getsovd.vehicle_detection.utils.AppConstants.OPTION_UNITS
import bg.getsovd.vehicle_detection.utils.AppConstants.SELECTED_OBJECTS_ANGLE
import bg.getsovd.vehicle_detection.utils.AppConstants.SELECTED_TRIGGER_SPEED
import bg.getsovd.vehicle_detection.utils.AppConstants.SELECTED_UNITS
import bg.getsovd.vehicle_detection.utils.TrackingData
import bg.getsovd.vehicle_detection.utils.MessageDisplayer
import bg.getsovd.vehicle_detection.utils.OverlayUtils
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.common.collect.EvictingQueue
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Queue
import kotlin.math.abs


private const val CHECK_UNITS_COMMAND = "U?"

class MainActivity : ComponentActivity() {
    private var overlayUpdateJob: Job? = null
    private var triggerSpeed = DEFAULT_TRIGGER_SPEED
    private var objectsAngle = 0F
    private lateinit var recentSpeedData: Queue<Float>
    private lateinit var optionsLauncher: ActivityResultLauncher<Intent>
    private val uiHandler: Handler = Handler(Looper.getMainLooper())
    private  lateinit var messageDisplayer:MessageDisplayer
    private lateinit var cameraServiceImpl: CameraServiceImpl
    private  val CAMERA_PERMISSION_REQUEST_CODE = 1001
    private val RECORD_AUDIO_REQUEST_CODE = 101//TODO const?
    @Volatile
    private lateinit var binding: ActivityMainBinding// TODO volatile?
    private lateinit var resultTextView: TextView
    private lateinit var speedTextView: TextView
    private lateinit var overlayTextView: TextView
    private lateinit var previewView: PreviewView
    private lateinit var infoTextView: TextView
    private lateinit var boundingBoxOverlay: BoundingBoxOverlay
    private var ACTION_USB_PERMISSION: String = "bg.getsovd.vehicle_detection.USB_PERMISSION"
    private var serialManager:SerialInputOutputManager?=null
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            // Optionally disable location features or explain why permission is needed
        }
    }

    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager

            when (action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.d("usbActions", "USB was attached")
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null && !manager.hasPermission(device)) {
                        val permissionIntent = PendingIntent.getBroadcast(
                            context, 0,
                            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                            PendingIntent.FLAG_MUTABLE
                        )
                        manager.requestPermission(device, permissionIntent)
                    }
                    messageDisplayer.showMessage("USB device attached.",MessageType.SUCCESS,2000)
                }

                ACTION_USB_PERMISSION -> {

                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    if (permissionGranted && device != null) {
                        Log.d("usbActions", "USB permission granted")
                        onPermission(manager, device)
                    } else {
                        Log.d(TAG, "Permission denied for device: $device")
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.d("usbActions", "USB was detached")
                    messageDisplayer.showMessage("USB device detached!",MessageType.WARNING)
                    Log.d(TAG, "USB device detached")
                    UsbSerialPortService.close()
                    serialManager?.stop()
                    serialManager = null
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
        infoTextView=findViewById(R.id.infoTextView)
        overlayTextView=findViewById(R.id.overlayTextView)
        infoTextView.visibility=View.GONE
        messageDisplayer= MessageDisplayer(infoTextView,uiHandler)
        val optionsButton = findViewById<Button>(R.id.optionsButton)
        val sharedPref = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        triggerSpeed = sharedPref.getFloat("TRIGGER_SPEED", DEFAULT_TRIGGER_SPEED)// if no shared pref use the AppConfig object
        objectsAngle = sharedPref.getFloat("OBJECTS_ANGLE", DEFAULT_OBJECTS_ANGLE)

        checkAndRequestLocationPermission()
        // register launcher
        optionsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val optionType = data?.getStringExtra(OPTION_TYPE)

                when (optionType) {
                    OPTION_TRIGGER_SPEED -> {
                        val selectedSpeed = data.getFloatExtra(SELECTED_TRIGGER_SPEED, DEFAULT_TRIGGER_SPEED)
                        triggerSpeed = selectedSpeed
                    }
                    OPTION_COSINE_ERROR ->{
                        val selectedObjectsAngle = data.getFloatExtra(SELECTED_OBJECTS_ANGLE, DEFAULT_OBJECTS_ANGLE)
                        objectsAngle = selectedObjectsAngle
                    }
                   OPTION_UNITS -> {
                        val newUnits = data.getStringExtra(SELECTED_UNITS)
                        TrackingData.currentSpeedUnits = SpeedUnit.entries.find { it.symbol == newUnits }!!//TODO check for mismatch if blank returned
                    }
                    // add more cases if needed
                }
            }
        }

// set options button listener and launching activities
        optionsButton.setOnClickListener { //TODO extract somewhere as build meny or kind of
            val popupMenu = PopupMenu(this, optionsButton, Gravity.END)
            popupMenu.menuInflater.inflate(R.menu.options_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.threshold -> {
                        val intent = Intent(this, TriggeringSpeedActivity::class.java)
                        intent.putExtra(OPTION_TYPE, OPTION_TRIGGER_SPEED)
                        intent.putExtra(AppConstants.DEFAULT_TRIGGER_SPEED, triggerSpeed)
                        optionsLauncher.launch(intent)
                        true
                    }
                    R.id.speed_units -> {
                        // Handle units
                        val intent = Intent(this, SpeedUnitsActivity::class.java)
                        intent.putExtra(OPTION_TYPE,OPTION_UNITS)
                        optionsLauncher.launch(intent)
                        true
                    }
                    R.id.power_save_mode -> {
                        Handler(Looper.getMainLooper()).postDelayed({
                            showBlackoutOverlay()
                            reduceBrightness()
                        }, 200)
                        true
                    }
                    R.id.cosine_error_correction -> {
                        val intent = Intent(this, CosineError::class.java)
                        intent.putExtra(OPTION_TYPE, COSINE_ERROR)
                        intent.putExtra(AppConstants.DEFAULT_OBJECTS_ANGLE, objectsAngle)
                        optionsLauncher.launch(intent)
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
            startOverlayUpdates();
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {//TODO can this method be used for other permission granted? deprecated
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cameraServiceImpl.startCamera(previewView)
                requestAudioPermission()
            } else {
                messageDisplayer.showMessage("Camera permission required!",MessageType.WARNING,3000)
            }
        }
        else if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            Log.d("myLog", "AUDIO was requested")
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                // All good
                Log.d("myLog", "AUDIO was approved")
            } else {
                messageDisplayer.showMessage("Audio will not be recorded!",MessageType.WARNING,3000)
                Log.d("myLog", "AUDIO was denied")
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
    private fun onPermission(usbManager: UsbManager, usbDevice: UsbDevice) {

        CoroutineScope(SupervisorJob() + Dispatchers.IO)
        .launch {
                val speedHandler = SpeedDataHandler(
                    uiHandler = Handler(Looper.getMainLooper()),
                    onSpeedUpdate = { speedTextView.text = it },
                    shouldCapture = { speed ->
                        abs(speed) > triggerSpeed
                    },
                    onCapture = {
                        Log.d("capturetimeStart", System.nanoTime().toString())
                        cameraServiceImpl.startRecording(hasAudioPermission(), recentSpeedData)
                    },
                    { objectsAngle}
                )

                UsbDataDispatcher.registerConsumer(speedHandler)
                UsbDataDispatcher.registerConsumer(UsbCommandManager)
            try {
                val port = UsbSerialPortService.initializePort(usbDevice, usbManager)
                UsbSerialPortService.flushStalePortData()
                val manager = SerialInputOutputManager(port, UsbDataDispatcher)// use local variable to avoid null issues with shared one
                serialManager = manager
                manager.start()
            } catch (e: Exception) {
                Log.e("myLog", "Error initializing port or starting listener", e)
            }

            Log.d("myLog", "will sync units")
            try {
                val response = UsbCommandManager. sendCommand(CHECK_UNITS_COMMAND, UsbSerialPortService.getSerialPort())// calls suspend function here
                Log.d("myLog", "response: $response")
                val currentSpeedUnit = SpeedUnit.fromResponse(response)
                TrackingData.currentSpeedUnits = currentSpeedUnit
                messageDisplayer.showMessage(
                    "Retrieved device default speed units: ${currentSpeedUnit.symbol}",
                    MessageType.INFO,
                    5000
                )
            } catch (e: NoDeviceResponseException) {
                Log.e("myLog", "Error retrieving device default speed units, no device response", e)

                messageDisplayer.showMessage(
                    "Failed to retrieve device speed units. " +
                            "This can lead to improper behaviour. Refer to the device user manual to check default units reporting",
                    MessageType.WARNING
                )
            } catch (e: InvalidSpeedUnitException) {
                Log.e("myLog", "Error retrieving speed units from response", e)
                messageDisplayer.showMessage(
                    "Failed to retrieve device speed units. " +
                            "This can lead to improper behaviour. Refer to the device user manual to check default units reporting",
                    MessageType.WARNING
                )
            }
            catch (e: IllegalStateException) {
                Log.e("myLog", "Serial port not available", e)
                messageDisplayer.showMessage(
                    "USB connection lost before retrieving device speed units.",
                    MessageType.WARNING
                )
                return@launch
            }

           // val runtime = Runtime.getRuntime()
            //val usedMemory = runtime.totalMemory() - runtime.freeMemory()
           // println("Used memory: $usedMemory bytes")
           // messageDisplayer.showMessage(usedMemory.toString(),MessageType.WARNING,5000)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        cameraServiceImpl.close() // ✅ Clean up executor
        UsbSerialPortService.close()//✅ close port and connection
    }
    private fun requestAudioPermission() {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_REQUEST_CODE
            )
    }
    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkConnectedDevices() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            messageDisplayer.showMessage("USB device not found!",MessageType.INFO)
        }
    }

    private fun requestLocationPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            AlertDialog.Builder(this)
                .setTitle("Location permission required")
                .setMessage("Location is used to show speed and coordinates on recorded videos.")
                .setPositiveButton("Grant") { _, _ ->
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            600_000L // 10 minutes
        ).setMinUpdateIntervalMillis(600_000L).build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                TrackingData.currentLocation = result.lastLocation
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }
    @SuppressLint("MissingPermission")
    private fun checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        } else {
            requestLocationPermission()
        }
    }
    fun startOverlayUpdates() {
        require(OVERLAY_UPDATES_INTERVAL_MS > 0) { "Overlay update interval must be greater than 0" }
        overlayUpdateJob = CoroutineScope(Dispatchers.Default).launch {
            recentSpeedData = EvictingQueue.create(((VIDEO_DURATION_MS+CAMERA_STARTUP_LATENCY_MS)/OVERLAY_UPDATES_INTERVAL_MS).toInt())
            while (isActive) {
                val speed = extractSpeedFromText(speedTextView.text.toString())
                val overlayText = OverlayUtils.buildOverlay(speed)
                withContext(Dispatchers.Main) {
                    overlayTextView.text = overlayText
                }
                recentSpeedData.add(speed)
                delay(OVERLAY_UPDATES_INTERVAL_MS)//TODO beware uf UI thread overload, no throttling
            }
        }
    }
    private fun extractSpeedFromText(text: String): Float {
        val regex = Regex("""-?\d+(\.\d+)?""")
        val match = regex.find(text)
        return match?.value?.toFloatOrNull() ?: 0f
    }

    private fun showBlackoutOverlay() {
        val blackoutView = findViewById<View>(R.id.blackout_overlay)
        blackoutView.visibility = View.VISIBLE
        // Hide system bars
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}
        window.attributes = window.attributes.apply {
            screenBrightness = 0f
        }
        // Dim screen
        blackoutView.setOnClickListener {
            blackoutView.visibility = View.GONE
            blackoutView.setOnTouchListener(null)
            window.insetsController?.show(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        }
    }

    private fun reduceBrightness(){
        val layoutParams = window.attributes
        layoutParams.screenBrightness = 0f  // 0f = dimmest
        window.attributes = layoutParams

    }


}