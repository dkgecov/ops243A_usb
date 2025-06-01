package bg.getsovd.vehicle_detection.model

import bg.getsovd.vehicle_detection.usb.exceptions.InvalidSpeedUnitException

enum class SpeedUnit(val symbol: String) {
    KPH("km/h"),
    MPH("mph"),
    MPS("m/s");

    companion object {
        fun fromResponse(response: String): SpeedUnit {
            return when {
                response.contains("km-per-hr", ignoreCase = true) -> KPH
                response.contains("mph", ignoreCase = true) -> MPH
                response.contains("m-per-sec", ignoreCase = true) -> MPS
                else -> throw InvalidSpeedUnitException("Unrecognized unit in response: $response")
            }
        }
    }
}