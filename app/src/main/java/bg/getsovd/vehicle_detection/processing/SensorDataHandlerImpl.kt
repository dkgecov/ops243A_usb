package bg.getsovd.vehicle_detection.processing

import android.os.Handler
import android.util.Log

private const val CR = 13.toByte()  // Carriage Return
private const val LF = 10.toByte()  // Line Feed
class SensorDataHandlerImpl (//TODO rename to ...HandlerSerialPort
    private val uiHandler: Handler,
    private val onSpeedUpdate: (String) -> Unit,
    private val shouldCapture: (Float) -> Boolean,
    private val onCapture: (Float) -> Unit,
) : SensorDataHandler {
    @Volatile
    private var updateScheduled = false
    private var incomingBuffer  = StringBuilder()

    @Synchronized
    override fun handleNewData(newData: ByteArray?) {//TODO Wifi case may not accept ByteArray
        if(newData == null) return;
        val latestValue = String(newData, Charsets.UTF_8)
        Log.e("myLog","latest value: "+latestValue +" END")
        if (!updateScheduled) {
            if (latestValue.endsWith("\n")) {
                processLine(latestValue)
            } else {
                Log.e("myLog", "buffering..." + latestValue)
                incomingBuffer.append(latestValue)
            }
        }
    }

    private fun processLine(latestValue: String) {
        updateScheduled = true
        incomingBuffer.append(latestValue)
        Log.e("myLog", "buffer:" + incomingBuffer.toString())
        val fullLine = incomingBuffer.toString()
        uiHandler.post({
            onSpeedUpdate(fullLine)
            updateScheduled = false
            incomingBuffer.clear()
        })
        val speed = extractFirstNumber(fullLine);
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