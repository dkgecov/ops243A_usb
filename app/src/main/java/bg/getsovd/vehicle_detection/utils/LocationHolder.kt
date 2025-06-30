package bg.getsovd.vehicle_detection.utils

import android.location.Location

object LocationHolder {
    var currentLocation: Location? = null
    val currentSpeedKmh: Float
        get() = currentLocation?.speed?.times(3.6f) ?: 0f
}