package bg.getsovd.vehicle_detection.processing

interface SensorDataConsumer {

    fun handleNewData(line: String?)

    fun isDataSuitable(line: String) : Boolean
}