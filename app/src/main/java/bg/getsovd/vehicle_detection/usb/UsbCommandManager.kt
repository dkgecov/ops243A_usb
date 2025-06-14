package bg.getsovd.vehicle_detection.usb

import android.service.controls.ControlsProviderService.TAG
import android.util.Log
import bg.getsovd.vehicle_detection.processing.SensorDataConsumer
import bg.getsovd.vehicle_detection.usb.exceptions.NoDeviceResponseException
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.IOException

object UsbCommandManager : SensorDataConsumer{
    private var waitingForResponse = false
    private var pendingResponse: CompletableDeferred<String>? = null
    private val commandMutex = Mutex()

    suspend fun sendCommand(command:String, port:UsbSerialPort):String {
        return commandMutex.withLock {
            val request = command.toByteArray()
            // Prepare for incoming response
            val responseDeferred = CompletableDeferred<String>()
            pendingResponse = responseDeferred

            port.write(request, 200)
            waitingForResponse = true
            try {
                // Wait up to 300ms for the response
                val response = withTimeout(300) {
                    responseDeferred.await()
                }
                return response
            } finally {
                pendingResponse = null // Clear on success or failure
                waitingForResponse = false
            }
        }
    }
    override fun handleNewData(line: String?) {
        if (line != null && pendingResponse?.isCompleted == false) {
            pendingResponse?.complete(line)
        }
    }

    override fun isDataSuitable(line: String): Boolean {
        return waitingForResponse;
    }
}