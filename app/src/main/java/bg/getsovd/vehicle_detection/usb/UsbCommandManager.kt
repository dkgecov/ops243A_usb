package bg.getsovd.vehicle_detection.usb

import android.service.controls.ControlsProviderService.TAG
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.IOException

class UsbCommandManager (){

    fun sendCommand(command:String, port:UsbSerialPort):ByteArray{
        val buffer = ByteArray(64)
        try {
            while (port.read(buffer, 50) > 0) {
                // discard anything available before sending
            }
        } catch (e: IOException) {
            Log.e(TAG, "Flush read failed: ${e.message}")
        }
        val request = command.toByteArray()

        port.write(request, 10)
        val response = ByteArray(90)
        port.read(response,10)
        return response
    }
}