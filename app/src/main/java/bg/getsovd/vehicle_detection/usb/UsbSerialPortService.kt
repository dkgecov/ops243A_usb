package bg.getsovd.vehicle_detection.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

object UsbSerialPortService {
      var serialPort: UsbSerialPort? =null
      var connection: UsbDeviceConnection? =null

    fun initializePort(device: UsbDevice,usbManager: UsbManager): UsbSerialPort {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: throw IllegalStateException("No suitable USB driver found for device.")

        val connection = usbManager.openDevice(device)
            ?: throw IllegalStateException("Could not open USB device connection.")

        val port = driver.ports.firstOrNull()
            ?: throw IllegalStateException("No USB serial ports found.")

        try {
            Log.d("myLog","port will be opened")
            port.open(connection)
            port.setParameters(
                115200,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            Log.d("myLog","port was opened")
        } catch (e: IOException) {
            Log.d("myLog","PORT OPEN FAILED:" + e.cause)
            throw RuntimeException("Failed to initialize serial port", e)//TODO, causes permissions dialog loop
        }
        this.serialPort=port
        this.connection=connection
        return port
    }
    fun close() {
        try {
            serialPort?.close()
        } catch (_: Exception) {

        }
        connection?.close()
        serialPort = null
        connection = null
    }
}