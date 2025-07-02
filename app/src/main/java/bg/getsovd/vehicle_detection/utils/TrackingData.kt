package bg.getsovd.vehicle_detection.utils

import android.location.Location
import bg.getsovd.vehicle_detection.model.SpeedUnit

object TrackingData {
    var currentLocation: Location? = null
    var currentSpeedUnits:SpeedUnit?=null
}