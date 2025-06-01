package bg.getsovd.vehicle_detection.usb

import android.service.controls.ControlsProviderService.TAG
import android.util.Log
import bg.getsovd.vehicle_detection.usb.exceptions.NoDeviceResponseException
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
            Log.e("myLog", "Flush read failed: ${e.message}")//TODO TAG
        }
        val request = command.toByteArray()

        port.write(request, 10)
        val response = ByteArray(32)
        val bytesRead = port.read(response,10)
        if (bytesRead <= 0) {
            throw NoDeviceResponseException("No response received for command: '$command'")
        }
        Log.d("myLog", "bytes read: "+bytesRead)
         return response.copyOf(bytesRead)
    }
}