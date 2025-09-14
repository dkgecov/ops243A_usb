package bg.getsovd.vehicle_detection.processing

import android.os.Handler
import android.util.Log
import bg.getsovd.vehicle_detection.usb.UsbDataParsers
import bg.getsovd.vehicle_detection.utils.TrackingData
import kotlin.math.cos

private const val CR = 13.toByte()  // Carriage Return
private const val LF = 10.toByte()  // Line Feed
class SpeedDataHandler (
    private val uiHandler: Handler,
    private val onSpeedUpdate: (String) -> Unit,
    private val shouldCapture: (Float) -> Boolean,
    private val onCapture: (Float) -> Unit,
    private val getObjectsAngle: () -> Float
) : SensorDataConsumer {
    @Volatile
    private var updateScheduled = false

    @Synchronized // two threads can enter it because the coroutines can be scheduled on different threads
    override fun handleNewData(line: String?) {
        if (!updateScheduled) {
            if (line != null) {
                processLine(line)
            }
        }
    }

    override fun isDataSuitable(line: String): Boolean {
        return UsbDataParsers.isLikelySpeedReport(line)
    }

    private fun processLine(line: String) {
        Log.d("angle", getObjectsAngle().toString())
        updateScheduled = true
        Log.d("myLog", "processing line: " + line)

        val measuredSpeed = extractFirstNumber(line);
        if(measuredSpeed==null){
            Log.w("SpeedCalculation", "Speed parse failed from USB data: $line")
            uiHandler.post {
                onSpeedUpdate("N/A")
                updateScheduled = false
            }
            return
        }
        val realSpeed = ((applyCosineError(measuredSpeed,getObjectsAngle()) * 100).toInt() / 100f)
        Log.d("angle","measured"+measuredSpeed+"..."+"real:"+realSpeed)
        val displayText = buildString {
            append(realSpeed)
            append(' ')
            append(TrackingData.currentSpeedUnits?.symbol ?: "")
        }
        uiHandler.post {
            onSpeedUpdate(displayText)
            updateScheduled = false
        }
        if (shouldCapture(realSpeed)) {
            onCapture(realSpeed)
        }
    }

    private fun extractFirstNumber(input: String): Float? {
        val sb = StringBuilder()
        var numberStarted = false

        for (char in input) {
            if (!numberStarted) {
                if (char == '-' || char.isDigit()) {
                    sb.append(char)
                    numberStarted = true
                }
            } else {
                if (char.isDigit() || char == '.') {
                    sb.append(char)
                } else {
                    break
                }
            }
        }

        return sb.toString().toFloatOrNull()
    }

private fun applyCosineError(measuredSpeed:Float, objectsAngle: Float): Double {
    val realSpeed = measuredSpeed/cos(Math.toRadians(objectsAngle.toDouble()))

    return realSpeed;
}
}