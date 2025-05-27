package bg.getsovd.vehicle_detection.processing

import android.os.Handler

class SensorDataHandlerImpl (//TODO rename to ...HandlerSerialPort
    private val uiHandler: Handler,
    private val onSpeedUpdate: (String) -> Unit,
    private val shouldCapture: (Float) -> Boolean,
    private val onCapture: (Float) -> Unit,
) : SensorDataHandler {
    @Volatile
    private var updateScheduled = false

    override fun handleNewData(newData: ByteArray?) {//TODO Wifi case may not accept ByteArray
        val latestValue = newData?.toString(Charsets.UTF_8) ?: return

        if (!isDigitOrMinus(latestValue.first())) return

        if (!updateScheduled) {
            updateScheduled = true
            uiHandler.post({
                onSpeedUpdate(latestValue)//TODO, can this be method?
                updateScheduled = false
            })
        }

        val speed = latestValue.toFloatOrNull() ?: return

        if (shouldCapture(speed)) {
            onCapture(speed)
        }
    }

    private fun isDigitOrMinus(char: Char): Boolean {
        return char.isDigit() || char == '-'
    }
}