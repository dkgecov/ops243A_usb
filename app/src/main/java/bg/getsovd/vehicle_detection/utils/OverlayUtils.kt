package bg.getsovd.vehicle_detection.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OverlayUtils {


    fun buildOverlay(speed: Float?): String {
        val location = TrackingData.currentLocation
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val locationText = if (location != null) {
            "Lat: %.5f, Lng: %.5f".format(location.latitude, location.longitude)
        } else {
            "Location: N/A"
        }
        val unitSymbol = TrackingData.currentSpeedUnits?.symbol ?: ""
        val speedToDisplay = speed?:0f
        return "Time: $time\nSpeed: %.1f %s\n$locationText".format(speedToDisplay, unitSymbol)
    }
}