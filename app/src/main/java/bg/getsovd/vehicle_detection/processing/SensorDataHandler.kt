package bg.getsovd.vehicle_detection.processing

interface SensorDataHandler {

    fun handleNewData(newData: ByteArray?)
}