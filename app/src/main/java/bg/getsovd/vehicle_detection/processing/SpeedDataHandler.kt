package bg.getsovd.vehicle_detection.processing

import android.os.Handler
import android.util.Log
import bg.getsovd.vehicle_detection.usb.UsbDataParsers

private const val CR = 13.toByte()  // Carriage Return
private const val LF = 10.toByte()  // Line Feed
class SpeedDataHandler (
    private val uiHandler: Handler,
    private val onSpeedUpdate: (String) -> Unit,
    private val shouldCapture: (Float) -> Boolean,
    private val onCapture: (Float) -> Unit,
) : SensorDataConsumer {
    @Volatile
    private var updateScheduled = false

    @Synchronized
    override fun handleNewData(line: String?) {//TODO Wifi case may not accept ByteArray
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
        updateScheduled = true
        Log.d("myLog", "processing line: " + line)
        uiHandler.post {
            onSpeedUpdate(line)
            updateScheduled = false
        }
        val speed = extractFirstNumber(line);
        if (speed != null && shouldCapture(speed)) {
            onCapture(speed)
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


}