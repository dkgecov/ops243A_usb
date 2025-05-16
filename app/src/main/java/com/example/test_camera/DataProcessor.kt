package com.example.test_camera

import android.hardware.usb.UsbDeviceConnection
import android.os.Handler
import android.util.Log
import android.widget.TextView
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.File
import java.io.IOException
import kotlin.math.abs

class DataProcessor(
    private val uiHandler: Handler,
    private val speedTextView: TextView,
    private val imageProcessor: ImageProcessor
) {
    @Volatile
    private var updateScheduled = false
    private var lastCaptureTime = 0L
    private fun process (driver: UsbSerialDriver, connection: UsbDeviceConnection){
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
        val listener: SerialInputOutputManager.Listener = object : SerialInputOutputManager.Listener {
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
                          //  imageProcessor.takePhoto(speed)
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

}