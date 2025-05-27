package bg.getsovd.vehicle_detection.usb

import android.util.Log
import bg.getsovd.vehicle_detection.processing.SensorDataHandler
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager

class SerialPortListenerManager(
    private val port: UsbSerialPort,
    private val sensorDataHandler: SensorDataHandler
) {
    private var usbIoManager: SerialInputOutputManager? = null

    fun start() {
        val listener = object : SerialInputOutputManager.Listener {
            override fun onNewData(data: ByteArray?) {
                sensorDataHandler.handleNewData(data)
            }

            override fun onRunError(e: Exception?) {
                Log.e("SerialPortListener", "Run error", e)
            }
        }

        usbIoManager = SerialInputOutputManager(port, listener).apply {
            start()
        }
    }

    fun stop() {
        usbIoManager?.stop()
        usbIoManager = null
    }
}