package bg.getsovd.vehicle_detection.usb

import android.util.Log
import bg.getsovd.vehicle_detection.processing.SensorDataConsumer
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CompletableDeferred
import java.lang.Exception
import java.util.concurrent.atomic.AtomicReference

object UsbDataDispatcher : SerialInputOutputManager.Listener  {
    private var incomingBuffer = StringBuilder()
    private val consumers = mutableListOf<SensorDataConsumer>()

    fun registerConsumer(consumer: SensorDataConsumer) {
        if (consumers.none { it::class == consumer::class }) {
            consumers.add(consumer)
        }
    }
    fun unregisterConsumer(consumer: SensorDataConsumer) {
        consumers -= consumer
    }

    @Synchronized
    override fun onNewData(newData: ByteArray?) {// TODO Synchronize
        Log.d("ThreadCheck", "Running on thread: ${Thread.currentThread().id}")
        if (newData == null) return;
        val latestValue = String(newData, Charsets.UTF_8)

        if (latestValue.endsWith("\n")) {//TODO later can be modified to handle multiple \n
            Log.d("myLog", "buffering...$latestValue")
            incomingBuffer.append(latestValue)
            Log.d("myLog", "dispatching buffer: $incomingBuffer")
            dispatchLine(incomingBuffer.toString())//TODO blocking, async ?
            incomingBuffer.clear()
        } else {
            Log.d("myLog", "buffering...$latestValue")
            incomingBuffer.append(latestValue)
        }
    }

    private fun dispatchLine(line: String) {
        consumers.forEach {
            if (it.isDataSuitable(line)) {
                it.handleNewData(line)
            }
        }
    }

    override fun onRunError(e: Exception?) {
        Log.e("myLog",e.toString())
    }
}