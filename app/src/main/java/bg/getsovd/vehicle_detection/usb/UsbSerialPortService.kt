package bg.getsovd.vehicle_detection.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

object UsbSerialPortService {
      private var serialPort: UsbSerialPort? =null//TODO null because of the clear method,may be make private
    private  var connection: UsbDeviceConnection? =null

    fun getSerialPort(): UsbSerialPort {
        return serialPort ?: throw IllegalStateException("Serial port not initialized")
    }
    fun getConnection(): UsbDeviceConnection {
        return connection ?: throw IllegalStateException("Connectiont not initialized")
    }
    fun initializePort(device: UsbDevice,usbManager: UsbManager): UsbSerialPort {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: throw IllegalStateException("No suitable USB driver found for device.")

        val connection = usbManager.openDevice(device)
            ?: throw IllegalStateException("Could not open USB device connection.")

        val port = driver.ports.firstOrNull()//TODO is this first or null ok ?
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


    fun flushStalePortData() {
        val port = serialPort ?: return  // Return immediately if port is not initialized
        val buffer = ByteArray(64)
        try {
            while (port.read(buffer, 100) > 0) {
                // discard incoming bytes
            }
        }  catch (e: IOException) {
            Log.e("myLog", "Flush read failed: ${e.message}")
        }
    }

    fun close() {
        try {
            serialPort?.close()
        } catch (e: Exception) {
            Log.e("UsbSerialPortService", "Error closing serial port", e)
        }
        try{
        connection?.close()}
        catch (e: Exception) {
            Log.e("UsbSerialPortService", "Error closing device connection", e)
        }

        serialPort = null
        connection = null
    }
}