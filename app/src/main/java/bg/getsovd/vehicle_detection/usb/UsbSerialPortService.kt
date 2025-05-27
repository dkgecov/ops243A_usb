package bg.getsovd.vehicle_detection.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

class UsbSerialPortService(private val usbManager: UsbManager) {

    fun initializePort(device: UsbDevice): UsbSerialPort {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: throw IllegalStateException("No suitable USB driver found for device.")

        val connection = usbManager.openDevice(device)
            ?: throw IllegalStateException("Could not open USB device connection.")

        val port = driver.ports.firstOrNull()
            ?: throw IllegalStateException("No USB serial ports found.")

        try {
            port.open(connection)
            port.setParameters(
                115200,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
        } catch (e: IOException) {
            throw RuntimeException("Failed to initialize serial port", e)
        }

        return port
    }
}