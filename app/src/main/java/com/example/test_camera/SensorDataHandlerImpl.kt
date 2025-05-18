package com.example.test_camera

import android.os.Handler
import com.example.test_camera.interfaces.SensorDataHandler

class SensorDataHandlerImpl (//TODO rename to ...HandlerSerialPort
    private val uiHandler: Handler,
    private val onSpeedUpdate: (String) -> Unit,
    private val shouldTriggerPhoto: (Float) -> Boolean,
    private val onPhotoTrigger: (Float) -> Unit,
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

        if (shouldTriggerPhoto(speed)) {
            onPhotoTrigger(speed)
        }
    }

    private fun isDigitOrMinus(char: Char): Boolean {
        return char.isDigit() || char == '-'
    }
}